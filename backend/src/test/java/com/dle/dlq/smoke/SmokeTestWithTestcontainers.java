package com.dle.dlq.smoke;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SmokeTestWithTestcontainers {

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("7.6.1");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("dle.dlqPattern", () -> ".*-DLQ$");
    }

    @LocalServerPort
    int port;

    WebTestClient client;

    static final String SUFFIX = UUID.randomUUID().toString().substring(0, 8);
    static final String DLQ_TOPIC = "orders-DLQ";
    static final String REGULAR_TOPIC = "orders-" + SUFFIX;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @BeforeAll
    static void createTopicsAndData() throws Exception {
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(DLQ_TOPIC, 1, (short) 1),
                    new NewTopic(REGULAR_TOPIC, 1, (short) 1))).all().get();
        }

        var props = new Properties();
        props.put("bootstrap.servers", KAFKA.getBootstrapServers());
        props.put("key.serializer", ByteArraySerializer.class.getName());
        props.put("value.serializer", ByteArraySerializer.class.getName());

        try (var producer = new KafkaProducer<byte[], byte[]>(props)) {
            for (int i = 0; i < 3; i++) {
                var rec = new ProducerRecord<byte[], byte[]>(
                        DLQ_TOPIC,
                        null,
                        ("v-" + i).getBytes(StandardCharsets.UTF_8));
                rec.headers().add(new RecordHeader("X-Correlation-Id", ("cid-" + i).getBytes(StandardCharsets.UTF_8)));
                producer.send(rec).get();
            }
        }
    }

    @Test
    void topics_endpoint_lists_only_dlq_topics() {
        var topics = client.get()
                .uri("/api/dlq/topics")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<String>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(topics).isNotNull();
        assertThat(topics).contains(DLQ_TOPIC);
        assertThat(topics).doesNotContain(REGULAR_TOPIC);
    }

    @Test
    void messages_endpoint_returns_recent_messages_from_dlq() {
        var messages = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/dlq/messages")
                        .queryParam("topic", DLQ_TOPIC)
                        .queryParam("limit", 10)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(messages).isNotNull();
        assertThat(messages).hasSizeGreaterThanOrEqualTo(3);

        var first = messages.get(0);
        assertThat(first.get("topic")).isEqualTo(DLQ_TOPIC);

    }
}

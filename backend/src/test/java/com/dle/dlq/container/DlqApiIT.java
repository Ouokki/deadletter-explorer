package com.dle.dlq.container;

import com.dle.dlq.container.KeycloakTc;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class DlqApiIT extends KeycloakTc {

    @LocalServerPort
    int port;

    @Autowired
    private org.springframework.context.ApplicationContext ctx;

    private WebTestClient client(String bearer) {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .defaultHeader("Authorization", "Bearer " + bearer)
                .build();
    }

    @Test
    void endToEnd_readFromDlq() throws Exception {
        String dlqTopic = "orders-DLQ-" + UUID.randomUUID().toString().substring(0, 8);

        // Create topic
        try (var admin = AdminClient.create(Map.of("bootstrap.servers",
                TestContainersBase.KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(dlqTopic, 1, (short) 1))).all().get();
        }

        // Push 3 messages into DLQ
        var props = new Properties();
        props.put("bootstrap.servers", TestContainersBase.KAFKA.getBootstrapServers());
        props.put("key.serializer", ByteArraySerializer.class.getName());
        props.put("value.serializer", ByteArraySerializer.class.getName());
        try (var producer = new KafkaProducer<byte[], byte[]>(props)) {
            for (int i = 0; i < 3; i++) {
                var rec = new org.apache.kafka.clients.producer.ProducerRecord<byte[], byte[]>(
                        dlqTopic, null, ("v-" + i).getBytes(StandardCharsets.UTF_8));
                rec.headers().add(new org.apache.kafka.common.header.internals.RecordHeader(
                        "X-Correlation-Id", ("cid-" + i).getBytes(StandardCharsets.UTF_8)));
                producer.send(rec).get();
            }
        }

        // Get a token with all roles
        String token = tokenFor("alice", "password");

        var res = client(token)
                .get()
                .uri(uriBuilder -> uriBuilder.path("/api/dlq/messages")
                        .queryParam("topic", dlqTopic)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .returnResult()
                .getResponseBody();

        assertThat(res).isNotNull();
        assertThat(res).hasSizeGreaterThanOrEqualTo(3);
        assertThat(res.get(0).get("topic")).isEqualTo(dlqTopic);
    }
}

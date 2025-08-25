package com.dle.dlq.container;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class TestContainersBase {
    @org.testcontainers.junit.jupiter.Container
    public static final org.testcontainers.containers.KafkaContainer KAFKA =
            new org.testcontainers.containers.KafkaContainer(
                    org.testcontainers.utility.DockerImageName.parse("confluentinc/cp-kafka:7.5.4"));

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        r.add("dle.dlqPattern", () -> ".*-DLQ$");
    }
}

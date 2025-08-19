package com.dle.dlq.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MdcRecordInterceptorUnitTest {

    private final MdcRecordInterceptor interceptor = new MdcRecordInterceptor();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void intercept_setsMdc_fromRecord_andCorrelationHeader_and_returnsSameRecord() {
        ConsumerRecord<byte[], byte[]> rec =
                new ConsumerRecord<>("topic-A", 2, 42L, "k".getBytes(StandardCharsets.UTF_8), "v".getBytes(StandardCharsets.UTF_8));
        rec.headers().add(new RecordHeader("X-Correlation-Id", "abc-123".getBytes(StandardCharsets.UTF_8)));

        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> consumer = mock(Consumer.class);

        ConsumerRecord<byte[], byte[]> returned = interceptor.intercept(rec, consumer);

        assertThat(returned).isSameAs(rec);
        assertThat(MDC.get("kafka.topic")).isEqualTo("topic-A");
        assertThat(MDC.get("kafka.partition")).isEqualTo("2");
        assertThat(MDC.get("kafka.offset")).isEqualTo("42");
        assertThat(MDC.get("correlationId")).isEqualTo("abc-123");

        interceptor.afterRecord(rec, (Exception) null);
        assertThat(MDC.get("kafka.topic")).isNull();
        assertThat(MDC.get("kafka.partition")).isNull();
        assertThat(MDC.get("kafka.offset")).isNull();
        assertThat(MDC.get("correlationId")).isEqualTo("abc-123");
    }

    @Test
    void intercept_withoutCorrelationHeader_doesNotSetCorrelationId() {
        ConsumerRecord<byte[], byte[]> rec =
                new ConsumerRecord<>("topic-Z", 0, 7L, null, "v".getBytes(StandardCharsets.UTF_8));

        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> consumer = mock(Consumer.class);

        interceptor.intercept(rec, consumer);

        assertThat(MDC.get("kafka.topic")).isEqualTo("topic-Z");
        assertThat(MDC.get("kafka.partition")).isEqualTo("0");
        assertThat(MDC.get("kafka.offset")).isEqualTo("7");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void afterRecord_also_clearsKeys_whenExceptionIsPresent() {
        MDC.put("kafka.topic", "t");
        MDC.put("kafka.partition", "1");
        MDC.put("kafka.offset", "9");
        MDC.put("correlationId", "keep-me");

        ConsumerRecord<byte[], byte[]> rec =
                new ConsumerRecord<>("t", 1, 9L, null, null);

        interceptor.afterRecord(rec, new RuntimeException("boom"));

        assertThat(MDC.get("kafka.topic")).isNull();
        assertThat(MDC.get("kafka.partition")).isNull();
        assertThat(MDC.get("kafka.offset")).isNull();
        assertThat(MDC.get("correlationId")).isEqualTo("keep-me");
    }

    @Test
    void intercept_doesNotModifyHeaders() {
        ConsumerRecord<byte[], byte[]> rec =
                new ConsumerRecord<>("t", 0, 1L, null, null);
        rec.headers().add(new RecordHeader("X-Correlation-Id", "cid".getBytes(StandardCharsets.UTF_8)));

        int beforeSize = rec.headers().toArray().length;

        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> consumer = mock(Consumer.class);
        interceptor.intercept(rec, consumer);

        int afterSize = rec.headers().toArray().length;
        assertThat(afterSize).isEqualTo(beforeSize);
    }
}

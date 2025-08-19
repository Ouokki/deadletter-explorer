package com.dle.dlq.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class MdcRecordInterceptor implements RecordInterceptor<byte[], byte[]> {

    @Override
    public ConsumerRecord<byte[], byte[]> intercept(ConsumerRecord<byte[], byte[]> record,
            Consumer<byte[], byte[]> consumer) {
        // Add Kafka metadata to MDC
        MDC.put("kafka.topic", record.topic());
        MDC.put("kafka.partition", String.valueOf(record.partition()));
        MDC.put("kafka.offset", String.valueOf(record.offset()));

        // Optionally propagate correlationId from headers
        Header corr = record.headers().lastHeader("X-Correlation-Id");
        if (corr != null) {
            MDC.put("correlationId", new String(corr.value(), StandardCharsets.UTF_8));
        }
        return record;
    }

    public void afterRecord(ConsumerRecord<byte[], byte[]> record, Exception ex) {
        // Clean up MDC after processing
        MDC.remove("kafka.topic");
        MDC.remove("kafka.partition");
        MDC.remove("kafka.offset");
    }

}

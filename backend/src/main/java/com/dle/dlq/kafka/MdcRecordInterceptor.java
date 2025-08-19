package com.dle.dlq.kafka;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MdcRecordInterceptor implements RecordInterceptor<byte[], byte[]> {

    @Override
    public ConsumerRecord<byte[], byte[]> intercept(ConsumerRecord<byte[], byte[]> record,
                                                    Consumer<byte[], byte[]> consumer) {
        MDC.put("kafka.topic", record.topic());
        MDC.put("kafka.partition", String.valueOf(record.partition()));
        MDC.put("kafka.offset", String.valueOf(record.offset()));
        log.debug("MDC set: topic='{}', partition={}, offset={}", record.topic(), record.partition(), record.offset());

        Header corr = record.headers().lastHeader("X-Correlation-Id");
        if (corr != null) {
            String cid = new String(corr.value(), StandardCharsets.UTF_8);
            MDC.put("correlationId", cid);
            log.debug("Propagated correlationId from header X-Correlation-Id: {}", cid);
        } else {
            log.debug("No X-Correlation-Id header present; leaving MDC correlationId as-is");
        }
        return record;
    }

    public void afterRecord(ConsumerRecord<byte[], byte[]> record, Exception ex) {
        if (ex != null) {
            log.error("Listener processing failed for topic='{}', partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), ex);
        } else {
            log.debug("Listener processed record successfully for topic='{}', partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
        }

        MDC.remove("kafka.topic");
        MDC.remove("kafka.partition");
        MDC.remove("kafka.offset");
        log.trace("MDC cleared: topic, partition, offset (correlationId left untouched)");
    }

}

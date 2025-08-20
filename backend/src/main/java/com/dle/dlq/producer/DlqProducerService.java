package com.dle.dlq.producer;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.dle.dlq.dto.ReplayItem;
import com.dle.dlq.dto.ReplayRequest;
import com.dle.dlq.util.MessageMapper;

@Slf4j
@Service
public class DlqProducerService {

    private final KafkaTemplate<byte[], byte[]> template;

    @Value("${dle.replay.throttlePerSec:50}")
    int throttlePerSec;

    private final Set<String> allow = new HashSet<>();

    public DlqProducerService(
            @Value("${dle.replay.headerAllowList:content-type,correlation-id}") String allowList,
            KafkaTemplate<byte[], byte[]> template) {
        this.template = template;
        Arrays.stream(allowList.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(allow::add);

        log.info("DlqProducerService initialized: defaultThrottlePerSec={}, headerAllowList={}",
                throttlePerSec, allow);
    }

    /**
     * Replays the provided items to targetTopic with throttling and header allow-list.
     * Returns the number of successfully sent records (blocking send).
     */
    public int replay(ReplayRequest req) throws Exception { // keep checked throws, or narrow to InterruptedException
        Objects.requireNonNull(req.targetTopic(), "targetTopic required");

        final int requestedTps = Optional.ofNullable(req.throttlePerSec()).orElse(throttlePerSec);
        final int effectiveTps = Math.max(1, Math.min(10_000, requestedTps));
        final long intervalMs = Math.max(1, 1000L / effectiveTps);

        if (req.items() == null || req.items().isEmpty()) {
            log.info("Replay requested with empty items for targetTopic='{}' (nothing to send)", req.targetTopic());
            return 0;
        }

        log.info("Starting replay: targetTopic='{}', items={}, requestedTps={}, effectiveTps={}, intervalMs={}",
                req.targetTopic(), req.items().size(), requestedTps, effectiveTps, intervalMs);

        int sent = 0;
        final long startNanos = System.nanoTime();

        try {
            for (ReplayItem it : req.items()) {
                byte[] value = null;
                if (it.valueBase64() != null) {
                    try {
                        value = Base64.getDecoder().decode(it.valueBase64());
                    } catch (IllegalArgumentException bad64) {
                        log.warn("Skipping item due to invalid Base64 payload (offset={} partition={}): {}",
                                it.offset(), it.partition(), bad64.toString());
                        continue;
                    }
                } else {
                    log.debug("Replay item has null payload (offset={} partition={})", it.offset(), it.partition());
                }

                Map<String, Object> filtered = MessageMapper.filterAllowed(it.headersBase64(), allow);

                Message<byte[]> msg = MessageBuilder.withPayload(value)
                        .setHeader(KafkaHeaders.TOPIC, req.targetTopic())
                        .copyHeaders(filtered)
                        .build();

                try {
                    // This can throw InterruptedException or ExecutionException
                    template.send(msg).get();
                    sent++;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); // restore flag
                    log.warn("Replay interrupted during send; sentSoFar={}", sent, ie);
                    throw ie; // propagate (preferred)
                } catch (ExecutionException ee) {
                    // Sending failed but we keep going with the next item
                    log.error("Failed to send item to topic='{}' (offset={}, partition={})",
                            req.targetTopic(), it.offset(), it.partition(), ee.getCause() != null ? ee.getCause() : ee);
                } catch (RuntimeException re) {
                    // Synchronous Kafka client/runtime error
                    log.error("Failed to send item to topic='{}' (offset={}, partition={})",
                            req.targetTopic(), it.offset(), it.partition(), re);
                }

                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Replay interrupted while throttling; sentSoFar={}", sent, ie);
                    throw ie; // propagate (preferred)
                }
            }
        } finally {
            long tookMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("Replay finished: targetTopic='{}', sent={}, totalItems={}, tookMs={}",
                    req.targetTopic(), sent, req.items().size(), tookMs);
        }

        return sent;
    }
}

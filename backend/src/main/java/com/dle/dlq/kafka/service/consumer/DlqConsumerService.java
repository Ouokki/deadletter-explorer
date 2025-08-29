package com.dle.dlq.kafka.service.consumer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.Setter;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Service;

import com.dle.dlq.dto.MessageDto;
import com.dle.dlq.util.MessageMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqConsumerService {

    private final ConsumerFactory<byte[], byte[]> cf;

    @Getter
    @Setter
    @Value("${dle.fetchDefault:200}")
    int fetchDefault;

    /**
     * Fetch the last N records from each partition of the given topic (bounded to 5k).
     */
    public List<MessageDto> fetchLastN(String topic, Integer limit) {
        if (topic == null || topic.isBlank()) {
            log.warn("fetchLastN called with empty topic");
            throw new IllegalArgumentException("topic must not be null/blank");
        }

        final int requested = (limit == null ? fetchDefault : limit);
        final int n = (requested <= 0) ? fetchDefault : Math.min(5000, requested);

        final long startNanos = System.nanoTime();
        log.info("Fetching last N messages: topic='{}', requestedLimit={}, effectiveLimit={}", topic, requested, n);

        try (var consumer = cf.createConsumer("dle-reader-" + UUID.randomUUID(), null)) {
            var partitionsInfo = consumer.partitionsFor(topic);
            if (partitionsInfo == null || partitionsInfo.isEmpty()) {
                log.info("No partitions found for topic='{}' (does the topic exist?)", topic);
                return List.of();
            }

            var partitions = partitionsInfo.stream()
                    .map(i -> new TopicPartition(topic, i.partition()))
                    .toList();

            log.info("Assigning {} partitions for topic='{}'", partitions.size(), topic);
            consumer.assign(partitions);

            var end = consumer.endOffsets(partitions);
            var begin = consumer.beginningOffsets(partitions);

            // Seek to max(begin, end - n) per partition
            partitions.forEach(tp -> {
                long seekOffset = Math.max(begin.get(tp), end.get(tp) - n);
                consumer.seek(tp, seekOffset);
                if (log.isDebugEnabled()) {
                    log.debug("Partition {}: begin={}, end={}, seek={}", tp.partition(), begin.get(tp), end.get(tp), seekOffset);
                }
            });

            var out = new ArrayList<MessageDto>();
            long deadline = System.currentTimeMillis() + 1500;
            int pollIters = 0;

            while (System.currentTimeMillis() < deadline && out.size() < (long) n * partitions.size()) {
                var records = consumer.poll(Duration.ofMillis(100));
                pollIters++;
                records.forEach(rec -> out.add(MessageMapper.toDto(rec)));
                if (log.isDebugEnabled()) {
                    log.debug("Polled {} records (accumulated={})", records.count(), out.size());
                }
            }

            out.sort(Comparator.comparingLong(MessageDto::offset).reversed());

            long tookMs = (System.nanoTime() - startNanos) / 1_000_000;
            boolean hitDeadline = System.currentTimeMillis() >= deadline;
            log.info(
                    "Fetched {} messages from topic='{}' across {} partitions in {} ms (pollIters={}, hitDeadline={})",
                    out.size(), topic, partitions.size(), tookMs, pollIters, hitDeadline
            );

            if (out.isEmpty()) {
                log.debug("No messages returned for topic='{}' with limit={}", topic, n);
            } else if (log.isDebugEnabled()) {
                var maxOffset = out.stream().mapToLong(MessageDto::offset).max().orElse(-1);
                var minOffset = out.stream().mapToLong(MessageDto::offset).min().orElse(-1);
                log.debug("Result offsets range: minOffset={}, maxOffset={}", minOffset, maxOffset);
            }

            return out;
        } catch (Exception e) {
            log.error("Failed to fetch last N messages for topic='{}' (requested={}, effective={})", topic, requested, n, e);
            throw e;
        }
    }
}

package com.dle.dlq.consumer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Service;

import com.dle.dlq.dto.MessageDto;
import com.dle.dlq.util.MessageMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DlqConsumerService {

    private final ConsumerFactory<byte[], byte[]> cf;
    @Value("${dle.fetchDefault:200}")
    int fetchDefault;

    public List<MessageDto> fetchLastN(String topic, Integer limit) {
        int n = (limit == null || limit <= 0) ? fetchDefault : Math.min(5000, limit);
        try (var consumer = cf.createConsumer("dle-reader-" + UUID.randomUUID(), null)) {
            var partitions = consumer.partitionsFor(topic).stream()
                    .map(i -> new TopicPartition(topic, i.partition())).toList();
            consumer.assign(partitions);
            var end = consumer.endOffsets(partitions);
            var begin = consumer.beginningOffsets(partitions);
            partitions.forEach(tp -> consumer.seek(tp, Math.max(begin.get(tp), end.get(tp) - n)));

            var out = new ArrayList<MessageDto>();
            long deadline = System.currentTimeMillis() + 1500;
            while (System.currentTimeMillis() < deadline && out.size() < n * partitions.size()) {
                consumer.poll(Duration.ofMillis(100)).forEach(rec -> out.add(MessageMapper.toDto(rec)));
            }
            out.sort(Comparator.comparingLong(MessageDto::offset).reversed());
            return out;
        }
    }

}

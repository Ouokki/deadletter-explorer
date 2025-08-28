package com.dle.dlq.consumer;

import com.dle.dlq.dto.MessageDto;
import com.dle.dlq.kafka.service.consumer.DlqConsumerService;
import com.dle.dlq.util.MessageMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.kafka.core.ConsumerFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

class DlqConsumerServiceUnitTest {

    @Test
    void fetchLastN_acrossTwoPartitions_seeksFromEnd_andSortsDesc() {
        String topic = "deadletters";
        int limit = 3;

        @SuppressWarnings("unchecked")
        ConsumerFactory<byte[], byte[]> cf = mock(ConsumerFactory.class);
        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> consumer = mock(Consumer.class);

        when(cf.createConsumer(anyString(), isNull())).thenReturn(consumer);

        List<PartitionInfo> pis = List.of(
                new PartitionInfo(topic, 0, null, new org.apache.kafka.common.Node[0], new org.apache.kafka.common.Node[0]),
                new PartitionInfo(topic, 1, null, new org.apache.kafka.common.Node[0], new org.apache.kafka.common.Node[0])
        );
        when(consumer.partitionsFor(topic)).thenReturn(pis);

        TopicPartition tp0 = new TopicPartition(topic, 0);
        TopicPartition tp1 = new TopicPartition(topic, 1);

        Map<TopicPartition, Long> begin = Map.of(tp0, 100L, tp1, 200L);
        Map<TopicPartition, Long> end   = Map.of(tp0, 105L, tp1, 205L);

        when(consumer.beginningOffsets(anyCollection())).thenReturn(begin);
        when(consumer.endOffsets(anyCollection())).thenReturn(end);

        ConsumerRecords<byte[], byte[]> polled = records(
                Map.of(
                        tp0, List.of(rec(topic, 0, 102), rec(topic, 0, 103), rec(topic, 0, 104)),
                        tp1, List.of(rec(topic, 1, 202), rec(topic, 1, 203), rec(topic, 1, 204))
                )
        );
        when(consumer.poll(any(Duration.class)))
                .thenReturn(polled)
                .thenReturn(ConsumerRecords.empty());

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.toDto(any())).thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                ConsumerRecord<byte[], byte[]> r = inv.getArgument(0);
                MessageDto dto = mock(MessageDto.class);
                when(dto.offset()).thenReturn(r.offset());
                return dto;
            });

            DlqConsumerService svc = new DlqConsumerService(cf);
            svc.fetchDefault = 200;

            List<MessageDto> out = svc.fetchLastN(topic, limit);

            verify(consumer).assign(argThat(ps -> ps.containsAll(List.of(tp0, tp1))));
            verify(consumer).seek(argThat(tp -> tp.topic().equals(topic) && tp.partition() == 0), eq(102L)); // max(100, 105-3)
            verify(consumer).seek(argThat(tp -> tp.topic().equals(topic) && tp.partition() == 1), eq(202L)); // max(200, 205-3)

            List<Long> offsets = out.stream().map(MessageDto::offset).collect(Collectors.toList());
            assertThat(offsets).containsExactly(204L, 203L, 202L, 104L, 103L, 102L);

            verify(consumer).close();
        }
    }

    @Test
    void fetchLastN_nullLimit_usesFetchDefault_andSeeksFromEndOrBegin() {
        String topic = "deadletters";
        Integer limit = null;
        int fetchDefault = 5;

        @SuppressWarnings("unchecked")
        ConsumerFactory<byte[], byte[]> cf = mock(ConsumerFactory.class);
        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> consumer = mock(Consumer.class);

        when(cf.createConsumer(anyString(), isNull())).thenReturn(consumer);

        List<PartitionInfo> pis = List.of(
                new PartitionInfo(topic, 0, null, new org.apache.kafka.common.Node[0], new org.apache.kafka.common.Node[0])
        );
        when(consumer.partitionsFor(topic)).thenReturn(pis);

        TopicPartition tp0 = new TopicPartition(topic, 0);
        Map<TopicPartition, Long> begin = Map.of(tp0, 40L);
        Map<TopicPartition, Long> end   = Map.of(tp0, 50L);

        when(consumer.beginningOffsets(anyCollection())).thenReturn(begin);
        when(consumer.endOffsets(anyCollection())).thenReturn(end);

        List<ConsumerRecord<byte[], byte[]>> recs = LongStream.range(45, 50)
                .mapToObj(off -> rec(topic, 0, off))
                .toList();
        ConsumerRecords<byte[], byte[]> polled = records(Map.of(tp0, recs));
        when(consumer.poll(any(Duration.class)))
                .thenReturn(polled)
                .thenReturn(ConsumerRecords.empty());

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.toDto(any())).thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                ConsumerRecord<byte[], byte[]> r = inv.getArgument(0);
                MessageDto dto = mock(MessageDto.class);
                when(dto.offset()).thenReturn(r.offset());
                return dto;
            });

            DlqConsumerService svc = new DlqConsumerService(cf);
            svc.fetchDefault = fetchDefault;

            List<MessageDto> out = svc.fetchLastN(topic, limit);

            verify(consumer).seek(argThat(tp -> tp.topic().equals(topic) && tp.partition() == 0), eq(45L)); // max(40, 50-5)
            assertThat(out).hasSize(5);
            assertThat(out.stream().map(MessageDto::offset)).containsExactly(49L, 48L, 47L, 46L, 45L);

            verify(consumer).close();
        }
    }

    @Test
    void fetchLastN_capsLimitAt5000_andSeeksFromEndMinus5000() {
        String topic = "deadletters";
        int requested = 10_000;
        int n = 5_000;

        @SuppressWarnings("unchecked")
        ConsumerFactory<byte[], byte[]> cf = mock(ConsumerFactory.class);
        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> consumer = mock(Consumer.class);

        when(cf.createConsumer(anyString(), isNull())).thenReturn(consumer);

        List<PartitionInfo> pis = List.of(
                new PartitionInfo(topic, 0, null, new org.apache.kafka.common.Node[0], new org.apache.kafka.common.Node[0])
        );
        when(consumer.partitionsFor(topic)).thenReturn(pis);

        TopicPartition tp0 = new TopicPartition(topic, 0);
        long end = 10_100L;
        long seekFrom = end - n; // 5100
        Map<TopicPartition, Long> begin = Map.of(tp0, 0L);
        Map<TopicPartition, Long> endMap = Map.of(tp0, end);

        when(consumer.beginningOffsets(anyCollection())).thenReturn(begin);
        when(consumer.endOffsets(anyCollection())).thenReturn(endMap);

        List<ConsumerRecord<byte[], byte[]>> recs = new ArrayList<>(n);
        for (long off = seekFrom; off < seekFrom + n; off++) {
            recs.add(rec(topic, 0, off));
        }
        ConsumerRecords<byte[], byte[]> polled = records(Map.of(tp0, recs));
        when(consumer.poll(any(Duration.class)))
                .thenReturn(polled)
                .thenReturn(ConsumerRecords.empty());

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.toDto(any())).thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                ConsumerRecord<byte[], byte[]> r = inv.getArgument(0);
                MessageDto dto = mock(MessageDto.class);
                when(dto.offset()).thenReturn(r.offset());
                return dto;
            });

            DlqConsumerService svc = new DlqConsumerService(cf);
            svc.fetchDefault = 200;

            List<MessageDto> out = svc.fetchLastN(topic, requested);

            verify(consumer).seek(argThat(tp -> tp.topic().equals(topic) && tp.partition() == 0), eq(seekFrom));
            assertThat(out).hasSize(n);
            assertThat(out.get(0).offset()).isEqualTo(seekFrom + n - 1);

            verify(consumer).close();
        }
    }

    @Test
    void fetchLastN_throwsOnNullOrBlankTopic() {
        @SuppressWarnings("unchecked")
        ConsumerFactory<byte[], byte[]> cf = mock(ConsumerFactory.class);

        DlqConsumerService svc = new DlqConsumerService(cf);

        assertThatThrownBy(() -> svc.fetchLastN(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> svc.fetchLastN("   ", 1))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(cf);
    }

    @Test
    void fetchLastN_returnsEmpty_whenNoPartitions() {
        @SuppressWarnings("unchecked")
        ConsumerFactory<byte[], byte[]> cf = mock(ConsumerFactory.class);
        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> consumer = mock(Consumer.class);

        when(cf.createConsumer(anyString(), isNull())).thenReturn(consumer);
        when(consumer.partitionsFor("t")).thenReturn(Collections.emptyList());

        DlqConsumerService svc = new DlqConsumerService(cf);
        assertThat(svc.fetchLastN("t", 5)).isEmpty();

        verify(consumer).close();
        verify(consumer, never()).poll(any());
    }

    @Test
    void fetchLastN_requestedZero_usesFetchDefaultZero_exitsImmediately_andLogsSeekDebug() {
        String topic = "t";

        @SuppressWarnings("unchecked")
        ConsumerFactory<byte[], byte[]> cf = mock(ConsumerFactory.class);
        @SuppressWarnings("unchecked")
        Consumer<byte[], byte[]> consumer = mock(Consumer.class);

        when(cf.createConsumer(anyString(), isNull())).thenReturn(consumer);
        List<PartitionInfo> pis = List.of(
                new PartitionInfo(topic, 0, null, new org.apache.kafka.common.Node[0], new org.apache.kafka.common.Node[0])
        );
        when(consumer.partitionsFor(topic)).thenReturn(pis);

        TopicPartition tp0 = new TopicPartition(topic, 0);
        when(consumer.beginningOffsets(anyCollection())).thenReturn(Map.of(tp0, 10L));
        when(consumer.endOffsets(anyCollection())).thenReturn(Map.of(tp0, 25L));

        Logger logger = (Logger) LoggerFactory.getLogger(DlqConsumerService.class);
        Level old = logger.getLevel();
        try {
            logger.setLevel(Level.DEBUG);

            DlqConsumerService svc = new DlqConsumerService(cf);
            svc.fetchDefault = 0;

            List<MessageDto> out = svc.fetchLastN(topic, 0);

            assertThat(out).isEmpty();

            verify(consumer).seek(eq(tp0), eq(25L));
            verify(consumer, never()).poll(any());
        } finally {
            logger.setLevel(old);
        }
    }


    private static ConsumerRecord<byte[], byte[]> rec(String topic, int partition, long offset) {
        return new ConsumerRecord<>(topic, partition, offset, null, null);
    }

    private static ConsumerRecords<byte[], byte[]> records(Map<TopicPartition, List<ConsumerRecord<byte[], byte[]>>> map) {
        return new ConsumerRecords<>(map);
    }
}

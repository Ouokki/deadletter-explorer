package com.dle.dlq;

import com.dle.dlq.dto.MessageDto;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DlqService {

  private final Properties baseConsumerProps;
  private final Properties producerProps;
  private final String bootstrap;
  private final Pattern dlqPattern;
  private final int fetchDefault;
  private final int throttlePerSec;
  private final Set<String> headerAllowList;

  public DlqService(
      @Value("${spring.kafka.bootstrap-servers}") String bootstrap,
      @Value("${dle.dlqPattern:.*-DLQ$}") String dlqPattern,
      @Value("${dle.fetchDefault:200}") int fetchDefault,
      @Value("${dle.replay.throttlePerSec:50}") int throttlePerSec,
      @Value("${dle.replay.headerAllowList:content-type,correlation-id}") String headerAllowList
  ) {
    this.bootstrap = bootstrap;
    this.dlqPattern = Pattern.compile(dlqPattern);
    this.fetchDefault = fetchDefault;
    this.throttlePerSec = throttlePerSec;
    this.headerAllowList = Arrays.stream(headerAllowList.split(","))
        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());

    this.baseConsumerProps = new Properties();
    baseConsumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
    baseConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    baseConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    baseConsumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "dle-reader-" + UUID.randomUUID());
    baseConsumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    baseConsumerProps.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

    this.producerProps = new Properties();
    producerProps.put("bootstrap.servers", bootstrap);
    producerProps.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
    producerProps.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
  }

  public List<String> listDlqTopics() throws Exception {
    try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", bootstrap))) {
      var names = admin.listTopics(new ListTopicsOptions().listInternal(false)).names().get();
      return names.stream().filter(t -> dlqPattern.matcher(t).matches())
          .sorted()
          .toList();
    }
  }

  public List<MessageDto> fetchLastN(String topic, Integer limit) {
    int n = (limit == null || limit <= 0) ? fetchDefault : Math.min(5000, limit);
    Properties props = new Properties();
    props.putAll(baseConsumerProps);
    try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
      var partitions = consumer.partitionsFor(topic).stream()
          .map(info -> new TopicPartition(topic, info.partition()))
          .toList();
      consumer.assign(partitions);

      Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
      Map<TopicPartition, Long> beginOffsets = consumer.beginningOffsets(partitions);

      for (TopicPartition tp : partitions) {
        long end = endOffsets.get(tp);
        long begin = beginOffsets.get(tp);
        long start = Math.max(begin, end - n);
        consumer.seek(tp, start);
      }

      List<MessageDto> out = new ArrayList<>();
      long deadline = System.currentTimeMillis() + 1500;
      while (System.currentTimeMillis() < deadline && out.size() < n * partitions.size()) {
        var polled = consumer.poll(Duration.ofMillis(100));
        for (ConsumerRecord<byte[], byte[]> rec : polled) {
          Map<String, String> hdrs = new LinkedHashMap<>();
          for (Header h : rec.headers()) {
            hdrs.put(h.key(), Base64.getEncoder().encodeToString(h.value()));
          }
          String keyUtf8 = rec.key() != null ? tryUtf8(rec.key()) : null;
          String valueUtf8 = rec.value() != null ? tryUtf8(rec.value()) : null;
          String valueB64 = rec.value() != null ? Base64.getEncoder().encodeToString(rec.value()) : null;
          out.add(new MessageDto(
              rec.topic(), rec.partition(), rec.offset(), rec.timestamp(),
              keyUtf8, valueUtf8, valueB64, hdrs
          ));
        }
      }
      // Sort by offset desc
      out.sort(Comparator.comparingLong(MessageDto::offset).reversed());
      return out;
    }
  }

  private String tryUtf8(byte[] bytes) {
    String s = new String(bytes, StandardCharsets.UTF_8);
    // Basic heuristic: if it contains many control chars, return null
    long ctrls = s.chars().filter(c -> c < 0x09 || (c > 0x0D && c < 0x20)).count();
    return ctrls > 2 ? null : s;
  }

  public record ReplayRequest(String sourceTopic, String targetTopic, List<ReplayItem> items, Integer throttlePerSec,
                              Set<String> headerAllowList) {}
  public record ReplayItem(int partition, long offset, String valueBase64, Map<String, String> headersBase64) {}

  public int replay(ReplayRequest req) throws Exception {
    Objects.requireNonNull(req.targetTopic(), "targetTopic required");
    int tps = Optional.ofNullable(req.throttlePerSec()).orElse(throttlePerSec);
    Set<String> allowed = Optional.ofNullable(req.headerAllowList()).orElse(this.headerAllowList);
    int sent = 0;
    try (var producer = new org.apache.kafka.clients.producer.KafkaProducer<byte[], byte[]>(producerProps)) {
      long intervalMs = Math.max(1, 1000L / Math.max(1, tps));
      for (ReplayItem it : req.items()) {
        byte[] value = it.valueBase64() != null ? Base64.getDecoder().decode(it.valueBase64()) : null;
        var record = new org.apache.kafka.clients.producer.ProducerRecord<byte[], byte[]>(req.targetTopic(), null, value);
        if (it.headersBase64() != null) {
          for (var e : it.headersBase64().entrySet()) {
            if (allowed.contains(e.getKey())) {
              byte[] hv = e.getValue() != null ? Base64.getDecoder().decode(e.getValue()) : null;
              record.headers().add(e.getKey(), hv);
            }
          }
        }
        producer.send(record).get();
        sent++;
        Thread.sleep(intervalMs);
      }
    }
    return sent;
  }
}

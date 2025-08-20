package com.dle.dlq.config;

import java.util.HashMap;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import com.dle.dlq.kafka.MdcRecordInterceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrap;

    @Bean
    public ConsumerFactory<byte[], byte[]> consumerFactory() {
        var props = new HashMap<String, Object>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

        log.info("Kafka ConsumerFactory initialized with bootstrapServers='{}', enableAutoCommit={}, isolationLevel={}",
                bootstrap, props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG), props.get(ConsumerConfig.ISOLATION_LEVEL_CONFIG));

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ProducerFactory<byte[], byte[]> producerFactory() {
        var props = new HashMap<String, Object>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        log.info("Kafka ProducerFactory initialized with bootstrapServers='{}'", bootstrap);

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<byte[], byte[]> kafkaTemplate(ProducerFactory<byte[], byte[]> pf) {
        log.debug("KafkaTemplate bean created");
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<byte[], byte[]> kafkaListenerContainerFactory(
            ConsumerFactory<byte[], byte[]> cf, MdcRecordInterceptor interceptor) {
        var f = new ConcurrentKafkaListenerContainerFactory<byte[], byte[]>();
        f.setConsumerFactory(cf);
        f.setRecordInterceptor(interceptor);

        log.info("KafkaListenerContainerFactory initialized with MDC interceptor");

        return f;
    }

}

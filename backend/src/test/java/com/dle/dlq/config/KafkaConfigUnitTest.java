package com.dle.dlq.config;

import com.dle.dlq.kafka.MdcRecordInterceptor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConfigUnitTest {

    @Test
    void consumerFactory_hasExpectedProperties() {
        var cfg = new KafkaConfig();
        cfg.bootstrap = "localhost:9092";

        ConsumerFactory<byte[], byte[]> cf = cfg.consumerFactory();
        assertThat(cf).isInstanceOf(DefaultKafkaConsumerFactory.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> props = ((DefaultKafkaConsumerFactory<byte[], byte[]>) cf).getConfigurationProperties();
        assertThat(props.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9092");
        assertThat(props.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)).isEqualTo(ByteArrayDeserializer.class);
        assertThat(props.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)).isEqualTo(ByteArrayDeserializer.class);
        assertThat(props.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
        assertThat(props.get(ConsumerConfig.ISOLATION_LEVEL_CONFIG)).isEqualTo("read_committed");
    }

    @Test
    void producerFactory_hasExpectedProperties() {
        var cfg = new KafkaConfig();
        cfg.bootstrap = "brokerA:19092";

        ProducerFactory<byte[], byte[]> pf = cfg.producerFactory();
        assertThat(pf).isInstanceOf(DefaultKafkaProducerFactory.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> props = ((DefaultKafkaProducerFactory<byte[], byte[]>) pf).getConfigurationProperties();
        assertThat(props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("brokerA:19092");
        assertThat(props.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(ByteArraySerializer.class);
        assertThat(props.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(ByteArraySerializer.class);
    }

    @Test
    void kafkaTemplate_usesProvidedProducerFactory() {
        var cfg = new KafkaConfig();
        @SuppressWarnings("unchecked")
        ProducerFactory<byte[], byte[]> pf = mock(ProducerFactory.class);

        KafkaTemplate<byte[], byte[]> template = cfg.kafkaTemplate(pf);
        assertThat(template).isNotNull();
        assertThat(template.getProducerFactory()).isSameAs(pf);
    }

    @Test
    void listenerContainerFactory_setsConsumerFactory_and_RecordInterceptor() {
        var cfg = new KafkaConfig();
        @SuppressWarnings("unchecked")
        ConsumerFactory<byte[], byte[]> cf = mock(ConsumerFactory.class);
        MdcRecordInterceptor interceptor = mock(MdcRecordInterceptor.class);

        ConcurrentKafkaListenerContainerFactory<byte[], byte[]> f =
                cfg.kafkaListenerContainerFactory(cf, interceptor);

        assertThat(f.getConsumerFactory()).isSameAs(cf);
    }
}

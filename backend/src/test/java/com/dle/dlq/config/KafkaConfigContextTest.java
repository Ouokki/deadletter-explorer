package com.dle.dlq.config;

import com.dle.dlq.kafka.MdcRecordInterceptor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = KafkaConfig.class)
@ActiveProfiles("test")
class KafkaConfigContextTest {

    @MockBean
    MdcRecordInterceptor mdcRecordInterceptor;

    @Autowired
    ConsumerFactory<byte[], byte[]> consumerFactory;

    @Autowired
    ProducerFactory<byte[], byte[]> producerFactory;

    @Autowired
    KafkaTemplate<byte[], byte[]> kafkaTemplate;

    @Autowired
    ConcurrentKafkaListenerContainerFactory<byte[], byte[]> listenerFactory;

    @Test
    void beans_load_with_expected_properties() {
        assertThat(consumerFactory).isInstanceOf(DefaultKafkaConsumerFactory.class);
        assertThat(producerFactory).isInstanceOf(DefaultKafkaProducerFactory.class);
        assertThat(kafkaTemplate.getProducerFactory()).isSameAs(producerFactory);
        assertThat(listenerFactory.getConsumerFactory()).isSameAs(consumerFactory);

        @SuppressWarnings("unchecked")
        Map<String, Object> cprops = ((DefaultKafkaConsumerFactory<byte[], byte[]>) consumerFactory).getConfigurationProperties();
        assertThat(cprops.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9092");
        assertThat(cprops.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)).isEqualTo(ByteArrayDeserializer.class);
        assertThat(cprops.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)).isEqualTo(ByteArrayDeserializer.class);
        assertThat(cprops.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)).isEqualTo(false);
        assertThat(cprops.get(ConsumerConfig.ISOLATION_LEVEL_CONFIG)).isEqualTo("read_committed");

        @SuppressWarnings("unchecked")
        Map<String, Object> pprops = ((DefaultKafkaProducerFactory<byte[], byte[]>) producerFactory).getConfigurationProperties();
        assertThat(pprops.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("localhost:9092");
        assertThat(pprops.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(ByteArraySerializer.class);
        assertThat(pprops.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(ByteArraySerializer.class);
    }
}

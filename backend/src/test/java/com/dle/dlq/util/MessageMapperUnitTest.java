package com.dle.dlq.util;

import com.dle.dlq.dto.MessageDto;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class MessageMapperUnitTest {

    @Test
    void toDto_mapsFields_encodesHeadersBase64_and_handlesUtf8_andValueB64() {
        byte[] k = "key-utf8".getBytes(StandardCharsets.UTF_8);
        byte[] v = "value-utf8".getBytes(StandardCharsets.UTF_8);
        ConsumerRecord<byte[], byte[]> rec = new ConsumerRecord<>("topicA", 1, 42L, k, v);
        rec.headers().add(new RecordHeader("h1", "abc".getBytes(StandardCharsets.UTF_8)));
        rec.headers().add(new RecordHeader("h2", new byte[]{0x01, 0x02}));

        MessageDto dto = MessageMapper.toDto(rec);

        assertThat(dto.topic()).isEqualTo("topicA");
        assertThat(dto.partition()).isEqualTo(1);
        assertThat(dto.offset()).isEqualTo(42L);
        assertThat(dto.keyUtf8()).isEqualTo("key-utf8");
        assertThat(dto.valueUtf8()).isEqualTo("value-utf8");

        var expected = new LinkedHashMap<String, String>();
        expected.put("h1", Base64.getEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8)));
        expected.put("h2", Base64.getEncoder().encodeToString(new byte[]{0x01, 0x02}));

        assertThat(dto.headers()).containsExactlyEntriesOf(expected);
        assertThat(new java.util.ArrayList<>(dto.headers().keySet())).containsExactly("h1", "h2");
        assertThat(dto.headers().keySet().stream().toList()).containsExactly("h1", "h2");
    }

    @Test
    void toDto_setsUtf8FieldsToNull_whenTooManyControlChars() {
        byte[] noisy = new byte[]{0x01, 0x02, 0x03, 0x04, 'A'};
        ConsumerRecord<byte[], byte[]> rec = new ConsumerRecord<>("t", 0, 0L, noisy, noisy);

        MessageDto dto = MessageMapper.toDto(rec);

        assertThat(dto.keyUtf8()).isNull();
        assertThat(dto.valueUtf8()).isNull();
    }

    @Test
    void toDto_handlesNullKeyAndValue() {
        ConsumerRecord<byte[], byte[]> rec = new ConsumerRecord<>("t", 0, 7L, null, null);

        MessageDto dto = MessageMapper.toDto(rec);

        assertThat(dto.keyUtf8()).isNull();
        assertThat(dto.valueUtf8()).isNull();
    }

    @Test
    void filterAllowed_decodesOnlyAllowedHeaders() {
        Map<String, String> base64 = Map.of(
                "content-type", Base64.getEncoder().encodeToString("application/json".getBytes(StandardCharsets.UTF_8)),
                "ignored", Base64.getEncoder().encodeToString("nope".getBytes(StandardCharsets.UTF_8)),
                "correlation-id", Base64.getEncoder().encodeToString("cid-123".getBytes(StandardCharsets.UTF_8))
        );
        Set<String> allow = Set.of("content-type", "correlation-id");

        Map<String, Object> out = MessageMapper.filterAllowed(base64, allow);

        assertThat(out).hasSize(2);
        assertThat(new String((byte[]) out.get("content-type"), StandardCharsets.UTF_8)).isEqualTo("application/json");
        assertThat(new String((byte[]) out.get("correlation-id"), StandardCharsets.UTF_8)).isEqualTo("cid-123");
        assertThat(out).doesNotContainKey("ignored");
    }

    @Test
    void filterAllowed_returnsEmptyMap_whenInputIsNull_orNoMatches() {
        assertThat(MessageMapper.filterAllowed(null, Set.of("a"))).isEmpty();

        Map<String, String> base64 = Map.of("x", Base64.getEncoder().encodeToString("y".getBytes(StandardCharsets.UTF_8)));
        assertThat(MessageMapper.filterAllowed(base64, Set.of("a", "b"))).isEmpty();
    }

    @Test
    void toDto_withMidRangeControlChars_validWhenAtMostTwo() {
        byte[] k = new byte[] { 'A', 0x10, 'B' };
        byte[] v = new byte[] { 'X', 0x10, 'Y' };

        ConsumerRecord<byte[], byte[]> rec = new ConsumerRecord<>("t", 0, 1L, k, v);

        MessageDto dto = MessageMapper.toDto(rec);

        assertThat(dto.keyUtf8()).isNotNull();
        assertThat(dto.valueUtf8()).isNotNull();

        assertThat(dto.keyUtf8().charAt(1)).isEqualTo((char) 0x10);
        assertThat(dto.valueUtf8().charAt(1)).isEqualTo((char) 0x10);
    }


}

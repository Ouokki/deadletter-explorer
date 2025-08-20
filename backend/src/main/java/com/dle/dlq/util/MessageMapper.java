package com.dle.dlq.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.dle.dlq.dto.MessageDto;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor
public final class MessageMapper {

    public static MessageDto toDto(ConsumerRecord<byte[], byte[]> rec) {
        Map<String, String> hdrs = new LinkedHashMap<>();
        rec.headers().forEach(h -> hdrs.put(h.key(), Base64.getEncoder().encodeToString(h.value())));

        String keyUtf8 = tryUtf8(rec.key());
        String valueUtf8 = tryUtf8(rec.value());
        String valueB64 = rec.value() != null ? Base64.getEncoder().encodeToString(rec.value()) : null;

        log.debug("Mapping record to DTO: topic='{}', partition={}, offset={}, timestamp={}, keyUtf8Present={}, valueUtf8Present={}, headers={}",
                rec.topic(), rec.partition(), rec.offset(), rec.timestamp(),
                keyUtf8 != null, valueUtf8 != null, hdrs.keySet());

        return new MessageDto(
                rec.topic(), rec.partition(), rec.offset(), rec.timestamp(),
                keyUtf8, valueUtf8, valueB64, hdrs
        );
    }

    public static Map<String, Object> filterAllowed(Map<String, String> base64, Set<String> allow) {
        var out = new HashMap<String, Object>();
        if (base64 == null) {
            log.debug("filterAllowed called with null base64 map -> returning empty");
            return out;
        }

        base64.forEach((k, v) -> {
            if (allow.contains(k)) {
                out.put(k, Base64.getDecoder().decode(v));
                log.trace("Header '{}' allowed and preserved", k);
            } else {
                log.trace("Header '{}' filtered out (not in allow-list)", k);
            }
        });

        log.debug("filterAllowed result: kept {} of {} headers (allowList={})",
                out.size(), base64.size(), allow);

        return out;
    }

    private static String tryUtf8(byte[] bytes) {
        if (bytes == null) return null;

        String s = new String(bytes, StandardCharsets.UTF_8);
        long ctrls = s.chars().filter(c -> c < 0x09 || (c > 0x0D && c < 0x20)).count();

        boolean valid = ctrls <= 2;
        if (log.isTraceEnabled()) {
            log.trace("tryUtf8: decoded string='{}' (validUtf8={})", s, valid);
        }
        return valid ? s : null;
    }
}

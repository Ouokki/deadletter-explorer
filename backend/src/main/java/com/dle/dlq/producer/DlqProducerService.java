package com.dle.dlq.producer;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import com.dle.dlq.dto.ReplayItem;
import com.dle.dlq.dto.ReplayRequest;
import com.dle.dlq.util.MessageMapper;

import lombok.RequiredArgsConstructor;

@Service
public class DlqProducerService {

    private final KafkaTemplate<byte[], byte[]> template;
    @Value("${dle.replay.throttlePerSec:50}")
    int throttlePerSec;
    private final Set<String> allow = new HashSet<>();

    public DlqProducerService(@Value("${dle.replay.headerAllowList:content-type,correlation-id}") String allowList,
            KafkaTemplate<byte[], byte[]> template) {
        this.template = template;
        Arrays.stream(allowList.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(allow::add);
    }

    public int replay(ReplayRequest req) throws Exception {
        Objects.requireNonNull(req.targetTopic(), "targetTopic required");
        int tps = Optional.ofNullable(req.throttlePerSec()).orElse(throttlePerSec);
        long intervalMs = Math.max(1, 1000L / Math.max(1, tps));
        int sent = 0;

        for (ReplayItem it : req.items()) {
            byte[] v = it.valueBase64() != null ? Base64.getDecoder().decode(it.valueBase64()) : null;
            var msg = MessageBuilder.withPayload(v)
                    .setHeader(KafkaHeaders.TOPIC, req.targetTopic())
                    .copyHeaders(MessageMapper.filterAllowed(it.headersBase64(), allow))
                    .build();
            template.send(msg).get();
            sent++;
            Thread.sleep(intervalMs);
        }
        return sent;
    }
}

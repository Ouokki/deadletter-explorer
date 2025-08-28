package com.dle.dlq.web;

import com.dle.dlq.admin.DlqAdminService;
import com.dle.dlq.kafka.service.consumer.DlqConsumerService;
import com.dle.dlq.dto.MessageDto;
import com.dle.dlq.model.ReplayRequest;
import com.dle.dlq.kafka.service.producer.DlqProducerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DlqController {

    private final DlqAdminService admin;
    private final DlqConsumerService consumer;
    private final DlqProducerService producer;

    @GetMapping("/topics")
    public List<String> topics() throws Exception {
        log.info("GET /api/dlq/topics called");
        List<String> topics = admin.listDlqTopics();
        log.info("Returning {} DLQ topics", topics.size());
        return topics;
    }

    @GetMapping(value = "/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<MessageDto> messages(@RequestParam String topic, @RequestParam(required = false) Integer limit) {
        log.info("GET /api/dlq/messages called for topic='{}', limit={}", topic, limit);
        List<MessageDto> msgs = consumer.fetchLastN(topic, limit);
        log.info("Returning {} messages for topic='{}'", msgs.size(), topic);
        return msgs;
    }

    @PostMapping("/replay")
    public int replay(@RequestBody ReplayRequest req) throws Exception {
        log.info("POST /api/dlq/replay called: targetTopic='{}', items={}, throttlePerSec={}",
                req.targetTopic(),
                req.items() != null ? req.items().size() : 0,
                req.throttlePerSec());
        int sent = producer.replay(req);
        log.info("Replay finished: sent {} messages to targetTopic='{}'", sent, req.targetTopic());
        return sent;
    }
}

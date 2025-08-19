package com.dle.dlq.web;

import com.dle.dlq.admin.DlqAdminService;
import com.dle.dlq.consumer.DlqConsumerService;
import com.dle.dlq.dto.MessageDto;
import com.dle.dlq.dto.ReplayRequest;
import com.dle.dlq.producer.DlqProducerService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DlqController {

  private final DlqAdminService admin;
  private final DlqConsumerService consumer;
  private final DlqProducerService producer;

  @GetMapping("/topics")
  public List<String> topics() throws Exception {
    return admin.listDlqTopics();
  }

  @GetMapping(value = "/messages", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<MessageDto> messages(@RequestParam String topic, @RequestParam(required = false) Integer limit) {
    return consumer.fetchLastN(topic, limit);
  }

  @PostMapping("/replay")
  public int replay(@RequestBody ReplayRequest req) throws Exception {
    return producer.replay(req);
  }
}

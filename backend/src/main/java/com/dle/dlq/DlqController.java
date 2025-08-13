package com.dle.dlq;

import com.dle.dlq.dto.MessageDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dlq")
public class DlqController {

  private final DlqService svc;

  public DlqController(DlqService svc) { this.svc = svc; }

  @GetMapping("/topics")
  public List<String> topics() throws Exception {
    return svc.listDlqTopics();
  }

  @GetMapping(value="/messages", produces = MediaType.APPLICATION_JSON_VALUE)
  public List<MessageDto> messages(@RequestParam String topic,
                                   @RequestParam(required = false) Integer limit) {
    return svc.fetchLastN(topic, limit);
  }

  @PostMapping("/replay")
  public Map<String, Object> replay(@RequestBody DlqService.ReplayRequest req) throws Exception {
    int sent = svc.replay(req);
    return Map.of("status", "ok", "sent", sent, "targetTopic", req.targetTopic());
  }
}

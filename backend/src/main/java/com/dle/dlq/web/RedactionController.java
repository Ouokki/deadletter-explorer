package com.dle.dlq.web;

import com.dle.dlq.model.PolicySaveRequest;
import com.dle.dlq.dto.RuleDto;
import com.dle.dlq.redaction.service.RedactionPolicyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/redaction")
public class RedactionController {

    private final RedactionPolicyService service;

    public RedactionController(RedactionPolicyService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    public List<RuleDto> getRules(@RequestParam(defaultValue = "global") String scope,
            @RequestParam(required = false) String key) {
        return service.getRules(scope, key);
    }

    @PutMapping("/rules")
    public ResponseEntity<Void> saveRules(@RequestBody PolicySaveRequest req) {
        service.saveRules(req.scope(), req.key(), req.rules());
        return ResponseEntity.noContent().build();
    }
}

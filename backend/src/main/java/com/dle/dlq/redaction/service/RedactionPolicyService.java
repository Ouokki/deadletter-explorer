package com.dle.dlq.redaction.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.dle.dlq.dto.RuleDto;
import com.dle.dlq.redaction.PolicyStore;

@Service
public class RedactionPolicyService {
    private final PolicyStore store;

    public RedactionPolicyService(PolicyStore store) {
        this.store = store;
    }

    public List<RuleDto> getRules(String scope, String key) {
        return store.load(scope, key);
    }

    public void saveRules(String scope, String key, List<RuleDto> rules) {
        store.save(scope, key, rules);
    }
}
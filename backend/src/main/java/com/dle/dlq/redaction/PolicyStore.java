package com.dle.dlq.redaction;

import java.util.List;

import com.dle.dlq.dto.RuleDto;

public interface PolicyStore {
    List<RuleDto> load(String scope, String key);

    void save(String scope, String key, List<RuleDto> rules);
}
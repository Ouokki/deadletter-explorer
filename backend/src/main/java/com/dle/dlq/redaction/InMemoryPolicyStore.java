package com.dle.dlq.redaction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.dle.dlq.dto.RuleDto;

public class InMemoryPolicyStore implements PolicyStore {
    private final Map<String, List<RuleDto>> db = new ConcurrentHashMap<>();

    private static String idx(String scope, String key) {
        return (scope == null ? "global" : scope.toLowerCase()) + ":" + (key == null ? "_" : key);
    }

    @Override
    public List<RuleDto> load(String scope, String key) {
        return db.getOrDefault(idx(scope, key), List.of());
    }

    @Override
    public void save(String scope, String key, List<RuleDto> rules) {
        db.put(idx(scope, key), rules == null ? List.of() : List.copyOf(rules));
    }
}

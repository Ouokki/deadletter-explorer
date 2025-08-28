package com.dle.dlq.model;

import com.dle.dlq.dto.RuleDto;

import java.util.List;

public record PolicySaveRequest(String scope, String key, List<RuleDto> rules) {
}
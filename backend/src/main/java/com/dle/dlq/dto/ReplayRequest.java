package com.dle.dlq.dto;

import java.util.List;
import java.util.Set;

public record ReplayRequest(String sourceTopic, String targetTopic, List<ReplayItem> items, Integer throttlePerSec,
        Set<String> headerAllowList) {
}
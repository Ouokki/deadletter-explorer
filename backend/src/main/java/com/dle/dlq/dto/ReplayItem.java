package com.dle.dlq.dto;

import java.util.Map;

public record ReplayItem(int partition, long offset, String valueBase64, Map<String, String> headersBase64) {
}

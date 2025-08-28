package com.dle.dlq.model;

import java.util.Map;

public record ReplayItem(int partition, long offset, String valueBase64, Map<String, String> headersBase64) {
}

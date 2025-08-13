package com.dle.dlq.dto;

import java.util.Map;

public record MessageDto(
    String topic,
    int partition,
    long offset,
    long timestamp,
    String keyUtf8,
    String valueUtf8,
    String valueBase64,
    Map<String, String> headers
) {}

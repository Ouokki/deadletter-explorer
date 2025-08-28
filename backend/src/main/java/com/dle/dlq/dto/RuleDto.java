package com.dle.dlq.dto;

import com.dle.dlq.model.HashOptions;
import com.dle.dlq.model.MaskOptions;
import com.dle.dlq.model.RedactionAction;

public record RuleDto(
        String id,
        String path,
        RedactionAction action,
        MaskOptions mask,
        HashOptions hash,
        Boolean enabled,
        String note) {
}

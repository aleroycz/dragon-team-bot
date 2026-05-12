package com.dragon.dto.moderation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModerationResult(
        boolean violates,
        Severity severity,
        String rule,
        String reason,
        Action action
) {}
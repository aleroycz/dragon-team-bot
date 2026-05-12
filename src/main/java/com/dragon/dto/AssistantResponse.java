package com.dragon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssistantResponse(
        Type type,
        String message
) {
    public enum Type {
        NORMAL,
        RULES,
        EVENT
    }
}
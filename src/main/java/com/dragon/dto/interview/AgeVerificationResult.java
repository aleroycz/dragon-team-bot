package com.dragon.dto.interview;

public record AgeVerificationResult(
        boolean confident,
        boolean likelyAdult,
        ConfidenceLevel confidence,
        String reason,
        String followUpQuestion
) {
    public enum ConfidenceLevel {
        LOW, MEDIUM, HIGH
    }
}
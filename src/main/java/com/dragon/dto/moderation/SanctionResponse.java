package com.dragon.dto.moderation;

import java.time.Instant;

public record SanctionResponse(
        Long id,
        String targetUserTag,
        String moderatorUserTag,
        SanctionType type,
        SanctionStatus status,
        String reason,
        Long durationSeconds,
        Instant expiresAt,
        Instant createdAt,
        boolean permanent
) {}
package com.dragon.dto.moderation;

public record ModerationLogEntry(
        String messageId,
        String userId,
        String username,
        String channelId,
        String channelName,
        String content,
        ModerationResult result
) {}
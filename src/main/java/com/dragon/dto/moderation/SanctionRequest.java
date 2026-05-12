package com.dragon.dto.moderation;


/**
 * Carries all information needed to issue a sanction.
 *
 * @param targetUserId      Discord user ID of the member being sanctioned
 * @param targetUserTag     Discord tag of the member being sanctioned
 * @param moderatorUserId   Discord user ID of the staff member issuing the sanction
 * @param moderatorUserTag  Discord tag of the staff member issuing the sanction
 * @param guildId           Discord guild ID
 * @param type              The type of sanction to apply
 * @param reason            The reason for the sanction
 * @param durationSeconds   Duration in seconds, or null for permanent
 */
public record SanctionRequest(
        String targetUserId,
        String targetUserTag,
        String moderatorUserId,
        String moderatorUserTag,
        String guildId,
        SanctionType type,
        String reason,
        Long durationSeconds
) {}

package com.dragon.entity;

import com.dragon.dto.moderation.SanctionStatus;
import com.dragon.dto.moderation.SanctionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "sanctions", indexes = {
        @Index(name = "idx_target_user", columnList = "target_user_id"),
        @Index(name = "idx_guild",       columnList = "guild_id"),
        @Index(name = "idx_type",        columnList = "type"),
        @Index(name = "idx_status",      columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanctionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "target_user_id", nullable = false)
    private String targetUserId;

    @Column(name = "target_user_tag", nullable = false)
    private String targetUserTag;

    @Column(name = "moderator_user_id", nullable = false)
    private String moderatorUserId;

    @Column(name = "moderator_user_tag", nullable = false)
    private String moderatorUserTag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SanctionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SanctionStatus status = SanctionStatus.ACTIVE;

    @Column(nullable = false, length = 1000)
    private String reason;

    /** Duration in seconds. Null means permanent. */
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    /** When the sanction expires. Null means permanent. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** When this sanction was revoked, if applicable. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Who revoked this sanction, if applicable. */
    @Column(name = "revoked_by")
    private String revokedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isPermanent() {
        return durationSeconds == null;
    }
}
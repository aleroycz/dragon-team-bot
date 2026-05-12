package com.dragon.entity;

import com.dragon.dto.moderation.SanctionType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "member_sanction_summary", indexes = {
        @Index(name = "idx_summary_user_guild", columnList = "user_id, guild_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberSanctionSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "user_tag", nullable = false)
    private String userTag;

    @Builder.Default
    @Column(name = "total_warns")
    private int totalWarns = 0;

    @Builder.Default
    @Column(name = "total_mutes")
    private int totalMutes = 0;

    @Builder.Default
    @Column(name = "total_timeouts")
    private int totalTimeouts = 0;

    @Builder.Default
    @Column(name = "total_bans")
    private int totalBans = 0;

    @Builder.Default
    @Column(name = "active_warns")
    private int activeWarns = 0;

    public void increment(SanctionType type) {
        switch (type) {
            case WARN    -> { totalWarns++;    activeWarns++; }
            case MUTE    -> totalMutes++;
            case TIMEOUT -> totalTimeouts++;
            case BAN     -> totalBans++;
            default      -> {}
        }
    }

    public void decrementActiveWarns() {
        if (activeWarns > 0) activeWarns--;
    }
}
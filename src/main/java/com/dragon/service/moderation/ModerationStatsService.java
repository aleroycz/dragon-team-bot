package com.dragon.service.moderation;

import com.dragon.dto.moderation.SanctionStatus;
import com.dragon.dto.moderation.SanctionType;
import com.dragon.entity.MemberSanctionSummaryEntity;
import com.dragon.repository.MemberSanctionSummaryRepository;
import com.dragon.repository.SanctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Aggregates server-wide moderation metrics for staff reporting.
 *
 * All heavy queries are delegated to the repository layer so that the logic
 * stays testable outside the JDA interaction context. Commands should consume
 * this service rather than calling repositories directly.
 */
@Service
@RequiredArgsConstructor
public class ModerationStatsService {

    private final SanctionRepository sanctionRepository;
    private final MemberSanctionSummaryRepository summaryRepository;

    /**
     * Computes a snapshot of moderation activity for a guild.
     *
     * @param guildId      The Discord guild snowflake.
     * @param lookbackDays How far back to count "recent" sanctions (typically 30).
     * @return An immutable {@link ServerModerationStats} record.
     */
    public ServerModerationStats getStats(String guildId, int lookbackDays) {
        Instant since = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);

        long activeSanctions   = sanctionRepository.countByGuildIdAndStatus(guildId, SanctionStatus.ACTIVE);
        long totalInPeriod     = sanctionRepository.countByGuildIdAndCreatedAtAfter(guildId, since);
        long warnsInPeriod     = sanctionRepository.countByGuildIdAndTypeAndCreatedAtAfter(guildId, SanctionType.WARN,    since);
        long mutesInPeriod     = sanctionRepository.countByGuildIdAndTypeAndCreatedAtAfter(guildId, SanctionType.MUTE,    since);
        long timeoutsInPeriod  = sanctionRepository.countByGuildIdAndTypeAndCreatedAtAfter(guildId, SanctionType.TIMEOUT, since);
        long bansInPeriod      = sanctionRepository.countByGuildIdAndTypeAndCreatedAtAfter(guildId, SanctionType.BAN,     since);
        List<MemberSanctionSummaryEntity> topOffenders = summaryRepository.findTop5MostSanctionedByGuildId(guildId);

        return new ServerModerationStats(
                activeSanctions,
                totalInPeriod,
                warnsInPeriod,
                mutesInPeriod,
                timeoutsInPeriod,
                bansInPeriod,
                topOffenders,
                lookbackDays
        );
    }

    /**
     * Immutable snapshot of guild-wide moderation statistics.
     *
     * @param activeSanctions  Number of currently active sanctions of any type.
     * @param sanctionsInPeriod Total sanctions issued within the lookback window.
     * @param warnsInPeriod     Warns issued within the lookback window.
     * @param mutesInPeriod     Mutes issued within the lookback window.
     * @param timeoutsInPeriod  Timeouts issued within the lookback window.
     * @param bansInPeriod      Bans issued within the lookback window.
     * @param topOffenders      Up to 5 members with the highest lifetime infraction total.
     * @param lookbackDays      The number of days the period covers.
     */
    public record ServerModerationStats(
            long activeSanctions,
            long sanctionsInPeriod,
            long warnsInPeriod,
            long mutesInPeriod,
            long timeoutsInPeriod,
            long bansInPeriod,
            List<MemberSanctionSummaryEntity> topOffenders,
            int lookbackDays
    ) {}
}

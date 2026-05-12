package com.dragon.repository;

import com.dragon.dto.moderation.SanctionStatus;
import com.dragon.dto.moderation.SanctionType;
import com.dragon.entity.SanctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SanctionRepository extends JpaRepository<SanctionEntity, Long> {

    List<SanctionEntity> findByTargetUserIdAndGuildId(String targetUserId, String guildId);

    List<SanctionEntity> findByTargetUserIdAndGuildIdAndStatus(
            String targetUserId, String guildId, SanctionStatus status);

    List<SanctionEntity> findByTargetUserIdAndGuildIdAndTypeAndStatus(
            String targetUserId, String guildId, SanctionType type, SanctionStatus status);

    List<SanctionEntity> findByGuildIdAndTypeAndStatus(
            String guildId, SanctionType type, SanctionStatus status);

    /** Find all sanctions that have expired but are still marked ACTIVE. */
    @Query("SELECT s FROM SanctionEntity s WHERE s.status = 'ACTIVE' AND s.expiresAt IS NOT NULL AND s.expiresAt <= :now")
    List<SanctionEntity> findExpiredSanctions(Instant now);

    int countByTargetUserIdAndGuildIdAndTypeAndStatus(
            String targetUserId, String guildId, SanctionType type, SanctionStatus status);

    @Query("SELECT s FROM SanctionEntity s WHERE s.type = 'WARN' AND s.status = 'ACTIVE' AND s.createdAt <= :threshold")
    List<SanctionEntity> findEligibleWarnResets(Instant threshold);

    // ── Server-wide statistics queries ───────────────────────────────────────

    @Query("SELECT COUNT(s) FROM SanctionEntity s WHERE s.guildId = :guildId AND s.status = :status")
    long countByGuildIdAndStatus(@Param("guildId") String guildId, @Param("status") SanctionStatus status);

    @Query("SELECT COUNT(s) FROM SanctionEntity s WHERE s.guildId = :guildId AND s.createdAt >= :since")
    long countByGuildIdAndCreatedAtAfter(@Param("guildId") String guildId, @Param("since") Instant since);

    @Query("SELECT COUNT(s) FROM SanctionEntity s WHERE s.guildId = :guildId AND s.type = :type AND s.createdAt >= :since")
    long countByGuildIdAndTypeAndCreatedAtAfter(
            @Param("guildId") String guildId,
            @Param("type")    SanctionType type,
            @Param("since")   Instant since);
}

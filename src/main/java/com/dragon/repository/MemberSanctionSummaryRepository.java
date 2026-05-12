package com.dragon.repository;

import com.dragon.entity.MemberSanctionSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberSanctionSummaryRepository extends JpaRepository<MemberSanctionSummaryEntity, Long> {

    Optional<MemberSanctionSummaryEntity> findByUserIdAndGuildId(String userId, String guildId);

    /**
     * Returns the top 5 members with the highest combined infraction count in a guild.
     * Computed inline to avoid loading all summaries and filtering in memory.
     */
    @Query("""
            SELECT m FROM MemberSanctionSummaryEntity m
            WHERE m.guildId = :guildId
            ORDER BY (m.totalWarns + m.totalMutes + m.totalTimeouts + m.totalBans) DESC
            LIMIT 5
            """)
    List<MemberSanctionSummaryEntity> findTop5MostSanctionedByGuildId(@Param("guildId") String guildId);
}

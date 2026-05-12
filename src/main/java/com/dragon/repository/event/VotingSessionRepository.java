package com.dragon.repository.event;

import com.dragon.dto.vote.VotingSessionStatus;
import com.dragon.entity.event.vote.VotingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface VotingSessionRepository extends JpaRepository<VotingSession, Long> {

    Optional<VotingSession> findByIsoYearAndIsoWeek(int isoYear, int isoWeek);

    List<VotingSession> findByStatusAndClosesAtBefore(VotingSessionStatus status, Instant threshold);

    Optional<VotingSession> findByDiscordMessageId(Long discordMessageId);

    Optional<VotingSession> findByStatus(VotingSessionStatus status);

    @Query("""
        SELECT s FROM VotingSession s
        LEFT JOIN FETCH s.votes v
        LEFT JOIN FETCH v.chosenEventIds
        LEFT JOIN FETCH s.eventIds
        WHERE s.status = :status
    """)
    Optional<VotingSession> findByStatusWithVotes(@Param("status") VotingSessionStatus status);
}
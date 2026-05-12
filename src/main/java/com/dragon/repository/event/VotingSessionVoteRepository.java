package com.dragon.repository.event;

import com.dragon.entity.event.vote.VotingSessionVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VotingSessionVoteRepository extends JpaRepository<VotingSessionVote, Long> {

    Optional<VotingSessionVote> findBySessionIdAndMemberId(Long sessionId, String memberId);
}
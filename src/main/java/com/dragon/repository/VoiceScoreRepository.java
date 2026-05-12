package com.dragon.repository;

import com.dragon.entity.VoiceScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoiceScoreRepository extends JpaRepository<VoiceScore, Long> {

    List<VoiceScore> findByUserIdOrderByRecordedAtDesc(String userId);

    @Query("SELECT AVG(v.score) FROM VoiceScore v WHERE v.userId = :userId")
    Optional<Double> findAverageScoreByUserId(@Param("userId") String userId);

    @Query("SELECT MAX(v.score) FROM VoiceScore v WHERE v.userId = :userId")
    Optional<Integer> findBestScoreByUserId(@Param("userId") String userId);

    @Query("SELECT MIN(v.score) FROM VoiceScore v WHERE v.userId = :userId")
    Optional<Integer> findWorstScoreByUserId(@Param("userId") String userId);

    @Query("SELECT COUNT(v) FROM VoiceScore v WHERE v.userId = :userId")
    long countByUserId(@Param("userId") String userId);

    @Query("SELECT v FROM VoiceScore v WHERE v.userId = :userId ORDER BY v.score DESC LIMIT 1")
    Optional<VoiceScore> findBestEntryByUserId(@Param("userId") String userId);

    @Query("SELECT v FROM VoiceScore v WHERE v.userId = :userId ORDER BY v.recordedAt DESC LIMIT 5")
    List<VoiceScore> findLast5ByUserId(@Param("userId") String userId);
}

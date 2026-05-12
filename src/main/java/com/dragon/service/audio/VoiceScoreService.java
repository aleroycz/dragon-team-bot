package com.dragon.service.audio;

import com.dragon.entity.VoiceScore;
import com.dragon.repository.VoiceScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceScoreService {

    private final VoiceScoreRepository voiceScoreRepository;

    public VoiceScore save(String userId, String username, String transcript, int score, String feedback) {
        VoiceScore entry = VoiceScore.builder()
                .userId(userId)
                .username(username)
                .transcript(transcript)
                .score(score)
                .feedback(feedback)
                .build();

        VoiceScore saved = voiceScoreRepository.save(entry);
        log.info("Saved voice score for {} — {}/10", username, score);
        return saved;
    }

    public UserVoiceStats getStats(String userId) {
        long total       = voiceScoreRepository.countByUserId(userId);
        double average   = voiceScoreRepository.findAverageScoreByUserId(userId).orElse(0.0);
        int best         = voiceScoreRepository.findBestScoreByUserId(userId).orElse(0);
        int worst        = voiceScoreRepository.findWorstScoreByUserId(userId).orElse(0);
        List<VoiceScore> recent = voiceScoreRepository.findLast5ByUserId(userId);
        Optional<VoiceScore> bestEntry = voiceScoreRepository.findBestEntryByUserId(userId);

        double recentAverage = recent.stream()
                .mapToInt(VoiceScore::getScore)
                .average()
                .orElse(0.0);

        Trend trend = recentAverage > average ? Trend.IMPROVING
                : recentAverage < average ? Trend.DECLINING
                : Trend.STABLE;

        return new UserVoiceStats(
                userId,
                total,
                average,
                best,
                worst,
                recentAverage,
                trend,
                recent,
                bestEntry.orElse(null)
        );
    }

    public List<VoiceScore> getHistory(String userId) {
        return voiceScoreRepository.findByUserIdOrderByRecordedAtDesc(userId);
    }

    public enum Trend {
        IMPROVING, DECLINING, STABLE;
    }

    public record UserVoiceStats(
            String userId,
            long totalCalls,
            double averageScore,
            int bestScore,
            int worstScore,
            double recentAverage,
            Trend trend,
            List<VoiceScore> recentEntries,
            VoiceScore bestEntry
    ) {

    }
}
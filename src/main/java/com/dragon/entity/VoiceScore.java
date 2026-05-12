package com.dragon.entity;

import com.dragon.utils.chart.dataset.interfaces.Countable;
import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "voice_scores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceScore extends Countable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "transcript", columnDefinition = "TEXT", nullable = false)
    private String transcript;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    public void prePersist() {
        if (recordedAt == null) recordedAt = LocalDateTime.now();
    }

    /**
     * Bridge: Countable expects a SQL Timestamp; we derive it from recordedAt.
     * This field is NOT persisted — it exists solely for the chart pipeline.
     */
    @Override
    public Timestamp getCreatedAt() {
        return recordedAt != null
                ? Timestamp.valueOf(recordedAt)
                : Timestamp.valueOf(LocalDateTime.now());
    }
}
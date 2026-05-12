package com.dragon.entity.event;

import com.dragon.dto.event.EventStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "event_v5",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_event_name_start",
                columnNames = {"event_name", "start"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(name = "event_description", nullable = false, length = 1000)
    private String eventDescription;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventStatus status;

    @Column(name = "start", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "location", nullable = false)
    private Long channelId;

    @Column(name = "discord_event_id")
    private Long discordEventId;

    @Column(name = "isVoted")
    private Boolean voted;

    @Column(name = "reminded")
    private Boolean hasBeenReminded;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isActive() {
        return this.status == EventStatus.STARTED;
    }
}
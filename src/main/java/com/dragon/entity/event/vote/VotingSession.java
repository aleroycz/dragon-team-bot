package com.dragon.entity.event.vote;

import com.dragon.dto.vote.VotingSessionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "voting_session",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_voting_session_week",
                columnNames = {"iso_year", "iso_week"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VotingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(name = "iso_year", nullable = false)
    private int isoYear;

    @Column(name = "iso_week", nullable = false)
    private int isoWeek;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "voting_session_event",
            joinColumns = @JoinColumn(name = "session_id"),
            indexes = @Index(name = "idx_vse_session_id", columnList = "session_id")
    )
    @OrderColumn(name = "list_index")
    @Column(name = "event_id", nullable = false)
    @Builder.Default
    private List<Long> eventIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private VotingSessionStatus status;

    @Column(name = "discord_message_id")
    private Long discordMessageId;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<VotingSessionVote> votes = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
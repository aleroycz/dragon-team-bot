package com.dragon.entity.event.vote;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "voting_session_vote",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_session_member",
                columnNames = {"session_id", "member_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VotingSessionVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private VotingSession session;

    @Column(name = "member_id", nullable = false)
    private String memberId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "voting_session_vote_choice",
            joinColumns = @JoinColumn(name = "vote_id"),
            indexes = @Index(name = "idx_vsvc_vote_id", columnList = "vote_id")
    )
    @OrderColumn(name = "list_index")
    @Column(name = "event_id", nullable = false)
    @Builder.Default
    private List<Long> chosenEventIds = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
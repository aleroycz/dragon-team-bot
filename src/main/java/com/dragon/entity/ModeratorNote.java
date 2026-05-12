package com.dragon.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Persists a private, staff-only text note attached to a guild member.
 *
 * Notes are not sanctions — they carry no enforcement weight and are never
 * shown to the member. They exist so that moderation context survives across
 * staff shifts (e.g. "alt account suspected", "verbal warning issued off-record").
 *
 * A hard cap of 20 notes per member per guild is enforced at the service layer.
 */
@Entity
@Table(name = "moderator_notes", indexes = {
        @Index(name = "idx_note_target_guild", columnList = "target_id, guild_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModeratorNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Discord snowflake of the member this note is about. */
    @Column(name = "target_id", nullable = false)
    private String targetId;

    /** Display tag of the target at the time the note was written. */
    @Column(name = "target_tag", nullable = false)
    private String targetTag;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    /** Discord snowflake of the moderator who wrote the note. */
    @Column(name = "author_id", nullable = false)
    private String authorId;

    /** Display tag of the author at the time of writing. */
    @Column(name = "author_tag", nullable = false)
    private String authorTag;

    /** Note body. Hard-capped at 1 000 chars to match the DB column length. */
    @Column(nullable = false, length = 1000)
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}

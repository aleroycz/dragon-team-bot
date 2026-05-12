package com.dragon.entity;

import com.dragon.dto.ticket.TicketStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Represents a single support ticket.
 *
 * {@code creatorId} and {@code creatorTag} are nullable to support system-generated
 * tickets (bug reports submitted via {@code /bug-report}), where the ticket is not
 * necessarily tied to a single member's identity in the Discord channel permissions.
 *
 * {@code ticketNumber} is a sequential counter scoped to the guild, used for
 * human-readable channel naming ({@code ticket-42}) and references in log messages.
 */
@Entity
@Table(name = "tickets", indexes = {
        @Index(name = "idx_ticket_guild",      columnList = "guild_id"),
        @Index(name = "idx_ticket_channel",    columnList = "channel_id"),
        @Index(name = "idx_ticket_creator",    columnList = "creator_id"),
        @Index(name = "idx_ticket_status",     columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    /** Discord text-channel snowflake — filled in after the channel is created. */
    @Column(name = "channel_id")
    private String channelId;

    /** Null for system-generated tickets (e.g. bug reports). */
    @Column(name = "creator_id")
    private String creatorId;

    /** Snapshot of the creator's tag at the time the ticket was opened. */
    @Column(name = "creator_tag")
    private String creatorTag;

    /** FK to {@link TicketType} — null for generic/system tickets. */
    @Column(name = "type_id")
    private Long typeId;

    /** Denormalized label so the type name survives type deletion. */
    @Column(name = "type_name", length = 80)
    private String typeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TicketStatus status = TicketStatus.OPEN;

    /** Optional short subject line — used for bug-report title or user-provided topic. */
    @Column(length = 200)
    private String subject;

    /** Sequential, guild-scoped number used for channel naming and references. */
    @Column(name = "ticket_number", nullable = false)
    private int ticketNumber;

    /** Snowflake of the staff member who claimed this ticket. */
    @Column(name = "claimed_by_id")
    private String claimedById;

    @Column(name = "claimed_by_tag")
    private String claimedByTag;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_id")
    private String closedById;

    @Column(name = "closed_by_tag")
    private String closedByTag;
}

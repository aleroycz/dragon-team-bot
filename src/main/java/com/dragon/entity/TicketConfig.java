package com.dragon.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Per-guild configuration for the ticket system.
 *
 * A single row exists per guild. Missing optional fields ({@code null}) mean
 * "not yet configured" — the service layer will auto-create Discord categories
 * when needed and back-fill the IDs here.
 */
@Entity
@Table(name = "ticket_configs", indexes = {
        @Index(name = "idx_ticket_cfg_guild", columnList = "guild_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false, unique = true)
    private String guildId;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * The channel where the ticket-panel embed with buttons is sent.
     * Configured via {@code /ticket-setup panel}.
     */
    @Column(name = "panel_channel_id")
    private String panelChannelId;

    /**
     * Message ID of the last-sent panel so it can be replaced on refresh rather
     * than accumulating duplicate panels in the channel.
     */
    @Column(name = "panel_message_id")
    private String panelMessageId;

    /** Role that can see and manage all tickets. */
    @Column(name = "staff_role_id")
    private String staffRoleId;

    /** Channel where ticket open/close events are logged. */
    @Column(name = "log_channel_id")
    private String logChannelId;

    /**
     * Discord category ID for open tickets.
     * Auto-created and stored here on the first ticket open if absent.
     */
    @Column(name = "category_open_id")
    private String categoryOpenId;

    /**
     * Discord category ID for closed tickets.
     * When set, closed tickets are moved here instead of deleted.
     */
    @Column(name = "category_closed_id")
    private String categoryClosedId;
}

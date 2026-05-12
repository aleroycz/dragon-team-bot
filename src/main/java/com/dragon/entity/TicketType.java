package com.dragon.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A named, configurable ticket category for a guild.
 *
 * Multiple types can coexist in the same guild (e.g. "General Support",
 * "Player Application", "Report a Member"). Each type is shown as a button
 * on the ticket panel. Staff can add or remove types without touching the bot
 * configuration files.
 */
@Entity
@Table(name = "ticket_types", indexes = {
        @Index(name = "idx_ticket_type_guild", columnList = "guild_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    /** Short label shown on the panel button — max 80 chars (Discord button label limit). */
    @Column(nullable = false, length = 80)
    private String label;

    /** Unicode emoji shown alongside the label on the button. */
    @Column(nullable = false, length = 8)
    private String emoji;

    /** One-line description shown under the button in the panel embed. */
    @Column(nullable = false, length = 200)
    private String description;

    /**
     * Optional category override for this type.
     * When null, the guild-level {@link TicketConfig#getCategoryOpenId()} is used.
     */
    @Column(name = "category_id")
    private String categoryId;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * Display order on the panel. Lower numbers appear first.
     * Defaults to insertion order (0) — staff can reorder via /ticket-setup.
     */
    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}

package com.dragon.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Per-guild module override � allows server admins to enable/disable a module
 * for their guild independently of the global state.
 *
 * The unique constraint on {@code (guild_id, module_name)} ensures exactly one
 * override record per guild per module.
 */
@Entity
@Table(
    name = "guild_module_overrides",
    indexes = @Index(name = "idx_gmo_guild_module", columnList = "guild_id, module_name", unique = true)
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GuildModuleOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "module_name", nullable = false)
    private String moduleName;

    @Builder.Default
    @Column(unique = true, nullable = false)
    private boolean enabled = true;
}

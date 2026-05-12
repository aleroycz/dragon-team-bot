package com.dragon.entity;

import com.dragon.module.ModuleName;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Global module state record � one row per {@link ModuleName}.
 *
 * {@code enabled} acts as a kill-switch that overrides all per-guild overrides.
 * {@code inMaintenance} allows temporary suspension with a human-readable reason
 * without toggling the enabled flag.
 */
@Entity
@Table(name = "bot_modules_v1")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BotModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String displayName;

    @Builder.Default
    @Column()
    private boolean enabled = true;

    @Builder.Default
    @Column()
    private boolean inMaintenance = false;

    @Column()
    private String maintenanceReason;

    @Column()
    private String updatedBy;

    @UpdateTimestamp
    @Column()
    private Instant updatedAt;
}

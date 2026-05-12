package com.dragon.service;

import com.dragon.entity.BotModule;
import com.dragon.entity.GuildModuleOverride;
import com.dragon.module.ModuleName;
import com.dragon.repository.BotModuleRepository;
import com.dragon.repository.GuildModuleOverrideRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central gate for all module-level enablement checks.
 *
 * <p>Two-layer model:
 * <ol>
 *   <li>Global ({@link BotModule}) � dev-controlled kill-switch + maintenance flag.
 *   <li>Per-guild ({@link GuildModuleOverride}) � server-admin opt-in/out.
 * </ol>
 *
 * A module is active when: global.enabled AND NOT global.inMaintenance AND guild.enabled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleService {

    private final BotModuleRepository botModuleRepository;
    private final GuildModuleOverrideRepository guildOverrideRepository;

    /** Seeds a {@link BotModule} row for every {@link ModuleName} value at startup. */
    @PostConstruct
    @Transactional
    public void initializeModules() {
        for (ModuleName module : ModuleName.values()) {
            if (botModuleRepository.findByName(module.name()).isEmpty()) {
                BotModule fresh = BotModule.builder()
                        .name(module.name())
                        .displayName(module.getDisplayName())
                        .enabled(true)
                        .inMaintenance(false)
                        .build();
                botModuleRepository.save(fresh);
                log.info("[Modules] Seeded module: {}", module.name());
            }
        }
    }

    // -- Query helpers ---------------------------------------------------------

    /**
     * Returns true when the module is globally enabled, not in maintenance,
     * and not disabled for the given guild.
     */
    public boolean isEnabled(ModuleName module, String guildId) {
        BotModule global = botModuleRepository.findByName(module.name()).orElse(null);
        if (global == null || !global.isEnabled() || global.isInMaintenance()) return false;

        return guildOverrideRepository
                .findByGuildIdAndModuleName(guildId, module.name())
                .map(GuildModuleOverride::isEnabled)
                .orElse(true); // default: enabled for all guilds
    }

    /** Returns true when the module is in maintenance regardless of guild state. */
    public boolean isInMaintenance(ModuleName module) {
        return botModuleRepository.findByName(module.name())
                .map(BotModule::isInMaintenance)
                .orElse(false);
    }

    /**
     * Convenience gate for slash commands.
     * Replies with an ephemeral message and returns {@code false} if the module
     * is disabled or in maintenance, so callers can {@code return} immediately.
     */
    public boolean checkAndReply(ModuleName module, SlashCommandInteractionEvent event) {
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        if (guildId == null) return true;

        BotModule global = botModuleRepository.findByName(module.name()).orElse(null);

        if (global != null && global.isInMaintenance()) {
            String reason = global.getMaintenanceReason() != null
                    ? " � " + global.getMaintenanceReason()
                    : "";
            event.reply("?? **" + module.displayName + "** is currently under maintenance" + reason + ". Please try again later.")
                    .setEphemeral(true).queue();
            return false;
        }

        if (!isEnabled(module, guildId)) {
            event.reply("? **" + module.displayName + "** is not enabled on this server.")
                    .setEphemeral(true).queue();
            return false;
        }

        return true;
    }

    // -- Mutation --------------------------------------------------------------

    @Transactional
    public void setGlobalEnabled(ModuleName module, boolean enabled, String updatedBy) {
        BotModule m = botModuleRepository.findByName(module.name())
                .orElseThrow(() -> new IllegalStateException("Module not seeded: " + module));
        m.setEnabled(enabled);
        m.setUpdatedBy(updatedBy);
        botModuleRepository.save(m);
        log.info("[Modules] {} global enabled={} by {}", module, enabled, updatedBy);
    }

    @Transactional
    public void setMaintenance(ModuleName module, boolean maintenance, String reason, String updatedBy) {
        BotModule m = botModuleRepository.findByName(module.name())
                .orElseThrow(() -> new IllegalStateException("Module not seeded: " + module));
        m.setInMaintenance(maintenance);
        m.setMaintenanceReason(maintenance ? reason : null);
        m.setUpdatedBy(updatedBy);
        botModuleRepository.save(m);
        log.info("[Modules] {} maintenance={} reason='{}' by {}", module, maintenance, reason, updatedBy);
    }

    @Transactional
    public void setGuildEnabled(ModuleName module, String guildId, boolean enabled) {
        GuildModuleOverride override = guildOverrideRepository
                .findByGuildIdAndModuleName(guildId, module.name())
                .orElseGet(() -> GuildModuleOverride.builder()
                        .guildId(guildId)
                        .moduleName(module.name())
                        .build());
        override.setEnabled(enabled);
        guildOverrideRepository.save(override);
        log.info("[Modules] {} guild={} enabled={}", module, guildId, enabled);
    }
}

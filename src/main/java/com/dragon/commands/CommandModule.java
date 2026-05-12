package com.dragon.commands;

import com.dragon.entity.BotModule;
import com.dragon.integration.SlashCommand;
import com.dragon.module.ModuleName;
import com.dragon.repository.BotModuleRepository;
import com.dragon.repository.GuildModuleOverrideRepository;
import com.dragon.service.ModuleService;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Developer/admin command for managing the module system.
 *
 * <p>Subcommands:
 * <ul>
 *   <li>{@code list}         – list all modules and their states
 *   <li>{@code enable}       – enable a module globally
 *   <li>{@code disable}      – disable a module globally
 *   <li>{@code maintenance}  – toggle maintenance mode with optional reason
 *   <li>{@code guild-enable} – enable a module for this guild
 *   <li>{@code guild-disable}– disable a module for this guild
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CommandModule implements SlashCommand {

    private final ModuleService moduleService;
    private final BotModuleRepository botModuleRepository;
    private final GuildModuleOverrideRepository guildOverrideRepository;
    private final Embed embed;

    @Override
    public String getName() { return "module"; }

    @Override
    public String getDescription() { return "Manage bot feature modules."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("list", "List all modules and their current state."),

                        new SubcommandData("enable", "Enable a module globally.")
                                .addOption(OptionType.STRING, "module", "Module name", true),

                        new SubcommandData("disable", "Disable a module globally.")
                                .addOption(OptionType.STRING, "module", "Module name", true),

                        new SubcommandData("maintenance", "Toggle maintenance mode for a module.")
                                .addOption(OptionType.STRING, "module",  "Module name",             true)
                                .addOption(OptionType.BOOLEAN,"enabled", "Enable maintenance?",      true)
                                .addOption(OptionType.STRING, "reason",  "Reason (shown to users)", false),

                        new SubcommandData("guild-enable", "Enable a module for this guild only.")
                                .addOption(OptionType.STRING, "module", "Module name", true),

                        new SubcommandData("guild-disable", "Disable a module for this guild only.")
                                .addOption(OptionType.STRING, "module", "Module name", true)
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_SERVER)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You need **Manage Server** to use this command.").build()
            ).setEphemeral(true).queue();
            return;
        }

        String sub = event.getSubcommandName();
        if (sub == null) return;

        switch (sub) {
            case "list"          -> handleList(event);
            case "enable"        -> handleGlobalToggle(event, true);
            case "disable"       -> handleGlobalToggle(event, false);
            case "maintenance"   -> handleMaintenance(event);
            case "guild-enable"  -> handleGuildToggle(event, true);
            case "guild-disable" -> handleGuildToggle(event, false);
        }
    }

    // -- Handlers --------------------------------------------------------------

    private void handleList(SlashCommandInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        List<BotModule> globals = botModuleRepository.findAll();

        var builder = embed.info(IconRegistry.ICON_BOOK, "📋 Module Status",
                "**" + globals.size() + "** module(s) registered.");

        for (BotModule m : globals) {
            String guildState = guildOverrideRepository
                    .findByGuildIdAndModuleName(guildId, m.getName())
                    .map(o -> o.isEnabled() ? "✅ guild-on" : "❌ guild-off")
                    .orElse("🔁 default");

            String status = m.isInMaintenance()
                    ? "🔧 Maintenance"
                    : (m.isEnabled() ? "✅ Enabled" : "❌ Disabled");

            builder.addField(
                    m.getName(),
                    status + "  |  " + guildState
                            + (m.isInMaintenance() && m.getMaintenanceReason() != null
                            ? "\n-# " + m.getMaintenanceReason() : ""),
                    false
            );
        }

        builder.setTimestamp(Instant.now());
        event.replyEmbeds(builder.build()).setEphemeral(true).queue();
    }

    private void handleGlobalToggle(SlashCommandInteractionEvent event, boolean enable) {
        ModuleName module = resolveModule(event);
        if (module == null) return;

        moduleService.setGlobalEnabled(module, enable, event.getMember().getUser().getAsTag());

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS,
                                (enable ? "✅" : "❌") + " Module " + (enable ? "Enabled" : "Disabled"),
                                "**" + module.displayName + "** has been " + (enable ? "enabled" : "disabled") + " globally.")
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();
    }

    private void handleMaintenance(SlashCommandInteractionEvent event) {
        ModuleName module = resolveModule(event);
        if (module == null) return;

        boolean maintenance = Objects.requireNonNull(event.getOption("enabled")).getAsBoolean();
        String reason = event.getOption("reason") != null
                ? Objects.requireNonNull(event.getOption("reason")).getAsString()
                : null;

        moduleService.setMaintenance(module, maintenance, reason, event.getMember().getUser().getAsTag());

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS,
                                maintenance ? "🔧 Maintenance Enabled" : "✅ Maintenance Lifted",
                                "**" + module.displayName + "** maintenance mode "
                                        + (maintenance ? "activated" : "deactivated") + "."
                                        + (reason != null ? "\n-# Reason: " + reason : ""))
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();
    }

    private void handleGuildToggle(SlashCommandInteractionEvent event, boolean enable) {
        ModuleName module = resolveModule(event);
        if (module == null) return;

        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        moduleService.setGuildEnabled(module, guildId, enable);

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS,
                                (enable ? "✅" : "❌") + " Module " + (enable ? "Enabled" : "Disabled") + " for Guild",
                                "**" + module.displayName + "** is now " + (enable ? "enabled" : "disabled")
                                        + " for **" + event.getGuild().getName() + "**.")
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();
    }

    private ModuleName resolveModule(SlashCommandInteractionEvent event) {
        String name = Objects.requireNonNull(event.getOption("module")).getAsString();
        try {
            return ModuleName.fromString(name);
        } catch (IllegalArgumentException e) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Unknown Module",
                    "No module named `" + name + "`. Valid modules: "
                            + java.util.Arrays.stream(ModuleName.values())
                            .map(Enum::name).collect(java.util.stream.Collectors.joining(", "))).build()
            ).setEphemeral(true).queue();
            return null;
        }
    }
}
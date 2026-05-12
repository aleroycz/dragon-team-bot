package com.dragon.commands.ticket;

import com.dragon.entity.TicketConfig;
import com.dragon.entity.TicketType;
import com.dragon.integration.SlashCommand;
import com.dragon.module.ModuleName;
import com.dragon.service.ModuleService;
import com.dragon.service.ticket.TicketConfigService;
import com.dragon.service.ticket.TicketService;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandTicketSetup implements SlashCommand {

    private final TicketConfigService configService;
    private final TicketService ticketService;
    private final ModuleService moduleService;
    private final Embed embed;

    @Override
    public String getName() { return "ticket-setup"; }

    @Override
    public String getDescription() { return "Configure the ticket system for this server."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER))
                .addSubcommands(
                        new SubcommandData("panel",
                                "Send or refresh the ticket-panel embed in a channel.")
                                .addOption(OptionType.CHANNEL, "channel",
                                        "Target channel (defaults to current channel)", false),

                        new SubcommandData("staff-role",
                                "Set the role that can see and manage all tickets.")
                                .addOption(OptionType.ROLE, "role", "The staff role", true),

                        new SubcommandData("log-channel",
                                "Set the channel where ticket events are logged.")
                                .addOption(OptionType.CHANNEL, "channel", "Log channel", true),

                        new SubcommandData("status",
                                "View the current ticket system configuration."),

                        new SubcommandData("add-type",
                                "Add a new ticket type to the panel (max 5).")
                                .addOption(OptionType.STRING, "label",       "Button label (max 80 chars)", true)
                                .addOption(OptionType.STRING, "emoji",       "Unicode emoji for the button", true)
                                .addOption(OptionType.STRING, "description", "One-line description of this type", false),

                        new SubcommandData("remove-type",
                                "Remove a ticket type by its ID (see /ticket-setup list-types).")
                                .addOption(OptionType.INTEGER, "type_id", "ID shown in /ticket-setup list-types", true),

                        new SubcommandData("list-types",
                                "List all configured ticket types for this server."),

                        new SubcommandData("enable",
                                "Enable the ticket system for this server."),

                        new SubcommandData("disable",
                                "Disable the ticket system for this server.")
                );
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_SERVER)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You need **Manage Server** to configure the ticket system.").build()
            ).setEphemeral(true).queue();
            return;
        }

        String sub = event.getSubcommandName();
        if (sub == null) return;

        switch (sub) {
            case "panel"       -> handlePanel(event);
            case "staff-role"  -> handleStaffRole(event);
            case "log-channel" -> handleLogChannel(event);
            case "status"      -> handleStatus(event);
            case "add-type"    -> handleAddType(event);
            case "remove-type" -> handleRemoveType(event);
            case "list-types"  -> handleListTypes(event);
            case "enable"      -> handleToggle(event, true);
            case "disable"     -> handleToggle(event, false);
        }
    }

    // ── Subcommand handlers ───────────────────────────────────────────────────

    private void handlePanel(SlashCommandInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        List<TicketType> types = configService.getEnabledTypes(guildId);
        if (types.isEmpty()) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "No Ticket Types",
                    "Add at least one ticket type first with `/ticket-setup add-type`.").build()
            ).setEphemeral(true).queue();
            return;
        }

        // Resolve target channel — default to current
        TextChannel target;
        OptionMapping channelOpt = event.getOption("channel");
        if (channelOpt != null) {
            target = channelOpt.getAsChannel().asTextChannel();
        } else {
            target = (TextChannel) event.getChannel();
        }

        TicketConfig config = configService.getOrCreate(guildId);
        ticketService.sendPanel(event.getGuild(), target, config, types);

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS, "✅ Panel Sent",
                                "The ticket panel has been sent to " + target.getAsMention() + ".")
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();
    }

    private void handleStaffRole(SlashCommandInteractionEvent event) {
        Role role    = Objects.requireNonNull(event.getOption("role")).getAsRole();
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        configService.setStaffRole(guildId, role.getId());

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS, "✅ Staff Role Updated",
                                "Ticket staff role set to " + role.getAsMention() + ".\n"
                                        + "-# New tickets will grant this role access automatically.")
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();
    }

    private void handleLogChannel(SlashCommandInteractionEvent event) {
        TextChannel channel = Objects.requireNonNull(event.getOption("channel"))
                .getAsChannel().asTextChannel();
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        configService.setLogChannel(guildId, channel.getId());

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS, "✅ Log Channel Updated",
                                "Ticket events will be logged in " + channel.getAsMention() + ".")
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();
    }

    private void handleStatus(SlashCommandInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        TicketConfig cfg = configService.getOrCreate(guildId);
        List<TicketType> types = configService.getAllTypes(guildId);

        String panelChannel = cfg.getPanelChannelId() != null ? "<#" + cfg.getPanelChannelId() + ">" : "Not set";
        String staffRole    = cfg.getStaffRoleId()    != null ? "<@&" + cfg.getStaffRoleId() + ">"  : "Not set";
        String logChannel   = cfg.getLogChannelId()   != null ? "<#" + cfg.getLogChannelId() + ">"   : "Not set";
        String openCat      = cfg.getCategoryOpenId() != null ? "<#" + cfg.getCategoryOpenId() + ">"  : "Auto-create on first use";
        String closedCat    = cfg.getCategoryClosedId() != null ? "<#" + cfg.getCategoryClosedId() + ">" : "Delete on close";

        var builder = embed.info(IconRegistry.ICON_BOOK,
                        "🎫 Ticket System — " + event.getGuild().getName(),
                        "Current configuration overview.")
                .addField("Status",          cfg.isEnabled() ? "✅ Enabled" : "🔴 Disabled", true)
                .addField("📢 Panel Channel", panelChannel,   true)
                .addField("👮 Staff Role",   staffRole,       true)
                .addField("📋 Log Channel",  logChannel,      true)
                .addField("📂 Open Category",   openCat,      true)
                .addField("🔒 Closed Category", closedCat,    true);

        if (!types.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (TicketType t : types) {
                sb.append(t.getEmoji()).append(" **#").append(t.getId()).append("** ")
                        .append(t.getLabel())
                        .append(t.isEnabled() ? "" : " *(disabled)*")
                        .append("\n-# ").append(t.getDescription()).append("\n");
            }
            builder.addField("🎫 Ticket Types (" + types.size() + ")", sb.toString(), false);
        } else {
            builder.addField("🎫 Ticket Types", "None configured — use `/ticket-setup add-type`.", false);
        }

        builder.setTimestamp(Instant.now());
        event.replyEmbeds(builder.build()).setEphemeral(true).queue();
    }

    private void handleAddType(SlashCommandInteractionEvent event) {
        String guildId     = Objects.requireNonNull(event.getGuild()).getId();
        String label       = Objects.requireNonNull(event.getOption("label")).getAsString();
        String emoji       = Objects.requireNonNull(event.getOption("emoji")).getAsString();
        String description = Objects.requireNonNullElse(
                event.getOption("description", OptionMapping::getAsString), "");

        try {
            TicketType type = configService.addType(guildId, label, emoji, description);

            event.replyEmbeds(
                    embed.info(IconRegistry.ICON_CHECKS, "✅ Type Added",
                                    emoji + " **" + label + "** (ID: `" + type.getId() + "`) added to the panel.\n"
                                            + "-# Refresh the panel with `/ticket-setup panel` to show the new type.")
                            .setTimestamp(Instant.now())
                            .build()
            ).setEphemeral(true).queue();
        } catch (IllegalArgumentException | IllegalStateException e) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Cannot Add Type", e.getMessage()).build()
            ).setEphemeral(true).queue();
        }
    }

    private void handleRemoveType(SlashCommandInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        long typeId    = Objects.requireNonNull(event.getOption("type_id")).getAsLong();

        boolean removed = configService.removeType(typeId, guildId);

        if (!removed) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Type Not Found",
                    "No ticket type with ID `" + typeId + "` found in this guild.").build()
            ).setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS, "🗑️ Type Removed",
                                "Ticket type **#" + typeId + "** has been removed.\n"
                                        + "-# Refresh the panel with `/ticket-setup panel`.")
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();
    }

    private void handleListTypes(SlashCommandInteractionEvent event) {
        String guildId       = Objects.requireNonNull(event.getGuild()).getId();
        List<TicketType> all = configService.getAllTypes(guildId);

        if (all.isEmpty()) {
            event.replyEmbeds(embed.info(IconRegistry.ICON_BOOK, "No Ticket Types",
                    "Use `/ticket-setup add-type` to add your first type.").build()
            ).setEphemeral(true).queue();
            return;
        }

        var builder = embed.info(IconRegistry.ICON_BOOK,
                "🎫 Ticket Types — " + event.getGuild().getName(),
                "**" + all.size() + "** type(s) configured.");

        for (TicketType t : all) {
            builder.addField(
                    t.getEmoji() + " #" + t.getId() + " — " + t.getLabel() + (t.isEnabled() ? "" : " *(disabled)*"),
                    "-# " + t.getDescription(),
                    false
            );
        }

        builder.setTimestamp(Instant.now());
        event.replyEmbeds(builder.build()).setEphemeral(true).queue();
    }

    private void handleToggle(SlashCommandInteractionEvent event, boolean enable) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        configService.setEnabled(guildId, enable);
        moduleService.setGuildEnabled(ModuleName.TICKETS, guildId, enable);

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS,
                                enable ? "✅ Ticket System Enabled" : "🔴 Ticket System Disabled",
                                "The ticket system has been " + (enable ? "enabled" : "disabled")
                                        + " for **" + event.getGuild().getName() + "**.")
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CommandHistory.java
// ─────────────────────────────────────────────────────────────────────────────
package com.dragon.commands.moderation;

import com.dragon.dto.moderation.SanctionResponse;
import com.dragon.dto.moderation.SanctionType;
import com.dragon.integration.SlashCommand;
import com.dragon.module.ModuleName;
import com.dragon.service.ModuleService;
import com.dragon.service.moderation.SanctionService;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandHistory implements SlashCommand {

    private final SanctionService sanctionService;
    private final ModuleService moduleService;
    private final Embed embed;

    @Override
    public String getName() { return "history"; }

    @Override
    public String getDescription() { return "View the full sanction history of a member."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "member", "The member to look up", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!moduleService.checkAndReply(ModuleName.MODERATION, event)) return;

        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MODERATE_MEMBERS)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You do not have permission to view sanction history.").build()
            ).setEphemeral(true).queue();
            return;
        }

        Member target = Objects.requireNonNull(event.getOption("member")).getAsMember();

        if (target == null) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Member Not Found",
                    "Could not resolve the target member. Are they still in the server?").build()
            ).setEphemeral(true).queue();
            return;
        }

        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        List<SanctionResponse> history = sanctionService.getHistory(target.getId(), guildId);
        int activeWarns = sanctionService.getActiveWarnCount(target.getId(), guildId);

        if (history.isEmpty()) {
            event.replyEmbeds(
                    embed.info(
                                    IconRegistry.ICON_BOOK,
                                    "📋 Sanction History — " + target.getUser().getAsTag(),
                                    "This member has a clean record. No sanctions have been issued."
                            )
                            .setThumbnail(target.getEffectiveAvatarUrl())
                            .setTimestamp(Instant.now())
                            .build()
            ).setEphemeral(true).queue();
            return;
        }

        long totalWarns    = history.stream().filter(s -> s.type() == SanctionType.WARN).count();
        long totalMutes    = history.stream().filter(s -> s.type() == SanctionType.MUTE).count();
        long totalTimeouts = history.stream().filter(s -> s.type() == SanctionType.TIMEOUT).count();
        long totalBans     = history.stream().filter(s -> s.type() == SanctionType.BAN).count();

        // Build sanction entries — cap at 10 most recent to avoid hitting embed limits
        List<SanctionResponse> recent = history.stream()
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .limit(10)
                .toList();

        var builder = embed.info(
                        IconRegistry.ICON_BOOK,
                        "📋 Sanction History — " + target.getUser().getAsTag(),
                        """
                        Showing the **%d** most recent sanction(s) out of **%d** total.
                        """.formatted(recent.size(), history.size())
                )
                .setThumbnail(target.getEffectiveAvatarUrl())
                .addField("⚠️ Warns",    totalWarns    + " total / " + activeWarns + " active", true)
                .addField("🔇 Mutes",    String.valueOf(totalMutes),                             true)
                .addField("⏱️ Timeouts", String.valueOf(totalTimeouts),                         true)
                .addField("🔨 Bans",     String.valueOf(totalBans),                             true)
                .addField("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "\u200B",                          false);

        for (SanctionResponse sanction : recent) {
            String title = getSanctionEmoji(sanction.type().name())
                    + " #" + sanction.id()
                    + " — " + sanction.type().name()
                    + " [" + sanction.status().name() + "]";

            String body = "**Reason:** " + sanction.reason() + "\n"
                    + "**Issued by:** " + sanction.moderatorUserTag() + "\n"
                    + "**Duration:** " + (sanction.permanent() ? "Permanent" : formatDuration(sanction.durationSeconds())) + "\n"
                    + "**Date:** " + sanction.createdAt().toString();

            builder.addField(title, body, false);
        }

        builder.setTimestamp(Instant.now());

        event.replyEmbeds(builder.build()).setEphemeral(true).queue();
    }

    private String getSanctionEmoji(String type) {
        return switch (type) {
            case "WARN"    -> "⚠️";
            case "MUTE"    -> "🔇";
            case "TIMEOUT" -> "⏱️";
            case "BAN"     -> "🔨";
            default        -> "📋";
        };
    }

    private String formatDuration(Long seconds) {
        if (seconds == null)    return "Permanent";
        if (seconds < 60)       return seconds + " second(s)";
        if (seconds < 3600)     return (seconds / 60) + " minute(s)";
        if (seconds < 86400)    return (seconds / 3600) + " hour(s)";
        return (seconds / 86400) + " day(s)";
    }
}
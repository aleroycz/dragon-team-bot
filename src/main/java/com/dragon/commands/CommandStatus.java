package com.dragon.commands;

import com.dragon.dto.moderation.SanctionResponse;
import com.dragon.dto.moderation.SanctionType;
import com.dragon.integration.SlashCommand;
import com.dragon.module.ModuleName;
import com.dragon.service.ModuleService;
import com.dragon.service.moderation.SanctionService;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandStatus implements SlashCommand {

    private final SanctionService sanctionService;
    private final ModuleService moduleService;
    private final Embed embed;

    @Override
    public String getName() { return "status"; }

    @Override
    public String getDescription() { return "View your current standing and active sanctions."; }

    @Override
    public void configure(SlashCommandData data) {}

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!moduleService.checkAndReply(ModuleName.MODERATION, event)) return;

        String userId  = Objects.requireNonNull(event.getMember()).getId();
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        List<SanctionResponse> active = sanctionService.getActiveSanctions(userId, guildId);
        int activeWarns = sanctionService.getActiveWarnCount(userId, guildId);

        if (active.isEmpty()) {
            event.replyEmbeds(
                    embed.info(
                                    IconRegistry.ICON_CHECKS,
                                    "✅ Your Standing — Clean Record",
                                    "You have no active sanctions. Keep up the great behaviour!"
                            )
                            .setThumbnail(event.getMember().getEffectiveAvatarUrl())
                            .setTimestamp(Instant.now())
                            .build()
            ).setEphemeral(true).queue();
            return;
        }

        long activeMutes    = active.stream().filter(s -> s.type() == SanctionType.MUTE).count();
        long activeTimeouts = active.stream().filter(s -> s.type() == SanctionType.TIMEOUT).count();
        long activeBans     = active.stream().filter(s -> s.type() == SanctionType.BAN).count();

        var builder = embed.warn(
                        IconRegistry.ICON_ALERT,
                        "⚠️ Your Standing — Active Sanctions",
                        "You currently have **" + active.size() + "** active sanction(s). " +
                                "Please review them below and ensure future compliance with the server rules."
                )
                .setThumbnail(event.getMember().getEffectiveAvatarUrl())
                .addField("⚠️ Active Warns",    activeWarns + " / 3",          true)
                .addField("🔇 Active Mutes",    String.valueOf(activeMutes),    true)
                .addField("⏱️ Active Timeouts", String.valueOf(activeTimeouts), true)
                .addField("🔨 Active Bans",     String.valueOf(activeBans),     true);

        List<SanctionResponse> recent = active.stream()
                .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                .limit(5)
                .toList();

        if (!recent.isEmpty()) {
            builder.addField("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", "​", false);
            for (SanctionResponse s : recent) {
                String title = getSanctionEmoji(s.type().name()) + " #" + s.id() + " — " + s.type().name();
                String body  = "**Reason:** " + s.reason() + "\n"
                        + "**Duration:** " + (s.permanent() ? "Permanent" : formatDuration(s.durationSeconds())) + "\n"
                        + "**Expires:** " + (s.expiresAt() != null ? "<t:" + s.expiresAt().getEpochSecond() + ":R>" : "Never") + "\n"
                        + "**Issued:** " + s.createdAt().toString();
                builder.addField(title, body, false);
            }
        }

        builder.addField("​",
                "-# Sanctions may be contested by contacting a staff member directly.", false);
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

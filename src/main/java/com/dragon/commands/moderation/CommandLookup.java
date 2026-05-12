package com.dragon.commands.moderation;

import com.dragon.dto.moderation.SanctionResponse;
import com.dragon.dto.moderation.SanctionType;
import com.dragon.integration.SlashCommand;
import com.dragon.service.UserConsentService;
import com.dragon.service.audio.VoiceScoreService;
import com.dragon.service.moderation.ModeratorNoteService;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandLookup implements SlashCommand {

    private final SanctionService sanctionService;
    private final VoiceScoreService voiceScoreService;
    private final UserConsentService userConsentService;
    private final ModeratorNoteService noteService;
    private final Embed embed;

    @Override
    public String getName() { return "lookup"; }

    @Override
    public String getDescription() { return "View the full staff profile of a member."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER, "member", "The member to look up", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MODERATE_MEMBERS)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You do not have permission to use this command.").build()
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
        String userId  = target.getId();

        // Sanction snapshot
        List<SanctionResponse> active = sanctionService.getActiveSanctions(userId, guildId);
        int    activeWarns    = sanctionService.getActiveWarnCount(userId, guildId);
        long   activeMutes    = active.stream().filter(s -> s.type() == SanctionType.MUTE).count();
        long   activeTimeouts = active.stream().filter(s -> s.type() == SanctionType.TIMEOUT).count();
        long   activeBans     = active.stream().filter(s -> s.type() == SanctionType.BAN).count();

        // Voice stats
        VoiceScoreService.UserVoiceStats voice = voiceScoreService.getStats(userId);

        // Consent + notes
        boolean hasConsent = userConsentService.hasConsent(target);
        long    noteCount  = noteService.countNotes(userId, guildId);

        String sanctionStatus = active.isEmpty()
                ? "✅ Clean"
                : "⚠️ " + active.size() + " active";

        String trendIcon = switch (voice.trend()) {
            case IMPROVING -> "📈";
            case DECLINING -> "📉";
            case STABLE    -> "➡️";
        };

        var builder = embed.info(IconRegistry.ICON_USER_PLUS,
                        "🔍 Member Lookup — " + target.getUser().getAsTag(),
                        "-# Staff-only profile. All fields are confidential.")
                .setThumbnail(target.getEffectiveAvatarUrl())
                .addField("🆔 User ID",           target.getId(),                                              true)
                .addField("📅 Joined",             "<t:" + target.getTimeJoined().toEpochSecond() + ":R>",    true)
                .addField("📋 Sanctions",          sanctionStatus,                                             true)
                .addField("⚠️ Active Warns",       activeWarns + " / 3",                                       true)
                .addField("🔇 Active Mutes",       String.valueOf(activeMutes),                                true)
                .addField("⏱️ Active Timeouts",    String.valueOf(activeTimeouts),                             true)
                .addField("🔨 Active Bans",        String.valueOf(activeBans),                                 true)
                .addField("🎙️ Voice Sessions",    String.valueOf(voice.totalCalls()),                         true)
                .addField("📊 Avg Voice Score",    voice.totalCalls() > 0
                        ? String.format("%.1f / 10  %s", voice.averageScore(), trendIcon) : "N/A",           true)
                .addField("🤝 AI Consent",         hasConsent ? "✅ Granted" : "❌ Denied",                    true)
                .addField("📝 Staff Notes",        noteCount + " note(s)  — use `/notes` to view",            true);

        // Append most recent active sanction for quick context
        active.stream()
                .max(Comparator.comparing(SanctionResponse::createdAt))
                .ifPresent(latest ->
                        builder.addField(
                                "🕐 Latest Active Sanction",
                                getSanctionEmoji(latest.type().name()) + " **" + latest.type().name()
                                        + "** — " + latest.reason()
                                        + "\n-# <t:" + latest.createdAt().getEpochSecond() + ":R>",
                                false
                        )
                );

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
}

// ─────────────────────────────────────────────────────────────────────────────
// CommandTimeout.java
// ─────────────────────────────────────────────────────────────────────────────
package com.dragon.commands.moderation;

import com.dragon.dto.moderation.SanctionRequest;
import com.dragon.dto.moderation.SanctionType;
import com.dragon.integration.SlashCommand;
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
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandTimeout implements SlashCommand {

    private final SanctionService sanctionService;
    private final Embed embed;

    private static final long MAX_TIMEOUT_SECONDS = 2419200L; // 28 days — Discord limit

    @Override
    public String getName() { return "timeout"; }

    @Override
    public String getDescription() { return "Temporarily timeout a member (max 28 days)."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER,    "member",   "The member to timeout",          true)
                .addOption(OptionType.INTEGER, "duration", "Duration in seconds (max 28d)",  true)
                .addOption(OptionType.STRING,  "reason",   "The reason for the timeout",     true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MODERATE_MEMBERS)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You do not have permission to timeout members.").build()
            ).setEphemeral(true).queue();
            return;
        }

        Member target  = Objects.requireNonNull(event.getOption("member")).getAsMember();
        long duration  = Objects.requireNonNull(event.getOption("duration")).getAsLong();
        String reason  = Objects.requireNonNull(event.getOption("reason")).getAsString();

        if (target == null) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Member Not Found",
                    "Could not resolve the target member. Are they still in the server?").build()
            ).setEphemeral(true).queue();
            return;
        }

        if (target.getIdLong() == event.getMember().getIdLong()) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Invalid Target",
                    "You cannot timeout yourself.").build()
            ).setEphemeral(true).queue();
            return;
        }

        if (target.isOwner() || target.hasPermission(Permission.ADMINISTRATOR)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Invalid Target",
                    "You cannot timeout a server owner or administrator.").build()
            ).setEphemeral(true).queue();
            return;
        }

        if (duration <= 0 || duration > MAX_TIMEOUT_SECONDS) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Invalid Duration",
                    "Duration must be between 1 second and 2,419,200 seconds (28 days).").build()
            ).setEphemeral(true).queue();
            return;
        }

        SanctionRequest request = new SanctionRequest(
                target.getId(),
                target.getUser().getAsTag(),
                event.getMember().getId(),
                event.getMember().getUser().getAsTag(),
                Objects.requireNonNull(event.getGuild()).getId(),
                SanctionType.TIMEOUT,
                reason,
                duration
        );

        try {
            var response = sanctionService.timeout(request);

            event.replyEmbeds(
                    embed.warn(
                                    IconRegistry.ICON_ALERT,
                                    "⏱️ Timeout Issued",
                                    """
                                    **%s** has been timed out for **%s**.
                                    
                                    **Reason:** %s
                                    """.formatted(target.getUser().getAsTag(), formatDuration(duration), reason)
                            )
                            .addField("Case ID",   "#" + response.id(),                true)
                            .addField("Duration",  formatDuration(duration),            true)
                            .addField("Expires",   response.expiresAt().toString(),     true)
                            .addField("Issued By", event.getMember().getAsMention(),    true)
                            .setTimestamp(Instant.now())
                            .build()
            ).setEphemeral(true).queue();

        } catch (Exception e) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Error",
                    "Failed to issue timeout: " + e.getMessage()).build()
            ).setEphemeral(true).queue();
        }
    }

    private String formatDuration(long seconds) {
        if (seconds < 60)    return seconds + " second(s)";
        if (seconds < 3600)  return (seconds / 60) + " minute(s)";
        if (seconds < 86400) return (seconds / 3600) + " hour(s)";
        return (seconds / 86400) + " day(s)";
    }
}
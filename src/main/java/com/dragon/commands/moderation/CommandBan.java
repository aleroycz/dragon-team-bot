// ─────────────────────────────────────────────────────────────────────────────
// CommandBan.java
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
public class CommandBan implements SlashCommand {

    private final SanctionService sanctionService;
    private final Embed embed;

    @Override
    public String getName() { return "ban"; }

    @Override
    public String getDescription() { return "Permanently or temporarily ban a member from the server."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS))
                .addOption(OptionType.USER,    "member",   "The member to ban",                          true)
                .addOption(OptionType.STRING,  "reason",   "The reason for the ban",                     true)
                .addOption(OptionType.INTEGER, "duration", "Duration in seconds (omit for permanent ban)", false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.BAN_MEMBERS)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You do not have permission to issue bans.").build()
            ).setEphemeral(true).queue();
            return;
        }

        Member target  = Objects.requireNonNull(event.getOption("member")).getAsMember();
        String reason  = Objects.requireNonNull(event.getOption("reason")).getAsString();
        Long duration  = event.getOption("duration") != null
                ? Objects.requireNonNull(event.getOption("duration")).getAsLong()
                : null;

        if (target == null) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Member Not Found",
                    "Could not resolve the target member. Are they still in the server?").build()
            ).setEphemeral(true).queue();
            return;
        }

        if (target.getIdLong() == event.getMember().getIdLong()) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Invalid Target",
                    "You cannot ban yourself.").build()
            ).setEphemeral(true).queue();
            return;
        }

        if (target.isOwner() || target.hasPermission(Permission.ADMINISTRATOR)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Invalid Target",
                    "You cannot ban a server owner or administrator.").build()
            ).setEphemeral(true).queue();
            return;
        }

        SanctionRequest request = new SanctionRequest(
                target.getId(),
                target.getUser().getAsTag(),
                event.getMember().getId(),
                event.getMember().getUser().getAsTag(),
                Objects.requireNonNull(event.getGuild()).getId(),
                SanctionType.BAN,
                reason,
                duration
        );

        try {
            var response = sanctionService.ban(request);

            event.replyEmbeds(
                    embed.error(
                                    IconRegistry.ICON_ALERT,
                                    "🔨 Ban Issued",
                                    """
                                    **%s** has been banned from the server.
                                    
                                    **Reason:** %s
                                    """.formatted(target.getUser().getAsTag(), reason)
                            )
                            .addField("Case ID",   "#" + response.id(),                                            true)
                            .addField("Duration",  response.permanent() ? "Permanent" : formatDuration(duration),  true)
                            .addField("Expires",   response.expiresAt() != null ? response.expiresAt().toString() : "Never", true)
                            .addField("Issued By", event.getMember().getAsMention(),                               true)
                            .setTimestamp(Instant.now())
                            .build()
            ).setEphemeral(true).queue();

        } catch (Exception e) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Error",
                    "Failed to issue ban: " + e.getMessage()).build()
            ).setEphemeral(true).queue();
        }
    }

    private String formatDuration(Long seconds) {
        if (seconds == null)    return "Permanent";
        if (seconds < 60)       return seconds + " second(s)";
        if (seconds < 3600)     return (seconds / 60) + " minute(s)";
        if (seconds < 86400)    return (seconds / 3600) + " hour(s)";
        return (seconds / 86400) + " day(s)";
    }
}
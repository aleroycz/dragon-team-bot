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
public class CommandWarn implements SlashCommand {

    private final SanctionService sanctionService;
    private final Embed embed;

    @Override
    public String getName() {
        return "warn";
    }

    @Override
    public String getDescription() {
        return "Issue a formal warning to a member.";
    }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER,   "member", "The member to warn",          true)
                .addOption(OptionType.STRING, "reason", "The reason for this warning", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MODERATE_MEMBERS)) {
            event.replyEmbeds(
                    embed.error(
                            IconRegistry.ICON_ALERT,
                            "Permission Denied",
                            "You do not have permission to issue warnings."
                    ).build()
            ).setEphemeral(true).queue();
            return;
        }

        Member target = Objects.requireNonNull(event.getOption("member")).getAsMember();
        String reason = Objects.requireNonNull(event.getOption("reason")).getAsString();

        if (target == null) {
            event.replyEmbeds(
                    embed.error(
                            IconRegistry.ICON_ALERT,
                            "Member Not Found",
                            "Could not resolve the target member. Are they still in the server?"
                    ).build()
            ).setEphemeral(true).queue();
            return;
        }

        if (target.getIdLong() == event.getMember().getIdLong()) {
            event.replyEmbeds(
                    embed.error(
                            IconRegistry.ICON_ALERT,
                            "Invalid Target",
                            "You cannot warn yourself."
                    ).build()
            ).setEphemeral(true).queue();
            return;
        }

        if (target.isOwner() || target.hasPermission(Permission.ADMINISTRATOR)) {
            event.replyEmbeds(
                    embed.error(
                            IconRegistry.ICON_ALERT,
                            "Invalid Target",
                            "You cannot issue a warning to a server owner or administrator."
                    ).build()
            ).setEphemeral(true).queue();
            return;
        }

        SanctionRequest request = new SanctionRequest(
                target.getId(),
                target.getUser().getAsTag(),
                event.getMember().getId(),
                event.getMember().getUser().getAsTag(),
                Objects.requireNonNull(event.getGuild()).getId(),
                SanctionType.WARN,
                reason,
                null
        );

        try {
            var response = sanctionService.warn(request);
            int activeWarns = sanctionService.getActiveWarnCount(target.getId(), event.getGuild().getId());

            event.replyEmbeds(
                    embed.warn(
                                    IconRegistry.ICON_ALERT,
                                    "⚠️ Warning Issued",
                                    """
                                    A formal warning has been issued to %s.
                                    
                                    **Reason:** %s
                                    """.formatted(target.getAsMention(), reason)
                            )
                            .addField("Case ID",        "#" + response.id(),                    true)
                            .addField("Active Warnings", activeWarns + " / 3",                  true)
                            .addField("Issued By",       event.getMember().getAsMention(),       true)
                            .addField("Next Action",     resolveNextAction(activeWarns),         false)
                            .setTimestamp(Instant.now())
                            .build()
            ).setEphemeral(true).queue();

        } catch (Exception e) {
            event.replyEmbeds(
                    embed.error(
                            IconRegistry.ICON_ALERT,
                            "Error",
                            "Failed to issue warning: " + e.getMessage()
                    ).build()
            ).setEphemeral(true).queue();
        }
    }

    private String resolveNextAction(int activeWarns) {
        return switch (activeWarns) {
            case 1 -> "⚠️ A 2nd warning will result in a **temporary mute**.";
            case 2 -> "🔇 A 3rd warning will result in **suspension or permanent removal**.";
            default -> "🔨 Member has reached the maximum warning threshold and has been **suspended**.";
        };
    }
}
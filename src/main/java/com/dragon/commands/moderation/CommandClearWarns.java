package com.dragon.commands.moderation;

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
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandClearWarns implements SlashCommand {

    private final SanctionService sanctionService;
    private final ModuleService moduleService;
    private final Embed embed;

    @Override
    public String getName() { return "clearwarns"; }

    @Override
    public String getDescription() { return "Clear all active warnings for a member."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.USER,   "member", "The member to clear warnings for", true)
                .addOption(OptionType.STRING, "reason", "Why the warnings are being cleared", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!moduleService.checkAndReply(ModuleName.MODERATION, event)) return;

        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MODERATE_MEMBERS)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You do not have permission to clear warnings.").build()
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

        String reason       = Objects.requireNonNull(event.getOption("reason")).getAsString();
        String guildId      = Objects.requireNonNull(event.getGuild()).getId();
        String clearedByTag = event.getMember().getUser().getAsTag() + " — " + reason;

        int cleared = sanctionService.clearWarns(target.getId(), guildId, clearedByTag);

        if (cleared == 0) {
            event.replyEmbeds(
                    embed.info(IconRegistry.ICON_CHECKS, "No Active Warnings",
                                    "**" + target.getUser().getAsTag() + "** has no active warnings to clear.")
                            .setThumbnail(target.getEffectiveAvatarUrl())
                            .build()
            ).setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS, "✅ Warnings Cleared",
                                "**" + cleared + "** active warning(s) cleared for **"
                                        + target.getUser().getAsTag() + "**.")
                        .addField("Cleared by", event.getMember().getAsMention(), true)
                        .addField("Count",       cleared + " warn(s)",            true)
                        .addField("Reason",      reason,                          false)
                        .setThumbnail(target.getEffectiveAvatarUrl())
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();
    }
}
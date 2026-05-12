package com.dragon.commands.moderation;

import com.dragon.integration.SlashCommand;
import com.dragon.module.ModuleName;
import com.dragon.service.ModuleService;
import com.dragon.service.moderation.SanctionService;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandRevoke implements SlashCommand {

    private final SanctionService sanctionService;
    private final ModuleService moduleService;
    private final Embed embed;

    @Override
    public String getName() { return "revoke"; }

    @Override
    public String getDescription() { return "Revoke an existing sanction by its case ID."; }

    @Override
    public void configure(SlashCommandData data) {
        data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS))
                .addOption(OptionType.INTEGER, "case_id", "The case ID of the sanction to revoke", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!moduleService.checkAndReply(ModuleName.MODERATION, event)) return;

        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MODERATE_MEMBERS)) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Permission Denied",
                    "You do not have permission to revoke sanctions.").build()
            ).setEphemeral(true).queue();
            return;
        }

        long caseId = Objects.requireNonNull(event.getOption("case_id")).getAsLong();

        if (caseId <= 0) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Invalid Case ID",
                    "Please provide a valid positive case ID.").build()
            ).setEphemeral(true).queue();
            return;
        }

        try {
            sanctionService.revoke(caseId, event.getMember().getUser().getAsTag());

            event.replyEmbeds(
                    embed.info(
                                    IconRegistry.ICON_CHECKS,
                                    "✅ Sanction Revoked",
                                    "Sanction **#" + caseId + "** has been successfully revoked."
                            )
                            .addField("Case ID",    "#" + caseId,                         true)
                            .addField("Revoked By", event.getMember().getAsMention(),      true)
                            .setTimestamp(Instant.now())
                            .build()
            ).setEphemeral(true).queue();

        } catch (IllegalArgumentException e) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Sanction Not Found",
                    "No sanction with ID **#" + caseId + "** was found.").build()
            ).setEphemeral(true).queue();
        } catch (Exception e) {
            event.replyEmbeds(embed.error(IconRegistry.ICON_ALERT, "Error",
                    "Failed to revoke sanction: " + e.getMessage()).build()
            ).setEphemeral(true).queue();
        }
    }
}

package com.dragon.commands.ticket;

import com.dragon.entity.TicketEntity;
import com.dragon.integration.SlashCommand;
import com.dragon.integration.components.modal.CustomModalHandler;
import com.dragon.module.ModuleName;
import com.dragon.service.ModuleService;
import com.dragon.service.ticket.TicketService;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.attachmentupload.AttachmentUpload;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Opens a modal for submitting a bug report and creates a follow-up ticket channel.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>User runs {@code /bug-report} → modal opens with two fields.
 *   <li>On submit, the bot creates a private ticket channel containing a
 *       Components V2 embed with the report details.
 *   <li>A file-upload prompt in the channel instructs the user to attach
 *       screenshots or logs directly (Discord modals cannot accept file uploads).
 *   <li>Staff are notified via their standard ticket permissions.
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommandBugReport implements SlashCommand {

    private final CustomModalHandler modalHandler;
    private final TicketService ticketService;
    private final ModuleService moduleService;
    private final Embed embed;

    @Override
    public String getName() { return "bug-report"; }

    @Override
    public String getDescription() { return "Submit a bug report — opens a private ticket for staff review."; }

    @Override
    public void configure(SlashCommandData data) {
        // No options — interaction is via modal
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!moduleService.checkAndReply(ModuleName.BUG_REPORT, event)) return;
        if (!moduleService.checkAndReply(ModuleName.TICKETS, event))    return;

        Member member = Objects.requireNonNull(event.getMember());
        String modalId = "bug-report-" + member.getId();

        List<ModalTopLevelComponent> rows = List.of(
                Label.of("Feature / Command", TextInput.create("feature", TextInputStyle.SHORT)
                        .setPlaceholder("e.g. Voting system, Ticket creation, Bot startup")
                        .setRequired(true)
                        .setMinLength(3)
                        .setMaxLength(100)
                        .build()),
                Label.of("Description & Steps to Reproduce", TextInput.create("description", TextInputStyle.PARAGRAPH)
                        .setPlaceholder(
                                "1. Navigate to...\n"
                                        + "2. Click / run...\n"
                                        + "3. Expected: ...\n"
                                        + "4. Actual: ...")
                        .setRequired(true)
                        .setMinLength(20)
                        .setMaxLength(1000)
                        .build()),

                Label.of("Screenshot / Video", AttachmentUpload.of("banner-file"))
        );

        modalHandler.handleCustomModal(
                event,
                "Submit a Bug Report",
                List.copyOf(rows),
                modalId,
                (interaction, id) -> handleSubmit(interaction, event.getGuild(), member)
        );
    }

    // ── Modal submission ──────────────────────────────────────────────────────

    private void handleSubmit(
            net.dv8tion.jda.api.interactions.modals.ModalInteraction interaction,
            Guild guild,
            Member reporter) {

        String feature     = Objects.requireNonNull(interaction.getValue("feature")).getAsString();
        String description = Objects.requireNonNull(interaction.getValue("description")).getAsString();

        // Acknowledge the modal immediately so Discord doesn't time out
        interaction.deferReply(true).queue();

        Thread.ofVirtual().start(() -> {
            try {
                TicketEntity ticket = ticketService.openBugReport(guild, reporter, feature, description);

                String channelMention = ticket.getChannelId() != null
                        ? "<#" + ticket.getChannelId() + ">"
                        : "being created";

                interaction.getHook().editOriginalEmbeds(
                        embed.info(IconRegistry.ICON_CHECKS, "🐛 Bug Report Submitted",
                                        "Your report has been received and a private channel has been created: "
                                                + channelMention + "\n\n"
                                                + "Please head over and **attach any screenshots, logs, or recordings** "
                                                + "directly in that channel to help us reproduce the issue.")
                                .addField("📦 Feature",     feature,     false)
                                .addField("🎫 Ticket #",   String.valueOf(ticket.getTicketNumber()), true)
                                .build()
                ).queue();

                log.info("[BugReport] Ticket #{} created by {}", ticket.getTicketNumber(), reporter.getUser().getAsTag());

            } catch (IllegalStateException e) {
                interaction.getHook().editOriginalEmbeds(
                        embed.error(IconRegistry.ICON_ALERT, "Cannot Submit Report", e.getMessage()).build()
                ).queue();
            } catch (Exception e) {
                log.error("[BugReport] Error creating ticket for {}", reporter.getUser().getAsTag(), e);
                interaction.getHook().editOriginalEmbeds(
                        embed.error(IconRegistry.ICON_ALERT, "Something Went Wrong",
                                "Failed to create the ticket. Please try again or contact a staff member.").build()
                ).queue();
            }
        });
    }
}

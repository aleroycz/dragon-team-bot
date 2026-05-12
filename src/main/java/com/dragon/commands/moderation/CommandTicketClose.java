package com.dragon.commands.moderation;

import com.dragon.dto.moderation.SanctionRequest;
import com.dragon.dto.moderation.SanctionType;
import com.dragon.dto.interview.InterviewState;
import com.dragon.integration.SlashCommand;
import com.dragon.integration.listeners.InterviewAgeVerificationListener;
import com.dragon.service.moderation.SanctionService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CommandTicketClose extends ListenerAdapter implements SlashCommand {

    // ── Icon URLs (replace with your hosted CDN paths) ────────────────────────
    private static final String ICON_CLOSE_CONFIRM = "https://cdn.discordapp.com/attachments/1501768878051692624/1501774762349690910/1778121411679.png?ex=69fd4c3c&is=69fbfabc&hm=6760fc4d05ebb4de344311718b68f0229bc115d42038247b2992714479543cab&"; // amber warning
    private static final String ICON_OUTCOME_OK    = "https://cdn.discordapp.com/attachments/1501768878051692624/1501774761737453729/1778121411679.png?ex=69fd4c3c&is=69fbfabc&hm=9674d223b9088dbca6402192409c547ce7c8aedf7342197d3ae8105753714034&";    // green check
    private static final String ICON_OUTCOME_DENY  = "https://cdn.discordapp.com/attachments/1501768878051692624/1501774760894267483/1778121411679.png?ex=69fd4c3c&is=69fbfabc&hm=904d78cd8e27a99b4d7eb08ae17707b68004b3000788ba169ac13bd5c002e51b&";  // red ban

    private static final int COLOR_CLOSE  = 0xF5A623; // amber
    private static final int COLOR_ACCEPT = 0x2ECC71; // green
    private static final int COLOR_REJECT = 0xE74C3C; // red

    // ── Button / modal IDs ────────────────────────────────────────────────────
    private static final String BTN_ACCEPTED          = "ticket:close:accepted";
    private static final String BTN_REJECTED_INTERNAL = "ticket:close:rejected_internal";
    private static final String BTN_REJECTED_RULES    = "ticket:close:rejected_rules";

    @Value("${discord.closed-ticket.category.id}")
    private String closedInterviewId;

    @Value("${discord.player.role.id}")
    private String playerRoleId;

    private final InterviewAgeVerificationListener interviewAgeVerificationListener;
    private final SanctionService sanctionService;

    @Override
    public String getName() {
        return "ticket-close";
    }

    @Override
    public String getDescription() {
        return "Close the current ticket.";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        TextChannel currentChannel = (TextChannel) event.getChannel();
        if (Objects.equals(currentChannel.getParentCategoryId(), closedInterviewId)) {
            event.reply("This ticket has already been closed.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Container container = Container.of(

                // ── Header ────────────────────────────────────────────────────
                Section.of(
                        Thumbnail.fromUrl(ICON_CLOSE_CONFIRM),
                        TextDisplay.of(
                                "# Close Ticket\n" +
                                        "**Dragon Team**\n" +
                                        "-# SELECT OUTCOME"
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                // ── Instructions ──────────────────────────────────────────────
                TextDisplay.of("-# **INTERVIEW OUTCOME**"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        "Please select the outcome of this interview before closing the ticket. " +
                                "This will be logged and the applicant will be notified accordingly."
                ),
                TextDisplay.of("-# This action cannot be undone. Choose carefully."),

                Separator.createDivider(Separator.Spacing.LARGE),

                // ── Outcome options ───────────────────────────────────────────
                Section.of(
                        Thumbnail.fromUrl(ICON_OUTCOME_OK),
                        TextDisplay.of("-# **OPTION 1 — ACCEPTED**"),
                        TextDisplay.of("The applicant has met all requirements and is approved to join the team."),
                        TextDisplay.of("-# A welcome message will be sent and roles will be assigned automatically.")

                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(
                        Thumbnail.fromUrl(ICON_OUTCOME_DENY),
                        TextDisplay.of("-# **OPTION 2 — REJECTED (INTERNAL)**"),
                        TextDisplay.of("The applicant did not meet internal team requirements at this time."),
                        TextDisplay.of("-# No specific rule violation — the team simply was not a match.")

                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(
                        Thumbnail.fromUrl(ICON_OUTCOME_DENY),
                        TextDisplay.of("-# **OPTION 3 — REJECTED (RULE VIOLATION)**"),
                        TextDisplay.of("The applicant was rejected due to a breach of community or team rules."),
                        TextDisplay.of("-# You will be asked to provide a reason before the ticket is closed.")

                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                // ── Action buttons ────────────────────────────────────────────
                ActionRow.of(
                        Button.success(BTN_ACCEPTED,          "Accept"),
                        Button.secondary(BTN_REJECTED_INTERNAL, "Reject — Internal"),
                        Button.danger(BTN_REJECTED_RULES,     "Reject — Rule Violation")
                )

        ).withAccentColor(COLOR_CLOSE);

        event.replyComponents(container)
                .useComponentsV2()
                .setEphemeral(true)
                .queue();

        event.getJDA().addEventListener(this);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Button interactions
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        switch (event.getCustomId()) {

            case BTN_ACCEPTED -> handleOutcome(event, OutcomeType.ACCEPTED);
            case BTN_REJECTED_INTERNAL -> handleOutcome(event, OutcomeType.REJECTED_INTERNAL);
            case BTN_REJECTED_RULES -> handleOutcome(event, OutcomeType.REJECTED_RULES);
        }
    }

    private void handleOutcome(ButtonInteractionEvent event, OutcomeType outcome) {
        TextChannel currentChannel = (TextChannel) event.getChannel();

        InterviewState currentState = interviewAgeVerificationListener.getChannelState(event.getChannelId());
        if (currentState == null) {
            event.reply("Error while reading the channel state.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Member targetMember = event.getGuild().getMemberById(currentState.memberId());
        if (targetMember == null) {
            event.reply("This member has left the server.")
                    .setEphemeral(true)
                    .queue();
            interviewAgeVerificationListener.unregisterInterview(event.getChannelId());
            return;
        }

        Role playerRole = event.getGuild().getRoleById(playerRoleId);
        if (playerRole == null) {
            event.reply("The player role is not available.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Category closedCategory = event.getGuild().getCategoryById(closedInterviewId);
        if (closedCategory == null) {
            event.reply("Closed interview category not found. Please contact a developer.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // TODO: implement per-outcome logic:
        //   ACCEPTED          → assign member role, send welcome message, move/delete channel
        //   REJECTED_INTERNAL → notify applicant, move/delete channel
        //   REJECTED_RULES    → notify applicant with reason, log violation, move/delete channel

        switch (outcome) {
            case ACCEPTED -> event.getGuild().addRoleToMember(targetMember, playerRole).queue(success -> {
                this.handleOfficialWelcome(targetMember);
            });

            case REJECTED_INTERNAL -> sanctionService.ban(new SanctionRequest(
                    targetMember.getId(),
                    targetMember.getUser().getAsTag(),
                    event.getMember().getId(),
                    event.getMember().getUser().getAsTag(),
                    event.getGuild().getId(),
                    SanctionType.BAN,
                    "Interview Rejected : Internal reasons.",
                    null
            ));

            case REJECTED_RULES -> sanctionService.ban(new SanctionRequest(
                    targetMember.getId(),
                    targetMember.getUser().getAsTag(),
                    event.getMember().getId(),
                    event.getMember().getUser().getAsTag(),
                    event.getGuild().getId(),
                    SanctionType.BAN,
                    "Interview Rejected : Rules violations.",
                    null
            ));
        }

        currentChannel.getManager().setParent(closedCategory).queue();

        interviewAgeVerificationListener.unregisterInterview(event.getChannelId());
    }

    private void handleOfficialWelcome(Member targetMember) {

    }

    // ─────────────────────────────────────────────────────────────────────────
    // Outcome types
    // ─────────────────────────────────────────────────────────────────────────

    private enum OutcomeType {
        ACCEPTED("Accepted"),
        REJECTED_INTERNAL("Rejected — Internal"),
        REJECTED_RULES("Rejected — Rule Violation");

        final String label;
        OutcomeType(String label) { this.label = label; }
    }
}
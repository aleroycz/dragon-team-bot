package com.dragon.integration.listeners;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import com.dragon.integration.DiscordEventListener;
import com.dragon.integration.components.button.confirm.ConfirmHandler;
import com.dragon.utils.Embed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static com.dragon.commands.CommandAbout.LOGO_URL;

/**
 * Handles member join/leave events and interview ticket lifecycle management.
 */
@Component
@RequiredArgsConstructor
public class UserManagementListener extends ListenerAdapter implements DiscordEventListener {

    // ── Interview icon URLs ───────────────────────────────────────────────────────
    private static final String ICON_INTERVIEW_SCROLL = "https://cdn.discordapp.com/attachments/1501768878051692624/1501782974889328781/1778123370061.png?ex=69fd53e2&is=69fc0262&hm=3089063ea76abb69b90ae9f719fe70af89fe4a836503dc41b2015b9be4f8aba2&"; // blurple scroll
    private static final String ICON_INTERVIEW_AGE    = "https://cdn.discordapp.com/attachments/1501768878051692624/1501782976285904916/1778123370061.png?ex=69fd53e3&is=69fc0263&hm=a7e5a41a6ced98df82a64a0cda48e84a110773215634e831d03e4028f11142a9&";    // amber cake
    private static final String ICON_INTERVIEW_RANK   = "https://cdn.discordapp.com/attachments/1501768878051692624/1501782976009076827/1778123370061.png?ex=69fd53e3&is=69fc0263&hm=19cf269226e548c7ba518684f424488b31753e9b6ab47e9998a7328f0d92dbbb&";   // blurple trophy
    private static final String ICON_INTERVIEW_EXP    = "https://cdn.discordapp.com/attachments/1501768878051692624/1501782975732383875/1778123370061.png?ex=69fd53e3&is=69fc0263&hm=6ff74e4273fa77223f11b9de1f671a469a774040cc460e7b14c82892ebf86872&";    // blurple controller

    // ── Welcome icon URLs ─────────────────────────────────────────────────────────
    private static final String ICON_WELCOME_WAVE     = "https://cdn.discordapp.com/attachments/1501768878051692624/1501782975459758153/1778123370061.png?ex=69fd53e3&is=69fc0263&hm=2e235d41ed1d6a6dd8958959f598af62f291fba84e61f26166a7218f9eb17c55&";     // green wave
    private static final String ICON_WELCOME_RULES    = "https://cdn.discordapp.com/attachments/1501768878051692624/1501782975186993342/1778123370061.png?ex=69fd53e3&is=69fc0263&hm=d076d5b94e58bc0eac8f98b698d022ab1753ed6e8a961c8c786e89a8139c0d8f&";    // blurple book

    private static final int COLOR_INTERVIEW = 0x7289DA; // Discord blurple
    private static final int COLOR_WELCOME   = 0x2ECC71; // green

    private static final long INTERVIEW_PERMISSIONS = Permission.MESSAGE_SEND.getRawValue()
            | Permission.VIEW_CHANNEL.getRawValue()
            | Permission.MESSAGE_HISTORY.getRawValue()
            | Permission.MESSAGE_ATTACH_FILES.getRawValue();

    private final ConfirmHandler confirmHandler;

    @Value("${discord.recrue.role.id}")
    private String recrueRoleId;

    @Value("${discord.entry-channel.id}")
    private String welcomeChannelId;

    @Value("${discord.open-ticket.category.id}")
    private String openInterviewId;

    @Value("${discord.closed-ticket.category.id}")
    private String closedInterviewId;

    private final InterviewAgeVerificationListener ageVerification;

    // -------------------------------------------------------------------------
    // Member Join
    // -------------------------------------------------------------------------

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Role recrueRole = event.getJDA().getRoleById(recrueRoleId);
        if (recrueRole == null) {
            handleFail(event.getJDA(), event.getMember(), new IllegalStateException("Recrue role not found: " + recrueRoleId));
            return;
        }

        event.getGuild()
                .addRoleToMember(event.getUser(), recrueRole)
                .reason("Automatic role assignment on join.")
                .queue(
                        success -> handleSuccess(event.getJDA(), event.getMember(), event.getGuild()),
                        error -> handleFail(event.getJDA(), event.getMember(), error)
                );
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private void handleSuccess(JDA jda, Member member, Guild guild) {
        sendWelcomeMessage(jda, member);
        openInterviewTicket(jda, member, guild);
    }

    private void handleFail(JDA jda, Member member, Throwable error) {
        error.printStackTrace();
    }

    // -------------------------------------------------------------------------
    // Welcome Message
    // -------------------------------------------------------------------------

    private void sendWelcomeMessage(JDA jda, Member member) {
        TextChannel welcomeChannel = jda.getTextChannelById(welcomeChannelId);
        if (welcomeChannel == null) {
            System.err.println("[UserManagement] Welcome channel not found: " + welcomeChannelId);
            return;
        }

        net.dv8tion.jda.api.components.container.Container container = net.dv8tion.jda.api.components.container.Container.of(
                Section.of(
                        Thumbnail.fromUrl(member.getAvatarUrl() != null ? member.getAvatarUrl() : LOGO_URL),
                        TextDisplay.of(
                                "# Welcome to Dragon Team!\n" +
                                        "**Dragon Team**\n" +
                                        "-# NEW MEMBER"
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **HEY, " + member.getEffectiveName().toUpperCase() + "!**"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        "We are thrilled to have you here, " + member.getAsMention() + "!"
                ),
                TextDisplay.of(
                        "Before diving in, please take a moment to read our **rules** — " +
                                "they keep our community friendly and fun for everyone."
                ),
                TextDisplay.of(
                        "-# If you have any questions, don't hesitate to reach out to a staff member. We're happy to help."
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(
                        Thumbnail.fromUrl(ICON_WELCOME_RULES),
                        TextDisplay.of("-# **BEFORE YOU GET STARTED**"),
                        TextDisplay.of("Familiarise yourself with our community rules to ensure a positive experience for everyone."),
                        TextDisplay.of("-# Violations may result in warnings, mutes, or removal from the server.")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# Use the button below to read the full rules."),
                ActionRow.of(
                        Button.link(
                                "https://discord.com/channels/1488995745062195493/1489005045671264397",
                                "Read the Rules"
                        )
                )

        ).withAccentColor(COLOR_WELCOME);

        welcomeChannel.sendMessageComponents(container)
                .useComponentsV2()
                .mention(member)
                .queue();
    }

    private void openInterviewTicket(JDA jda, Member member, Guild guild) {
        Category interviewCategory = guild.getCategoryById(openInterviewId);
        if (interviewCategory == null) {
            System.err.println("[UserManagement] Interview category not found: " + openInterviewId);
            return;
        }

        Embed embed = new Embed(jda);

        guild.createTextChannel("interview-" + member.getEffectiveName(), interviewCategory)
                .setNSFW(false)
                .addMemberPermissionOverride(member.getIdLong(), INTERVIEW_PERMISSIONS, 0)
                .addRolePermissionOverride(guild.getPublicRole().getIdLong(), 0, INTERVIEW_PERMISSIONS)
                .queue(
                        channel -> sendInterviewEmbed(embed, channel, member),
                        Throwable::printStackTrace
                );
    }

    private void sendInterviewEmbed(Embed embed, TextChannel channel, Member member) {
        ageVerification.registerInterview(channel.getId(), member.getId(), member.getEffectiveName());

        net.dv8tion.jda.api.components.container.Container container = net.dv8tion.jda.api.components.container.Container.of(
                Section.of(
                        Thumbnail.fromUrl(ICON_INTERVIEW_SCROLL),
                        TextDisplay.of(
                                "# Dragon Team — Interview"
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **WELCOME, " + member.getEffectiveName().toUpperCase() + "**"),
                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(
                        "Thank you for your interest in joining **Dragon Team**!"
                ),
                TextDisplay.of(
                        "Please answer the questions below as honestly and thoroughly as you can. " +
                                "Your answers help us understand you better and ensure the best fit for the team. " +
                                "A staff member will review your responses and follow up shortly."
                ),
                TextDisplay.of("-# Take your time — there are no wrong answers. Good luck!"),

                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(
                        Thumbnail.fromUrl(ICON_INTERVIEW_AGE),
                        TextDisplay.of("-# **QUESTION 1 — AGE**"),
                        TextDisplay.of("How old are you?"),
                        TextDisplay.of("-# We ask this to ensure compliance with our community age requirements.")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(
                        Thumbnail.fromUrl(ICON_INTERVIEW_RANK),
                        TextDisplay.of("-# **QUESTION 2 — RANK**"),
                        TextDisplay.of("What is your **current** and **peak rank** in Valorant?"),
                        TextDisplay.of("-# Please include both ranks if they differ.")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(
                        Thumbnail.fromUrl(ICON_INTERVIEW_EXP),
                        TextDisplay.of("-# **QUESTION 3 — EXPERIENCE**"),
                        TextDisplay.of("When did you first start playing Valorant?"),
                        TextDisplay.of("-# Approximate date or patch era is fine.")

                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of(
                        "-# Reply to each question directly in this channel. " +
                                "If you need to close this interview, use the button below."
                )
        ).withAccentColor(COLOR_INTERVIEW);

        channel.sendMessageComponents(container)
                .useComponentsV2()
                .queue();
    }
}
package com.dragon.integration.listeners;

import com.dragon.dto.interview.AgeVerificationQuestion;
import com.dragon.dto.interview.AgeVerificationResult;
import com.dragon.dto.interview.InterviewState;
import com.dragon.integration.DiscordEventListener;
import com.dragon.service.moderation.AgeVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterviewAgeVerificationListener extends ListenerAdapter implements DiscordEventListener {

    private static final String ICON_AV_SHIELD  = "https://cdn.discordapp.com/attachments/1501768878051692624/1501777488865525790/1778122061238.png?ex=69fd4ec7&is=69fbfd47&hm=86053e90c1fa7cd25068fc93fe461f1e960930ba7d36ba1a6c4fd20efdce474d&";  // blue shield-lock
    private static final String ICON_AV_IDCARD  = "https://cdn.discordapp.com/attachments/1501768878051692624/1501777489423499264/1778122061238.png?ex=69fd4ec7&is=69fbfd47&hm=d13cda1665ac7e459f7064908f923ce042edd37e4f8e4a02a48e22444632b62a&";  // amber ID card
    private static final String ICON_AV_EYEOFF  = "https://cdn.discordapp.com/attachments/1501768878051692624/1501777489695871086/1778122061238.png?ex=69fd4ec7&is=69fbfd47&hm=6b9e4b432d0076c17a659b10f670569f78ad6b61b845e48d8c70537184675a96&";  // blue eye-off
    private static final String ICON_AV_CHECK   = "https://cdn.discordapp.com/attachments/1501768878051692624/1501777489150742680/1778122061238.png?ex=69fd4ec7&is=69fbfd47&hm=a3c5412b9ca6dd874ed4fd7ca2d8c3ffdb8bbde4b6de87ff40df192eebd263fa&";   // green check
    private static final int COLOR_AV = 0x5A78C8; // slate-blue — neutral/informational

    private static final int MAX_FOLLOW_UPS         = 2;
    private static final int MAX_HISTORY_MESSAGES   = 30;
    private static final int MAX_MESSAGE_LENGTH     = 500;

    private final AgeVerificationService verificationService;

    private final Map<String, InterviewState> activeInterviews = new ConcurrentHashMap<>();

    /**
     * Registers a newly created interview channel for age verification monitoring.
     * Call this from {@code UserManagementListener} after the interview channel is created.
     */
    public void registerInterview(String channelId, String memberId, String memberName) {
        activeInterviews.put(channelId, InterviewState.create(channelId, memberId, memberName));
        log.info("[AgeVerification] Registered interview — channel: {}, member: {}", channelId, memberName);
    }

    /**
     * Unregisters an interview channel when the ticket is closed.
     * Call this from {@code UserManagementListener} when the ticket is closed.
     */
    public void unregisterInterview(String channelId) {
        InterviewState removed = activeInterviews.remove(channelId);
        if (removed != null) {
            log.info("[AgeVerification] Unregistered interview — channel: {}, member: {}",
                    channelId, removed.memberName());
        }
    }

    public InterviewState getChannelState(String channelId) {
        return this.activeInterviews.getOrDefault(channelId, null);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String channelId = event.getChannel().getId();
        InterviewState state = activeInterviews.get(channelId);
        if (state == null) return;

        if (event.getAuthor().isBot()) return;

        InterviewState.VerificationStage stage = state.mutableState().stage();
        if (stage == InterviewState.VerificationStage.VERIFICATION_SENT
                || stage == InterviewState.VerificationStage.PASSED
                || stage == InterviewState.VerificationStage.FAILED) return;

        String content = event.getMessage().getContentStripped().trim();
        if (content.isEmpty()) return;
        if (!event.getAuthor().getId().equals(state.memberId())) return;

        if (state.messageHistory().size() >= MAX_HISTORY_MESSAGES) {
            log.warn("[AgeVerification] Message history limit reached for channel {} — ignoring further input", channelId);
            return;
        }

        String capped = content.length() > MAX_MESSAGE_LENGTH
                ? content.substring(0, MAX_MESSAGE_LENGTH)
                : content;

        state.appendMessage(state.memberName(), capped);

        if (state.messageHistory().isEmpty()) return;

        TextChannel channel = event.getJDA().getTextChannelById(channelId);
        if (channel == null) return;

        Thread.ofVirtual().start(() -> process(channel, state, event.getJDA()));
    }

    private void process(TextChannel channel, InterviewState state, JDA jda) {
        try {
            AgeVerificationResult result = verificationService.analyse(state.buildHistoryText());

            log.info("[AgeVerification] Channel {} — confident={}, likelyAdult={}, confidence={}, reason={}",
                    channel.getId(),
                    result.confident(),
                    result.likelyAdult(),
                    result.confidence(),
                    result.reason()
            );

            if (result.confident() && result.likelyAdult()) {
                activeInterviews.put(channel.getId(), state.withStage(InterviewState.VerificationStage.PASSED));
                log.info("[AgeVerification] Channel {} passed — member is 18+", channel.getId());
                return;
            }

            if (result.confident()) {
                log.warn("[AgeVerification] Channel {} failed — member appears to be under 18", channel.getId());
                activeInterviews.put(channel.getId(), state.withStage(InterviewState.VerificationStage.FAILED));
                sendIdRequestEmbed(channel, jda);
                return;
            }

            int rounds = state.mutableState().followUpCount();
            if (rounds < MAX_FOLLOW_UPS) {
                String question = resolveFollowUpQuestion(result, rounds);
                activeInterviews.put(channel.getId(), state.withFollowUpIncremented());
                channel.sendMessage(
                        "💬 Just a couple of quick questions to complete your application — " + question
                ).queue();
            } else {
                log.info("[AgeVerification] Channel {} — follow-ups exhausted, escalating to Veriff", channel.getId());
                sendIdRequestEmbed(channel, jda);
            }

        } catch (Exception e) {
            log.error("[AgeVerification] Error processing channel {}", channel.getId(), e);
        }
    }

    /**
     * Resolves the follow-up question to ask.
     * Prefers the AI-generated question, falls back to a predefined one from the enum.
     */
    private String resolveFollowUpQuestion(AgeVerificationResult result, int round) {
        if (result.followUpQuestion() != null && !result.followUpQuestion().isBlank()) {
            return result.followUpQuestion();
        }
        AgeVerificationQuestion[] questions = AgeVerificationQuestion.values();
        return questions[round % questions.length].text;
    }

    /**
     * Sends the ID photo request embed into the interview channel.
     *
     * Informs the applicant they may redact everything except their date of birth.
     * Call this instead of (or before) sendVeriffEmbed when you want a manual
     * staff-reviewed step prior to escalating to Veriff.
     */
    private void sendIdRequestEmbed(TextChannel channel, JDA jda) {
        if (Boolean.TRUE.equals(activeInterviews.get(channel.getId()).mutableState().veriffTriggered())) return;

        activeInterviews.put(channel.getId(),
                activeInterviews.get(channel.getId()).withVeriffTriggered());

        Container container = Container.of(

                // ── Header ────────────────────────────────────────────────────
                Section.of(
                        Thumbnail.fromUrl(ICON_AV_IDCARD),
                        TextDisplay.of(
                                "# Age Verification Required\n" +
                                        "**Dragon Team**\n" +
                                        "-# IDENTITY CONFIRMATION"
                        )
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("-# **WHY THIS IS NEEDED**"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(
                        "Our automated screening and your interview responses were not sufficient " +
                                "to confirm your age with confidence."
                ),
                TextDisplay.of(
                        "This is not a negative reflection on you — it simply means we need a little more to go on. " +
                                "To proceed, please send a photo of a valid ID confirming you are **18 years of age or older**."
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(
                        Thumbnail.fromUrl(ICON_AV_IDCARD),
                        TextDisplay.of("-# **WHAT TO SEND**"),
                        TextDisplay.of("Only your **date of birth** needs to be visible — everything else can be covered."),
                        TextDisplay.of("-# Passport  —  Driving licence  —  National ID card")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(
                        Thumbnail.fromUrl(ICON_AV_EYEOFF),
                        TextDisplay.of("-# **YOU MAY REDACT EVERYTHING ELSE**"),
                        TextDisplay.of("Cover or black out any other details before sending. We only need your date of birth."),
                        TextDisplay.of("-# Full name  —  Address  —  ID number  —  Photo  —  Signature  —  Nationality")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(
                        Thumbnail.fromUrl(ICON_AV_SHIELD),
                        TextDisplay.of("-# **YOUR PRIVACY IS PROTECTED**"),
                        TextDisplay.of("Your image is reviewed by staff solely to confirm your age."),
                        TextDisplay.of("-# It is never stored or shared, and is deleted immediately after verification.")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                Section.of(
                        Thumbnail.fromUrl(ICON_AV_CHECK),
                        TextDisplay.of("-# **HOW TO PROCEED**"),
                        TextDisplay.of("Send your photo directly in this channel when you are ready."),
                        TextDisplay.of("-# If you have any concerns, please contact a senior staff member before proceeding.")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of(
                        "-# If you have any concerns about this process, please contact a senior staff member before proceeding."
                )

        ).withAccentColor(COLOR_AV);

        channel.sendMessageComponents(container)
                .useComponentsV2()
                .queue();

        log.info("[AgeVerification] ID request container sent — channel: {}", channel.getId());
    }
}
package com.dragon.service;

import com.dragon.entity.UserConsentEntity;
import com.dragon.repository.UserConsentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserConsentService {

    private final JDA jda;
    private final UserConsentRepository userConsentRepository;

    @Transactional
    public UserConsentEntity getConsent(UserSnowflake member) {
        return this.userConsentRepository
                .findByMemberId(member.getId())
                .orElse(UserConsentEntity.builder().memberId(member.getId()).consent(false).consentRequested(false).build());
    }

    @Transactional
    public void updateOrCreateConsent(Member member, boolean value) {
        UserConsentEntity result = this.userConsentRepository
                .findByMemberId(member.getId())
                .orElse(UserConsentEntity.builder().memberId(member.getId()).consent(value).consentRequested(false).build());
        userConsentRepository.save(result);
    }

    @Transactional
    public boolean hasConsent(Member member) {
        return this.userConsentRepository.findByMemberId(member.getId())
                .orElse(UserConsentEntity.builder() // Fallback to no consent if the entity doesn't exist.
                        .consent(false)
                        .memberId(member.getId())
                        .consentRequested(false)
                        .build()).getConsent();
    }

    @Transactional
    public boolean hasConsent(UserSnowflake member) {
        return this.userConsentRepository.findByMemberId(member.getId())
                .orElse(UserConsentEntity.builder() // Fallback to no consent if the entity doesn't exist.
                        .consent(false)
                        .memberId(member.getId())
                        .consentRequested(false)
                        .build()).getConsent();
    }

    public void handleConsentForm(UserConsentEntity consentEntity) {
        this.handleConsentForm(consentEntity, false, null);
    }

    public void handleConsentForm(UserConsentEntity consentEntity, boolean ephemeral, SlashCommandInteractionEvent event) {
        consentEntity.setConsentRequested(true);
        userConsentRepository.save(consentEntity);

        Container container = Container.of(
                Section.of(
                        Thumbnail.fromUrl("https://cdn.discordapp.com/attachments/1501768878051692624/1501782975186992342/1778123370061.png?ex=6a014863&is=69fff6e3&hm=0480f7e1288bb5263faa5f7fab235cb7d16def8935968999dfc9646758e1ad14&"),
                        TextDisplay.of("## 🎙️ A quick thing before we get started"),
                        TextDisplay.of("*This will only take a second, we promise.*")
                ),

                Separator.create(false, Separator.Spacing.LARGE),

                Section.of(
                        Thumbnail.fromUrl("https://cdn.discordapp.com/attachments/1501768878051692624/1501777489423499264/1778122061238.png?ex=6a01ec07&is=6a009a87&hm=b0aa9c6f72524827088a3c35c0595d020437d054365f462c5b25585a27cf20bf&"),
                        TextDisplay.of("""
                            Hey! 👋
                            
                            We've been working on something we think you'll genuinely find useful — \
                            an **AI-powered personal performance tool** that listens to your voice \
                            during team sessions and gives you honest, private feedback on how you're doing.
                            
                            Think of it like having a coach in your ear — except it's just for you, \
                            and nobody else ever sees it.
                            """),

                                    TextDisplay.of("""
                            ### So what does it actually do?
                            
                            During voice sessions, the system will quietly:
                            
                            • Listen to and **transcribe** what you say in voice channel (Premier).
                            • Pick up on things like your **callout quality**, **communication clarity**, and **team presence**
                            • Put together a **personal report** that only you can see
                            • Track how you evolve **over time** so you can see real progress
                            
                            That's it. No judgement, no leaderboard, no staff involvement. Just useful feedback for yourself.
                            """),

                                    TextDisplay.of("""
                            ### What about my privacy?
                            
                            We take this seriously. Here's exactly what we commit to:
                            
                            • 🔒 Your transcriptions and reports are **yours alone** — no staff, no teammates, nobody else
                            • 🚫 Your data is **never used** for anything other than generating your personal feedback
                            • 🗑️ You can ask us to **delete everything** at any point, no questions asked
                            • 🔄 You can **change your mind** whenever you want with `/user-consent`
                            """),

                                    TextDisplay.of("""
                            ### Not interested? That's completely fine.
                            
                            This is 100% optional. Saying no doesn't affect your place in the team, \
                            your access to events, or anything else. You'll just miss out on the \
                            personal feedback side of things — which, honestly, we think is pretty cool, \
                            but we respect your choice either way. 🙂
                            """)
                ),

                Separator.create(false, Separator.Spacing.LARGE),

                TextDisplay.of("**What would you like to do?**"),

                ActionRow.of(
                        Button.success("user:consent:" + consentEntity.getMemberId() + ":accept", "Yes, I'm in!"),
                        Button.danger("user:consent:" + consentEntity.getMemberId() + ":deny", "No thanks")
                ),

                Separator.create(false, Separator.Spacing.SMALL),

                TextDisplay.of("*Changed your mind later? Just run `/user-consent` and we'll sort it out.*")
        );

        if (ephemeral) {
            event.replyComponents(container)
                    .useComponentsV2()
                    .setEphemeral(true)
                    .queue(
                            messageForm -> {
                                consentEntity.setMessageId(messageForm.getId());
                                userConsentRepository.save(consentEntity);

                                log.info("[Callback/Consent] Consent form sent to {} successfully.", consentEntity.getMemberId());
                            },
                            error -> {
                                log.error("[Callback/Consent] Unable to send consent form to {}, Error : {}", consentEntity.getMemberId(), error.getMessage());
                                error.printStackTrace();
                            }
                    );
        } else {
            Objects.requireNonNull(this.jda.getUserById(consentEntity.getMemberId()))
                    .openPrivateChannel()
                    .queue(
                            success -> {
                                success.sendMessageComponents(container)
                                        .useComponentsV2()
                                        .queue(
                                                messageForm -> {
                                                    consentEntity.setMessageId(messageForm.getId());
                                                    userConsentRepository.save(consentEntity);

                                                    log.info("[Callback/Consent] Consent form sent to {} successfully.", consentEntity.getMemberId());
                                                },
                                                error -> {
                                                    log.error("[Callback/Consent] Unable to send consent form to {}, Error : {}", consentEntity.getMemberId(), error.getMessage());
                                                    error.printStackTrace();
                                                }
                                        );
                            },
                            error -> {
                                log.warn("[Callback/Consent] Unable to send private message to user {}. Ignoring consent form.", consentEntity.getMemberId());
                                error.printStackTrace(); // Logging the trace
                            }
                    );
        }
    }
}

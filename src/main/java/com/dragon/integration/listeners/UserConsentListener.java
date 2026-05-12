package com.dragon.integration.listeners;

import com.dragon.entity.UserConsentEntity;
import com.dragon.integration.DiscordEventListener;
import com.dragon.repository.UserConsentRepository;
import com.dragon.service.UserConsentService;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserConsentListener extends ListenerAdapter implements DiscordEventListener {
    private static final String CONSENT_PREFIX = "user:consent:";

    private final UserConsentRepository userConsentRepository;
    private final UserConsentService userConsentService;
    private final Embed embed;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith(CONSENT_PREFIX)) return;

        String[] parts = buttonId.split(":");
        if (parts.length < 4) {
            log.warn("[Consent] Malformed button ID: {}", buttonId);
            return;
        }

        String memberId = parts[2];
        String action   = parts[3];

        if (!event.getUser().getId().equals(memberId)) {
            event.replyEmbeds(
                    embed.error(IconRegistry.ICON_ALERT, "Not your form",
                                    "This consent form wasn't sent to you.")
                            .build()
            ).setEphemeral(true).queue();
            return;
        }

        Optional<UserConsentEntity> consentOpt = userConsentRepository.findByMemberId(memberId);
        if (consentOpt.isEmpty()) {
            log.warn("[Consent] No consent entity found for member: {}", memberId);
            event.replyEmbeds(
                    embed.error(IconRegistry.ICON_ALERT, "Something went wrong",
                                    "We couldn't find your consent record. Please contact a staff member.")
                            .build()
            ).setEphemeral(true).queue();
            return;
        }

        UserConsentEntity consent = consentOpt.get();

        switch (action) {
            case "accept" -> handleAccept(event, consent);
            case "deny"   -> handleDeny(event, consent);
            default -> log.warn("[Consent] Unknown action '{}' in button ID: {}", action, buttonId);
        }
    }

    private void handleAccept(ButtonInteractionEvent event, UserConsentEntity consent) {
        this.userConsentService.updateOrCreateConsent(Objects.requireNonNull(event.getMember()), true);
        this.handleEditButtons(event);

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS, "✅ Consent Accepted",
                                """
                                Thank you! You're all set. 🎉
                                
                                Your voice activity will now be processed during **Premier** sessions \
                                to generate your personal performance reports.
                                
                                *You can change this at any time with `/user-consent`.*
                                """)
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();

        log.info("[Consent] Member {} accepted consent.", consent.getMemberId());
    }

    private void handleDeny(ButtonInteractionEvent event, UserConsentEntity consent) {
        this.userConsentService.updateOrCreateConsent(Objects.requireNonNull(event.getMember()), false);
        this.handleEditButtons(event);

        event.replyEmbeds(
                embed.info(IconRegistry.ICON_CHECKS, "👍 No problem at all",
                                """
                                Your choice has been saved. Your voice activity will **not** be processed.
                                
                                You can still participate in all team activities as normal. \
                                If you change your mind, just run `/user-consent` anytime. 🙂
                                """)
                        .setTimestamp(Instant.now())
                        .build()
        ).setEphemeral(true).queue();

        log.info("[Consent] Member {} declined consent.", consent.getMemberId());
    }

    private void handleEditButtons(ButtonInteractionEvent event) {
        List<ActionRow> updatedRows = event.getMessage().getComponents().stream()
                .flatMap(top -> top.asContainer().getComponents().stream())
                .filter(child -> {
                    child.asActionRow();
                    return true;
                })
                .map(child -> ActionRow.of(
                        child.asActionRow().getComponents().stream()
                                .map(c -> c.asButton().withDisabled(true))
                                .toList()
                ))
                .toList();

        event.getMessage().editMessageComponents(updatedRows).queue();
    }
}

package com.dragon.commands;

import com.dragon.entity.UserConsentEntity;
import com.dragon.integration.SlashCommand;
import com.dragon.integration.components.button.confirm.ConfirmCallback;
import com.dragon.integration.components.button.confirm.ConfirmHandler;
import com.dragon.service.UserConsentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CommandUserConsent implements SlashCommand {

    private final ConfirmHandler confirmHandler;
    private final UserConsentService userConsentService;

    @Override
    public String getName() {
        return "user-consent";
    }

    @Override
    public String getDescription() {
        return "Change the consent for AI evaluation over-voice-chat.";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        UserConsentEntity userConsent = this.userConsentService.getConsent(event.getUser());

        if (userConsent.getId() == null) {
            this.userConsentService.handleConsentForm(userConsent, true, event);
            return;
        }

        this.confirmHandler.sendConfirmation(event, (userConsent.getConsent() ? "Turn off AI-Assisted evaluation." : "Turn on AI-Assisted evaluation."), (userConsent.getConsent() ? "Turn off." : "Turn on.") , new ConfirmCallback() {
            @Override
            public void onConfirm(ButtonInteractionEvent event) throws Exception {
                userConsent.setConsent(!userConsent.getConsent());

                event.reply((userConsent.getConsent() ? "You have turned off AI-Assisted evaluation." : "You have turned on AI-Assisted evaluation.")).setEphemeral(true)
                        .queue();
            }

            @Override
            public void onCancel(ButtonInteractionEvent event) throws Exception {
                event.reply("You cancelled this interaction").setEphemeral(true).queue();
            }
        });
    }
}

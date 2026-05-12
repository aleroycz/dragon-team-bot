package com.dragon.integration.components.button.confirm;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class ConfirmOperation extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ConfirmOperation.class);

    private final ConfirmCallback callback;
    private final String confirmButtonId;
    private final String cancelButtonId;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String clickedId = event.getComponentId();

        if (!clickedId.equals(confirmButtonId) && !clickedId.equals(cancelButtonId)) {
            return;
        }

        if (!event.getUser().getId().equals(event.getComponentId().split("_")[1])) {
            event.reply("This confirmation is not for you.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        try {
            if (clickedId.equals(confirmButtonId)) {
                callback.onConfirm(event);
            } else {
                callback.onCancel(event);
            }
        } catch (Exception e) {
            log.error("Error in confirmation callback", e);
            event.getHook()
                    .editOriginal("❌ An error occurred while processing.")
                    .setComponents()
                    .queue();
        }

        event.getJDA().removeEventListener(this);
    }
}
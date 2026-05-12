package com.dragon.integration.components.modal;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Temporary event listener for a single custom modal submission.
 * Matches the original logic with improved error handling.
 */
@RequiredArgsConstructor
public class ModalOperation extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ModalOperation.class);

    private final ModalCallback callback;
    private final String expectedModalId;
    private final String acceptanceId;

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!event.getModalId().equals(expectedModalId)) {
            return;
        }

        try {
            callback.onSubmit(event.getInteraction(), acceptanceId);
        } catch (Exception e) {
            log.error("Error handling modal submission [id: {}]", acceptanceId, e);
        }

        event.getJDA().removeEventListener(this);
    }
}
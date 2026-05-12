package com.dragon.integration.components.button.accept;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import com.dragon.utils.MessageUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Temporary listener that waits for a specific confirmation button click.
 * Handles security (only original user), error handling, and auto-cleanup.
 */
@RequiredArgsConstructor
public class AcceptOperation extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(AcceptOperation.class);

    private final String acceptanceId;
    private final String expectedButtonId;
    private final Consumer<AcceptContext> onAccept;         // ← changed to Consumer
    private final long requestingUserId;

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!event.getComponentId().equals(expectedButtonId)) {
            return;
        }

        if (event.getUser().getIdLong() != requestingUserId) {
            event.reply("This confirmation is not for you!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        try {
            onAccept.accept(new AcceptContext(event));
        } catch (Exception e) {
            log.error("Confirmation failed [id: {}]", acceptanceId, e);
        }

        MessageUtil.updateInteraction(event.getMessage());

        event.getJDA().removeEventListener(this);
    }
}
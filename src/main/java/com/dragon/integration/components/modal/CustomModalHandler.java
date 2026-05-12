package com.dragon.integration.components.modal;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.ModalTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.interactions.commands.CommandInteraction;

import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;

import com.dragon.integration.listeners.JdaEventListenerManager;
import net.dv8tion.jda.api.modals.Modal;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handler for creating and managing custom modals in Discord.
 * Provides overloads for different interaction types.
 */
@Service
@RequiredArgsConstructor
public class CustomModalHandler {

    private final JdaEventListenerManager listenerManager;

    /**
     * Opens a custom modal from a command interaction.
     */
    public void handleCustomModal(CommandInteraction interaction, String title, List<ModalTopLevelComponent> actionRows, String acceptanceId, ModalCallback callback) {
        String modalId = "custom:" + acceptanceId;

        ModalOperation operation = new ModalOperation(callback, modalId, acceptanceId);

        listenerManager.registerTemporaryListener(operation, 15);

        Modal modal = Modal.create(modalId, title)
                .addComponents(actionRows)
                .build();

        interaction.replyModal(modal).queue();
    }

    /**
     * Opens a custom modal from a button interaction.
     */
    public void handleCustomModal(ButtonInteraction interaction, String title, List<ModalTopLevelComponent> actionRows, String acceptanceId, ModalCallback callback) {
        String modalId = "custom:" + acceptanceId;

        ModalOperation operation = new ModalOperation(callback, modalId, acceptanceId);

        listenerManager.registerTemporaryListener(operation, 15);

        Modal modal = Modal.create(modalId, title)
                .addComponents(actionRows)
                .build();

        interaction.replyModal(modal).queue();
    }

    /**
     * Opens a custom modal from a string select interaction.
     */
    public void handleCustomModal(StringSelectInteraction interaction, String title, List<ModalTopLevelComponent> actionRows, String acceptanceId, ModalCallback callback) {
        String modalId = "custom:" + acceptanceId;

        ModalOperation operation = new ModalOperation(callback, modalId, acceptanceId);

        listenerManager.registerTemporaryListener(operation, 15);

        Modal modal = Modal.create(modalId, title)
                .addComponents(actionRows)
                .build();

        interaction.replyModal(modal).queue();
    }
}
package com.dragon.integration.components.button.confirm;


import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/**
 * Functional interface for handling user confirmation choices.
 * Both methods are called with the full event context.
 */
@FunctionalInterface
public interface ConfirmCallback {

    /**
     * Called when user clicks the "Confirm" button
     */
    void onConfirm(ButtonInteractionEvent event) throws Exception;

    /**
     * Called when user clicks the "Cancel" button
     * (optional default implementation provided)
     */
    default void onCancel(ButtonInteractionEvent event) throws Exception {
        event.deferEdit().queue();
        event.getHook()
                .editOriginal("❌ Action cancelled.")
                .setComponents()
                .queue();
    }
}
package com.dragon.integration.components.modal;

import net.dv8tion.jda.api.interactions.modals.ModalInteraction;

/**
 * Functional interface for handling custom modal submissions
 */
@FunctionalInterface
public interface ModalCallback {

    /**
     * Called when user submits the modal
     *
     * @param interaction The modal interaction event
     * @param acceptanceId The unique identifier used when creating the modal
     * @throws Exception if something goes wrong (will be caught & shown to user)
     */
    void onSubmit(ModalInteraction interaction, String acceptanceId) throws Exception;
}
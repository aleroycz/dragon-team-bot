package com.dragon.integration.components.modal;

import net.dv8tion.jda.api.interactions.modals.ModalInteraction;

/**
 * Simple context wrapper for modal callbacks
 */
public record ModalContext(ModalInteraction interaction, String acceptanceId) {

    public String getValue(String componentId) {
        return interaction.getValue(componentId).getAsString();
    }

    public void replyEphemeral(String message) {
        interaction.reply(message).setEphemeral(true).queue();
    }

    public void deferReply() {
        interaction.deferReply().queue();
    }

    public long getUserId() {
        return interaction.getUser().getIdLong();
    }
}
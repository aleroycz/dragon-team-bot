package com.dragon.integration.components.button.confirm;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

public record ConfirmContext(ButtonInteractionEvent event) {

    public void deferEdit() {
        event.deferEdit().queue();
    }

    public void replyEphemeral(String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    public long getUserId() {
        return event.getUser().getIdLong();
    }
}
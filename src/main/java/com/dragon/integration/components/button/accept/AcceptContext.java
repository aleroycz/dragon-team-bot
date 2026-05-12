package com.dragon.integration.components.button.accept;


import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/**
 * Simple context object passed to AcceptCallback.
 * Gives access to the interaction and useful shortcuts.
 */
public record AcceptContext(ButtonInteractionEvent event) {

    /**
     * @return The ID of the user who clicked the button
     */
    public long getUserId() {
        return event.getUser().getIdLong();
    }

    /**
     * @return The username (tag) of the user who clicked
     */
    public String getUsername() {
        return event.getUser().getAsTag();
    }

    /**
     * @return The effective name (nickname if present) of the user
     */
    public String getEffectiveName() {
        return event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getUser().getName();
    }

    /**
     * Quick access to defer edit (common pattern)
     */
    public void deferEdit() {
        event.deferEdit().queue();
    }

    /**
     * Quick access to reply ephemerally (for errors, etc.)
     */
    public void replyEphemeral(String message) {
        event.reply(message).setEphemeral(true).queue();
    }
}

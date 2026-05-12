package com.dragon.integration.components.button.accept;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

import com.dragon.integration.listeners.JdaEventListenerManager;
import com.dragon.utils.Embed;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * Handles confirmation dialogs with Accept/Reject buttons in Discord.
 * Uses a temporary event listener for each confirmation.
 */
@Service
@RequiredArgsConstructor
public class AcceptHandler {

    private final Embed embedUtil;
    private final JdaEventListenerManager listenerManager;

    public void sendConfirmation(
            IReplyCallback interaction,
            String title,
            String description,
            String confirmText,
            String acceptanceId,
            Consumer<AcceptContext> onAccept
    ) {
        String buttonId = "button:accept_" + acceptanceId;
        long userId = interaction.getUser().getIdLong();

        AcceptOperation listener = new AcceptOperation(
                acceptanceId, buttonId, onAccept, userId
        );

        listenerManager.registerTemporaryListener(listener);

        MessageEmbed embed = embedUtil.simpleAuthoredEmbed()
                .setTitle(title)
                .setDescription(description)
                .setColor(0x5865F2)
                .setFooter("Click to confirm • Expires in 5 minutes")
                .build();

        interaction.replyEmbeds(embed)
                .addComponents(ActionRow.of(Button.success(buttonId, confirmText)))
                .setEphemeral(true)
                .queue();
    }
}
package com.dragon.integration.components.button.confirm;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import com.dragon.integration.listeners.JdaEventListenerManager;
import com.dragon.utils.Embed;
import com.dragon.utils.IconRegistry;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConfirmHandler {

    private final Embed embedUtil;
    private final JdaEventListenerManager listenerManager;

    public void sendConfirmation(
            IReplyCallback interaction,
            String description,
            ConfirmCallback callback
    ) {
        sendConfirmation(interaction, description, "Confirm", callback);
    }

    public void sendConfirmation(
            IReplyCallback interaction,
            String description,
            String confirmButtonText,
            ConfirmCallback callback
    ) {
        String userId = interaction.getUser().getId();
        String confirmId = "button:confirm_" + userId;
        String cancelId = "button:cancel_" + userId;

        ConfirmOperation listener = new ConfirmOperation(callback, confirmId, cancelId);

        listenerManager.registerTemporaryListener(listener);

        MessageEmbed embed = embedUtil.simpleAuthoredEmbed()
                .setAuthor("Confirm Operation", null, IconRegistry.ICON_QUESTION_MARK)
                .setDescription(description)
                .setColor(0x5865F2)
                .setFooter("This message expires in 5 minutes • Only you can respond")
                .build();

        interaction.replyEmbeds(embed)
                .addComponents(
                        ActionRow.of(
                                Button.success(confirmId, confirmButtonText),
                                Button.danger(cancelId, "Cancel")
                        )
                )
                .setEphemeral(true)
                .queue();
    }
}
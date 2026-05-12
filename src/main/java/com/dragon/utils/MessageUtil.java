/*
---------------------------------------------------------------------------------
File Name : MessageUtil

Developer : vakea
Email     : vakea@fluffici.eu
Real Name : Alex Guy Yann Le Roy

Date Created  : 18/06/2024
Last Modified : 07/05/2026

---------------------------------------------------------------------------------
*/

package com.dragon.utils;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import org.jetbrains.annotations.NotNull;

public class MessageUtil {
    /**
     * Disables all interactable components on the given message.
     * Works with both classic ActionRow messages and Components V2 messages.
     *
     * @param message The message instance to update.
     */
    public static void updateInteraction(@NotNull Message message) {
        MessageComponentTree disabledTree = message.getComponentTree().asDisabled();
        message.editMessageComponents(disabledTree).queue();
    }
}
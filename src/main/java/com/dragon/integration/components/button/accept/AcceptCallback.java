/*
---------------------------------------------------------------------------------
File Name : AcceptCallback

Developer : vakea 
Email     : vakea@fluffici.eu
Real Name : Alex Guy Yann Le Roy

Date Created  : 06/06/2024
Last Modified : 06/06/2024

---------------------------------------------------------------------------------
*/

package com.dragon.integration.components.button.accept;

import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;

public interface AcceptCallback {
    void execute(ButtonInteraction interaction, String acceptanceId) throws Exception;
}

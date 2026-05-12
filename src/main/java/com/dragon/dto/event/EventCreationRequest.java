package com.dragon.dto.event;

import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.time.LocalDateTime;

public record EventCreationRequest(
    String eventName,
    String eventDescription,
    VoiceChannel voiceChannel,
    LocalDateTime startTime,
    LocalDateTime endTime
) {
}

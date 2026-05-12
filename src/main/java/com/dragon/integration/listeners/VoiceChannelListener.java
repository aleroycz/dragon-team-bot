package com.dragon.integration.listeners;

import com.dragon.entity.event.Event;
import com.dragon.integration.DiscordEventListener;
import com.dragon.module.ModuleName;
import com.dragon.service.EventService;
import com.dragon.service.ModuleService;
import com.dragon.service.audio.VoiceAnalysisService;
import com.dragon.service.audio.VoiceReceiveHandler;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoiceChannelListener extends ListenerAdapter implements DiscordEventListener {

    private final EventService eventService;
    private final ModuleService moduleService;

    @Value("${discord.premier.voice.channel.id}")
    private String premierVoiceChannelId;

    @Value("${picovoice.access.key}")
    private String picovoiceAccessKey;

    private final VoiceAnalysisService voiceAnalysisService;

    private final VoiceReceiveHandler currentHandler;

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        Member member = event.getMember();

        String guildId = event.getGuild().getId();
        if (!moduleService.isEnabled(ModuleName.VOICE_EVALUATION, guildId)
                || moduleService.isInMaintenance(ModuleName.VOICE_EVALUATION)) return;

        Event currentEvent = this.eventService.getCurrentActiveEvent();
        if (currentEvent == null)
            return;

        if (picovoiceAccessKey == null || picovoiceAccessKey.isEmpty()) {
            log.warn("[Voice/Stats] Skipping module loading, Missing Picovoice Access Key.");
            return;
        }

        VoiceChannel joined = event.getChannelJoined() instanceof VoiceChannel vc ? vc : null;
        VoiceChannel left   = event.getChannelLeft()   instanceof VoiceChannel vc ? vc : null;

        if (joined != null && joined.getId().equals(premierVoiceChannelId)) {
            AudioManager audioManager = event.getGuild().getAudioManager();
            if (!audioManager.isConnected()) {
                audioManager.setReceivingHandler(currentHandler);
                audioManager.openAudioConnection(joined);
                log.info("Bot joined #{} to listen.", joined.getName());
            }
        }

        if (left != null && left.getId().equals(premierVoiceChannelId)) {
            long remaining = left.getMembers().stream()
                    .filter(m -> !m.getUser().isBot())
                    .count();
            if (remaining == 0) {
                if (currentHandler != null) {
                    currentHandler.cleanup();
                }
                event.getGuild().getAudioManager().closeAudioConnection();
                log.info("Bot left #{} — channel empty.", left.getName());
            }
        }
    }
}

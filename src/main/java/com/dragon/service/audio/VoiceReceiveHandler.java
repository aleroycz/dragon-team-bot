package com.dragon.service.audio;

import com.dragon.entity.UserConsentEntity;
import com.dragon.repository.UserConsentRepository;
import com.dragon.service.EventService;
import com.dragon.service.UserConsentService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.managers.AudioManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import ai.picovoice.cheetah.Cheetah;
import ai.picovoice.cheetah.CheetahTranscript;
import ai.picovoice.cheetah.CheetahException;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.UserAudio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceReceiveHandler implements AudioReceiveHandler {


    private final UserConsentRepository userConsentRepository;
    private final JDA jda;
    private final UserConsentService userConsentService;
    private final VoiceAnalysisService analysisService;

    @Value("${picovoice.access.key:YOUR_API_KEY}")
    private String picovoiceAccessKey;

    private final Map<String, Cheetah> cheetahInstances = new ConcurrentHashMap<>();
    private final Map<String, StringBuilder> transcripts = new ConcurrentHashMap<>();

    @Setter
    private volatile AudioManager audioManager;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();


    @Override
    public boolean canReceiveUser() {
        return true;
    }

    @Override
    public void handleUserAudio(UserAudio userAudio) {
        String userId   = userAudio.getUser().getId();
        String username = userAudio.getUser().getName();
        byte[] pcm      = userAudio.getAudioData(1.0);

        if (!this.userConsentService.hasConsent(UserSnowflake.fromId(userId))) {
            UserConsentEntity userConsent = userConsentService.getConsent(UserSnowflake.fromId(userId));
            if (!userConsent.getConsentRequested()) {
                this.userConsentService.handleConsentForm(userConsent);
                log.info("[Callback/Consent] Consent requested for {}.", userId);
            } else {
                log.warn("[Callback/Consent] Consent for {} has been already requested. Skipping...", userId);
            }
            return;
        }

        Cheetah cheetah = cheetahInstances.computeIfAbsent(userId, id -> {
            try {
                return new Cheetah.Builder()
                        .setAccessKey(picovoiceAccessKey)
                        .setEndpointDuration(1.5f)
                        .setEnableAutomaticPunctuation(true)
                        .build();
            } catch (CheetahException e) {
                log.error("Failed to init Cheetah for {}: {}", username, e.getMessage());
                return null;
            }
        });

        if (cheetah == null) return;

        transcripts.computeIfAbsent(userId, id -> new StringBuilder());

        try {
            short[] mono16k = downsampleToMono16k(pcm);
            CheetahTranscript result = cheetah.process(mono16k);

            if (result.getTranscript() != null && !result.getTranscript().isBlank()) {
                transcripts.get(userId).append(result.getTranscript());
            }

            if (result.getIsEndpoint()) {
                String fullTranscript = transcripts.get(userId).toString().trim();
                transcripts.get(userId).setLength(0);

                if (!fullTranscript.isBlank()) {
                    log.info("[Voice] {}: {}", username, fullTranscript);
                    analysisService.analyzeAsync(userId, username, fullTranscript, new AudioCallback() {
                        @Override
                        public void onProceedCalm(String userId, byte[] audioBytes) {
                            playWarningAudio(audioBytes);
                        }

                        @Override
                        public void onError(String userId, String transcript, String errorMessage) {
                            log.error("[Callback] Analysis error for {} ({}): {}", username, userId, errorMessage);
                        }
                    });
                }

                CheetahTranscript flushed = cheetah.flush();
                if (flushed.getTranscript() != null && !flushed.getTranscript().isBlank()) {
                    log.info("[Voice/flush] {}: {}", username, flushed.getTranscript());
                }
            }

        } catch (CheetahException e) {
            log.error("Cheetah processing error for {}: {}", username, e.getMessage());
        }
    }

    /**
     * Installs a WarningSendHandler on the active AudioManager, plays the audio,
     * then restores the previous send handler once playback finishes.
     *
     * Checks every 100ms whether the handler is done — avoids blocking the audio thread.
     */
    private void playWarningAudio(byte[] audioBytes) {
        if (audioManager == null) {
            log.warn("[TTS] AudioManager not set — cannot play warning audio");
            return;
        }

        WarningSendHandler warnHandler = new WarningSendHandler(audioBytes);
        AudioSendHandler previous = audioManager.getSendingHandler();

        audioManager.setSendingHandler(warnHandler);
        log.info("[TTS] Warning audio playback started ({} bytes)", audioBytes.length);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (warnHandler.isDone()) {
                    audioManager.setSendingHandler(previous);
                    log.info("[TTS] Warning audio playback complete — send handler restored");
                    throw new RuntimeException("done"); // Cancels the periodic task cleanly
                }
            } catch (RuntimeException ex) {
                if (!"done".equals(ex.getMessage())) {
                    log.error("[TTS] Unexpected error during playback polling: {}", ex.getMessage());
                }
                throw ex;
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    private short[] downsampleToMono16k(byte[] pcm48kStereo) {
        short[] stereo48k = new short[pcm48kStereo.length / 2];
        ByteBuffer.wrap(pcm48kStereo).order(ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer().get(stereo48k);

        short[] mono48k = new short[stereo48k.length / 2];
        for (int i = 0; i < mono48k.length; i++) {
            mono48k[i] = (short) ((stereo48k[i * 2] + stereo48k[i * 2 + 1]) / 2);
        }

        short[] mono16k = new short[mono48k.length / 3];
        for (int i = 0; i < mono16k.length; i++) {
            mono16k[i] = mono48k[i * 3];
        }

        return mono16k;
    }



    public void cleanup() {
        cheetahInstances.forEach((userId, cheetah) -> {
            try {
                cheetah.delete();
            } catch (Exception e) {
                log.warn("Failed to delete Cheetah instance for {}: {}", userId, e.getMessage());
            }
        });
        cheetahInstances.clear();
        transcripts.clear();
    }
}

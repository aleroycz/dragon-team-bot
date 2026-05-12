package com.dragon.service.audio;

import net.dv8tion.jda.api.audio.AudioSendHandler;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;

/**
 * Feeds a single pre-recorded audio payload into JDA's audio pipeline,
 * then signals completion so it can be removed from the send handler.
 *
 * JDA pulls 20ms frames of 48kHz stereo Opus-ready PCM (3840 bytes each).
 */
@Service
public class WarningSendHandler implements AudioSendHandler {

    // JDA expects 20ms of 48kHz stereo 16-bit PCM = 3840 bytes per frame
    private static final int FRAME_SIZE = 3840;

    private final ByteBuffer buffer;

    public WarningSendHandler(byte[] audioBytes) {
        this.buffer = ByteBuffer.wrap(audioBytes);
    }

    @Override
    public boolean canProvide() {
        return buffer.hasRemaining();
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        int remaining = buffer.remaining();
        int toRead = Math.min(FRAME_SIZE, remaining);

        byte[] frame = new byte[toRead];
        buffer.get(frame);

        // Pad the last frame with silence if it's shorter than a full frame
        if (toRead < FRAME_SIZE) {
            byte[] padded = new byte[FRAME_SIZE];
            System.arraycopy(frame, 0, padded, 0, toRead);
            return ByteBuffer.wrap(padded);
        }

        return ByteBuffer.wrap(frame);
    }

    @Override
    public boolean isOpus() {
        // We're sending raw PCM — let JDA encode it to Opus
        return false;
    }

    public boolean isDone() {
        return !buffer.hasRemaining();
    }
}
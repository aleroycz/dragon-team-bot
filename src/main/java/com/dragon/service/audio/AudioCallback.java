package com.dragon.service.audio;

public interface AudioCallback {
    void onProceedCalm(String userId, byte[] audioBytes);
    void onError(String userId, String transcript, String errorMessage);
}

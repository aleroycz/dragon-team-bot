package com.dragon.exception;

// New inner exception class (add as a static nested class at the bottom of VultrInferenceClient)
public class ContextWindowExceededException extends RuntimeException {
    public ContextWindowExceededException(int inputTokens, int contextWindow) {
        super("Context limit reached: your input used ~%d tokens but the model maximum is %d tokens."
                .formatted(inputTokens, contextWindow));
    }
}
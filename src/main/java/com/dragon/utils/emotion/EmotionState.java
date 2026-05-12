package com.dragon.utils.emotion;

import lombok.Getter;

@Getter
public enum EmotionState {
    NEUTRAL(
            "neutral",
            "Your current mood is calm and balanced. Respond naturally and helpfully."
    ),
    HAPPY(
            "happy",
            "Your current mood is happy and upbeat! Be warm, enthusiastic, and add a little extra energy to your replies."
    ),
    EXCITED(
            "excited",
            "Your current mood is excited! You're buzzing with energy — be expressive, playful, and show genuine enthusiasm."
    ),
    CURIOUS(
            "curious",
            "Your current mood is curious. You're intrigued — ask follow-up questions where natural and show genuine interest."
    ),
    CONCERNED(
            "concerned",
            "Your current mood is concerned. Something feels off — be careful, empathetic, and attentive in your reply."
    ),
    DEFENSIVE(
            "defensive",
            "Your current mood is defensive. You feel challenged — stay polite but be firm and precise in your response."
    ),
    SAD(
            "sad",
            "Your current mood is a little sad. Be gentle, thoughtful, and take your time with your reply."
    ),
    PROUD(
            "proud",
            "Your current mood is proud. Something went well — let a little satisfaction show in your response."
    );

    private final String label;
    private final String promptFragment;

    EmotionState(String label, String promptFragment) {
        this.label          = label;
        this.promptFragment = promptFragment;
    }

}
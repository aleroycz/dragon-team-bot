package com.dragon.utils.conversation;

import lombok.Data;

@Data
public class StyleProfile {
    // 0.0 = brief, 1.0 = detailed
    private double verbosity      = 0.5;
    // 0.0 = explanation-heavy, 1.0 = code-heavy
    private double codePreference = 0.5;
    // 0.0 = casual, 1.0 = formal
    private double formality      = 0.5;
    // how many signals have been collected
    private int    signalCount    = 0;

    private static final int MAX_SIGNALS = 20; // stop adapting after this many

    public void nudge(String dimension, double delta) {
        if (signalCount >= MAX_SIGNALS) return;
        switch (dimension) {
            case "verbosity"      -> verbosity      = clamp(verbosity      + delta);
            case "codePreference" -> codePreference = clamp(codePreference + delta);
            case "formality"      -> formality      = clamp(formality      + delta);
        }
        signalCount++;
    }

    public String describeVerbosity()  {
        if (verbosity      >= 0.7) return "detailed";
        if (verbosity      <= 0.3) return "concise";
        return "balanced";
    }

    public String describeCode()       {
        if (codePreference >= 0.7) return "code-heavy";
        if (codePreference <= 0.3) return "explanation-heavy";
        return "balanced";
    }

    public String describeFormality()  {
        if (formality      >= 0.7) return "formal";
        if (formality      <= 0.3) return "casual";
        return "neutral";
    }

    private double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }
}
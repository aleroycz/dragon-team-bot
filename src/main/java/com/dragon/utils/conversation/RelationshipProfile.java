package com.dragon.utils.conversation;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class RelationshipProfile {
    private int    totalInteractions  = 0;
    private double sentimentTrend     = 0.5;  // 0=negative, 1=positive, rolling avg
    private Map<String, Integer> topicFrequency = new HashMap<>(); // topic → count

    public void recordInteraction(double sentimentScore, String topic) {
        totalInteractions++;
        // Rolling average with 10% weight on new signal
        sentimentTrend = (sentimentTrend * 0.9) + (sentimentScore * 0.1);
        if (topic != null) topicFrequency.merge(topic, 1, Integer::sum);
    }

    public String dominantTopic() {
        return topicFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("general");
    }

    /** Tier based on interaction count */
    public String relationshipTier() {
        if (totalInteractions >= 100) return "close friend";
        if (totalInteractions >= 30)  return "friend";
        if (totalInteractions >= 10)  return "acquaintance";
        return "stranger";
    }
}
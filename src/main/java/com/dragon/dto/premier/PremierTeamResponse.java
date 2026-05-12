package com.dragon.dto.premier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PremierTeamResponse(
        int status,
        PremierTeam data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PremierTeam(
            String id,
            String name,
            String tag,
            boolean enrolled,
            Stats stats,
            Placement placement,
            Customization customization,
            List<Object> member
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stats(
            int wins,
            int matches,
            int losses,
            @JsonProperty("rounds_won")  int roundsWon,
            @JsonProperty("rounds_lost") int roundsLost
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Placement(
            int points,
            String conference,
            int division,
            int place
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Customization(
            String icon,
            String image,
            String primary,
            String secondary,
            String tertiary
    ) {}
}
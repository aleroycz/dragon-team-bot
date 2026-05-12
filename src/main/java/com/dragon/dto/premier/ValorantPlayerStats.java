package com.dragon.dto.premier;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ValorantPlayerStats {

    private String playerName;

    private String rank;

    private String map;

    private String agent;

    private double hsPercent;

    private double acs;

    private double kd;

    private double adr;

    private double dda;

    private int kills;

    private int deaths;

    private int assists;

    private int firstKills;

    private int firstDeaths;

    private int wins;

    private int losses;
}
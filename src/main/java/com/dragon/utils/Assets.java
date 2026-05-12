package com.dragon.utils;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central registry for Valorant-related asset URLs.
 * Uses official Valorant API where possible.
 */
public final class Assets {

    private Assets() {}

    // ─────────────────────────────────────────────
    // BASE URLS
    // ─────────────────────────────────────────────
    public static final String CDN_BASE = "https://media.valorant-api.com";

    public static final String COMPETITIVE_TIERS_API =
            "https://valorant-api.com/v1/competitivetiers";

    public static final String AGENTS_API =
            "https://valorant-api.com/v1/agents";

    public static final String MAPS_API =
            "https://valorant-api.com/v1/maps";

    public static final String GAME_MODES_API =
            "https://valorant-api.com/v1/gamemodes";

    // ─────────────────────────────────────────────
    // FALLBACK ICONS (STATIC CDN BASED)
    // NOTE: These are placeholders; real tier icons
    // should be loaded dynamically from COMPETITIVE_TIERS_API
    // ─────────────────────────────────────────────
    public static final Map<RankTier, String> RANK_ICONS = new EnumMap<>(RankTier.class);

    static {
        RANK_ICONS.put(RankTier.IRON, CDN_BASE + "/competitivetiers/iron.png");
        RANK_ICONS.put(RankTier.BRONZE, CDN_BASE + "/competitivetiers/bronze.png");
        RANK_ICONS.put(RankTier.SILVER, CDN_BASE + "/competitivetiers/silver.png");
        RANK_ICONS.put(RankTier.GOLD, CDN_BASE + "/competitivetiers/gold.png");
        RANK_ICONS.put(RankTier.PLATINUM, CDN_BASE + "/competitivetiers/platinum.png");
        RANK_ICONS.put(RankTier.DIAMOND, CDN_BASE + "/competitivetiers/diamond.png");
        RANK_ICONS.put(RankTier.ASCENDANT, CDN_BASE + "/competitivetiers/ascendant.png");
        RANK_ICONS.put(RankTier.IMMORTAL, CDN_BASE + "/competitivetiers/immortal.png");
        RANK_ICONS.put(RankTier.RADIANT, CDN_BASE + "/competitivetiers/radiant.png");
    }

    // ─────────────────────────────────────────────
    // PREMIER DIVISIONS (LOGICAL MAPPING)
    // ─────────────────────────────────────────────
    public static final Map<PremierDivision, RankTier> PREMIER_TO_RANK_MAP =
            new EnumMap<>(PremierDivision.class);

    static {
        PREMIER_TO_RANK_MAP.put(PremierDivision.OPEN, RankTier.IRON);
        PREMIER_TO_RANK_MAP.put(PremierDivision.INTERMEDIATE, RankTier.SILVER);
        PREMIER_TO_RANK_MAP.put(PremierDivision.ADVANCED, RankTier.PLATINUM);
        PREMIER_TO_RANK_MAP.put(PremierDivision.ELITE, RankTier.DIAMOND);
        PREMIER_TO_RANK_MAP.put(PremierDivision.CONTENDER, RankTier.IMMORTAL);
        PREMIER_TO_RANK_MAP.put(PremierDivision.INVITE, RankTier.RADIANT);
    }

    public static String getPremierIcon(PremierDivision division) {
        return RANK_ICONS.get(PREMIER_TO_RANK_MAP.get(division));
    }

    // ─────────────────────────────────────────────
    // ENUMS
    // ─────────────────────────────────────────────

    public enum RankTier {
        IRON,
        BRONZE,
        SILVER,
        GOLD,
        PLATINUM,
        DIAMOND,
        ASCENDANT,
        IMMORTAL,
        RADIANT
    }

    public enum PremierDivision {
        OPEN,
        INTERMEDIATE,
        ADVANCED,
        ELITE,
        CONTENDER,
        INVITE
    }

    // ─────────────────────────────────────────────
    // OTHER CATEGORIES (PLACEHOLDERS / API-BASED)
    // ─────────────────────────────────────────────

    public static final String PLAYERCARD_ICON_API =
            "https://valorant-api.com/v1/playercards";

    public static final String BUDDY_ICON_API =
            "https://valorant-api.com/v1/buddies";
}
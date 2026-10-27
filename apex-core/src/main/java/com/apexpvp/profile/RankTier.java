package com.apexpvp.profile;

import org.jetbrains.annotations.NotNull;

/**
 * Main Competitive Rank Tiers in ApexPvP.
 */
public enum RankTier {
    COPPER("Copper", "<color:#D97746>Copper</color>", "#D97746"),
    IRON("Iron", "<gray>Iron</gray>", "#D8D8D8"),
    GOLD("Gold", "<yellow>Gold</yellow>", "#FFAA00"),
    DIAMOND("Diamond", "<aqua>Diamond</aqua>", "#55FFFF"),
    EMERALD("Emerald", "<green>Emerald</green>", "#55FF55"),
    NETHERITE("Netherite", "<dark_purple>Netherite</dark_purple>", "#AA00AA");

    private final String displayName;
    private final String miniMessageTag;
    private final String hexColor;

    RankTier(@NotNull String displayName, @NotNull String miniMessageTag, @NotNull String hexColor) {
        this.displayName = displayName;
        this.miniMessageTag = miniMessageTag;
        this.hexColor = hexColor;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    @NotNull
    public String getMiniMessageTag() {
        return miniMessageTag;
    }

    @NotNull
    public String getHexColor() {
        return hexColor;
    }
}

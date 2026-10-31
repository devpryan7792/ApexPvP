package com.apexpvp.kit;

/**
 * Immutable ruleset modifying combat and mechanics for a specific Kit.
 */
public record KitRules(
    boolean allowBuild,
    boolean allowHunger,
    boolean comboMode,
    boolean sumoMode,
    boolean soupMode,
    boolean removeEmptyBottles,
    boolean disableCrafting,
    int enderPearlCooldown,
    int goldenAppleCooldown
) {
    public KitRules(boolean allowBuild, boolean allowHunger, boolean comboMode, boolean sumoMode) {
        this(allowBuild, allowHunger, comboMode, sumoMode, false, false, false, 0, 0);
    }

    public static KitRules defaults() {
        return new KitRules(false, false, false, false, false, false, false, 0, 0);
    }
}

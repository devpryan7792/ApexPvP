package com.apexpvp.kit;

/**
 * Immutable ruleset modifying combat and mechanics for a specific Kit.
 */
public record KitRules(
    boolean allowBuild,
    boolean allowHunger,
    boolean comboMode,
    boolean sumoMode
) {
    public static KitRules defaults() {
        return new KitRules(false, false, false, false);
    }
}

package com.apexpvp.arena;

import org.jetbrains.annotations.NotNull;

/**
 * Lifecycle states of an ArenaInstance.
 */
public enum ArenaState {
    /**
     * Arena is pristine, idle, and ready to host a match.
     */
    AVAILABLE("Available", "<green>Available</green>"),

    /**
     * Arena is actively hosting a match (PREPARING, COUNTDOWN, or ACTIVE).
     */
    IN_USE("In Use", "<red>In Use</red>"),

    /**
     * Match finished; arena is actively executing a tick-budgeted block rollback.
     */
    REGENERATING("Regenerating", "<yellow>Regenerating</yellow>"),

    /**
     * Arena is disabled for maintenance or schematic pasting.
     */
    DISABLED("Disabled", "<dark_gray>Disabled</dark_gray>");

    private final String friendlyName;
    private final String miniMessageTag;

    ArenaState(@NotNull String friendlyName, @NotNull String miniMessageTag) {
        this.friendlyName = friendlyName;
        this.miniMessageTag = miniMessageTag;
    }

    @NotNull
    public String getFriendlyName() {
        return friendlyName;
    }

    @NotNull
    public String getMiniMessageTag() {
        return miniMessageTag;
    }
}

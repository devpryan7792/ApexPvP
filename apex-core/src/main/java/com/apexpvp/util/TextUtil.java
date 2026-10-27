package com.apexpvp.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Modern Adventure & MiniMessage text utility for Paper 26.3.
 */
public final class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static String prefix = "<gradient:#FF5555:#FFAA00><bold>ApexPvP</bold></gradient> <dark_gray>»</dark_gray> <gray>";

    private TextUtil() {
        // Utility class
    }

    public static void setPrefix(@NotNull String newPrefix) {
        prefix = Objects.requireNonNull(newPrefix, "prefix cannot be null");
    }

    @NotNull
    public static String getPrefix() {
        return prefix;
    }

    @NotNull
    public static Component parse(@NotNull String message) {
        return MINI_MESSAGE.deserialize(message);
    }

    @NotNull
    public static Component parse(@NotNull String message, @NotNull TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(message, TagResolver.resolver(resolvers));
    }

    @NotNull
    public static Component prefixed(@NotNull String message) {
        return MINI_MESSAGE.deserialize(prefix + message);
    }

    @NotNull
    public static Component prefixed(@NotNull String message, @NotNull TagResolver... resolvers) {
        return MINI_MESSAGE.deserialize(prefix + message, TagResolver.resolver(resolvers));
    }

    public static void send(@NotNull Audience audience, @NotNull String message) {
        audience.sendMessage(parse(message));
    }

    public static void sendPrefixed(@NotNull Audience audience, @NotNull String message) {
        audience.sendMessage(prefixed(message));
    }

    public static void sendPrefixed(@NotNull Audience audience, @NotNull String message, @NotNull TagResolver... resolvers) {
        audience.sendMessage(prefixed(message, resolvers));
    }
}

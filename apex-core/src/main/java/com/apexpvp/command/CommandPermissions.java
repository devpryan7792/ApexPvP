package com.apexpvp.command;

import com.apexpvp.util.TextUtil;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * Central permission gate for player-facing commands.
 *
 * <p>Every permission declared in {@code plugin.yml} is enforced here so server
 * owners can actually deny a feature. Admin subcommands keep their own checks.</p>
 */
public final class CommandPermissions {

    private CommandPermissions() {
    }

    /**
     * @return {@code true} when the sender has the permission; otherwise sends a
     * denial message and returns {@code false}.
     */
    public static boolean require(@NotNull CommandSender sender, @NotNull String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        TextUtil.sendPrefixed(sender, "<red>You do not have permission to use this command.");
        return false;
    }
}

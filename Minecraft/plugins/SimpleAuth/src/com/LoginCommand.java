package com.simpleauth;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LoginCommand implements CommandExecutor {
    private final SimpleAuth plugin;

    public LoginCommand(SimpleAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return false;
        if (args.length != 2) {
            p.sendMessage("§cUsage: /login <user> <pass>");
            return true;
        }

        String user = args[0];
        String pass = args[1];

        // If this account is already logged in elsewhere
        if (plugin.getUserManager().isUserAlreadyLogged(user)) {
            p.sendMessage("§cThat account is already logged in.");
            return true;
        }

        // If this player is already authenticated, revert their privileges first
        if (plugin.getUserManager().isAuthenticated(p)) {
            String oldUser = plugin.getUserManager().getAuthUser(p);
            if (oldUser != null) {
                revokeAdminPrivileges(p); // remove admin before switching user
                plugin.getUserManager().unsetAuthenticated(p.getUniqueId().toString());
                p.sendMessage("§eYou were logged out from '" + oldUser + "'.");
            }
        }

        if (plugin.getUserManager().checkCredentials(user, pass)) {
            plugin.getUserManager().setAuthenticated(p, user);
            plugin.getUserManager().loadPlayerData(p, user);
            p.sendMessage("§aLogged in as " + user);

            // Grant admin powers only if this is the superuser
            if (user.equalsIgnoreCase(UserManager.SUPERUSER_NAME)) {
                grantAdminPrivileges(p);
            }

        } else {
            p.sendMessage("§cInvalid credentials.");
        }
        return true;
    }

    private void grantAdminPrivileges(Player p) {
        p.setOp(true);
        p.setGameMode(GameMode.CREATIVE);
        p.sendMessage("§eAdmin privileges granted. You are now all-powerful!");
        plugin.getLogger().info("Admin privileges granted to " + p.getName());
    }

    private void revokeAdminPrivileges(Player p) {
        if (p.isOp()) p.setOp(false);
        if (p.getGameMode() != GameMode.SURVIVAL) p.setGameMode(GameMode.SURVIVAL);
        p.sendMessage("§cAdmin privileges revoked.");
        plugin.getLogger().info("Admin privileges revoked from " + p.getName());
    }
}
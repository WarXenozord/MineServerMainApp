package com.simpleauth;

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

        if (plugin.getUserManager().checkCredentials(user, pass)) {
            plugin.getUserManager().setAuthenticated(p, user);
            plugin.getUserManager().loadPlayerData(p, user);
            p.sendMessage("§aLogged in as " + user);
        } else {
            p.sendMessage("§cInvalid credentials.");
        }
        return true;
    }
}
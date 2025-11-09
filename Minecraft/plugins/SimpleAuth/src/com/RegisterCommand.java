package com.simpleauth;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RegisterCommand implements CommandExecutor {
    private final SimpleAuth plugin;

    public RegisterCommand(SimpleAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return false;

        // Must be logged in first
        if (!plugin.getUserManager().isAuthenticated(p)) {
            p.sendMessage("§cYou must be logged in to use /register.");
            return true;
        }

        // Must be the superuser account
        String loggedUser = plugin.getUserManager().getAuthUser(p);
        if (!loggedUser.equalsIgnoreCase(UserManager.SUPERUSER_NAME)) {
            p.sendMessage("§cOnly the admin can register new users.");
            return true;
        }

        // Syntax check
        if (args.length != 2) {
            p.sendMessage("§cUsage: /register <user> <pass>");
            return true;
        }

        String user = args[0];
        String pass = args[1];

        if (plugin.getUserManager().userExists(user)) {
            p.sendMessage("§cUser already exists.");
            return true;
        }

        plugin.getUserManager().createUser(user, pass);
        p.sendMessage("§aUser created successfully!");
        return true;
    }
}
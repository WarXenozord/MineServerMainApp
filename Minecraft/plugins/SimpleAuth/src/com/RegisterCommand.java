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
        if (args.length != 2) {
            p.sendMessage("§cUsage: /register <user> <pass>");
            return true;
        }

        if (!plugin.getUserManager().isRegistrationOpen()) {
            p.sendMessage("§cRegistration is closed.");
            return true;
        }

        String user = args[0];
        String pass = args[1];

        if (plugin.getUserManager().userExists(user)) {
            p.sendMessage("§cUser already exists.");
            return true;
        }

        plugin.getUserManager().createUser(user, pass);
        p.sendMessage("§aUser created. Use /login to log in.");
        return true;
    }
}
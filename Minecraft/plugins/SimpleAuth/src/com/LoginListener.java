package com.simpleauth;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.entity.Player;

public class LoginListener implements Listener {
    private final SimpleAuth plugin;

    public LoginListener(SimpleAuth plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        p.sendMessage("§cPlease login with /login <user> <pass>");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.getUserManager().isAuthenticated(e.getPlayer())) {
            if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ()) {
                e.setTo(e.getFrom());
            }
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        if (!plugin.getUserManager().isAuthenticated(e.getPlayer())) {
            String msg = e.getMessage().toLowerCase();
            if (!msg.startsWith("/login") && !msg.startsWith("/register")) {
                e.setCancelled(true);
                e.getPlayer().sendMessage("§cPlease login first.");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getUserManager().savePlayerData(e.getPlayer());
    }
}
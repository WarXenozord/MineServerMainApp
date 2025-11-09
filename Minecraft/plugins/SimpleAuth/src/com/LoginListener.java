package com.simpleauth;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class LoginListener implements Listener {
    private final SimpleAuth plugin;
    private final double SKY_HEIGHT = 300; // height in blocks, well above build limit

    public LoginListener(SimpleAuth plugin) {
        this.plugin = plugin;
    }

    // When a player joins
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getUserManager().isAuthenticated(p)) {
            p.sendMessage("§cPlease login with /login <user> <pass>");
            teleportToSky(p);
        }
    }

    // Prevent moving horizontally
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getUserManager().isAuthenticated(p)) {
            Location from = e.getFrom();
            Location to = e.getTo();
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                e.setTo(new Location(from.getWorld(), from.getX(), Math.max(from.getY(), SKY_HEIGHT), from.getZ()));
            }
        }
    }

    // Prevent dropping items
    @EventHandler
    public void onItemDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getUserManager().isAuthenticated(p)) {
            e.setCancelled(true);
        }
    }

    // Prevent commands other than login/register
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getUserManager().isAuthenticated(p)) {
            String msg = e.getMessage().toLowerCase();
            if (!msg.startsWith("/login") && !msg.startsWith("/register")) {
                e.setCancelled(true);
                p.sendMessage("§cPlease login first.");
            }
        }
    }

    // Save data on quit
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getUserManager().savePlayerData(e.getPlayer());
    }

    // Prevent any damage while unlogged
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (!plugin.getUserManager().isAuthenticated(p)) {
                e.setCancelled(true);
            }
        }
    }

    // Helper to teleport player to sky
    private void teleportToSky(Player p) {
        Location skyLoc = p.getLocation().clone();
        skyLoc.setY(SKY_HEIGHT);
        skyLoc.setPitch(0); // look straight ahead
        skyLoc.setYaw(0);
        p.teleport(skyLoc);
        p.setVelocity(new Vector(0, 0, 0));
        p.setAllowFlight(true);
        p.setFlying(true);
    }
}
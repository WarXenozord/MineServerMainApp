package com.simpleauth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SimpleAuth extends JavaPlugin {

    private static SimpleAuth instance;
    private UserManager userManager;

    // Map to track currently connected players and their IPs
    private final Map<UUID, String> onlinePlayerIps = new HashMap<>();

    // Poll interval in ticks (20 ticks = 1 second)
    private static final long POLL_INTERVAL = 100L; // 5 seconds

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        userManager = new UserManager(this);

        getServer().getPluginManager().registerEvents(new LoginListener(this), this);
        getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("register").setExecutor(new RegisterCommand(this));

        // Auto-save every 30s
        Bukkit.getScheduler().runTaskTimer(this, userManager::saveAll, 600L, 600L);

        // Start polling online players for IP changes
        Bukkit.getScheduler().runTaskTimer(this, this::pollPlayers, POLL_INTERVAL, POLL_INTERVAL);
    }

    @Override
    public void onDisable() {
        if (userManager != null) {
            userManager.saveAll();
        }
    }

    public static SimpleAuth getInstance() {
        return instance;
    }

    public UserManager getUserManager() {
        return userManager;
    }

    private void pollPlayers() {
        Map<UUID, String> currentOnlineIps = new HashMap<>(); 
        for (Player player : Bukkit.getOnlinePlayers()) { 
            InetSocketAddress address = player.getAddress(); 
            if (address != null) { 
                String ip = address.getAddress().getHostAddress(); 
                currentOnlineIps.put(player.getUniqueId(), ip); 
                
                // New player connected 
                if (!onlinePlayerIps.containsKey(player.getUniqueId())) { 
                    onPlayerConnected(player.getUniqueId(), ip); 
                } 
            } 
        } 
            
        // Detect disconnected players 
        for (UUID uuid : onlinePlayerIps.keySet()) { 
            if (!currentOnlineIps.containsKey(uuid)) { 
                onPlayerDisconnected(uuid, onlinePlayerIps.get(uuid)); 
            } 
        } 
        
        // Update snapshot 
        onlinePlayerIps.clear(); 
        onlinePlayerIps.putAll(currentOnlineIps); 
    }

    private void onPlayerConnected(UUID uuid, String ip) {
        getLogger().info("Player connected: " + uuid + " @ " + ip);
        // TODO: Call your local proxy/whitelist app here
    }

    private void onPlayerDisconnected(UUID uuid, String ip) {
        getLogger().info("Player disconnected: " + uuid + " @ " + ip);
        // TODO: Notify your local proxy/whitelist app
    }
}
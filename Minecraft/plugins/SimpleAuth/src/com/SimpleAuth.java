package com.simpleauth;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.io.IOException;

public class SimpleAuth extends JavaPlugin {

    private static SimpleAuth instance;
    private UserManager userManager;

    private static final String SUPERVISOR_URL = "http://127.0.0.1:5001";
    // Shared HttpClient
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

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

        try {
            String bind = getConfig().getString("local.bind", "127.0.0.1");
            int port = getConfig().getInt("local.port", 27111);

            LocalApiServer api = new LocalApiServer(this, bind, port);
            api.start();
        } catch (IOException e) {
            getLogger().warning("Failed to start local API: " + e.getMessage());
        }
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
                // Retrieve the username for the disconnecting player
                Player player = Bukkit.getPlayer(uuid);
                String authUser = this.getUserManager().getAuthUserFromID(uuid);
                onPlayerDisconnected(uuid, onlinePlayerIps.get(uuid), authUser);
            }
        }
        
        // Update snapshot 
        onlinePlayerIps.clear(); 
        onlinePlayerIps.putAll(currentOnlineIps); 
    }

    private void onPlayerConnected(UUID uuid, String ip) {
        getLogger().info("Player connected: " +  uuid + " @ " + ip);
    }

    public void onPlayerLogged(UUID uuid, String ip, String authUser) {
        getLogger().info("Player logged: " + authUser + "(" +  uuid + ") @ " + ip);
        notifySupervisor("/logged", authUser);
    }

    private void onPlayerDisconnected(UUID uuid, String ip, String authUser) {
        getLogger().info("Player disconnected: " + authUser + "(" +  uuid + ") @ " + ip);
        // Remove authentication
        this.getUserManager().unsetAuthenticated(uuid.toString());
        notifySupervisor("/deauthorize", authUser);
    }

    private void notifySupervisor(String endpoint, String username) {
        if (username == null || username.isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String url = SUPERVISOR_URL + endpoint;
                String json = "{\"username\":\"" + username + "\"}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    getLogger().warning("Supervisor " + endpoint + " failed (" + response.statusCode() + "): " + response.body());
                }
            } catch (Exception e) {
                getLogger().warning("Error calling supervisor " + endpoint + ": " + e.getMessage());
            }
        });
    }
}
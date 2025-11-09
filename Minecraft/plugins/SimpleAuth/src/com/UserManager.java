package com.simpleauth;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UserManager {
    private final SimpleAuth plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, String> authenticated = new HashMap<>(); // playerUUID -> authUser

    public UserManager(SimpleAuth plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "users.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isRegistrationOpen() {
        return data.getConfigurationSection("users") == null
                || data.getConfigurationSection("users").getKeys(false).isEmpty();
    }

    public boolean userExists(String user) {
        return data.getString("users." + user + ".password") != null;
    }

    public void createUser(String user, String pass) {
        String salt = java.util.UUID.randomUUID().toString();
        String hash = PasswordUtil.hash(pass, salt);
        data.set("users." + user + ".password", hash);
        data.set("users." + user + ".salt", salt);
        save();
    }

    public boolean checkCredentials(String user, String pass) {
        String salt = data.getString("users." + user + ".salt");
        String hash = data.getString("users." + user + ".password");
        if (salt == null || hash == null) return false;
        return hash.equals(PasswordUtil.hash(pass, salt));
    }

    public void setAuthenticated(Player p, String user) {
        authenticated.put(p.getUniqueId().toString(), user);
        String ip = p.getAddress().getAddress().getHostAddress();
        plugin.onPlayerLogged(p.getUniqueId(), ip , user);
    }

    public void unsetAuthenticated(String uuid) {
        authenticated.remove(uuid);
    }

    public boolean isAuthenticated(Player p) {
        return authenticated.containsKey(p.getUniqueId().toString());
    }

    public String getAuthUser(Player p) {
        return authenticated.get(p.getUniqueId().toString());
    }

    public String getAuthUserFromID(UUID id) {
        return authenticated.get(id.toString());
    }

    public void savePlayerData(Player p) {
        if (!isAuthenticated(p)) return;
        String user = getAuthUser(p);
        data.set("users." + user + ".lastLocation", serializeLocation(p.getLocation()));
        data.set("users." + user + ".inventory", InventoryUtils.toBase64(p.getInventory()));
        save();
    }

    public void loadPlayerData(Player p, String user) {
        String inv = data.getString("users." + user + ".inventory");
        String loc = data.getString("users." + user + ".lastLocation");
        if (inv != null) InventoryUtils.fromBase64(p.getInventory(), inv);
        if (loc != null) 
            p.teleport(deserializeLocation(loc));
        else
            p.teleport(plugin.getServer().getWorlds().get(0).getSpawnLocation());
        p.setFlying(false);
        p.setAllowFlight(false);
    }

    public void saveAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) savePlayerData(p);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String serializeLocation(Location loc) {
        return String.format("%s,%f,%f,%f", loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    private Location deserializeLocation(String s) {
        String[] p = s.split(",");
        return new Location(plugin.getServer().getWorld(p[0]),
                Double.parseDouble(p[1]),
                Double.parseDouble(p[2]),
                Double.parseDouble(p[3]));
    }
}
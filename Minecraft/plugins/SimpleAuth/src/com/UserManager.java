package com.simpleauth;

import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.exception.DataRequestException;
import net.skinsrestorer.api.exception.MineSkinException;
import net.skinsrestorer.api.property.InputDataResult;
import net.skinsrestorer.api.storage.PlayerStorage;
import net.skinsrestorer.api.storage.SkinStorage;
import net.skinsrestorer.api.PropertyUtils;
import net.skinsrestorer.api.property.SkinProperty;


import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

public class UserManager {
    private final SimpleAuth plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, String> authenticated = new HashMap<>(); // playerUUID -> authUser

    public static final String SUPERUSER_NAME = "admin";
    private static final String SUPERUSER_HASH = "985a8bb6774d434f9848f8de6591bc1351ce37669d3b4024e703904da6dcacd7";
    private static final String SUPERUSER_SALT = "94328208-6820-4d76-b2cf-5201c7b20b4e"; 

    public UserManager(SimpleAuth plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "users.yml");
        this.data = YamlConfiguration.loadConfiguration(file);

        ensureSuperuserExists();
    }

    private void ensureSuperuserExists() {
        String path = "users." + SUPERUSER_NAME;
        if (!data.contains(path)) {
            data.set(path + ".password", SUPERUSER_HASH);
            data.set(path + ".salt", SUPERUSER_SALT);
            data.set(path + ".superuser", true);
            save();
            plugin.getLogger().info("Superuser entry created in users.yml");
        }
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

    public boolean isUserAlreadyLogged(String username) {
        return authenticated.containsValue(username);
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
        try {
            SkinsRestorer api = SkinsRestorerProvider.get();
            PlayerStorage playerStorage = api.getPlayerStorage();
            Optional<SkinProperty> property = playerStorage.getSkinForPlayer(p.getUniqueId(), p.getName());
            if (property.isPresent()) {
                String textureUrl = PropertyUtils.getSkinTextureUrl(property.get());
                data.set("users." + user + ".skinUrl", textureUrl);
            }
        } catch (DataRequestException e) {
            e.printStackTrace();
        }
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

        // Restore skin
        String savedSkinUrl = data.getString("users." + user + ".skinUrl");
        if (savedSkinUrl != null && !savedSkinUrl.isEmpty()) {
            SkinUtil.setAndApplySkin(p, savedSkinUrl);
            p.sendMessage("§aRestored saved skin for user " + user);
        } else {
            p.sendMessage("§eNo saved skin found, using default.");
        }
    }

    public class SkinUtil {

        public static void setAndApplySkin(Player player, String input) {
            SkinsRestorer api = SkinsRestorerProvider.get();

            try {
                SkinStorage skinStorage = api.getSkinStorage();
                PlayerStorage playerStorage = api.getPlayerStorage();

                // findOrCreateSkinData supports player names AND URLs
                Optional<InputDataResult> result = skinStorage.findOrCreateSkinData(input);

                if (result.isEmpty()) {
                    player.sendMessage("§cSkin not found: " + input);
                    return;
                }

                // store skin ID for player
                playerStorage.setSkinIdOfPlayer(player.getUniqueId(), result.get().getIdentifier());

                // apply immediately
                api.getSkinApplier(Player.class).applySkin(player);

            } catch (DataRequestException | MineSkinException e) {
                e.printStackTrace();
                player.sendMessage("§cFailed to set skin: " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                player.sendMessage("§cUnexpected error while applying skin.");
            }
        }
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
        return String.join(";",
            loc.getWorld().getName(),
            Double.toString(loc.getX()),
            Double.toString(loc.getY()),
            Double.toString(loc.getZ())
        );
    }

    private Location deserializeLocation(String s) {
        String[] p = s.split(";");
        return new Location(
            plugin.getServer().getWorld(p[0]),
            Double.parseDouble(p[1]),
            Double.parseDouble(p[2]),
            Double.parseDouble(p[3])
        );
    }
}
package com.simpleauth;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ExternalNotifier {
    private final Plugin plugin;
    private final HttpClient client;
    private final String supervisorUrl;
    private final int maxRetries;

    public ExternalNotifier(Plugin plugin, String supervisorUrl, int maxRetries, int timeoutSeconds) {
        this.plugin = plugin;
        this.supervisorUrl = supervisorUrl;
        this.maxRetries = maxRetries;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public void notifyAsync(String event, String ip, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> doNotify(event, ip, playerName, 0));
    }

    private void doNotify(String event, String ip, String playerName, int attempt) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("event", event);
            body.addProperty("player", playerName);
            body.addProperty("ip", ip);
            body.addProperty("timestamp", System.currentTimeMillis());

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(supervisorUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) return; // success

            if (attempt < maxRetries) {
                Thread.sleep(1000L * (attempt + 1));
                doNotify(event, ip, playerName, attempt + 1);
            } else {
                plugin.getLogger().warning("Notifier failed after retries: " + resp.statusCode() + " " + resp.body());
            }
        } catch (Exception ex) {
            if (attempt < maxRetries) {
                try { Thread.sleep(1000L * (attempt + 1)); } catch (InterruptedException ignored) {}
                doNotify(event, ip, playerName, attempt + 1);
            } else {
                plugin.getLogger().warning("Notifier error: " + ex.getMessage());
            }
        }
    }

    // convenience: reconciliation push of full online list
    public void pushCurrentOnline(String endpointSuffix) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                JsonArray arr = new JsonArray();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("name", p.getName());
                    var addr = p.getAddress();
                    o.addProperty("ip", addr == null ? "" : addr.getAddress().getHostAddress());
                    arr.add(o);
                }

                JsonObject body = new JsonObject();
                body.addProperty("event", "reconcile");
                body.add("players", arr);
                body.addProperty("timestamp", System.currentTimeMillis());

                String url = supervisorUrl; // or supervisorUrl + "/reconcile"
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .timeout(Duration.ofSeconds(5))
                        .build();

                client.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ex) {
                plugin.getLogger().warning("Reconcile push failed: " + ex.getMessage());
            }
        });
    }
}
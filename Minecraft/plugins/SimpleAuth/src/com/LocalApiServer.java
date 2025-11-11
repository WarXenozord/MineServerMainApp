package com.simpleauth;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class LocalApiServer {
    private final Plugin plugin;
    private HttpServer server;

    public LocalApiServer(Plugin plugin, String bind, int port) throws IOException {
        this.plugin = plugin;
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/online", this::handleOnline);
        server.setExecutor(Executors.newFixedThreadPool(2));
    }

   private void handleOnline(HttpExchange ex) throws IOException {
        JsonObject result = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Player p : Bukkit.getOnlinePlayers()) {
            JsonObject o = new JsonObject();
            String authUser = ((SimpleAuth) plugin).getUserManager().getAuthUser(p);
            o.addProperty("name", authUser != null ? authUser : "Not-Logged");
            var addr = p.getAddress();
            o.addProperty("ip", addr == null ? "" : addr.getAddress().getHostAddress());
            arr.add(o);
        }

        result.add("players", arr);
        result.addProperty("port", Bukkit.getPort()); // 👈 add actual MC server port

        byte[] resp = result.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, resp.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(resp);
        }
    }

    public void start() {
        server.start();
        plugin.getLogger().info("Local API started on " + server.getAddress());
    }

    public void stop() {
        server.stop(0);
        plugin.getLogger().info("Local API stopped");
    }
}
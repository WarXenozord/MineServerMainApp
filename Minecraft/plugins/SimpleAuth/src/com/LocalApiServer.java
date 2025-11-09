package com.simpleauth;

import com.sun.net.httpserver.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class LocalApiServer {
    private final Plugin plugin;
    private HttpServer server;

    public LocalApiServer(Plugin plugin, String bind, int port) throws IOException {
        this.plugin = plugin;
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/online", this::handleOnline);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
    }

    private void handleOnline(HttpExchange ex) throws IOException {
        JSONArray arr = new JSONArray();
        for (Player p : Bukkit.getOnlinePlayers()) {
            JSONObject o = new JSONObject();
            String authUser = ((SimpleAuth) plugin).getUserManager().getAuthUser(p);
            o.put("name", authUser != null ? authUser : p.getName());
            var addr = p.getAddress();
            o.put("ip", addr == null ? "" : addr.getAddress().getHostAddress());
            arr.put(o);
        }

        byte[] resp = arr.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(200, resp.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(resp);
        }
    }

    public void start() { server.start(); plugin.getLogger().info("Local API started on " + server.getAddress()); }
    public void stop() { server.stop(0); plugin.getLogger().info("Local API stopped"); }
}
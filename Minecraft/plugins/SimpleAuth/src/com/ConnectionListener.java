package com.simpleauth;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

public class ConnectionListener implements Listener {
    private final ExternalNotifier notifier;

    public ConnectionListener(ExternalNotifier notifier) {
        this.notifier = notifier;
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent e) {
        // player is attempting to login; address is available
        String ip = e.getAddress().getHostAddress();
        notifier.notifyAsync("connect_attempt", ip, e.getPlayer().getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // after join: fully connected
        var addr = e.getPlayer().getAddress();
        String ip = addr == null ? "" : addr.getAddress().getHostAddress();
        notifier.notifyAsync("connected", ip, e.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        var addr = e.getPlayer().getAddress();
        String ip = addr == null ? "" : addr.getAddress().getHostAddress();
        notifier.notifyAsync("disconnected", ip, e.getPlayer().getName());
    }
}
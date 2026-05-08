package me.efekt.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class Messages {
    private final JavaPlugin plugin;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public String msg(String key) {
        return color(plugin.getConfig().getString("messages." + key, ""));
    }

    public String prefixed(String key) {
        return msg("prefix") + msg(key);
    }

    public String format(String raw, Map<String, String> vars) {
        String out = raw;
        for (var e : vars.entrySet()) {
            out = out.replace("%" + e.getKey() + "%", e.getValue());
        }
        return out;
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(prefixed(key));
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public FileConfiguration cfg() {
        return plugin.getConfig();
    }
}


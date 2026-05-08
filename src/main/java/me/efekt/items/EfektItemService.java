package me.efekt.items;

import me.efekt.util.Messages;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EfektItemService {
    private final JavaPlugin plugin;
    private final Messages messages;
    private final EfektKeys keys;
    private final long durationMs;

    public EfektItemService(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.keys = new EfektKeys(plugin);
        long hours = plugin.getConfig().getLong("settings.self_destruct.duration_hours", 72);
        this.durationMs = Duration.ofHours(Math.max(1, hours)).toMillis();
    }

    public EfektKeys keys() {
        return keys;
    }

    public boolean isEfektItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(keys.type, PersistentDataType.STRING);
    }

    public EfektItemType getType(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        String raw = meta.getPersistentDataContainer().get(keys.type, PersistentDataType.STRING);
        return EfektItemType.fromKey(raw);
    }

    public long getExpiresAt(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1;
        Long v = meta.getPersistentDataContainer().get(keys.expiresAtMs, PersistentDataType.LONG);
        return v == null ? -1 : v;
    }

    public ItemStack create(EfektItemType type, int amount) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("items." + type.key());
        if (sec == null) throw new IllegalStateException("items." + type.key() + " missing in config");

        Material mat = Material.matchMaterial(sec.getString("material", ""));
        if (mat == null) mat = Material.STICK;

        ItemStack stack = new ItemStack(mat, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        String name = messages.color(sec.getString("name", ""));
        if (!name.isBlank()) meta.setDisplayName(name);

        List<String> loreRaw = sec.getStringList("lore");
        if (loreRaw != null && !loreRaw.isEmpty()) {
            List<String> lore = new ArrayList<>(loreRaw.size());
            String initialTimer = formatTimer(durationMs);
            for (String line : loreRaw) {
                lore.add(messages.color(line.replace("%timer%", initialTimer)));
            }
            meta.setLore(lore);
        }

        long now = System.currentTimeMillis();
        long exp = now + durationMs;

        meta.getPersistentDataContainer().set(keys.type, PersistentDataType.STRING, type.key());
        meta.getPersistentDataContainer().set(keys.createdAtMs, PersistentDataType.LONG, now);
        meta.getPersistentDataContainer().set(keys.expiresAtMs, PersistentDataType.LONG, exp);
        meta.getPersistentDataContainer().set(keys.uniqueId, PersistentDataType.STRING, UUID.randomUUID().toString());

        if (type == EfektItemType.KOVA) {
            meta.getPersistentDataContainer().set(keys.bucketFilled, PersistentDataType.BYTE, (byte) 0);
        }

        stack.setItemMeta(meta);
        return stack;
    }

    public boolean updateTimerLore(ItemStack item) {
        if (!isEfektItem(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        List<String> lore = meta.getLore();
        if (lore == null || lore.isEmpty()) return false;

        long exp = getExpiresAt(item);
        if (exp <= 0) return false;
        long remaining = Math.max(0, exp - System.currentTimeMillis());
        String timer = formatTimer(remaining);

        boolean changed = false;
        List<String> newLore = new ArrayList<>(lore.size());
        for (String line : lore) {
            String replaced = line;
            if (line.contains("ᴋᴇɴᴅɪɴɪ ɪᴍʜᴀ")) {
                replaced = messages.color("&8ᴋᴇɴᴅɪɴɪ ɪᴍʜᴀ: &7" + timer);
            }
            newLore.add(replaced);
            changed |= !replaced.equals(line);
        }

        if (!changed) return false;
        meta.setLore(newLore);
        item.setItemMeta(meta);
        return true;
    }

    public boolean isExpired(ItemStack item) {
        if (!isEfektItem(item)) return false;
        long exp = getExpiresAt(item);
        return exp > 0 && System.currentTimeMillis() >= exp;
    }

    public static String formatTimer(long ms) {
        if (ms <= 0) return "00:00";

        // Lore now updates hourly to avoid flicker + load.
        // Show remaining time as "gün:saat" => "DD:HH" (e.g. 03:05).
        long totalHours = (ms + 3_600_000L - 1) / 3_600_000L; // ceil to next hour
        long days = totalHours / 24;
        long hours = totalHours % 24;
        return String.format("%02d:%02d", days, hours);
    }
}


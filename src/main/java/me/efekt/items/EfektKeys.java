package me.efekt.items;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class EfektKeys {
    public final NamespacedKey type;
    public final NamespacedKey createdAtMs;
    public final NamespacedKey expiresAtMs;
    public final NamespacedKey bucketFilled;
    public final NamespacedKey uniqueId;

    public EfektKeys(JavaPlugin plugin) {
        this.type = new NamespacedKey(plugin, "efekt_type");
        this.createdAtMs = new NamespacedKey(plugin, "created_at_ms");
        this.expiresAtMs = new NamespacedKey(plugin, "expires_at_ms");
        this.bucketFilled = new NamespacedKey(plugin, "bucket_filled");
        this.uniqueId = new NamespacedKey(plugin, "unique_id");
    }
}


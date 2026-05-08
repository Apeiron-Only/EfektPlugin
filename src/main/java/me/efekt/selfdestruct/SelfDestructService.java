package me.efekt.selfdestruct;

import me.efekt.items.EfektItemService;
import me.efekt.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SelfDestructService {
    private final JavaPlugin plugin;
    private final Messages messages;
    private final EfektItemService items;
    private BukkitTask task;

    public SelfDestructService(JavaPlugin plugin, Messages messages, EfektItemService items) {
        this.plugin = plugin;
        this.messages = messages;
        this.items = items;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("settings.self_destruct.enabled", true)) return;
        long interval = plugin.getConfig().getLong("settings.self_destruct.scan.interval_ticks", 200);
        int max = plugin.getConfig().getInt("settings.self_destruct.scan.max_items_per_scan", 5000);

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> scanOnlinePlayers(max), interval, interval);
    }

    public void shutdown() {
        if (task != null) task.cancel();
    }

    public void scanOnlinePlayers(int maxItems) {
        int remaining = maxItems;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (remaining <= 0) return;
            remaining -= scanInventory(p.getInventory(), p);
            remaining -= scanInventory(p.getEnderChest(), p);

            Inventory open = p.getOpenInventory().getTopInventory();
            if (open != null && open.getHolder() != null) {
                remaining -= scanInventory(open, p);
            }
        }
    }

    public int scanInventory(Inventory inv, Player notifyPlayer) {
        if (inv == null) return 0;
        int changed = 0;
        ItemStack[] contents = inv.getContents();
        boolean notified = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null) continue;
            // Root item
            if (items.isEfektItem(it)) {
                if (items.isExpired(it)) {
                    inv.setItem(i, null);
                    changed++;
                    if (!notified && notifyPlayer != null) {
                        notifyPlayer.sendMessage(messages.prefixed("item_expired"));
                        notified = true;
                    }
                    continue;
                }
                items.updateTimerLore(it);
            }

            // Nested: shulker box contents
            scanItemRecursiveAndRemoveExpired(it, inv, i, notifyPlayer);
        }
        return changed;
    }

    public void scanChunkForContainers(Chunk chunk) {
        if (!plugin.getConfig().getBoolean("settings.self_destruct.scan.chunk_scan_on_load", true)) return;
        if (chunk == null) return;

        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Container c) {
                scanInventory(c.getInventory(), null);
            } else if (state instanceof InventoryHolder h) {
                scanInventory(h.getInventory(), null);
            }
        }
    }

    public void scanItemRecursiveAndRemoveExpired(ItemStack root, Inventory parent, int slot, Player notifyPlayer) {
        if (root == null) return;

        if (items.isEfektItem(root) && items.isExpired(root)) {
            parent.setItem(slot, null);
            if (notifyPlayer != null) notifyPlayer.sendMessage(messages.prefixed("item_expired"));
            return;
        }
        if (items.isEfektItem(root)) items.updateTimerLore(root);

        if (!(root.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta bsm)) return;
        BlockState bs = bsm.getBlockState();
        if (!(bs instanceof org.bukkit.block.ShulkerBox sh)) return;

        Inventory shInv = sh.getInventory();
        ItemStack[] shContents = shInv.getContents();
        boolean changed = false;
        for (int i = 0; i < shContents.length; i++) {
            ItemStack it = shContents[i];
            if (it == null) continue;
            if (items.isEfektItem(it) && items.isExpired(it)) {
                shInv.setItem(i, null);
                changed = true;
                if (notifyPlayer != null) notifyPlayer.sendMessage(messages.prefixed("item_expired"));
                continue;
            }
            if (items.isEfektItem(it)) {
                changed |= items.updateTimerLore(it);
            }
        }

        if (changed) {
            sh.update();
            bsm.setBlockState(sh);
            root.setItemMeta(bsm);
        }
    }
}


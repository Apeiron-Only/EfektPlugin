package me.efekt.listeners;

import me.efekt.economy.EconomyService;
import me.efekt.items.EfektItemService;
import me.efekt.items.EfektItemType;
import me.efekt.selfdestruct.SelfDestructService;
import me.efekt.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public final class EfektListeners implements Listener {
    private final JavaPlugin plugin;
    private final Messages messages;
    private final EfektItemService items;
    private final EconomyService economy;
    private final SelfDestructService selfDestruct;

    // Prevent recursion when we break blocks programmatically
    private final Set<Location> internalBreaks = new HashSet<>();
    private final Map<UUID, Location> dropTargetByPlayer = new java.util.HashMap<>();

    public EfektListeners(
            JavaPlugin plugin,
            Messages messages,
            EfektItemService items,
            EconomyService economy,
            SelfDestructService selfDestruct
    ) {
        this.plugin = plugin;
        this.messages = messages;
        this.items = items;
        this.economy = economy;
        this.selfDestruct = selfDestruct;
    }

    private boolean isSurvival(Player p) {
        return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;
    }

    private ItemStack hand(Player p, EquipmentSlot slot) {
        return slot == EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
    }

    private void setHand(Player p, EquipmentSlot slot, ItemStack item) {
        if (slot == EquipmentSlot.OFF_HAND) p.getInventory().setItemInOffHand(item);
        else p.getInventory().setItemInMainHand(item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent e) {
        selfDestruct.scanChunkForContainers(e.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p) {
            selfDestruct.scanInventory(p.getInventory(), p);
            selfDestruct.scanInventory(p.getEnderChest(), p);
            selfDestruct.scanInventory(e.getInventory(), p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        selfDestruct.scanInventory(p.getInventory(), p);
        selfDestruct.scanInventory(p.getEnderChest(), p);
        selfDestruct.scanInventory(e.getInventory(), p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        selfDestruct.scanInventory(p.getInventory(), p);
        selfDestruct.scanInventory(p.getEnderChest(), p);
        selfDestruct.scanInventory(e.getInventory(), p);
    }

    // iksir/kristal sistemi kaldırıldı

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Player p = e.getPlayer();
        if (!isSurvival(p)) return;

        EquipmentSlot slot = e.getHand();
        ItemStack inHand = hand(p, slot);
        if (!items.isEfektItem(inHand)) return;
        if (items.getType(inHand) != EfektItemType.KOVA) return;

        if (items.isExpired(inHand)) {
            setHand(p, slot, null);
            p.sendMessage(messages.prefixed("item_expired"));
            e.setCancelled(true);
            return;
        }

        // Replace with our custom water bucket while keeping PDC/expiry
        e.setCancelled(true);
        ItemStack water = new ItemStack(Material.WATER_BUCKET, inHand.getAmount());
        var meta = inHand.getItemMeta();
        if (meta != null) {
            water.setItemMeta(meta);
            var wMeta = water.getItemMeta();
            if (wMeta != null) {
                wMeta.getPersistentDataContainer().set(items.keys().bucketFilled, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                water.setItemMeta(wMeta);
            }
        }
        setHand(p, slot, water);
        p.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Player p = e.getPlayer();
        if (!isSurvival(p)) return;

        EquipmentSlot slot = e.getHand();
        ItemStack inHand = hand(p, slot);
        if (!items.isEfektItem(inHand)) return;
        if (items.getType(inHand) != EfektItemType.KOVA) return;

        if (items.isExpired(inHand)) {
            setHand(p, slot, null);
            p.sendMessage(messages.prefixed("item_expired"));
            e.setCancelled(true);
            return;
        }

        var meta = inHand.getItemMeta();
        if (meta == null) return;
        Byte filled = meta.getPersistentDataContainer().get(items.keys().bucketFilled, org.bukkit.persistence.PersistentDataType.BYTE);
        if (filled == null || filled == 0) return; // must fill first

        e.setCancelled(true);

        Block center = e.getBlockClicked().getRelative(e.getBlockFace());
        Location base = center.getLocation();
        var world = base.getWorld();
        if (world == null) return;

        int placed = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block b = world.getBlockAt(base.getBlockX() + dx, base.getBlockY(), base.getBlockZ() + dz);
                Material t = b.getType();
                if (canReplaceWithWater(b)) {
                    if (t != Material.AIR) {
                        b.breakNaturally();
                    }
                    b.setType(Material.WATER, true); // physics on => flowing
                    if (b.getBlockData() instanceof Levelled lvl) {
                        lvl.setLevel(0);
                        b.setBlockData(lvl, true);
                    }
                    placed++;
                } else if (b.getBlockData() instanceof Waterlogged wl && !wl.isWaterlogged()) {
                    wl.setWaterlogged(true);
                    b.setBlockData(wl, true);
                    placed++;
                }
            }
        }

        // turn back into empty special bucket
        ItemStack empty = new ItemStack(Material.BUCKET, inHand.getAmount());
        empty.setItemMeta(meta);
        var eMeta = empty.getItemMeta();
        if (eMeta != null) {
            eMeta.getPersistentDataContainer().set(items.keys().bucketFilled, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 0);
            empty.setItemMeta(eMeta);
        }
        setHand(p, slot, empty);
        p.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() == null) return;
        Player p = e.getPlayer();
        ItemStack inHand = hand(p, e.getHand());
        if (!items.isEfektItem(inHand)) return;

        if (items.isExpired(inHand)) {
            setHand(p, e.getHand(), null);
            p.sendMessage(messages.prefixed("item_expired"));
            e.setCancelled(true);
            return;
        }

        EfektItemType type = items.getType(inHand);
        if (type == null) return;

        if (type == EfektItemType.CUBUK) {
            if (e.getClickedBlock() == null) {
                if (e.getAction().isRightClick() || e.getAction().isLeftClick()) {
                    p.sendMessage(messages.prefixed("sell.no_chest"));
                }
                return;
            }
            Block b = e.getClickedBlock();
            if (!(b.getState() instanceof Container c)) {
                p.sendMessage(messages.prefixed("sell.no_chest"));
                return;
            }
            e.setCancelled(true);
            sellChest(p, c.getInventory());
        }
    }

    private void sellChest(Player p, Inventory inv) {
        if (!economy.hasEconomy()) {
            p.sendMessage(messages.prefixed("sell.economy_missing"));
            return;
        }
        if (inv == null) return;
        ItemStack[] contents = inv.getContents();
        double total = 0.0;
        boolean any = false;
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() == Material.AIR) continue;
            double value = economy.sellableValue(it);
            if (value <= 0.0) continue;
            total += value;
            inv.setItem(i, null);
            any = true;
        }
        if (!any) {
            p.sendMessage(messages.prefixed("sell.nothing_to_sell"));
            return;
        }
        economy.deposit(p, total);
        p.sendMessage(messages.format(messages.prefixed("sell.sold"), Map.of(
                "price", String.valueOf(total)
        )));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!isSurvival(p)) return;

        ItemStack tool = p.getInventory().getItemInMainHand();
        if (!items.isEfektItem(tool)) return;
        if (items.isExpired(tool)) {
            p.getInventory().setItemInMainHand(null);
            p.sendMessage(messages.prefixed("item_expired"));
            e.setCancelled(true);
            return;
        }

        EfektItemType type = items.getType(tool);
        if (type == null) return;

        Location loc = e.getBlock().getLocation();
        if (internalBreaks.contains(loc)) return;

        if (type == EfektItemType.BALTA) {
            if (isLog(e.getBlock().getType())) {
                e.setCancelled(true);
                Location dropLoc = dropLocationInFront(p);
                dropTargetByPlayer.put(p.getUniqueId(), dropLoc);
                try {
                    fellTree(p, e.getBlock(), tool);
                } finally {
                    dropTargetByPlayer.remove(p.getUniqueId());
                }
            }
        } else if (type == EfektItemType.KAZMA) {
            e.setCancelled(true);
            Location dropLoc = dropLocationInFront(p);
            dropTargetByPlayer.put(p.getUniqueId(), dropLoc);
            try {
                break3x3(p, e.getBlock(), tool, EfektItemType.KAZMA);
            } finally {
                dropTargetByPlayer.remove(p.getUniqueId());
            }
        } else if (type == EfektItemType.KUREK) {
            e.setCancelled(true);
            Location dropLoc = dropLocationInFront(p);
            dropTargetByPlayer.put(p.getUniqueId(), dropLoc);
            try {
                break3x3(p, e.getBlock(), tool, EfektItemType.KUREK);
            } finally {
                dropTargetByPlayer.remove(p.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDrops(BlockDropItemEvent e) {
        Player p = e.getPlayer();
        Location target = dropTargetByPlayer.get(p.getUniqueId());
        if (target == null) return;
        if (!internalBreaks.contains(e.getBlock().getLocation())) return;

        for (Item it : e.getItems()) {
            it.teleport(target);
            it.setVelocity(new Vector(0, 0.05, 0));
        }
    }

    private boolean isLog(Material m) {
        String name = m.name();
        return name.endsWith("_LOG")
                || name.endsWith("_WOOD")
                || name.endsWith("_STEM")
                || name.endsWith("_HYPHAE")
                || name.equals("BAMBOO_BLOCK")
                || name.equals("STRIPPED_BAMBOO_BLOCK");
    }

    private boolean isLeaves(Material m) {
        return m.name().endsWith("_LEAVES") || m.name().endsWith("_WART_BLOCK");
    }

    /** Yüz komşuluk: bitişik aynı tür ağaçların gövdeleri köşeden/çaprazdan birleşmez. */
    private static final int[][] LOG_FACE_OFFSETS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1},
    };

    /**
     * Kırılan blokla aynı bileşende sayılacak odunlar. 26 komşu yerine yüz komşusu kullanılır;
     * yan yana aynı tür iki ağaç köşeden birleşince tek dev ağaç sanılmaz, sunucu kasması önlenir.
     */
    private Set<Block> collectTreeLogsFaceConnected(Block start, Material logType, int maxLogs) {
        Queue<Block> q = new ArrayDeque<>();
        Set<Location> visited = new HashSet<>();
        Set<Block> logs = new HashSet<>();
        q.add(start);
        visited.add(start.getLocation());

        while (!q.isEmpty() && logs.size() < maxLogs) {
            Block b = q.poll();
            if (b.getType() != logType) continue;
            logs.add(b);
            for (int[] o : LOG_FACE_OFFSETS) {
                Block nb = b.getRelative(o[0], o[1], o[2]);
                Location l = nb.getLocation();
                if (!visited.add(l)) continue;
                if (nb.getType() == logType) q.add(nb);
            }
        }
        return logs;
    }

    /** Eski davranış: 26 komşu — bitişik aynı tür gövdeler tek ağaç sayılabilir. */
    private Set<Block> collectTreeLogsFull26(Block start, Material logType, int maxLogs) {
        Queue<Block> q = new ArrayDeque<>();
        Set<Location> visited = new HashSet<>();
        Set<Block> logs = new HashSet<>();
        q.add(start);
        visited.add(start.getLocation());

        while (!q.isEmpty() && logs.size() < maxLogs) {
            Block b = q.poll();
            if (b.getType() != logType) continue;
            logs.add(b);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block nb = b.getRelative(dx, dy, dz);
                        Location l = nb.getLocation();
                        if (!visited.add(l)) continue;
                        if (nb.getType() == logType) q.add(nb);
                    }
                }
            }
        }
        return logs;
    }

    private void fellTree(Player p, Block start, ItemStack tool) {
        Material logType = start.getType();
        int maxLogs = plugin.getConfig().getInt("settings.limits.tree.max_logs", 512);
        int maxLeaves = plugin.getConfig().getInt("settings.limits.tree.max_leaves", 2048);
        int maxConnected = plugin.getConfig().getInt("settings.limits.tree.max_connected_blocks", maxLogs + maxLeaves);
        // Must cover trunk + canopy; clamp so config typos can't make it tiny.
        if (maxConnected < maxLogs + 64) {
            maxConnected = maxLogs + maxLeaves;
        }

        boolean logsFull26 = plugin.getConfig().getBoolean("settings.limits.tree.logs_full_connectivity", false);
        Set<Block> ourLogs = logsFull26
                ? collectTreeLogsFull26(start, logType, maxLogs)
                : collectTreeLogsFaceConnected(start, logType, maxLogs);

        Set<Location> ourLogLocs = new HashSet<>();
        for (Block lb : ourLogs) {
            ourLogLocs.add(lb.getLocation());
        }

        /*
         * Taç BFS: yalnızca bu ağacın odunlarından “içeri” geçilir; komşu ağacın aynı tür odunu
         * duvar gibi davranır (kırılmaz, üzerinden yürünmez). Yapraklar yaprak üzerinden yayılabilir
         * (ortak taç / diğer gövdeye kadar). Tek yaprak türü (leafKind) korunur.
         */
        World world = start.getWorld();
        if (world == null) return;

        Set<Location> foliage = new HashSet<>();
        ArrayDeque<Block> grow = new ArrayDeque<>();
        Material leafKind = null;

        for (Block logBlock : ourLogs) {
            if (logBlock.getWorld() != world) continue;
            Location fl = logBlock.getLocation();
            if (foliage.add(fl)) {
                grow.add(logBlock);
            }
        }

        while (!grow.isEmpty() && foliage.size() < maxConnected) {
            Block b = grow.poll();
            if (b.getWorld() != world) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block nb = b.getRelative(dx, dy, dz);
                        Location nl = nb.getLocation();
                        Material t = nb.getType();
                        boolean take;
                        if (t == logType) {
                            take = ourLogLocs.contains(nl);
                        } else if (isLeaves(t)) {
                            if (leafKind == null) {
                                leafKind = t;
                                take = true;
                            } else {
                                take = t == leafKind;
                            }
                        } else {
                            take = false;
                        }
                        if (!take) continue;
                        if (!foliage.add(nl)) continue;
                        grow.add(nb);
                    }
                }
            }
        }

        ArrayList<Location> toProcess = new ArrayList<>(foliage);

        // Önce yalnızca bu ağacın gövde/ dalları (yabancı odun taçtan seçilmedi)
        for (Location loc : toProcess) {
            Block bl = world.getBlockAt(loc);
            if (ourLogLocs.contains(loc) && bl.getType() == logType) {
                breakWithRules(p, bl, tool, EfektItemType.BALTA);
            }
        }

        int removedLeaves = 0;
        for (Location loc : toProcess) {
            if (removedLeaves >= maxLeaves) break;
            Block nb = world.getBlockAt(loc);
            Material ft = nb.getType();
            if (leafKind == null || ft != leafKind || !isLeaves(ft)) continue;

            Location l = nb.getLocation();
            internalBreaks.add(l);
            try {
                nb.breakNaturally(tool, true);
            } finally {
                internalBreaks.remove(l);
            }
            removedLeaves++;
        }
    }

    private void dropAndBreak(Player p, Block b, ItemStack tool) {
        // Let Minecraft handle drops naturally (respects enchants)
        b.breakNaturally(tool, true);
    }

    private void break3x3(Player p, Block center, ItemStack tool, EfektItemType toolType) {
        Vector dir = p.getEyeLocation().getDirection();
        double ax = Math.abs(dir.getX());
        double ay = Math.abs(dir.getY());
        double az = Math.abs(dir.getZ());

        int maxBlocks = plugin.getConfig().getInt("settings.limits.break3x3.max_blocks", 9);
        int broken = 0;

        int[][] offsets;
        if (ay >= ax && ay >= az) {
            // looking up/down: horizontal plane (x,z)
            offsets = planeXZ();
        } else if (ax >= az) {
            // vertical plane (y,z)
            offsets = planeYZ();
        } else {
            // vertical plane (x,y)
            offsets = planeXY();
        }

        for (int[] o : offsets) {
            if (broken >= maxBlocks) break;
            Block b = center.getRelative(o[0], o[1], o[2]);
            if (b.getType() == Material.AIR) continue;
            if (b.getType() == Material.BEDROCK || b.getType() == Material.BARRIER) continue;
            if (!allowedForTool(toolType, b.getType())) continue;
            if (b.getDrops(tool, p).isEmpty()) continue; // if it won't drop with this tool, don't break
            if (breakWithRules(p, b, tool, toolType)) broken++;
        }
    }

    private boolean breakWithRules(Player p, Block b, ItemStack tool, EfektItemType toolType) {
        Location l = b.getLocation();
        internalBreaks.add(l);
        try {
            // Use native break logic (events, durability, etc.)
            return p.breakBlock(b);
        } finally {
            internalBreaks.remove(l);
        }
    }

    private static boolean allowedForTool(EfektItemType toolType, Material blockType) {
        if (toolType == EfektItemType.KAZMA) {
            return Tag.MINEABLE_PICKAXE.isTagged(blockType);
        }
        if (toolType == EfektItemType.KUREK) {
            return Tag.MINEABLE_SHOVEL.isTagged(blockType);
        }
        if (toolType == EfektItemType.BALTA) {
            return Tag.MINEABLE_AXE.isTagged(blockType) || blockType.name().endsWith("_LOG") || blockType.name().endsWith("_WOOD");
        }
        return true;
    }

    private static boolean canReplaceWithWater(Block b) {
        Material t = b.getType();
        if (t == Material.AIR) return true;
        if (t == Material.STRING) return true;
        if (t == Material.WATER || t == Material.LAVA) return false;

        // Passable + non-solid covers grass, flowers, torches, etc.
        if (b.isPassable() && !t.isSolid()) return true;

        String n = t.name();
        if (n.endsWith("_TORCH") || n.endsWith("_WALL_TORCH")) return true;
        if (n.endsWith("_SIGN") || n.endsWith("_WALL_SIGN")) return true;
        if (n.endsWith("_BANNER") || n.endsWith("_WALL_BANNER")) return true;
        if (n.endsWith("_CARPET")) return true;
        return false;
    }

    private static Location dropLocationInFront(Player p) {
        Block target = p.getTargetBlockExact(5);
        Location base = (target != null ? target.getLocation() : p.getLocation());
        return base.add(0.5, 1.2, 0.5);
    }

    private static int[][] planeXZ() {
        int idx = 0;
        int[][] arr = new int[9][3];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                arr[idx++] = new int[]{dx, 0, dz};
            }
        }
        return arr;
    }

    private static int[][] planeYZ() {
        int idx = 0;
        int[][] arr = new int[9][3];
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                arr[idx++] = new int[]{0, dy, dz};
            }
        }
        return arr;
    }

    private static int[][] planeXY() {
        int idx = 0;
        int[][] arr = new int[9][3];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                arr[idx++] = new int[]{dx, dy, 0};
            }
        }
        return arr;
    }
}


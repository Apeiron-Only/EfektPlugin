package me.efekt.economy;

import me.efekt.util.Messages;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

public final class EconomyService {
    private final JavaPlugin plugin;
    private final Messages messages;
    private Economy vault;
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    public EconomyService(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        loadVault();
        loadPrices();
    }

    private void loadVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return;
        this.vault = rsp.getProvider();
    }

    private void loadPrices() {
        prices.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("prices");
        if (sec == null) return;
        for (String k : sec.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m == null) continue;
            prices.put(m, sec.getDouble(k, 0.0));
        }
    }

    public boolean hasEconomy() {
        boolean preferVault = plugin.getConfig().getBoolean("economy.prefer_vault", true);
        if (preferVault && vault != null) return true;
        String cmd = plugin.getConfig().getString("economy.fallback_eco_command", "");
        return cmd != null && !cmd.isBlank();
    }

    public double getPrice(Material m) {
        Double v = prices.get(m);
        if (v == null) return 0.0;
        return Math.max(0.0, v);
    }

    public double sellableValue(ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return 0.0;
        double unit = getPrice(it.getType());
        if (unit <= 0.0) return 0.0;
        return unit * it.getAmount();
    }

    public boolean deposit(Player player, double amount) {
        if (amount <= 0.0) return true;
        boolean preferVault = plugin.getConfig().getBoolean("economy.prefer_vault", true);
        if (preferVault && vault != null) {
            return vault.depositPlayer(player, amount).transactionSuccess();
        }
        String template = plugin.getConfig().getString("economy.fallback_eco_command", "eco give %player% %amount%");
        String cmd = template
                .replace("%player%", player.getName())
                .replace("%amount%", String.valueOf(amount));
        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        return Bukkit.dispatchCommand(console, cmd);
    }
}


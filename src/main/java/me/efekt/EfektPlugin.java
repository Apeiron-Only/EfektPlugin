package me.efekt;

import me.efekt.commands.EfektCommand;
import me.efekt.economy.EconomyService;
import me.efekt.items.EfektItemService;
import me.efekt.listeners.EfektListeners;
import me.efekt.selfdestruct.SelfDestructService;
import me.efekt.util.Messages;
import org.bukkit.plugin.java.JavaPlugin;

public final class EfektPlugin extends JavaPlugin {
    private Messages messages;
    private EfektItemService itemService;
    private EconomyService economyService;
    private SelfDestructService selfDestructService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.messages = new Messages(this);
        this.itemService = new EfektItemService(this, messages);
        this.economyService = new EconomyService(this, messages);
        this.selfDestructService = new SelfDestructService(this, messages, itemService);

        var cmd = getCommand("efekt");
        if (cmd != null) {
            cmd.setExecutor(new EfektCommand(this, messages, itemService));
            cmd.setTabCompleter(new EfektCommand(this, messages, itemService));
        }

        getServer().getPluginManager().registerEvents(
                new EfektListeners(this, messages, itemService, economyService, selfDestructService),
                this
        );

        selfDestructService.start();
    }

    @Override
    public void onDisable() {
        // no-op
    }
}


package me.efekt.commands;

import me.efekt.items.EfektItemService;
import me.efekt.items.EfektItemType;
import me.efekt.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class EfektCommand implements CommandExecutor, TabCompleter {
    private final Object lock = new Object();
    private final Messages messages;
    private final EfektItemService items;

    public EfektCommand(Object plugin, Messages messages, EfektItemService items) {
        this.messages = messages;
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("efekt.admin")) {
            messages.send(sender, "no_permission");
            return true;
        }

        if (args.length < 1) {
            messages.send(sender, "invalid_usage");
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length != 4) {
                messages.send(sender, "invalid_usage");
                return true;
            }
            EfektItemType type = EfektItemType.fromKey(args[1]);
            if (type == null) {
                messages.send(sender, "invalid_usage");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                messages.send(sender, "player_not_found");
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                messages.send(sender, "amount_invalid");
                return true;
            }
            if (amount <= 0 || amount > 64 * 64) {
                messages.send(sender, "amount_invalid");
                return true;
            }

            ItemStack stack = items.create(type, amount);
            var leftovers = target.getInventory().addItem(stack);
            for (var left : leftovers.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), left);
            }

            sender.sendMessage(messages.format(messages.prefixed("give_success"), Map.of(
                    "player", target.getName(),
                    "amount", String.valueOf(amount),
                    "item", type.key()
            )));
            target.sendMessage(messages.format(messages.prefixed("received_item"), Map.of(
                    "amount", String.valueOf(amount),
                    "item", type.key()
            )));

            return true;
        }

        messages.send(sender, "invalid_usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("efekt.admin")) return out;
        if (args.length == 1) {
            out.add("give");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (var t : EfektItemType.values()) out.add(t.key());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        }
        return out;
    }
}


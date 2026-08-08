package com.example.maple;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * /maple give <log|stripped_log|wood|stripped_wood|planks|leaves> [amount]
 *
 * Simple admin command to obtain the custom items.
 */
public final class MapleCommand implements CommandExecutor, TabCompleter {

    private final MapleItems items;

    public MapleCommand(MapleItems items) {
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            return false;
        }

        MapleType type = MapleType.byId(args[1].toLowerCase());
        if (type == null) {
            return false;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {
                player.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
                return true;
            }
        }

        ItemStack stack = items.create(type, amount);
        player.getInventory().addItem(stack);
        player.sendMessage(Component.text("Выдано: ", NamedTextColor.GREEN)
                .append(stack.getItemMeta().displayName())
                .append(Component.text(" x" + amount, NamedTextColor.GREEN)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Stream.of("give"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(Arrays.stream(MapleType.values()).map(MapleType::id), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(Stream.of("1", "16", "64"), args[2]);
        }
        return List.of();
    }

    private List<String> filter(Stream<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.filter(option -> option.startsWith(lower)).toList();
    }
}

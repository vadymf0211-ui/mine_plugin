package com.example.maple;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Factory + identification for the custom Maple items.
 *
 * Items are plain vanilla NOTE_BLOCK ItemStacks with:
 *  - a CustomModelData value (used ONLY by the resource pack to swap the model),
 *  - a PersistentDataContainer tag (used by the plugin to identify the item —
 *    reliable even if another pack/plugin reuses the same CustomModelData),
 *  - a display name with italics explicitly disabled (so it looks 100% vanilla),
 *  - no lore at all.
 */
public final class MapleItems {

    /** CustomModelData for the Maple Log item (resource pack contract). */
    public static final int CMD_MAPLE_LOG = 1001;
    /** CustomModelData for the Maple Leaves item (resource pack contract). */
    public static final int CMD_MAPLE_LEAVES = 1002;

    /** PDC values. */
    public static final String TYPE_LOG = "log";
    public static final String TYPE_LEAVES = "leaves";

    private final MaplePlugin plugin;

    public MapleItems(MaplePlugin plugin) {
        this.plugin = plugin;
    }

    /** Creates the Maple Log item ("Клён"). */
    public ItemStack createMapleLog(int amount) {
        return build(Material.NOTE_BLOCK, "Клён", CMD_MAPLE_LOG, TYPE_LOG, amount);
    }

    /** Creates the Maple Leaves item ("Листва клёна"). */
    public ItemStack createMapleLeaves(int amount) {
        return build(Material.RED_MUSHROOM_BLOCK, "Листва клёна", CMD_MAPLE_LEAVES, TYPE_LEAVES, amount);
    }

    private ItemStack build(Material material, String name, int customModelData, String type, int amount) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();

        // Vanilla-looking name: white, non-italic, no lore.
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        meta.setCustomModelData(customModelData);
        meta.getPersistentDataContainer().set(MaplePlugin.MAPLE_KEY, PersistentDataType.STRING, type);

        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Returns "log", "leaves" or null for the given item.
     */
    public String getMapleType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer()
                .get(MaplePlugin.MAPLE_KEY, PersistentDataType.STRING);
    }
}

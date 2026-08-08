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
 * Items are plain vanilla ItemStacks of the host material with:
 *  - a CustomModelData value (used ONLY by the resource pack to swap the model),
 *  - a PersistentDataContainer tag (used by the plugin to identify the item —
 *    reliable even if another pack/plugin reuses the same CustomModelData),
 *  - a display name with italics explicitly disabled (so it looks 100% vanilla),
 *  - no lore at all.
 */
public final class MapleItems {

    private final MaplePlugin plugin;

    public MapleItems(MaplePlugin plugin) {
        this.plugin = plugin;
    }

    /** Creates the item for any maple type. */
    public ItemStack create(MapleType type, int amount) {
        ItemStack stack = new ItemStack(type.host(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();

        // Vanilla-looking name: white, non-italic, no lore.
        meta.displayName(Component.text(type.displayName()).decoration(TextDecoration.ITALIC, false));
        meta.setCustomModelData(type.customModelData());
        meta.getPersistentDataContainer().set(MaplePlugin.MAPLE_KEY, PersistentDataType.STRING, type.id());

        stack.setItemMeta(meta);
        return stack;
    }

    /** Convenience shortcuts used across the plugin. */
    public ItemStack createMapleLog(int amount) {
        return create(MapleType.LOG, amount);
    }

    public ItemStack createMapleLeaves(int amount) {
        return create(MapleType.LEAVES, amount);
    }

    /**
     * A RENAMED plain vanilla item (used for the warped-family takeover
     * results: stairs, slabs, fences, gates, buttons, plates, doors,
     * trapdoors). No PDC, no CustomModelData — the whole warped family is
     * re-textured by the resource pack, so these are ordinary vanilla items
     * with a clean white non-italic name and full vanilla mechanics.
     */
    public ItemStack renamed(Material material, String name, int amount) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Resolves the maple type of an item, or null if it is not a maple item.
     */
    public MapleType getMapleType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String id = stack.getItemMeta().getPersistentDataContainer()
                .get(MaplePlugin.MAPLE_KEY, PersistentDataType.STRING);
        return id == null ? null : MapleType.byId(id);
    }
}

package com.example.maple;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MapleBlocks — adds Maple Log and Maple Leaves as "clean" custom blocks.
 *
 * Implementation: reserved vanilla NOTE_BLOCK states (a real wooden block —
 * proper axe tool, vanilla wood sounds, wood-like hardness).
 *  - Maple Log    -> note_block[instrument=didgeridoo, note=1]
 *  - Maple Leaves -> note_block[instrument=didgeridoo, note=2]
 *
 * The plugin cancels physics updates for all note blocks, so a vanilla note
 * block can never change its instrument from the default "harp" — which makes
 * the reserved "didgeridoo" states unreachable in survival and safe for the
 * resource pack to re-texture without touching real note blocks.
 */
public final class MaplePlugin extends JavaPlugin {

    /** PDC key that marks our custom items. Value: "log" or "leaves". */
    public static NamespacedKey MAPLE_KEY;

    private MapleItems items;

    @Override
    public void onEnable() {
        MAPLE_KEY = new NamespacedKey(this, "maple_block");

        this.items = new MapleItems(this);

        getServer().getPluginManager().registerEvents(new MapleListener(this, items), this);

        MapleCommand command = new MapleCommand(items);
        getCommand("maple").setExecutor(command);
        getCommand("maple").setTabCompleter(command);

        getLogger().info("MapleBlocks enabled. Maple Log & Maple Leaves registered.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MapleBlocks disabled.");
    }

    public MapleItems getItems() {
        return items;
    }
}

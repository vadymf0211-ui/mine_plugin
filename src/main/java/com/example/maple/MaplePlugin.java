package com.example.maple;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MapleBlocks — adds Maple Log and Maple Leaves as "clean" custom blocks.
 *
 * Implementation: unused vanilla BlockStates.
 *  - Maple Log    -> BROWN_MUSHROOM_BLOCK  [up=true, down=true, north/south/east/west=false]
 *  - Maple Leaves -> RED_MUSHROOM_BLOCK    [up=true, down=true, north/south/east/west=false]
 *
 * These face combinations never occur in vanilla worldgen (huge mushroom caps
 * never have down=true with all sides false) and never occur from player
 * placement (a player-placed mushroom block has ALL faces = true), so the
 * resource pack can safely re-texture exactly these states without touching
 * the vanilla look of real mushroom blocks.
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

package com.example.maple;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MapleBlocks — adds Maple Log and Maple Leaves as "clean" custom blocks.
 *
 * Implementation: reserved vanilla BlockStates.
 *  - Maple Log    -> note_block[instrument=didgeridoo, note=1]
 *                    (real wooden block; the plugin locks note block physics so
 *                    the reserved instrument is unreachable, and places blocks
 *                    against it server-side so it stacks like a normal log)
 *  - Maple Leaves -> red_mushroom_block[up=true, down=true, sides=false]
 *                    (non-interactable, breaks fast like foliage; this face
 *                    combination never occurs in vanilla)
 *
 * oak_log / oak_leaves cannot be used as hosts: every one of their states is
 * reachable in vanilla gameplay, so there is nothing safe to re-texture.
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

package com.example.maple;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MapleBlocks — adds Maple Log and Maple Leaves as "clean" custom blocks.
 *
 * Implementation: reserved vanilla BlockStates — see MapleType for the full
 * registry (log, stripped log, wood, stripped wood, planks on note_block
 * states; leaves on an azalea_leaves state) and the reasoning behind each
 * host choice. MapleRecipes wires the vanilla-parity crafting set.
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

        new MapleRecipes(this, items).registerAll();

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

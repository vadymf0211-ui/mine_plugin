package com.example.maple;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * Vanilla-parity crafting recipes for the maple wood set.
 *
 * Custom ingredients are matched with RecipeChoice.ExactChoice (CustomModelData
 * + PDC tag + name must all match), so plain note blocks / azalea leaves never
 * satisfy these recipes, and maple items never satisfy vanilla tag recipes.
 *
 *   1 log / stripped log / wood / stripped wood -> 4 planks
 *   4 logs (2x2)                                -> 3 wood
 *   4 stripped logs (2x2)                       -> 3 stripped wood
 *   2 planks (column)                           -> 4 sticks (vanilla item)
 *   4 planks (2x2)                              -> crafting table
 *   8 planks (ring)                             -> chest
 *
 * Intentionally NOT included (per project scope): signs, hanging signs, shelves.
 * Doors/trapdoors/saplings are excluded because they cannot exist as custom
 * blocks with the reserved-BlockState method (see MapleType docs).
 */
public final class MapleRecipes {

    private final MaplePlugin plugin;
    private final MapleItems items;

    public MapleRecipes(MaplePlugin plugin, MapleItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void registerAll() {
        // 1 log-like block -> 4 planks (vanilla: any log/wood -> 4 planks)
        registerPlanksFrom(MapleType.LOG);
        registerPlanksFrom(MapleType.STRIPPED_LOG);
        registerPlanksFrom(MapleType.WOOD);
        registerPlanksFrom(MapleType.STRIPPED_WOOD);

        // 4 logs (2x2) -> 3 wood; same for stripped
        registerWoodFrom(MapleType.LOG, MapleType.WOOD);
        registerWoodFrom(MapleType.STRIPPED_LOG, MapleType.STRIPPED_WOOD);

        // 2 planks (column) -> 4 sticks (plain vanilla sticks)
        ShapedRecipe sticks = new ShapedRecipe(key("sticks_from_maple_planks"), new ItemStack(Material.STICK, 4));
        sticks.shape("P", "P");
        sticks.setIngredient('P', exact(MapleType.PLANKS));
        plugin.getServer().addRecipe(sticks);

        // 4 planks (2x2) -> crafting table
        ShapedRecipe craftingTable = new ShapedRecipe(key("crafting_table_from_maple_planks"),
                new ItemStack(Material.CRAFTING_TABLE));
        craftingTable.shape("PP", "PP");
        craftingTable.setIngredient('P', exact(MapleType.PLANKS));
        plugin.getServer().addRecipe(craftingTable);

        // 8 planks (ring) -> chest
        ShapedRecipe chest = new ShapedRecipe(key("chest_from_maple_planks"), new ItemStack(Material.CHEST));
        chest.shape("PPP", "P P", "PPP");
        chest.setIngredient('P', exact(MapleType.PLANKS));
        plugin.getServer().addRecipe(chest);

        plugin.getLogger().info("Registered maple crafting recipes.");
    }

    private void registerPlanksFrom(MapleType source) {
        ShapelessRecipe recipe = new ShapelessRecipe(key("maple_planks_from_" + source.id()),
                items.create(MapleType.PLANKS, 4));
        recipe.addIngredient(exact(source));
        plugin.getServer().addRecipe(recipe);
    }

    private void registerWoodFrom(MapleType source, MapleType result) {
        ShapedRecipe recipe = new ShapedRecipe(key("maple_" + result.id() + "_from_" + source.id()),
                items.create(result, 3));
        recipe.shape("LL", "LL");
        recipe.setIngredient('L', exact(source));
        plugin.getServer().addRecipe(recipe);
    }

    private RecipeChoice.ExactChoice exact(MapleType type) {
        return new RecipeChoice.ExactChoice(items.create(type, 1));
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name);
    }
}

package com.example.maple;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * Full vanilla-parity crafting set for the maple wood family.
 *
 * Custom ingredients are matched with RecipeChoice.ExactChoice (CustomModelData
 * + PDC tag + name must all match), so plain note blocks / azalea leaves never
 * satisfy these recipes, and maple items never satisfy vanilla tag recipes.
 *
 * CUSTOM results (real maple blocks):
 *   1 log / stripped log / wood / stripped wood -> 4 maple planks
 *   4 logs (2x2)                                -> 3 maple wood
 *   4 stripped logs (2x2)                       -> 3 stripped maple wood
 *
 * VANILLA results (shaped blocks cannot exist as reserved-state custom blocks —
 * their geometry/collision is engine-side — so these recipes yield the SPRUCE
 * counterparts, the closest vanilla wood tone to maple; change SHAPED_WOOD to
 * another vanilla family if desired):
 *   6 planks (two rows)      -> 4 stairs
 *   3 planks (row)           -> 6 slabs
 *   4 planks + 2 sticks      -> 3 fences
 *   2 planks + 4 sticks      -> 1 fence gate
 *   6 planks (2x3)           -> 3 doors
 *   6 planks (3x2)           -> 2 trapdoors
 *   1 plank                  -> 1 button
 *   2 planks (row)           -> 1 pressure plate
 *   5 planks (boat shape)    -> 1 boat
 *   6 planks + 2 wooden slabs-> 1 barrel
 *   3 planks (bowl shape)    -> 4 bowls
 *   2 planks (column)        -> 4 sticks
 *   4 planks (2x2)           -> crafting table
 *   8 planks (ring)          -> chest
 *
 * Intentionally NOT included (per project scope): signs, hanging signs, shelves.
 */
public final class MapleRecipes {

    private final MaplePlugin plugin;
    private final MapleItems items;

    public MapleRecipes(MaplePlugin plugin, MapleItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void registerAll() {
        // ---------- custom maple results ----------
        registerPlanksFrom(MapleType.LOG);
        registerPlanksFrom(MapleType.STRIPPED_LOG);
        registerPlanksFrom(MapleType.WOOD);
        registerPlanksFrom(MapleType.STRIPPED_WOOD);

        registerWoodFrom(MapleType.LOG, MapleType.WOOD);
        registerWoodFrom(MapleType.STRIPPED_LOG, MapleType.STRIPPED_WOOD);

        // ---------- vanilla results from maple planks ----------
        RecipeChoice.ExactChoice planks = exact(MapleType.PLANKS);
        RecipeChoice stick = new RecipeChoice.MaterialChoice(Material.STICK);

        // 2 planks (column) -> 4 sticks
        shaped("sticks_from_maple_planks", new ItemStack(Material.STICK, 4),
                new String[]{"P", "P"}, 'P', planks, null, null);

        // 4 planks (2x2) -> crafting table
        shaped("crafting_table_from_maple_planks", new ItemStack(Material.CRAFTING_TABLE),
                new String[]{"PP", "PP"}, 'P', planks, null, null);

        // 8 planks (ring) -> chest
        shaped("chest_from_maple_planks", new ItemStack(Material.CHEST),
                new String[]{"PPP", "P P", "PPP"}, 'P', planks, null, null);

        // 6 planks (two rows of three... vanilla stairs shape) -> 4 stairs
        shaped("stairs_from_maple_planks", new ItemStack(Material.SPRUCE_STAIRS, 4),
                new String[]{"P  ", "PP ", "PPP"}, 'P', planks, null, null);

        // 3 planks (row) -> 6 slabs
        shaped("slab_from_maple_planks", new ItemStack(Material.SPRUCE_SLAB, 6),
                new String[]{"PPP"}, 'P', planks, null, null);

        // 4 planks + 2 sticks -> 3 fences
        shaped("fence_from_maple_planks", new ItemStack(Material.SPRUCE_FENCE, 3),
                new String[]{"PSP", "PSP"}, 'P', planks, 'S', stick);

        // 2 planks + 4 sticks -> 1 fence gate
        shaped("fence_gate_from_maple_planks", new ItemStack(Material.SPRUCE_FENCE_GATE),
                new String[]{"SPS", "SPS"}, 'P', planks, 'S', stick);

        // 6 planks (2x3) -> 3 CUSTOM maple doors (warped_door[powered=true] host)
        shaped("door_from_maple_planks", items.create(MapleType.DOOR, 3),
                new String[]{"PP", "PP", "PP"}, 'P', planks, null, null);

        // 6 planks (3x2) -> 2 CUSTOM maple trapdoors (warped_trapdoor[powered=true] host)
        shaped("trapdoor_from_maple_planks", items.create(MapleType.TRAPDOOR, 2),
                new String[]{"PPP", "PPP"}, 'P', planks, null, null);

        // 1 plank -> 1 button
        ShapelessRecipe button = new ShapelessRecipe(key("button_from_maple_planks"),
                new ItemStack(Material.SPRUCE_BUTTON));
        button.addIngredient(planks);
        plugin.getServer().addRecipe(button);

        // 2 planks (row) -> 1 pressure plate
        shaped("pressure_plate_from_maple_planks", new ItemStack(Material.SPRUCE_PRESSURE_PLATE),
                new String[]{"PP"}, 'P', planks, null, null);

        // 5 planks (boat shape) -> 1 boat
        shaped("boat_from_maple_planks", new ItemStack(Material.SPRUCE_BOAT),
                new String[]{"P P", "PPP"}, 'P', planks, null, null);

        // 6 planks + 2 wooden slabs -> 1 barrel
        shaped("barrel_from_maple_planks", new ItemStack(Material.BARREL),
                new String[]{"PSP", "P P", "PSP"}, 'P', planks,
                'S', new RecipeChoice.MaterialChoice(Tag.WOODEN_SLABS));

        // 3 planks (bowl shape) -> 4 bowls
        shaped("bowls_from_maple_planks", new ItemStack(Material.BOWL, 4),
                new String[]{"P P", " P "}, 'P', planks, null, null);

        plugin.getLogger().info("Registered maple crafting recipes.");
    }

    private void shaped(String name, ItemStack result, String[] shape,
                        char c1, RecipeChoice choice1, Character c2, RecipeChoice choice2) {
        ShapedRecipe recipe = new ShapedRecipe(key(name), result);
        recipe.shape(shape);
        recipe.setIngredient(c1, choice1);
        if (c2 != null && choice2 != null) {
            recipe.setIngredient(c2, choice2);
        }
        plugin.getServer().addRecipe(recipe);
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

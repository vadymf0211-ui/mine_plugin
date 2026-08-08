package com.example.maple;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.FurnaceRecipe;
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

        // ---------- wooden tools & weapons ----------
        // (vanilla wooden tools are identical for every wood family — this IS
        //  the correct vanilla result for maple planks)
        shaped("wooden_sword_from_maple_planks", new ItemStack(Material.WOODEN_SWORD),
                new String[]{"P", "P", "S"}, 'P', planks, 'S', stick);
        shaped("wooden_pickaxe_from_maple_planks", new ItemStack(Material.WOODEN_PICKAXE),
                new String[]{"PPP", " S ", " S "}, 'P', planks, 'S', stick);
        shaped("wooden_axe_from_maple_planks", new ItemStack(Material.WOODEN_AXE),
                new String[]{"PP", "PS", " S"}, 'P', planks, 'S', stick);
        shaped("wooden_shovel_from_maple_planks", new ItemStack(Material.WOODEN_SHOVEL),
                new String[]{"P", "S", "S"}, 'P', planks, 'S', stick);
        shaped("wooden_hoe_from_maple_planks", new ItemStack(Material.WOODEN_HOE),
                new String[]{"PP", " S", " S"}, 'P', planks, 'S', stick);

        // ---------- utility blocks & items ----------
        RecipeChoice iron = new RecipeChoice.MaterialChoice(Material.IRON_INGOT);

        // shield: 6 planks + 1 iron ingot
        shaped("shield_from_maple_planks", new ItemStack(Material.SHIELD),
                new String[]{"PIP", "PPP", " P "}, 'P', planks, 'I', iron);

        // bed: 3 wool + 3 planks
        shaped("bed_from_maple_planks", new ItemStack(Material.WHITE_BED),
                new String[]{"WWW", "PPP"}, 'P', planks,
                'W', new RecipeChoice.MaterialChoice(Material.WHITE_WOOL));

        // bookshelf: 6 planks + 3 books
        shaped("bookshelf_from_maple_planks", new ItemStack(Material.BOOKSHELF),
                new String[]{"PPP", "BBB", "PPP"}, 'P', planks,
                'B', new RecipeChoice.MaterialChoice(Material.BOOK));

        // jukebox: 8 planks + 1 diamond
        shaped("jukebox_from_maple_planks", new ItemStack(Material.JUKEBOX),
                new String[]{"PPP", "PDP", "PPP"}, 'P', planks,
                'D', new RecipeChoice.MaterialChoice(Material.DIAMOND));

        // note block: 8 planks + 1 redstone (a REAL vanilla note block)
        shaped("note_block_from_maple_planks", new ItemStack(Material.NOTE_BLOCK),
                new String[]{"PPP", "PRP", "PPP"}, 'P', planks,
                'R', new RecipeChoice.MaterialChoice(Material.REDSTONE));

        // piston
        ShapedRecipe piston = new ShapedRecipe(key("piston_from_maple_planks"), new ItemStack(Material.PISTON));
        piston.shape("PPP", "CIC", "CRC");
        piston.setIngredient('P', planks);
        piston.setIngredient('C', new RecipeChoice.MaterialChoice(Material.COBBLESTONE));
        piston.setIngredient('I', iron);
        piston.setIngredient('R', new RecipeChoice.MaterialChoice(Material.REDSTONE));
        plugin.getServer().addRecipe(piston);

        // loom: 2 string + 2 planks
        shaped("loom_from_maple_planks", new ItemStack(Material.LOOM),
                new String[]{"TT", "PP"}, 'P', planks,
                'T', new RecipeChoice.MaterialChoice(Material.STRING));

        // cartography table: 2 paper + 4 planks
        shaped("cartography_table_from_maple_planks", new ItemStack(Material.CARTOGRAPHY_TABLE),
                new String[]{"MM", "PP", "PP"}, 'P', planks,
                'M', new RecipeChoice.MaterialChoice(Material.PAPER));

        // fletching table: 2 flint + 4 planks
        shaped("fletching_table_from_maple_planks", new ItemStack(Material.FLETCHING_TABLE),
                new String[]{"FF", "PP", "PP"}, 'P', planks,
                'F', new RecipeChoice.MaterialChoice(Material.FLINT));

        // smithing table: 2 iron + 4 planks
        shaped("smithing_table_from_maple_planks", new ItemStack(Material.SMITHING_TABLE),
                new String[]{"II", "PP", "PP"}, 'P', planks, 'I', iron);

        // grindstone: 2 sticks + stone slab + 2 planks
        ShapedRecipe grindstone = new ShapedRecipe(key("grindstone_from_maple_planks"),
                new ItemStack(Material.GRINDSTONE));
        grindstone.shape("SQS", "P P");
        grindstone.setIngredient('S', stick);
        grindstone.setIngredient('Q', new RecipeChoice.MaterialChoice(Material.STONE_SLAB));
        grindstone.setIngredient('P', planks);
        plugin.getServer().addRecipe(grindstone);

        // tripwire hook: iron + stick + plank -> 2
        ShapedRecipe tripwireHook = new ShapedRecipe(key("tripwire_hook_from_maple_planks"),
                new ItemStack(Material.TRIPWIRE_HOOK, 2));
        tripwireHook.shape("I", "S", "P");
        tripwireHook.setIngredient('I', iron);
        tripwireHook.setIngredient('S', stick);
        tripwireHook.setIngredient('P', planks);
        plugin.getServer().addRecipe(tripwireHook);

        // daylight detector: glass + quartz + any wooden slabs
        ShapedRecipe daylight = new ShapedRecipe(key("daylight_detector_from_maple"),
                new ItemStack(Material.DAYLIGHT_DETECTOR));
        daylight.shape("GGG", "QQQ", "WWW");
        daylight.setIngredient('G', new RecipeChoice.MaterialChoice(Material.GLASS));
        daylight.setIngredient('Q', new RecipeChoice.MaterialChoice(Material.QUARTZ));
        daylight.setIngredient('W', new RecipeChoice.MaterialChoice(Tag.WOODEN_SLABS));
        plugin.getServer().addRecipe(daylight);

        // ---------- campfires ----------
        RecipeChoice coal = new RecipeChoice.MaterialChoice(Material.COAL, Material.CHARCOAL);
        RecipeChoice mapleLog = exact(MapleType.LOG);

        ShapedRecipe campfire = new ShapedRecipe(key("campfire_from_maple_log"), new ItemStack(Material.CAMPFIRE));
        campfire.shape(" S ", "SCS", "LLL");
        campfire.setIngredient('S', stick);
        campfire.setIngredient('C', coal);
        campfire.setIngredient('L', mapleLog);
        plugin.getServer().addRecipe(campfire);

        ShapedRecipe soulCampfire = new ShapedRecipe(key("soul_campfire_from_maple_log"),
                new ItemStack(Material.SOUL_CAMPFIRE));
        soulCampfire.shape(" S ", "SXS", "LLL");
        soulCampfire.setIngredient('S', stick);
        soulCampfire.setIngredient('X', new RecipeChoice.MaterialChoice(Material.SOUL_SAND, Material.SOUL_SOIL));
        soulCampfire.setIngredient('L', mapleLog);
        plugin.getServer().addRecipe(soulCampfire);

        // ---------- smelting: maple logs -> charcoal ----------
        for (MapleType logLike : new MapleType[]{MapleType.LOG, MapleType.STRIPPED_LOG,
                MapleType.WOOD, MapleType.STRIPPED_WOOD}) {
            plugin.getServer().addRecipe(new FurnaceRecipe(
                    key("charcoal_from_maple_" + logLike.id()),
                    new ItemStack(Material.CHARCOAL),
                    exact(logLike), 0.15f, 200));
        }

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

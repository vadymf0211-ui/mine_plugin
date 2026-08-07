package com.example.maple;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;

/**
 * BlockState definitions and detection for the custom Maple blocks.
 *
 * The "magic" state used for both blocks:
 *   up = true, down = true, north = false, south = false, east = false, west = false
 *
 * - never produced by vanilla worldgen (huge mushroom caps/stems),
 * - never produced by player placement (players always place all-true mushroom blocks),
 * so it is safe to claim it for our custom blocks.
 */
public final class MapleBlocks {

    private MapleBlocks() {
    }

    private static final BlockFace[] TRUE_FACES = {BlockFace.UP, BlockFace.DOWN};
    private static final BlockFace[] FALSE_FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    /** Creates the BlockData for the Maple Log (brown_mushroom_block[up,down]). */
    public static BlockData mapleLogData() {
        return createData(Material.BROWN_MUSHROOM_BLOCK);
    }

    /** Creates the BlockData for the Maple Leaves (red_mushroom_block[up,down]). */
    public static BlockData mapleLeavesData() {
        return createData(Material.RED_MUSHROOM_BLOCK);
    }

    private static BlockData createData(Material material) {
        MultipleFacing data = (MultipleFacing) material.createBlockData();
        for (BlockFace face : TRUE_FACES) {
            data.setFace(face, true);
        }
        for (BlockFace face : FALSE_FACES) {
            data.setFace(face, false);
        }
        return data;
    }

    /** True if the block is our Maple Log. */
    public static boolean isMapleLog(Block block) {
        return block.getType() == Material.BROWN_MUSHROOM_BLOCK && hasMapleState(block);
    }

    /** True if the block is our Maple Leaves. */
    public static boolean isMapleLeaves(Block block) {
        return block.getType() == Material.RED_MUSHROOM_BLOCK && hasMapleState(block);
    }

    private static boolean hasMapleState(Block block) {
        if (!(block.getBlockData() instanceof MultipleFacing facing)) {
            return false;
        }
        for (BlockFace face : TRUE_FACES) {
            if (!facing.hasFace(face)) {
                return false;
            }
        }
        for (BlockFace face : FALSE_FACES) {
            if (facing.hasFace(face)) {
                return false;
            }
        }
        return true;
    }
}

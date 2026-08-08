package com.example.maple;

import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.NoteBlock;

/**
 * BlockState definitions and detection for the custom Maple blocks.
 *
 * Why not oak_log / oak_leaves directly: the reserved-BlockState method only
 * works with blocks that have UNREACHABLE states. oak_log has just 3 states
 * (axis=x/y/z) — all used by the game; oak_leaves has 28 states — all reachable
 * in survival (e.g. distance=7,persistent=false appears on every chopped tree
 * right before the leaves decay). There is nothing to reserve, so re-texturing
 * them would corrupt real oak blocks everywhere. Hence:
 *
 *   Maple Log    -> note_block[instrument=didgeridoo, note=1]
 *                   (real wooden block: axe tool, wood sounds, hardness 0.8;
 *                    the state is unreachable because the plugin cancels
 *                    BlockPhysicsEvent for all note blocks — the instrument is
 *                    recalculated from the block below ONLY through physics
 *                    updates, so vanilla note blocks stay "harp" forever)
 *
 *   Maple Leaves -> red_mushroom_block[up=true,down=true,sides=false]
 *                   (non-interactable full block: placing against it works
 *                    natively; breaks fast like foliage, hardness 0.2; this
 *                    face combination never occurs in vanilla worldgen and a
 *                    player-placed mushroom block always has ALL faces=true)
 */
public final class MapleBlocks {

    private MapleBlocks() {
    }

    // ---------------- Maple Log (note_block) ----------------

    /** The reserved instrument. Unreachable while note block physics is locked. */
    public static final Instrument MAPLE_INSTRUMENT = Instrument.DIDGERIDOO;

    /** note=1 -> Maple Log. */
    public static final Note NOTE_LOG = new Note(1);

    /** Creates the BlockData for the Maple Log (note_block[instrument=didgeridoo,note=1]). */
    public static BlockData mapleLogData() {
        NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        data.setInstrument(MAPLE_INSTRUMENT);
        data.setNote(NOTE_LOG);
        data.setPowered(false);
        return data;
    }

    /** True if the block is our Maple Log. */
    public static boolean isMapleLog(Block block) {
        if (block.getType() != Material.NOTE_BLOCK) {
            return false;
        }
        if (!(block.getBlockData() instanceof NoteBlock noteBlock)) {
            return false;
        }
        return noteBlock.getInstrument() == MAPLE_INSTRUMENT && noteBlock.getNote().equals(NOTE_LOG);
    }

    // ---------------- Maple Leaves (red_mushroom_block) ----------------

    private static final BlockFace[] TRUE_FACES = {BlockFace.UP, BlockFace.DOWN};
    private static final BlockFace[] FALSE_FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    /** Creates the BlockData for the Maple Leaves (red_mushroom_block[up,down]). */
    public static BlockData mapleLeavesData() {
        MultipleFacing data = (MultipleFacing) Material.RED_MUSHROOM_BLOCK.createBlockData();
        for (BlockFace face : TRUE_FACES) {
            data.setFace(face, true);
        }
        for (BlockFace face : FALSE_FACES) {
            data.setFace(face, false);
        }
        return data;
    }

    /** True if the block is our Maple Leaves. */
    public static boolean isMapleLeaves(Block block) {
        if (block.getType() != Material.RED_MUSHROOM_BLOCK) {
            return false;
        }
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

    /** True if the block is any of our custom blocks. */
    public static boolean isMapleBlock(Block block) {
        return isMapleLog(block) || isMapleLeaves(block);
    }
}

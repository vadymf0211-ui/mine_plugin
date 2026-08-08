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
 * in survival. There is nothing to reserve, so re-texturing them would corrupt
 * real oak blocks everywhere. Hence:
 *
 *   Maple Log    -> note_block[instrument=didgeridoo, note=1]
 *                   (real wooden block: axe tool, wood sounds, hardness 0.8;
 *                    unreachable because the plugin locks note block physics —
 *                    a vanilla note block stays "harp" forever)
 *
 *   Maple Leaves -> chorus_plant[north/south/east/west/up/down = false]
 *                   (the only full-size vanilla block that renders with CUTOUT
 *                    transparency and has spare states — transparent leaf
 *                    textures work in the world, exactly like in the hand.
 *                    The all-false "floating" state cannot exist in nature: an
 *                    unsupported chorus plant instantly breaks, and connected
 *                    natural chorus always has at least one face=true. The
 *                    plugin cancels physics for this exact state only, so End
 *                    chorus mechanics stay fully vanilla.)
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

    // ---------------- Maple Leaves (chorus_plant) ----------------

    private static final BlockFace[] ALL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    /** Creates the BlockData for the Maple Leaves (chorus_plant with no connections). */
    public static BlockData mapleLeavesData() {
        MultipleFacing data = (MultipleFacing) Material.CHORUS_PLANT.createBlockData();
        for (BlockFace face : ALL_FACES) {
            data.setFace(face, false);
        }
        return data;
    }

    /** True if the block is our Maple Leaves. */
    public static boolean isMapleLeaves(Block block) {
        if (block.getType() != Material.CHORUS_PLANT) {
            return false;
        }
        if (!(block.getBlockData() instanceof MultipleFacing facing)) {
            return false;
        }
        for (BlockFace face : ALL_FACES) {
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

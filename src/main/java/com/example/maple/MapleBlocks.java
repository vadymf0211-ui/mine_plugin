package com.example.maple;

import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.NoteBlock;

/**
 * BlockState definitions and detection for the custom Maple blocks.
 *
 *   Maple Log    -> note_block[instrument=didgeridoo, note=1]
 *                   (real wooden block: axe tool, wood sounds, hardness 0.8;
 *                    unreachable because the plugin locks note block physics —
 *                    a vanilla note block stays "harp" forever)
 *
 *   Maple Leaves -> azalea_leaves[distance=7, persistent=true, waterlogged=false]
 *                   (REAL leaves: full hitbox, cutout transparency, native leaf
 *                    sounds, floats without support, correct breaking speed.
 *                    Why this state is safe to reserve:
 *                     * worldgen / grown azalea leaves are persistent=false —
 *                       including the transient distance=7 "about to decay"
 *                       state of chopped trees — so they never match;
 *                     * a PLAYER placing vanilla azalea leaves far from logs
 *                       would produce persistent=true+distance=7, but the
 *                       distance property has NO visual effect on vanilla
 *                       leaves, so the plugin silently rewrites such
 *                       placements to distance=6 — identical look, no clash;
 *                     * waterlogged=false is pinned by cancelling bucket use
 *                       on our blocks.)
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

    // ---------------- Maple Leaves (azalea_leaves) ----------------

    /** The reserved leaf distance. */
    public static final int LEAVES_DISTANCE = 7;

    /** Creates the BlockData for the Maple Leaves. */
    public static BlockData mapleLeavesData() {
        Leaves data = (Leaves) Material.AZALEA_LEAVES.createBlockData();
        data.setDistance(LEAVES_DISTANCE);
        data.setPersistent(true);
        data.setWaterlogged(false);
        return data;
    }

    /**
     * The "escape" state for VANILLA azalea leaves that a player legitimately
     * placed far from logs (persistent=true, distance=7): visually identical,
     * decay-immune, but out of our reserved state.
     */
    public static BlockData vanillaEscapeLeavesData() {
        Leaves data = (Leaves) Material.AZALEA_LEAVES.createBlockData();
        data.setDistance(LEAVES_DISTANCE - 1);
        data.setPersistent(true);
        data.setWaterlogged(false);
        return data;
    }

    /** True if the block is our Maple Leaves. */
    public static boolean isMapleLeaves(Block block) {
        if (block.getType() != Material.AZALEA_LEAVES) {
            return false;
        }
        if (!(block.getBlockData() instanceof Leaves leaves)) {
            return false;
        }
        return leaves.isPersistent() && !leaves.isWaterlogged() && leaves.getDistance() == LEAVES_DISTANCE;
    }

    /** True if the block is any of our custom blocks. */
    public static boolean isMapleBlock(Block block) {
        return isMapleLog(block) || isMapleLeaves(block);
    }
}

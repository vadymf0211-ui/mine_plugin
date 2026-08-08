package com.example.maple;

import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;

/**
 * BlockState definitions and detection for the custom Maple blocks.
 *
 * Base block: NOTE_BLOCK — a real wooden block:
 *  - mined correctly with an axe (proper tool),
 *  - vanilla wood sounds out of the box,
 *  - wood-like hardness (0.8),
 *  - a huge state space (instrument × note × powered = 1150 states) to reserve from.
 *
 * Reserved states:
 *   Maple Log    -> note_block[instrument=didgeridoo, note=1]
 *   Maple Leaves -> note_block[instrument=didgeridoo, note=2]
 *
 * These states are unreachable in survival because the plugin cancels
 * BlockPhysicsEvent for all note blocks (the instrument is recalculated from
 * the block below ONLY through physics updates, so vanilla note blocks stay
 * "harp" forever and can never become "didgeridoo").
 */
public final class MapleBlocks {

    private MapleBlocks() {
    }

    /** The reserved instrument. Unreachable while note block physics is locked. */
    public static final Instrument MAPLE_INSTRUMENT = Instrument.DIDGERIDOO;

    /** note=1 -> Maple Log. */
    public static final Note NOTE_LOG = new Note(1);
    /** note=2 -> Maple Leaves. */
    public static final Note NOTE_LEAVES = new Note(2);

    /** Creates the BlockData for the Maple Log (note_block[instrument=didgeridoo,note=1]). */
    public static BlockData mapleLogData() {
        return createData(NOTE_LOG);
    }

    /** Creates the BlockData for the Maple Leaves (note_block[instrument=didgeridoo,note=2]). */
    public static BlockData mapleLeavesData() {
        return createData(NOTE_LEAVES);
    }

    private static BlockData createData(Note note) {
        NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        data.setInstrument(MAPLE_INSTRUMENT);
        data.setNote(note);
        data.setPowered(false);
        return data;
    }

    /** True if the block is our Maple Log. */
    public static boolean isMapleLog(Block block) {
        return matches(block, NOTE_LOG);
    }

    /** True if the block is our Maple Leaves. */
    public static boolean isMapleLeaves(Block block) {
        return matches(block, NOTE_LEAVES);
    }

    /** True if the block is any of our custom blocks. */
    public static boolean isMapleBlock(Block block) {
        return isMapleLog(block) || isMapleLeaves(block);
    }

    private static boolean matches(Block block, Note note) {
        if (block.getType() != Material.NOTE_BLOCK) {
            return false;
        }
        if (!(block.getBlockData() instanceof NoteBlock noteBlock)) {
            return false;
        }
        return noteBlock.getInstrument() == MAPLE_INSTRUMENT && noteBlock.getNote().equals(note);
    }
}

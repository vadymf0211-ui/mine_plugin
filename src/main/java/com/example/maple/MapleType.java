package com.example.maple;

import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.NoteBlock;

/**
 * Registry of all Maple blocks.
 *
 * Full-cube wooden blocks live on reserved note_block states
 * (instrument=didgeridoo is unreachable in survival because the plugin locks
 * note block physics — a vanilla note block stays "harp" forever):
 *
 *   note=1  Клён (log)
 *   note=2  Обтёсанный клён (stripped log)
 *   note=3  Кленовая древесина (wood, bark on all sides)
 *   note=4  Обтёсанная кленовая древесина (stripped wood)
 *   note=5  Кленовые доски (planks)
 *
 * Leaves live on azalea_leaves[distance=7, persistent=true, waterlogged=false]
 * (real leaves: full hitbox, cutout transparency, native sounds; worldgen and
 * grown leaves are persistent=false so they never match, and vanilla azalea
 * placements that would land in the reserved state are nudged to distance=6 —
 * visually identical).
 *
 * Doors, trapdoors and saplings can NOT be built with the reserved-BlockState
 * method: every state of every vanilla door/trapdoor is reachable in survival,
 * and their shapes/collision are engine-side. Their textures are kept in the
 * resource pack for a future display-entity based implementation.
 */
public enum MapleType {

    LOG("log", "Клён", 1001, 1),
    STRIPPED_LOG("stripped_log", "Обтёсанный клён", 1003, 2),
    WOOD("wood", "Кленовая древесина", 1005, 3),
    STRIPPED_WOOD("stripped_wood", "Обтёсанная кленовая древесина", 1006, 4),
    PLANKS("planks", "Кленовые доски", 1004, 5),
    LEAVES("leaves", "Листва клёна", 1002, -1);

    /** The reserved note block instrument. */
    public static final Instrument MAPLE_INSTRUMENT = Instrument.DIDGERIDOO;

    /** The reserved leaf distance. */
    public static final int LEAVES_DISTANCE = 7;

    private final String id;
    private final String displayName;
    private final int customModelData;
    private final int note;

    MapleType(String id, String displayName, int customModelData, int note) {
        this.id = id;
        this.displayName = displayName;
        this.customModelData = customModelData;
        this.note = note;
    }

    /** Stable identifier stored in the item PDC and used by /maple give. */
    public String id() {
        return id;
    }

    /** Russian display name, shown exactly like a vanilla item name. */
    public String displayName() {
        return displayName;
    }

    /** Resource pack contract. */
    public int customModelData() {
        return customModelData;
    }

    /** True for the note_block-hosted (full cube, wooden) types. */
    public boolean isNoteBased() {
        return this != LEAVES;
    }

    /** The vanilla material hosting this block (and used for the item). */
    public Material host() {
        return isNoteBased() ? Material.NOTE_BLOCK : Material.AZALEA_LEAVES;
    }

    /** Canonical BlockData of the reserved state. */
    public BlockData createData() {
        if (isNoteBased()) {
            NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
            data.setInstrument(MAPLE_INSTRUMENT);
            data.setNote(new Note(note));
            data.setPowered(false);
            return data;
        }
        Leaves data = (Leaves) Material.AZALEA_LEAVES.createBlockData();
        data.setDistance(LEAVES_DISTANCE);
        data.setPersistent(true);
        data.setWaterlogged(false);
        return data;
    }

    /** True if the block currently holds this type's reserved state. */
    public boolean matches(Block block) {
        if (block.getType() != host()) {
            return false;
        }
        if (isNoteBased()) {
            return block.getBlockData() instanceof NoteBlock noteBlock
                    && noteBlock.getInstrument() == MAPLE_INSTRUMENT
                    && noteBlock.getNote().equals(new Note(note));
        }
        return block.getBlockData() instanceof Leaves leaves
                && leaves.isPersistent()
                && !leaves.isWaterlogged()
                && leaves.getDistance() == LEAVES_DISTANCE;
    }

    /** Resolves the maple type of a block, or null if it is not a maple block. */
    public static MapleType of(Block block) {
        Material material = block.getType();
        if (material == Material.NOTE_BLOCK) {
            if (block.getBlockData() instanceof NoteBlock noteBlock
                    && noteBlock.getInstrument() == MAPLE_INSTRUMENT) {
                int n = noteBlock.getNote().getId();
                for (MapleType type : values()) {
                    if (type.isNoteBased() && type.note == n) {
                        return type;
                    }
                }
            }
            return null;
        }
        if (material == Material.AZALEA_LEAVES && LEAVES.matches(block)) {
            return LEAVES;
        }
        return null;
    }

    /** Resolves a type by its stable id ("log", "planks", ...), or null. */
    public static MapleType byId(String id) {
        for (MapleType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
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
}

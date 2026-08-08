package com.example.maple;

import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.block.data.type.TrapDoor;

/**
 * Registry of all Maple blocks.
 *
 * Full-cube wooden blocks live on reserved note_block states
 * (instrument=didgeridoo is unreachable in survival because the plugin locks
 * note block physics — a vanilla note block stays "harp" forever):
 *
 *   note=1  Клён (log)          note=2  Обтёсанный клён (stripped log)
 *   note=3  Кленовая древесина  note=4  Обтёсанная кленовая древесина
 *   note=5  Кленовые доски
 *
 * Leaves live on azalea_leaves[distance=7, persistent=true, waterlogged=false]
 * (real leaves; worldgen/grown leaves are persistent=false so they never match,
 * and colliding vanilla placements are nudged to a visually identical state).
 *
 * Door and trapdoor live on the POWERED branch of the warped family:
 *
 *   Кленовая дверь -> warped_door[powered=true]  (32 orientation states)
 *   Кленовый люк   -> warped_trapdoor[powered=true, waterlogged=false]
 *
 * The powered property is changed ONLY by the server (redstone). The plugin
 * cancels physics for all warped doors/trapdoors, so a vanilla warped door can
 * never become powered — the branch is unreachable in survival. Orientation
 * properties (facing/hinge/half/open) stay fully vanilla: opening, closing,
 * sounds and collision all work natively, and the client's open/close
 * prediction preserves powered, so the maple texture never flickers on use.
 * Trade-off (documented): vanilla WARPED doors/trapdoors on the server do not
 * respond to redstone, and neither do the maple ones.
 */
public enum MapleType {

    LOG("log", "Клён", 1001, 1),
    STRIPPED_LOG("stripped_log", "Обтёсанный клён", 1003, 2),
    WOOD("wood", "Кленовая древесина", 1005, 3),
    STRIPPED_WOOD("stripped_wood", "Обтёсанная кленовая древесина", 1006, 4),
    PLANKS("planks", "Кленовые доски", 1004, 5),
    LEAVES("leaves", "Листва клёна", 1002, -1),
    DOOR("door", "Кленовая дверь", 1007, -1),
    TRAPDOOR("trapdoor", "Кленовый люк", 1008, -1);

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

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int customModelData() {
        return customModelData;
    }

    /** True for the note_block-hosted (full cube, wooden) types. */
    public boolean isNoteBased() {
        return note > 0;
    }

    /** The vanilla material hosting this block (and used for the item). */
    public Material host() {
        return switch (this) {
            case LEAVES -> Material.AZALEA_LEAVES;
            case DOOR -> Material.WARPED_DOOR;
            case TRAPDOOR -> Material.WARPED_TRAPDOOR;
            default -> Material.NOTE_BLOCK;
        };
    }

    /**
     * The canonical reserved BlockData.
     *
     * For orientable types (door, trapdoor) the orientation properties of
     * {@code current} are preserved and only the reserved discriminator is
     * forced; pass the block's current data. For fixed types {@code current}
     * may be null.
     */
    public BlockData reservedData(BlockData current) {
        switch (this) {
            case LEAVES -> {
                Leaves data = (Leaves) Material.AZALEA_LEAVES.createBlockData();
                data.setDistance(LEAVES_DISTANCE);
                data.setPersistent(true);
                data.setWaterlogged(false);
                return data;
            }
            case DOOR -> {
                Door data = current instanceof Door door
                        ? (Door) door.clone()
                        : (Door) Material.WARPED_DOOR.createBlockData();
                data.setPowered(true);
                return data;
            }
            case TRAPDOOR -> {
                TrapDoor data = current instanceof TrapDoor trapDoor
                        ? (TrapDoor) trapDoor.clone()
                        : (TrapDoor) Material.WARPED_TRAPDOOR.createBlockData();
                data.setPowered(true);
                data.setWaterlogged(false);
                return data;
            }
            default -> {
                NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
                data.setInstrument(MAPLE_INSTRUMENT);
                data.setNote(new Note(note));
                data.setPowered(false);
                return data;
            }
        }
    }

    /** True if the block currently holds this type's reserved state. */
    public boolean matches(Block block) {
        return block.getType() == host() && matchesData(block.getBlockData());
    }

    private boolean matchesData(BlockData data) {
        return switch (this) {
            case LEAVES -> data instanceof Leaves leaves
                    && leaves.isPersistent()
                    && !leaves.isWaterlogged()
                    && leaves.getDistance() == LEAVES_DISTANCE;
            case DOOR -> data instanceof Door door && door.isPowered();
            case TRAPDOOR -> data instanceof TrapDoor trapDoor
                    && trapDoor.isPowered()
                    && !trapDoor.isWaterlogged();
            default -> data instanceof NoteBlock noteBlock
                    && noteBlock.getInstrument() == MAPLE_INSTRUMENT
                    && noteBlock.getNote().getId() == note;
        };
    }

    /** Resolves the maple type of a block, or null if it is not a maple block. */
    public static MapleType of(Block block) {
        return switch (block.getType()) {
            case NOTE_BLOCK -> {
                if (block.getBlockData() instanceof NoteBlock noteBlock
                        && noteBlock.getInstrument() == MAPLE_INSTRUMENT) {
                    int n = noteBlock.getNote().getId();
                    for (MapleType type : values()) {
                        if (type.isNoteBased() && type.note == n) {
                            yield type;
                        }
                    }
                }
                yield null;
            }
            case AZALEA_LEAVES -> LEAVES.matches(block) ? LEAVES : null;
            case WARPED_DOOR -> DOOR.matches(block) ? DOOR : null;
            case WARPED_TRAPDOOR -> TRAPDOOR.matches(block) ? TRAPDOOR : null;
            default -> null;
        };
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

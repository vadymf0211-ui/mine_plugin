package com.example.maple;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Handles the full lifecycle of all Maple blocks (see {@link MapleType}).
 *
 *  PLACE   — items place through the vanilla path and are swapped to the
 *            reserved state right after (doors: both halves).
 *  STRIP   — right-clicking the maple log / wood with an axe converts it to the
 *            stripped variant, exactly like vanilla logs.
 *  STACKING FIX — server-side placement against note-based maple blocks (the
 *            client refuses to place against a note block without sneaking).
 *  DOORS   — vanilla open/close works natively (the open property is toggled by
 *            interaction and preserves powered). Because door physics is
 *            locked, the plugin syncs the second half's open state itself and
 *            handles half-pair breaking for BOTH maple and vanilla warped doors.
 *  BREAK   — cancels vanilla drops, drops the correct custom item, respects the
 *            tool (wooden blocks: always drop; leaves: shears only).
 *  STATE PROTECTION — physics cancelled for host blocks, every root physics
 *            event queues a next-tick re-assertion of adjacent maple blocks,
 *            canonical states are re-sent to nearby clients, pistons cannot
 *            move maple blocks, growing trees cannot overwrite them.
 */
public final class MapleListener implements Listener {

    private static final BlockFace[] NEIGHBOURS = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    /** Radius within which clients get a corrective block update. */
    private static final double RESYNC_RADIUS = 48.0;

    private final MaplePlugin plugin;
    private final MapleItems items;

    /** Locations already queued for re-assertion this tick (dedup). */
    private final Set<Location> pendingReassert = new HashSet<>();

    public MapleListener(MaplePlugin plugin, MapleItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    // ------------------------------------------------------------------
    // PLACE
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        MapleType type = items.getMapleType(event.getItemInHand());

        if (type == null) {
            // A VANILLA azalea leaves placement that happens to land exactly in
            // our reserved state gets nudged to a visually identical one.
            if (block.getType() == Material.AZALEA_LEAVES && MapleType.LEAVES.matches(block)) {
                block.setBlockData(MapleType.vanillaEscapeLeavesData(), false);
            }
            // Placing ANY block can silently retune adjacent maple blocks.
            protectNeighbours(block);
            return;
        }

        // Preserve vanilla-computed orientation (doors: facing/hinge; trapdoors:
        // facing/half) and force only the reserved discriminator.
        block.setBlockData(type.reservedData(block.getBlockData()), false);

        if (type == MapleType.DOOR) {
            // Doors are two blocks: mark the upper half as well.
            Block top = block.getRelative(BlockFace.UP);
            if (top.getType() == Material.WARPED_DOOR) {
                top.setBlockData(MapleType.DOOR.reservedData(top.getBlockData()), false);
            }
        }

        protectArea(block);
    }

    // ------------------------------------------------------------------
    // STATE PROTECTION
    // ------------------------------------------------------------------

    /**
     * Root physics handler.
     *
     * Note blocks and warped doors/trapdoors: cancelled for ALL of them — the
     * reserved discriminators (instrument / powered) change only through
     * physics, so locking it makes the reserved branches unreachable.
     * Azalea leaves: cancelled only in our exact state.
     * Any other root update: shield adjacent maple blocks (Paper fires the
     * event only for the root; neighbours are recalculated silently).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type == Material.NOTE_BLOCK || type == Material.WARPED_DOOR || type == Material.WARPED_TRAPDOOR) {
            event.setCancelled(true);
            return;
        }
        if (type == Material.AZALEA_LEAVES && MapleType.LEAVES.matches(block)) {
            event.setCancelled(true);
            return;
        }
        protectNeighbours(block);
    }

    /** Safety net: our leaves are persistent and must never decay. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (MapleType.LEAVES.matches(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Waterlogging our leaves/trapdoors would knock them out of the reserved state. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block clicked = event.getBlockClicked();
        if (clicked == null) {
            return;
        }
        Block relative = clicked.getRelative(event.getBlockFace());
        if (MapleType.LEAVES.matches(clicked) || MapleType.LEAVES.matches(relative)
                || MapleType.TRAPDOOR.matches(clicked) || MapleType.TRAPDOOR.matches(relative)) {
            event.setCancelled(true);
        }
    }

    /** Maple wooden blocks must never behave like an instrument (left click / redstone). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        MapleType type = MapleType.of(event.getBlock());
        if (type != null && type.isNoteBased()) {
            event.setCancelled(true);
        }
    }

    /** Pistons must not move maple blocks. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (MapleType.of(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (MapleType.of(block) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Growing trees must not overwrite maple blocks. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        event.getBlocks().removeIf(state -> MapleType.of(state.getBlock()) != null);
    }

    private void protectArea(Block center) {
        List<Saved> saved = new ArrayList<>();
        collect(center, saved);
        for (BlockFace face : NEIGHBOURS) {
            collect(center.getRelative(face), saved);
        }
        reassertNextTick(saved);
    }

    private void protectNeighbours(Block center) {
        List<Saved> saved = new ArrayList<>();
        for (BlockFace face : NEIGHBOURS) {
            collect(center.getRelative(face), saved);
        }
        reassertNextTick(saved);
    }

    private void collect(Block block, List<Saved> out) {
        MapleType type = MapleType.of(block);
        if (type != null) {
            out.add(new Saved(block, type));
        }
    }

    /**
     * Next tick: restore the server-side state if vanilla managed to change it,
     * and re-send the canonical state to nearby clients (client-side prediction
     * can display a stale state the server never broadcast). Dedup per tick.
     */
    private void reassertNextTick(List<Saved> saved) {
        if (saved.isEmpty()) {
            return;
        }
        List<Saved> fresh = new ArrayList<>(saved.size());
        for (Saved s : saved) {
            if (pendingReassert.add(s.block().getLocation())) {
                fresh.add(s);
            }
        }
        if (fresh.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Saved s : fresh) {
                Block b = s.block();
                pendingReassert.remove(b.getLocation());

                if (b.getType() != s.type().host()) {
                    continue; // the block was legitimately removed meanwhile
                }
                BlockData canonical = s.type().reservedData(b.getBlockData());
                if (!b.getBlockData().equals(canonical)) {
                    b.setBlockData(canonical, false);
                }
                Location loc = b.getLocation();
                for (Player p : b.getWorld().getNearbyPlayers(loc, RESYNC_RADIUS)) {
                    p.sendBlockChange(loc, canonical);
                }
            }
        });
    }

    /** Snapshot of a maple block's identity, taken before a disruptive event. */
    private record Saved(Block block, MapleType type) {
    }

    // ------------------------------------------------------------------
    // INTERACT — axe stripping, door half sync, note-block placement fix
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        MapleType clickedType = MapleType.of(clicked);
        if (clickedType == null) {
            return;
        }

        // Maple door: vanilla toggles the clicked half's open state (preserving
        // powered), but with door physics locked the second half no longer
        // syncs itself — do it manually on the next tick.
        if (clickedType == MapleType.DOOR) {
            syncDoorHalvesNextTick(clicked);
            return;
        }
        if (clickedType == MapleType.TRAPDOOR) {
            return; // single block, vanilla handles everything
        }
        if (!clickedType.isNoteBased()) {
            return;
        }

        // Never allow vanilla note tuning on the custom blocks.
        event.setUseInteractedBlock(Event.Result.DENY);

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();

        // Axe stripping — vanilla parity: log -> stripped log, wood -> stripped wood.
        if (item != null && Tag.ITEMS_AXES.isTagged(item.getType())) {
            MapleType stripped = switch (clickedType) {
                case LOG -> MapleType.STRIPPED_LOG;
                case WOOD -> MapleType.STRIPPED_WOOD;
                default -> null;
            };
            if (stripped != null) {
                event.setUseItemInHand(Event.Result.DENY);
                clicked.setBlockData(stripped.reservedData(null), false);
                clicked.getWorld().playSound(clicked.getLocation().add(0.5, 0.5, 0.5),
                        Sound.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 1.0f, 1.0f);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    item.damage(1, player);
                }
                player.swingHand(hand);
                protectArea(clicked);
            }
            return;
        }

        if (player.isSneaking()) {
            return; // while sneaking the client performs normal placement itself
        }
        if (item == null || !item.getType().isBlock() || item.getType().isAir()) {
            return;
        }

        // The client refuses to place a block against a note block without
        // sneaking, so replicate the placement server-side.
        Block target = clicked.getRelative(event.getBlockFace());
        if (!target.isReplaceable() || isOccupied(target)) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        placeManually(player, item, target, clicked, hand);
    }

    /**
     * One tick after a maple door is used, copy the clicked half's open state
     * to the other half and keep both halves in the reserved (powered) state.
     */
    private void syncDoorHalvesNextTick(Block half) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (half.getType() != Material.WARPED_DOOR
                    || !(half.getBlockData() instanceof Door door)) {
                return;
            }
            Block other = door.getHalf() == Bisected.Half.BOTTOM
                    ? half.getRelative(BlockFace.UP)
                    : half.getRelative(BlockFace.DOWN);
            if (other.getType() != Material.WARPED_DOOR
                    || !(other.getBlockData() instanceof Door otherDoor)) {
                return;
            }
            boolean changed = false;
            if (otherDoor.isOpen() != door.isOpen()) {
                otherDoor.setOpen(door.isOpen());
                changed = true;
            }
            if (!otherDoor.isPowered()) {
                otherDoor.setPowered(true);
                changed = true;
            }
            if (changed) {
                other.setBlockData(otherDoor, false);
            }
            // Make sure clients see both halves in the canonical state.
            for (Block b : new Block[]{half, other}) {
                Location loc = b.getLocation();
                for (Player p : b.getWorld().getNearbyPlayers(loc, RESYNC_RADIUS)) {
                    p.sendBlockChange(loc, b.getBlockData());
                }
            }
        });
    }

    private boolean isOccupied(Block target) {
        return !target.getWorld()
                .getNearbyEntities(BoundingBox.of(target), entity -> entity instanceof LivingEntity)
                .isEmpty();
    }

    /**
     * Replicates vanilla block placement against note-based maple blocks: sets
     * the block, fires a regular BlockPlaceEvent (protection-plugin friendly,
     * and our own PLACE handler converts maple items), consumes the item and
     * plays the sound. Orientable vanilla blocks get default orientation here —
     * the client sends no aim data for a click it treats as an interaction.
     */
    private void placeManually(Player player, ItemStack item, Block target, Block against, EquipmentSlot hand) {
        org.bukkit.block.BlockState replaced = target.getState();

        MapleType mapleType = items.getMapleType(item);
        BlockData data = mapleType != null && mapleType != MapleType.DOOR
                ? mapleType.reservedData(null)
                : item.getType().createBlockData();

        target.setBlockData(data, true);

        BlockPlaceEvent placeEvent = new BlockPlaceEvent(target, replaced, against, item, player, true, hand);
        plugin.getServer().getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
            replaced.update(true, false);
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        Location center = target.getLocation().add(0.5, 0.5, 0.5);
        target.getWorld().playSound(center, target.getBlockData().getSoundGroup().getPlaceSound(),
                SoundCategory.BLOCKS, 1.0f, 0.9f);
        player.swingHand(hand);

        protectArea(target);
    }

    // ------------------------------------------------------------------
    // BREAK
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        MapleType type = MapleType.of(block);

        if (type == null) {
            // With warped-door physics locked, vanilla half-pair breaking no
            // longer happens by itself — emulate it for vanilla warped doors.
            if (block.getType() == Material.WARPED_DOOR
                    && block.getBlockData() instanceof Door door) {
                Block other = door.getHalf() == Bisected.Half.BOTTOM
                        ? block.getRelative(BlockFace.UP)
                        : block.getRelative(BlockFace.DOWN);
                if (other.getType() == Material.WARPED_DOOR) {
                    other.setType(Material.AIR, false);
                }
                // Vanilla loot lives on the bottom half; breaking the top half
                // must still yield the door.
                if (door.getHalf() == Bisected.Half.TOP
                        && player.getGameMode() != GameMode.CREATIVE) {
                    event.setDropItems(false);
                    dropItem(block, new ItemStack(Material.WARPED_DOOR));
                }
            }
            protectNeighbours(block);
            return;
        }

        // Never let the vanilla loot table run.
        event.setDropItems(false);
        event.setExpToDrop(0);

        if (type == MapleType.DOOR && block.getBlockData() instanceof Door door) {
            // Remove the second half silently; drop exactly one maple door.
            Block other = door.getHalf() == Bisected.Half.BOTTOM
                    ? block.getRelative(BlockFace.UP)
                    : block.getRelative(BlockFace.DOWN);
            if (other.getType() == Material.WARPED_DOOR) {
                other.setType(Material.AIR, false);
            }
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            if (type == MapleType.LEAVES) {
                if (isShears(player.getInventory().getItemInMainHand())) {
                    dropItem(block, items.create(type, 1));
                }
            } else {
                // Wooden blocks always drop themselves, whatever the tool.
                dropItem(block, items.create(type, 1));
            }
        }
        protectNeighbours(block);
    }

    // ------------------------------------------------------------------
    // EXPLOSIONS
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> blockList) {
        List<Block> touched = new ArrayList<>(blockList);
        Iterator<Block> iterator = blockList.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            MapleType type = MapleType.of(block);
            if (type == null) {
                continue;
            }
            iterator.remove();

            if (type == MapleType.DOOR) {
                boolean bottom = block.getBlockData() instanceof Door door
                        && door.getHalf() == Bisected.Half.BOTTOM;
                Block other = bottom ? block.getRelative(BlockFace.UP) : block.getRelative(BlockFace.DOWN);
                block.setType(Material.AIR, false);
                if (other.getType() == Material.WARPED_DOOR) {
                    other.setType(Material.AIR, false);
                }
                if (bottom || MapleType.of(other) == null) {
                    dropItem(block, items.create(MapleType.DOOR, 1));
                }
                continue;
            }

            block.setType(Material.AIR, false);
            if (type != MapleType.LEAVES) {
                dropItem(block, items.create(type, 1));
            }
            // Leaves destroyed by an explosion drop nothing — vanilla behaviour.
        }
        for (Block block : touched) {
            protectNeighbours(block);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean isShears(ItemStack tool) {
        return tool != null && tool.getType() == Material.SHEARS;
    }

    private void dropItem(Block block, ItemStack stack) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(center, stack);
    }
}

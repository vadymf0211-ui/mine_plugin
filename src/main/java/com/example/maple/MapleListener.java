package com.example.maple;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
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
 *            reserved state right after. Vanilla azalea leaves that would land
 *            in our reserved state are nudged to a visually identical one.
 *  STRIP   — right-clicking the maple log / wood with an axe converts it to the
 *            stripped variant, exactly like vanilla logs.
 *  STACKING FIX — the vanilla client refuses to place a block against a note
 *            block without sneaking (right click = "tune the note"), so the
 *            plugin performs that placement server-side for every note-based
 *            maple block.
 *  BREAK   — cancels vanilla drops, drops the correct custom item, respects the
 *            tool (wooden blocks: always drop, axe is just faster; leaves:
 *            shears only). Sounds are native (note block = wood, azalea =
 *            leaves).
 *  STATE PROTECTION — Paper fires BlockPhysicsEvent only for the ROOT block of
 *            an update; neighbours are recalculated silently. So: physics is
 *            cancelled for our blocks, EVERY root event queues a next-tick
 *            re-assertion of adjacent maple blocks, place/break/explosions
 *            protect the area explicitly, canonical states are re-sent to
 *            nearby clients, pistons cannot move maple blocks, and growing
 *            trees cannot overwrite them. Re-assertions dedup per tick.
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
            // our reserved state (persistent, far from logs -> distance=7) gets
            // nudged to distance=6: visually identical, decay-immune, no clash.
            if (block.getType() == Material.AZALEA_LEAVES && MapleType.LEAVES.matches(block)) {
                block.setBlockData(MapleType.vanillaEscapeLeavesData(), false);
            }
            // Placing ANY block can silently retune adjacent maple blocks.
            protectNeighbours(block);
            return;
        }

        block.setBlockData(type.createData(), false);
        protectArea(block);
    }

    // ------------------------------------------------------------------
    // STATE PROTECTION
    // ------------------------------------------------------------------

    /**
     * Root physics handler.
     *
     * For our own blocks the event is cancelled outright. For every OTHER root
     * update we schedule a re-assertion of adjacent maple blocks: Paper fires
     * this event only for the root of an update chain, and the neighbours
     * (whose instrument/distance the engine recalculates in the same pass) get
     * no event of their own.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type == Material.NOTE_BLOCK) {
            event.setCancelled(true);
            return;
        }
        if (type == Material.AZALEA_LEAVES && MapleType.LEAVES.matches(block)) {
            event.setCancelled(true);
            return;
        }
        // Root update of a foreign block: shield its maple neighbours.
        protectNeighbours(block);
    }

    /** Safety net: our leaves are persistent and must never decay. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (MapleType.LEAVES.matches(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    /** Waterlogging our leaves would knock them out of the reserved state. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block clicked = event.getBlockClicked();
        if (clicked == null) {
            return;
        }
        if (MapleType.LEAVES.matches(clicked)
                || MapleType.LEAVES.matches(clicked.getRelative(event.getBlockFace()))) {
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

    /**
     * Pistons must not move maple blocks: the vanilla move recomputes the
     * landing state with no event we could veto, scrambling the reserved state.
     */
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

    /** Growing trees must not overwrite maple blocks (vanilla trees can replace leaves). */
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
        // Cheap Material pre-check happens inside MapleType.of().
        MapleType type = MapleType.of(block);
        if (type != null) {
            out.add(new Saved(block, type));
        }
    }

    /**
     * Next tick: restore the server-side state if vanilla managed to change it,
     * and re-send the canonical state to nearby clients — their local
     * prediction may display a stale state even though the server never
     * broadcast a change. Deduplicated per tick via {@link #pendingReassert}.
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
                BlockData canonical = s.type().createData();
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
    // INTERACT — axe stripping + server-side placement against note blocks
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
        if (clickedType == null || !clickedType.isNoteBased()) {
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
                clicked.setBlockData(stripped.createData(), false);
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

    private boolean isOccupied(Block target) {
        return !target.getWorld()
                .getNearbyEntities(BoundingBox.of(target), entity -> entity instanceof LivingEntity)
                .isEmpty();
    }

    /**
     * Replicates vanilla block placement: sets the block, fires a regular
     * BlockPlaceEvent (so protection plugins can veto it and our own PLACE
     * handler converts maple items), consumes the item and plays the sound.
     *
     * Note: orientable vanilla blocks (stairs, logs, furnaces...) placed through
     * this path get their default orientation — the client does not send aim
     * data for a click it considers an interaction. Maple blocks themselves are
     * orientation-free, so they always place perfectly.
     */
    private void placeManually(Player player, ItemStack item, Block target, Block against, EquipmentSlot hand) {
        org.bukkit.block.BlockState replaced = target.getState();

        MapleType mapleType = items.getMapleType(item);
        BlockData data = mapleType != null ? mapleType.createData() : item.getType().createBlockData();

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
            // Breaking ANY block can silently retune adjacent maple blocks.
            protectNeighbours(block);
            return;
        }

        // Never let the vanilla loot table run (note block / azalea leaves / sticks).
        event.setDropItems(false);
        event.setExpToDrop(0);

        if (player.getGameMode() != GameMode.CREATIVE) {
            if (type.isNoteBased()) {
                // Wooden blocks always drop themselves, whatever the tool — vanilla behaviour.
                dropItem(block, items.create(type, 1));
            } else if (isShears(player.getInventory().getItemInMainHand())) {
                // Leaves drop themselves ONLY when cut with shears — vanilla behaviour.
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
            block.setType(Material.AIR, false);
            if (type.isNoteBased()) {
                dropItem(block, items.create(type, 1));
            }
            // Leaves destroyed by an explosion drop nothing — vanilla behaviour.
        }
        // Protect maple blocks bordering ANY destroyed block.
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

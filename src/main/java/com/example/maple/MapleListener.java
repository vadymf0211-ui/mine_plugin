package com.example.maple;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
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
 * Handles the full lifecycle of the Maple blocks.
 *
 *  Maple Log    = note_block[instrument=didgeridoo, note=1]
 *  Maple Leaves = azalea_leaves[distance=7, persistent=true, waterlogged=false]
 *
 * STATE PROTECTION — the critical part. Minecraft recalculates a note block's
 * INSTRUMENT (from the blocks below/above) and a leaf's DISTANCE on every
 * neighbour update, and Paper only guarantees a BlockPhysicsEvent for the ROOT
 * block of an update — the neighbours are often recalculated silently. So:
 *
 *   1) physics is cancelled for all note blocks and for azalea leaves in our
 *      exact state — this covers updates addressed to the block itself;
 *   2) EVERY root physics event additionally schedules a next-tick
 *      re-assertion of any maple blocks adjacent to the root — this covers the
 *      silent neighbour recalculations (placing/breaking blocks next to a
 *      maple log, flowing water, falling sand, pistons firing, etc.);
 *   3) place/break/explosion handlers also protect the surrounding area
 *      explicitly (covers operations that skip physics entirely);
 *   4) re-asserted states are re-SENT to nearby clients, because client-side
 *      prediction can display a stale state that the server never broadcast;
 *   5) pistons are prevented from moving maple blocks (a move would scramble
 *      the reserved state on landing);
 *   6) growing trees are prevented from overwriting maple blocks.
 *
 * A per-tick dedup set keeps the re-assertion queue from ballooning during
 * update storms (water flows, big explosions).
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
        String type = items.getMapleType(event.getItemInHand());

        if (type == null) {
            // A VANILLA azalea leaves placement that happens to land exactly in
            // our reserved state (persistent, far from logs -> distance=7) gets
            // nudged to distance=6: visually identical, decay-immune, no clash.
            if (block.getType() == Material.AZALEA_LEAVES && MapleBlocks.isMapleLeaves(block)) {
                block.setBlockData(MapleBlocks.vanillaEscapeLeavesData(), false);
            }
            // Placing ANY block can silently retune/reconnect adjacent maple
            // blocks — protect them.
            protectNeighbours(block);
            return;
        }

        switch (type) {
            case MapleItems.TYPE_LOG -> block.setBlockData(MapleBlocks.mapleLogData(), false);
            case MapleItems.TYPE_LEAVES -> block.setBlockData(MapleBlocks.mapleLeavesData(), false);
            default -> {
                return;
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
     * For our own blocks the event is cancelled outright. For every OTHER root
     * update we schedule a re-assertion of adjacent maple blocks: Paper fires
     * this event only for the root of an update chain, and the neighbours
     * (whose instrument/distance the engine recalculates in the same pass) get
     * no event of their own — that was exactly the "log turns into a note
     * block when you build next to it" bug.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type == Material.NOTE_BLOCK) {
            event.setCancelled(true);
            return;
        }
        if (type == Material.AZALEA_LEAVES && MapleBlocks.isMapleLeaves(block)) {
            event.setCancelled(true);
            return;
        }
        // Root update of a foreign block: shield its maple neighbours.
        protectNeighbours(block);
    }

    /** Safety net: our leaves are persistent and must never decay. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (MapleBlocks.isMapleLeaves(event.getBlock())) {
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
        if (MapleBlocks.isMapleLeaves(clicked)
                || MapleBlocks.isMapleLeaves(clicked.getRelative(event.getBlockFace()))) {
            event.setCancelled(true);
        }
    }

    /** The Maple Log must never behave like an instrument (left click / redstone). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        if (MapleBlocks.isMapleLog(event.getBlock())) {
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
            if (MapleBlocks.isMapleBlock(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (MapleBlocks.isMapleBlock(block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Growing trees must not overwrite maple blocks (vanilla trees can replace leaves). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        event.getBlocks().removeIf(state -> MapleBlocks.isMapleBlock(state.getBlock()));
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
        // Cheap Material pre-check happens inside the isMaple* methods.
        if (MapleBlocks.isMapleLog(block)) {
            out.add(new Saved(block, MapleItems.TYPE_LOG));
        } else if (MapleBlocks.isMapleLeaves(block)) {
            out.add(new Saved(block, MapleItems.TYPE_LEAVES));
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

                Material host = s.type().equals(MapleItems.TYPE_LOG) ? Material.NOTE_BLOCK : Material.AZALEA_LEAVES;
                if (b.getType() != host) {
                    continue; // the block was legitimately removed meanwhile
                }
                BlockData canonical = s.type().equals(MapleItems.TYPE_LOG)
                        ? MapleBlocks.mapleLogData()
                        : MapleBlocks.mapleLeavesData();
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
    private record Saved(Block block, String type) {
    }

    // ------------------------------------------------------------------
    // STACKING FIX — server-side placement against the Maple Log
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || !MapleBlocks.isMapleLog(clicked)) {
            return;
        }

        // Never allow vanilla note tuning on the custom block.
        event.setUseInteractedBlock(Event.Result.DENY);

        Player player = event.getPlayer();
        if (player.isSneaking()) {
            return; // while sneaking the client performs normal placement itself
        }

        ItemStack item = event.getItem();
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
        placeManually(player, item, target, clicked,
                event.getHand() == null ? EquipmentSlot.HAND : event.getHand());
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

        String mapleType = items.getMapleType(item);
        BlockData data;
        if (MapleItems.TYPE_LOG.equals(mapleType)) {
            data = MapleBlocks.mapleLogData();
        } else if (MapleItems.TYPE_LEAVES.equals(mapleType)) {
            data = MapleBlocks.mapleLeavesData();
        } else {
            data = item.getType().createBlockData();
        }

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

        if (MapleBlocks.isMapleLog(block)) {
            // Never let the vanilla note block loot table run.
            event.setDropItems(false);
            event.setExpToDrop(0);
            if (player.getGameMode() != GameMode.CREATIVE) {
                // Logs always drop themselves, whatever the tool — vanilla behaviour.
                dropItem(block, items.createMapleLog(1));
            }
            protectNeighbours(block);
            return;
        }

        if (MapleBlocks.isMapleLeaves(block)) {
            // Never let the vanilla leaves loot table run (it would drop plain
            // azalea leaves with shears, or sticks without).
            event.setDropItems(false);
            event.setExpToDrop(0);

            if (player.getGameMode() != GameMode.CREATIVE && isShears(player.getInventory().getItemInMainHand())) {
                // Leaves drop themselves ONLY when cut with shears — vanilla behaviour.
                dropItem(block, items.createMapleLeaves(1));
            }
            protectNeighbours(block);
            return;
        }

        // Breaking ANY block can silently retune/reconnect adjacent maple
        // blocks — protect them.
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
            if (MapleBlocks.isMapleLog(block)) {
                iterator.remove();
                block.setType(Material.AIR, false);
                dropItem(block, items.createMapleLog(1));
            } else if (MapleBlocks.isMapleLeaves(block)) {
                // Vanilla leaves destroyed by an explosion drop nothing.
                iterator.remove();
                block.setType(Material.AIR, false);
            }
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

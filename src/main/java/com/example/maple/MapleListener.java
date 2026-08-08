package com.example.maple;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Handles the full lifecycle of the Maple blocks.
 *
 *  PLACE   — Maple Log places through the vanilla path (note blocks place fine)
 *            and is swapped to the reserved state afterwards. Maple Leaves is a
 *            chorus plant, which vanilla refuses to place on ordinary ground
 *            (chorus needs end stone), so the plugin ALWAYS places the leaves
 *            item manually server-side.
 *  STACKING FIX — the vanilla client refuses to place a block against a note
 *            block without sneaking (right click = "tune the note"), so the
 *            plugin also performs THAT placement server-side.
 *  BREAK   — cancels vanilla drops (note block / chorus fruit), drops the
 *            correct custom item, plays wood / leaves sounds, respects the tool.
 *
 *  STATE PROTECTION — Minecraft recalculates a note block's INSTRUMENT (from the
 *            block below) and a chorus plant's CONNECTIONS + SUPPORT on every
 *            neighbour update. Protection is layered:
 *              1) BlockPhysicsEvent is cancelled for all note blocks (keeps the
 *                 reserved instrument unreachable) and for chorus plants that
 *                 are in OUR exact state (End chorus stays fully vanilla);
 *              2) around every place/break/explosion, maple neighbours are
 *                 remembered and their exact state re-asserted next tick —
 *                 Paper only guarantees the physics event for the ROOT block of
 *                 an update, so adjacent recalculations can slip through;
 *              3) the canonical state is re-SENT to nearby players, because the
 *                 client locally predicts connection shapes (that is what made
 *                 leaves "stick" as the wrong texture even when the server
 *                 state was correct — the server saw no change to broadcast).
 *  EXPLODE — keeps drops consistent when blocks are destroyed by explosions.
 */
public final class MapleListener implements Listener {

    private static final BlockFace[] NEIGHBOURS = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    /** Radius within which clients get a corrective block update. */
    private static final double RESYNC_RADIUS = 48.0;

    private final MaplePlugin plugin;
    private final MapleItems items;

    public MapleListener(MaplePlugin plugin, MapleItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    // ------------------------------------------------------------------
    // PLACE (vanilla path — maple log & any regular block)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        String type = items.getMapleType(event.getItemInHand());
        if (type == null) {
            return;
        }

        Block block = event.getBlockPlaced();

        switch (type) {
            case MapleItems.TYPE_LOG -> block.setBlockData(MapleBlocks.mapleLogData(), false);
            case MapleItems.TYPE_LEAVES -> {
                // Normally leaves are placed manually via onInteract, but this
                // also covers synthetic BlockPlaceEvents from other plugins.
                block.setBlockData(MapleBlocks.mapleLeavesData(), false);
            }
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
     * Note blocks: cancel for ALL of them — the instrument must never change,
     * otherwise the reserved "didgeridoo" state becomes reachable in survival.
     * Chorus plants: cancel ONLY for our exact all-false state, so natural End
     * chorus keeps its vanilla connection / breaking behaviour.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (type == Material.NOTE_BLOCK) {
            event.setCancelled(true);
        } else if (type == Material.CHORUS_PLANT && MapleBlocks.isMapleLeaves(block)) {
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
        if (MapleBlocks.isMapleLog(block)) {
            out.add(new Saved(block, MapleItems.TYPE_LOG));
        } else if (MapleBlocks.isMapleLeaves(block)) {
            out.add(new Saved(block, MapleItems.TYPE_LEAVES));
        }
    }

    /**
     * Next tick: restore the server-side state if vanilla managed to change it,
     * and ALWAYS re-send the canonical state to nearby clients — their local
     * prediction may display a stale connected/retuned shape even though the
     * server state never changed (in which case no packet was broadcast).
     */
    private void reassertNextTick(List<Saved> saved) {
        if (saved.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Saved s : saved) {
                Block b = s.block();
                Material host = s.type().equals(MapleItems.TYPE_LOG) ? Material.NOTE_BLOCK : Material.CHORUS_PLANT;
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
    // MANUAL PLACEMENT (leaves everywhere + anything against the maple log)
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

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        EquipmentSlot hand = event.getHand() == null ? EquipmentSlot.HAND : event.getHand();

        boolean clickedMapleLog = MapleBlocks.isMapleLog(clicked);
        boolean holdingLeaves = item != null && MapleItems.TYPE_LEAVES.equals(items.getMapleType(item));

        if (clickedMapleLog) {
            // Never allow vanilla note tuning on the custom block.
            event.setUseInteractedBlock(Event.Result.DENY);
        }

        if (holdingLeaves) {
            // The leaves item is a chorus plant: vanilla refuses to place it on
            // ordinary ground (chorus requires end stone), so placement is
            // ALWAYS done manually server-side.
            if (!clickedMapleLog && clicked.getType().isInteractable() && !player.isSneaking()) {
                return; // let chests / doors / crafting tables open normally
            }
            event.setUseItemInHand(Event.Result.DENY);
            Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(event.getBlockFace());
            if (!target.isReplaceable() || isOccupied(target)) {
                return;
            }
            placeManually(player, item, target, clicked, hand);
            return;
        }

        if (!clickedMapleLog || player.isSneaking()) {
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
        if (MapleItems.TYPE_LEAVES.equals(mapleType)) {
            // Foliage sound instead of the chorus plant's default.
            target.getWorld().playSound(center, Sound.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1.0f, 1.0f);
        } else {
            target.getWorld().playSound(center, target.getBlockData().getSoundGroup().getPlaceSound(),
                    SoundCategory.BLOCKS, 1.0f, 0.8f);
        }
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
            // Never let the chorus plant loot table run (it can drop chorus fruit).
            event.setDropItems(false);
            event.setExpToDrop(0);

            playSound(block.getLocation(), Sound.BLOCK_GRASS_BREAK);

            if (player.getGameMode() != GameMode.CREATIVE && isShears(player.getInventory().getItemInMainHand())) {
                // Leaves drop themselves ONLY when cut with shears — vanilla behaviour.
                dropItem(block, items.createMapleLeaves(1));
            }
            protectNeighbours(block);
        }
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
        List<Block> touched = new ArrayList<>();
        Iterator<Block> iterator = blockList.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (MapleBlocks.isMapleLog(block)) {
                iterator.remove();
                touched.add(block);
                block.setType(Material.AIR, false);
                dropItem(block, items.createMapleLog(1));
            } else if (MapleBlocks.isMapleLeaves(block)) {
                // Vanilla leaves destroyed by an explosion drop nothing.
                iterator.remove();
                touched.add(block);
                block.setType(Material.AIR, false);
            }
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

    private void playSound(Location location, Sound sound) {
        location.getWorld().playSound(location.add(0.5, 0.5, 0.5), sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }
}

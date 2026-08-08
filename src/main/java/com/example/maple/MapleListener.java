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
 *  PLACE   — swaps the freshly placed block to the reserved "maple" BlockState.
 *  STACKING FIX — the Maple Log is a note block, and the vanilla client refuses
 *            to place a block against a note block without sneaking (it thinks a
 *            plain right click means "tune the note"). The plugin performs that
 *            placement server-side so maple logs stack like ordinary logs. The
 *            Maple Leaves is a mushroom block (not interactable) — no fix needed.
 *  BREAK   — cancels vanilla drops, drops the correct custom item, plays the
 *            vanilla wood / leaves sounds, respects the tool.
 *
 *  STATE PROTECTION — the tricky part. Minecraft recalculates:
 *            * a note block's INSTRUMENT from the block below it, and
 *            * a mushroom block's connected FACES from its neighbours,
 *            on every neighbour update. That would knock our blocks out of their
 *            reserved state (log -> plain note block, leaves -> mushroom texture).
 *            BlockPhysicsEvent alone is NOT enough: Paper only guarantees the
 *            event for the "root" block of an update, so ADJACENT blocks that get
 *            recalculated slip through (this is why breaking the middle of a
 *            stack reverted the neighbours). We therefore combine:
 *              1) cancel BlockPhysicsEvent for note & mushroom blocks (cheap,
 *                 catches the root/direct updates), and
 *              2) before every place/break/explosion, remember which neighbours
 *                 were maple blocks and re-assert their exact state on the next
 *                 tick (catches the adjacent updates the event misses).
 *            For servers that want a zero-flicker guarantee, enabling
 *            `block-updates.disable-noteblock-updates: true` in paper-global.yml
 *            is recommended on top of this (see README).
 *  EXPLODE — keeps drops consistent when blocks are destroyed by explosions.
 */
public final class MapleListener implements Listener {

    private static final BlockFace[] NEIGHBOURS = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final MaplePlugin plugin;
    private final MapleItems items;

    public MapleListener(MaplePlugin plugin, MapleItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    // ------------------------------------------------------------------
    // PLACE
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        String type = items.getMapleType(event.getItemInHand());
        if (type == null) {
            return;
        }

        Block block = event.getBlockPlaced();

        switch (type) {
            case MapleItems.TYPE_LOG -> {
                block.setBlockData(MapleBlocks.mapleLogData(), false);
            }
            case MapleItems.TYPE_LEAVES -> {
                block.setBlockData(MapleBlocks.mapleLeavesData(), false);
                playSound(block.getLocation(), Sound.BLOCK_GRASS_PLACE);
            }
            default -> {
                return;
            }
        }

        // Placing next to another maple block makes both recalculate; protect the
        // placed block AND its neighbours so nothing connects / retunes.
        protectArea(block);
    }

    // ------------------------------------------------------------------
    // STATE PROTECTION
    // ------------------------------------------------------------------

    /**
     * Cancels physics updates for note & mushroom blocks — the two host types.
     * Cheap and catches the "root" updates. Adjacent updates are handled by
     * {@link #protectArea(Block)} / {@link #protectNeighbours(Block)}.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Material type = event.getBlock().getType();
        if (type == Material.NOTE_BLOCK || type == Material.RED_MUSHROOM_BLOCK) {
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
     * Re-asserts a maple block's canonical state on the NEXT tick.
     *
     * We record the type NOW (while the block is still ours) and re-apply it
     * after vanilla has finished its neighbour recalculations. If by then the
     * host block is still present but no longer matches our reserved state
     * (i.e. it was retuned / reconnected), we set it back.
     */
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

    private void reassertNextTick(List<Saved> saved) {
        if (saved.isEmpty()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Saved s : saved) {
                Block b = s.block();
                if (s.type().equals(MapleItems.TYPE_LOG)) {
                    // Still a note block but retuned away from our state -> restore.
                    if (b.getType() == Material.NOTE_BLOCK && !MapleBlocks.isMapleLog(b)) {
                        b.setBlockData(MapleBlocks.mapleLogData(), false);
                    }
                } else {
                    // Still a mushroom block but reconnected away from our state -> restore.
                    if (b.getType() == Material.RED_MUSHROOM_BLOCK && !MapleBlocks.isMapleLeaves(b)) {
                        b.setBlockData(MapleBlocks.mapleLeavesData(), false);
                    }
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
            return; // While sneaking the client performs normal placement itself.
        }

        ItemStack item = event.getItem();
        if (item == null || !item.getType().isBlock() || item.getType().isAir()) {
            return;
        }

        Block target = clicked.getRelative(event.getBlockFace());
        if (!target.isReplaceable()) {
            return;
        }
        boolean occupied = !target.getWorld()
                .getNearbyEntities(BoundingBox.of(target), entity -> entity instanceof LivingEntity)
                .isEmpty();
        if (occupied) {
            return;
        }

        event.setUseItemInHand(Event.Result.DENY);
        placeManually(player, item, target, clicked,
                event.getHand() == null ? EquipmentSlot.HAND : event.getHand());
    }

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
                SoundCategory.BLOCKS, 1.0f, 0.8f);
        player.swingHand(hand);

        // A maple item was just placed manually: apply foliage sound + protect.
        if (MapleItems.TYPE_LEAVES.equals(mapleType)) {
            playSound(target.getLocation(), Sound.BLOCK_GRASS_PLACE);
        }
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
            event.setDropItems(false);
            event.setExpToDrop(0);
            if (player.getGameMode() != GameMode.CREATIVE) {
                dropItem(block, items.createMapleLog(1));
            }
            // Neighbours (e.g. logs above/below) would recalc — protect them.
            protectNeighbours(block);
            return;
        }

        if (MapleBlocks.isMapleLeaves(block)) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            playSound(block.getLocation(), Sound.BLOCK_GRASS_BREAK);
            if (player.getGameMode() != GameMode.CREATIVE && isShears(player.getInventory().getItemInMainHand())) {
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
                iterator.remove();
                touched.add(block);
                block.setType(Material.AIR, false);
            }
        }
        // Protect any maple blocks bordering the blast that survived.
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

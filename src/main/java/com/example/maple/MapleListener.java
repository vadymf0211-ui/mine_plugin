package com.example.maple;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
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
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;

import java.util.Iterator;
import java.util.List;

/**
 * Handles the full lifecycle of the Maple blocks:
 *
 *  PLACE   — swaps the freshly placed block to the reserved "maple" BlockState
 *            so the resource pack re-textures it.
 *  STACKING FIX — the vanilla client refuses to place a block against a note
 *            block without sneaking (a note block is "interactable": a plain
 *            right click means "tune the note"). For the Maple Log the plugin
 *            therefore performs the placement server-side, so building against
 *            it works exactly like against a normal log. Maple Leaves are a
 *            mushroom block (not interactable) — no fix needed there.
 *  BREAK   — cancels vanilla drops, drops the correct custom item, plays the
 *            vanilla wood / leaves sounds and respects the correct tool:
 *              * Maple Log    — always drops (an axe simply mines faster), like vanilla logs;
 *              * Maple Leaves — drops ONLY when mined with shears, like vanilla leaves.
 *  PHYSICS — cancels BlockPhysicsEvent for ALL note blocks. This is the key
 *            trick of the note_block method: the instrument property is only
 *            recalculated from the block below during a physics update, so with
 *            physics locked a vanilla note block stays "harp" forever and the
 *            reserved "didgeridoo" state is unreachable in survival.
 *  EXPLODE — keeps drops consistent when blocks are destroyed by explosions.
 */
public final class MapleListener implements Listener {

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
                // note_block[instrument=didgeridoo,note=1,powered=false]
                block.setBlockData(MapleBlocks.mapleLogData(), false);
                // Vanilla already plays the wood place sound for note blocks — perfect for a log.
            }
            case MapleItems.TYPE_LEAVES -> {
                // red_mushroom_block[up=true,down=true,north/south/east/west=false]
                block.setBlockData(MapleBlocks.mapleLeavesData(), false);
                // Layer the vanilla leaves (grass) place sound on top so it sounds like foliage.
                playSound(block.getLocation(), Sound.BLOCK_GRASS_PLACE);
            }
            default -> {
                // Unknown tag value — leave the vanilla block untouched.
            }
        }
    }

    // ------------------------------------------------------------------
    // PHYSICS LOCK — the core of the note_block method
    // ------------------------------------------------------------------

    /**
     * Cancels physics updates for ALL note blocks.
     *
     * Without this, placing e.g. a pumpkin under a note block would switch its
     * instrument to "didgeridoo" and a right-click would produce our reserved
     * state in pure survival. With physics locked, the instrument property can
     * never change, so the reserved state stays exclusive.
     *
     * Known trade-off (documented in README): vanilla note blocks keep the
     * default "harp" instrument regardless of the block below them.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (event.getBlock().getType() == Material.NOTE_BLOCK) {
            event.setCancelled(true);
        }
    }

    /**
     * The Maple Log must never behave like an instrument (left click / redstone).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNotePlay(NotePlayEvent event) {
        if (MapleBlocks.isMapleLog(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // STACKING FIX — server-side placement against the Maple Log
    // ------------------------------------------------------------------

    /**
     * A right click on our Maple Log:
     *  1) must never tune the note (that would change the BlockState),
     *  2) must place the held block, because the CLIENT refuses to do it
     *     itself: it thinks it is clicking a note block, and note blocks only
     *     accept placement while sneaking. We replicate the vanilla placement
     *     server-side so maple logs stack like ordinary logs.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null || !MapleBlocks.isMapleLog(clicked)) {
            return;
        }

        // 1) Never allow vanilla note tuning on the custom block.
        event.setUseInteractedBlock(Event.Result.DENY);

        Player player = event.getPlayer();
        if (player.isSneaking()) {
            // While sneaking the client performs normal placement itself.
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !item.getType().isBlock() || item.getType().isAir()) {
            return;
        }

        // 2) Manual server-side placement.
        Block target = clicked.getRelative(event.getBlockFace());
        if (!target.isReplaceable()) {
            return;
        }
        // Don't place inside players/mobs.
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

    /**
     * Replicates vanilla block placement: sets the block, fires a regular
     * BlockPlaceEvent (so protection plugins can veto it and our own PLACE
     * handler converts maple items), consumes the item and plays the sound.
     *
     * Note: orientable blocks (stairs, logs, furnaces...) are placed with their
     * default orientation here — the client does not send us its aim data for
     * this click. Maple blocks themselves are orientation-free, so they always
     * place perfectly.
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
                SoundCategory.BLOCKS, 1.0f, 0.8f);
        player.swingHand(hand);
    }

    // ------------------------------------------------------------------
    // BREAK
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (MapleBlocks.isMapleLog(block)) {
            // Never let the vanilla note block loot table run (it would drop a note block).
            event.setDropItems(false);
            event.setExpToDrop(0);

            // Vanilla plays the wood break sound for note blocks automatically — correct for a log.
            if (player.getGameMode() != GameMode.CREATIVE) {
                // Logs always drop themselves, whatever the tool (an axe is just faster) — vanilla behaviour.
                dropItem(block, items.createMapleLog(1));
            }
            return;
        }

        if (MapleBlocks.isMapleLeaves(block)) {
            // Never let the vanilla mushroom loot table run.
            event.setDropItems(false);
            event.setExpToDrop(0);

            // Vanilla leaves use the grass sound family.
            playSound(block.getLocation(), Sound.BLOCK_GRASS_BREAK);

            if (player.getGameMode() != GameMode.CREATIVE && isShears(player.getInventory().getItemInMainHand())) {
                // Leaves drop themselves ONLY when cut with shears — vanilla behaviour.
                dropItem(block, items.createMapleLeaves(1));
            }
        }
    }

    // ------------------------------------------------------------------
    // EXPLOSIONS — keep drops consistent
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
        Iterator<Block> iterator = blockList.iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (MapleBlocks.isMapleLog(block)) {
                // Remove from the vanilla explosion handling (which would drop a
                // note block) and resolve it ourselves: logs drop themselves.
                iterator.remove();
                block.setType(Material.AIR, false);
                dropItem(block, items.createMapleLog(1));
            } else if (MapleBlocks.isMapleLeaves(block)) {
                // Vanilla leaves destroyed by an explosion drop nothing (no shears involved).
                iterator.remove();
                block.setType(Material.AIR, false);
            }
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

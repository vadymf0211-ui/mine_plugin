package com.example.maple;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.List;

/**
 * Handles the full lifecycle of the Maple blocks:
 *
 *  PLACE  — swaps the freshly placed vanilla mushroom block to the reserved
 *           "maple" BlockState so the resource pack re-textures it.
 *  BREAK  — cancels vanilla drops, drops the correct custom item, plays the
 *           vanilla wood / leaves sounds and respects the correct tool:
 *             * Maple Log    — always drops (an axe simply mines faster), like vanilla logs;
 *             * Maple Leaves — drops ONLY when mined with shears, like vanilla leaves.
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
                // brown_mushroom_block[up=true,down=true,north=false,south=false,east=false,west=false]
                // applyPhysics = false: mushroom blocks have no physics anyway,
                // and we avoid triggering neighbour updates twice.
                block.setBlockData(MapleBlocks.mapleLogData(), false);
                // Vanilla already plays the wood place sound for mushroom blocks — perfect for a log.
            }
            case MapleItems.TYPE_LEAVES -> {
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
    // BREAK
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (MapleBlocks.isMapleLog(block)) {
            // Never let the vanilla mushroom loot table run.
            event.setDropItems(false);
            event.setExpToDrop(0);

            // Vanilla plays the wood break sound for mushroom blocks automatically — correct for a log.
            if (player.getGameMode() != GameMode.CREATIVE) {
                // Logs always drop themselves, whatever the tool (an axe is just faster) — vanilla behaviour.
                dropItem(block, items.createMapleLog(1));
            }
            return;
        }

        if (MapleBlocks.isMapleLeaves(block)) {
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
                // Remove from the vanilla explosion handling (which would roll the
                // mushroom loot table) and resolve it ourselves: logs drop themselves.
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

package sys.vitae.lorem.listeners;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

import io.papermc.paper.block.TileStateInventoryHolder;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import sys.vitae.lorem.utils.PlacedItemData;
import sys.vitae.lorem.utils.PlacedItemData.Preserved;

/**
 * Block entities placed from an item with lore or a glint override drop it with them again, like vanilla does with
 * their name. A broken container's spilled contents are dropped along with the container itself.
 */
public class PlacedItemListener implements Listener {

    /**
     * A block whose drop lost its preserved properties. Its drops are as computed while the block still stood, as they
     * depend on its block entity: an item that differs from them in any other way (its name, say) isn't the block's.
     */
    private record Lost(Block block, Preserved preserved, Collection<ItemStack> drops) {
        boolean droppedAs(ItemStack stack) {
            return preserved.isMissingFrom(stack) && drops.stream().anyMatch(stack::isSimilar);
        }
    }

    private record Drop(Item item, Block block) {}

    /**
     * The blocks an explosion destroys, the contents they spill that are identical to a lost drop, and the items
     * spawned among them before its drops can be matched.
     */
    private record Explosion(Set<Block> blocks, List<Lost> lost, List<ItemStack> spilled, List<Drop> drops) {}

    private final Plugin plugin;
    private final PlacedItemData data;
    private final List<Explosion> explosions = new ArrayList<>();
    /** The block a player is breaking, between its break and drop events. */
    private @Nullable Lost broken;

    public PlacedItemListener(Plugin plugin, PlacedItemData data) {
        this.plugin = plugin;
        this.data = data;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        List<BlockState> placed = event instanceof BlockMultiPlaceEvent multi
                ? multi.getReplacedBlockStates()
                : List.of(event.getBlockReplacedState());
        for (BlockState replaced : placed) {
            if (replaced.getBlock().getState() instanceof TileState tile
                    && data.store(event.getItemInHand(), tile.getPersistentDataContainer())) {
                tile.update();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        broken = lost(block, block.getState(false), event.getPlayer().getInventory().getItemInMainHand(),
                event.getPlayer());
    }

    // Creative breaks drop nothing of the block itself, though containers still spill their contents.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        Lost lost = broken;
        broken = null;
        if (lost == null || !lost.block().equals(event.getBlock())
                || event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        for (Item item : event.getItems().reversed()) {
            ItemStack stack = item.getItemStack();
            if (stack.getAmount() == 1 && lost.droppedAs(stack)) {
                item.setItemStack(lost.preserved().restoreOnto(stack));
                return;
            }
        }
    }

    /** Pistons and fluids. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreakBlock(BlockBreakBlockEvent event) {
        Preserved preserved = data.read(event.getBlock().getState());
        if (preserved == null) {
            return;
        }
        List<ItemStack> drops = event.getDrops();
        for (int i = drops.size() - 1; i >= 0; i--) {
            ItemStack stack = drops.get(i);
            if (stack.getAmount() == 1 && preserved.isMissingFrom(stack)) {
                drops.set(i, preserved.restoreOnto(stack));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        track(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        track(event.blockList());
    }

    /**
     * Explosions drop items without a drop event, after destroying all their blocks, merging identical drops into one
     * stack where the first of them dropped. They're matched up with the destroyed blocks once spawned.
     * Their spilled contents are copied now: an item identical to a lost drop that spills out of any of them (a plain
     * chest in a furnace next to an exploding chest, say) doesn't mean that block dropped.
     */
    private void track(List<Block> blocks) {
        List<Lost> lost = new ArrayList<>();
        List<TileStateInventoryHolder> holders = new ArrayList<>();
        for (Block block : blocks) {
            BlockState state = block.getState(false);
            Lost exploded = lost(block, state, null, null);
            if (exploded != null) {
                lost.add(exploded);
            }
            // Shulker boxes keep their contents in their drop.
            if (state instanceof TileStateInventoryHolder holder && !(state instanceof ShulkerBox)) {
                holders.add(holder);
            }
        }
        if (lost.isEmpty()) {
            return;
        }
        List<ItemStack> spilled = new ArrayList<>();
        for (TileStateInventoryHolder holder : holders) {
            for (ItemStack stack : holder.getSnapshotInventory().getContents()) {
                if (stack != null && lost.stream().anyMatch(exploded -> exploded.droppedAs(stack))) {
                    spilled.add(stack.clone());
                }
            }
        }
        if (explosions.isEmpty()) {
            plugin.getServer().getScheduler().runTask(plugin, this::restoreExplosionDrops);
        }
        explosions.add(new Explosion(Set.copyOf(blocks), lost, spilled, new ArrayList<>()));
    }

    private @Nullable Lost lost(Block block, BlockState state, @Nullable ItemStack tool, @Nullable Entity breaker) {
        Preserved preserved = data.read(state);
        return preserved == null ? null : new Lost(block, preserved, block.getDrops(tool, breaker));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (explosions.isEmpty()) {
            return;
        }
        Block block = event.getLocation().getBlock();
        for (Explosion explosion : explosions) {
            if (explosion.blocks().contains(block)) {
                explosion.drops().add(new Drop(event.getEntity(), block));
                return;
            }
        }
    }

    /**
     * Restores each destroyed block's properties onto one item it would have dropped. Identical items are
     * interchangeable, so only as many are restored as spawned beyond the spilled ones. Merged stacks are split.
     */
    private void restoreExplosionDrops() {
        List<Explosion> pending = List.copyOf(explosions);
        explosions.clear();
        for (Explosion explosion : pending) {
            List<Lost> unmatched = new ArrayList<>(explosion.lost());
            List<ItemStack> spilled = new ArrayList<>(explosion.spilled());
            for (Drop drop : explosion.drops()) {
                if (unmatched.isEmpty()) {
                    break;
                }
                Item item = drop.item();
                if (!item.isValid()) {
                    continue;
                }
                ItemStack stack = item.getItemStack();
                int dropped = stack.getAmount() - takeSpilled(spilled, stack);
                List<Lost> matches = unmatched.stream().filter(lost -> lost.droppedAs(stack))
                        .sorted(Comparator.comparing(lost -> !lost.block().equals(drop.block())))
                        .limit(dropped).toList();
                unmatched.removeAll(matches);
                restore(item, stack, matches);
            }
        }
    }

    /** Counts off up to the stack's amount of identical spilled items, returning how many there were. */
    private static int takeSpilled(List<ItemStack> spilled, ItemStack stack) {
        int taken = 0;
        for (ListIterator<ItemStack> spills = spilled.listIterator(); spills.hasNext() && taken < stack.getAmount();) {
            ItemStack spill = spills.next();
            if (!spill.isSimilar(stack)) {
                continue;
            }
            int take = Math.min(spill.getAmount(), stack.getAmount() - taken);
            taken += take;
            if (take == spill.getAmount()) {
                spills.remove();
            } else {
                spills.set(spill.asQuantity(spill.getAmount() - take));
            }
        }
        return taken;
    }

    private static void restore(Item item, ItemStack stack, List<Lost> matches) {
        int rest = stack.getAmount() - matches.size();
        for (int i = 0; i < matches.size(); i++) {
            ItemStack restored = matches.get(i).preserved().restoreOnto(stack.asOne());
            if (rest == 0 && i == matches.size() - 1) {
                item.setItemStack(restored);
            } else {
                item.getWorld().dropItem(item.getLocation(), restored, split -> {
                    split.setVelocity(item.getVelocity());
                    split.setPickupDelay(item.getPickupDelay());
                });
            }
        }
        if (rest > 0 && !matches.isEmpty()) {
            stack.setAmount(rest);
            item.setItemStack(stack);
        }
    }
}

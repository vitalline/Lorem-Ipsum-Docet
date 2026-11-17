package sys.vitae.lorem.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.kyori.adventure.text.Component;
import sys.vitae.lorem.utils.PlacedItemData;
import sys.vitae.lorem.utils.PlacedItemData.Preserved;

class PlacedItemListenerTest {

    private Plugin plugin;
    private BukkitScheduler scheduler;
    private PlacedItemData data;
    private PlacedItemListener listener;

    @BeforeEach
    void setUp() {
        plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        data = mock(PlacedItemData.class);
        listener = new PlacedItemListener(plugin, data);
    }

    @Test
    void storesTheItemsDataOnThePlacedBlockEntity() {
        ItemStack item = mock(ItemStack.class);
        TileState tile = mock(TileState.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(tile.getPersistentDataContainer()).thenReturn(container);
        when(data.store(item, container)).thenReturn(true);
        listener.onPlace(place(item, tile));
        verify(tile).update();

        when(data.store(item, container)).thenReturn(false);
        TileState plain = mock(TileState.class);
        when(plain.getPersistentDataContainer()).thenReturn(container);
        listener.onPlace(place(item, plain));
        verify(plain, never()).update();

        listener.onPlace(place(item, mock(BlockState.class)));
        verify(data, times(2)).store(any(), any());
    }

    @Test
    void restoresOnlyTheItemTheBrokenBlockWouldHaveDropped() {
        Block block = mock(Block.class);
        Preserved preserved = preserved(block, "Loot");
        ItemStack tool = mock(ItemStack.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(tool);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        ItemStack expected = chest(1);
        when(block.getDrops(tool, player)).thenReturn(List.of(expected));
        BlockBreakEvent breaking = mock(BlockBreakEvent.class);
        when(breaking.getBlock()).thenReturn(block);
        when(breaking.getPlayer()).thenReturn(player);

        // Spilled contents: a stack of chests, and a chest that differs by name or other data, even though it's last.
        Item own = item(similar(chest(1), expected));
        Item stacked = item(similar(chest(2), expected));
        Item spilled = item(chest(1));
        BlockDropItemEvent event = mock(BlockDropItemEvent.class);
        when(event.getBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItems()).thenReturn(new ArrayList<>(List.of(own, stacked, spilled)));

        listener.onBreak(breaking);
        listener.onDrop(event);
        ItemStack ownStack = own.getItemStack();
        verify(own).setItemStack(ownStack);
        verify(ownStack.getItemMeta()).lore(preserved.lore());
        verify(spilled, never()).setItemStack(any());
        verify(stacked, never()).setItemStack(any());

        // Creative breaks don't drop the container, only its contents.
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        clearInvocations(own);
        listener.onBreak(breaking);
        listener.onDrop(event);
        // Nor does a drop that didn't follow the block's break.
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        listener.onDrop(event);
        verify(own, never()).setItemStack(any());
    }

    @Test
    void splitsAMergedExplosionDropAmongTheBlocksThatLostTheirData() {
        Block first = mock(Block.class);
        Block second = mock(Block.class);
        Block plain = mock(Block.class);
        Preserved firstData = preserved(first, "First");
        Preserved secondData = preserved(second, "Second");
        ItemStack expected = chest(1);
        when(first.getDrops(null, null)).thenReturn(List.of(expected));
        when(second.getDrops(null, null)).thenReturn(List.of(expected));
        when(plain.getState(false)).thenReturn(mock(BlockState.class));
        Runnable task = explode(plain, second, first);

        // The explosion's three plain chests merged where the first dropped. A chest differing by name or other data
        // isn't one of them, even when it spawned last.
        ItemStack merged = similar(chest(3), expected);
        ItemStack firstChest = chest(1);
        ItemStack secondChest = chest(1);
        when(merged.asOne()).thenReturn(firstChest, secondChest);
        Item drop = spawn(merged, first);
        ItemStack spilledChest = chest(1);
        Item spilled = spawn(spilledChest, first);
        spawn(similar(chest(1), expected), mock(Block.class));
        task.run();

        World world = drop.getWorld();
        Location location = drop.getLocation();
        verify(firstChest.getItemMeta()).lore(firstData.lore());
        verify(secondChest.getItemMeta()).lore(secondData.lore());
        verify(world).dropItem(eq(location), eq(firstChest), any());
        verify(world).dropItem(eq(location), eq(secondChest), any());
        verify(merged).setAmount(1);
        verify(drop).setItemStack(merged);
        verify(spilled, never()).setItemStack(any());
        verify(spilledChest, never()).setItemMeta(any());
    }

    @Test
    void countsOffIdenticalItemsSpilledByAnyExplodedBlock() {
        Block first = mock(Block.class);
        Block second = mock(Block.class);
        Block furnace = mock(Block.class);
        Block shulker = mock(Block.class);
        Preserved firstData = preserved(first, "First");
        preserved(second, "Second");
        ItemStack expected = chest(1);
        when(first.getDrops(null, null)).thenReturn(List.of(expected));
        when(second.getDrops(null, null)).thenReturn(List.of(expected));
        // The furnace burns a plain chest; a shulker box keeps the same in its drop.
        ItemStack fuel = similar(chest(1), expected);
        ItemStack counted = chest(1);
        when(fuel.clone()).thenReturn(counted);
        FurnaceInventory inventory = mock(FurnaceInventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[] {null, fuel, null});
        Furnace furnaceState = mock(Furnace.class);
        when(furnaceState.getSnapshotInventory()).thenReturn(inventory);
        when(furnace.getState(false)).thenReturn(furnaceState);
        ShulkerBox shulkerState = mock(ShulkerBox.class);
        when(shulkerState.getSnapshotInventory()).thenReturn(inventory);
        when(shulker.getState(false)).thenReturn(shulkerState);
        Runnable task = explode(furnace, shulker, second, first);

        // The fuel spilled, and only the first chest dropped.
        ItemStack spilledChest = similar(chest(1), expected);
        ItemStack droppedChest = similar(chest(1), expected);
        ItemStack restoredChest = chest(1);
        when(counted.isSimilar(spilledChest)).thenReturn(true);
        when(counted.isSimilar(droppedChest)).thenReturn(true);
        when(droppedChest.asOne()).thenReturn(restoredChest);
        Item spilled = spawn(spilledChest, furnace);
        Item drop = spawn(droppedChest, first);
        task.run();

        verify(spilled, never()).setItemStack(any());
        verify(drop).setItemStack(restoredChest);
        verify(restoredChest.getItemMeta()).lore(firstData.lore());
    }

    private Runnable explode(Block... blocks) {
        EntityExplodeEvent explosion = mock(EntityExplodeEvent.class);
        when(explosion.blockList()).thenReturn(new ArrayList<>(List.of(blocks)));
        listener.onEntityExplode(explosion);
        ArgumentCaptor<@NonNull Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        return task.getValue();
    }

    private Preserved preserved(Block block, @NonNull String lore) {
        BlockState state = mock(BlockState.class);
        Preserved preserved = new Preserved(Material.CHEST, List.<Component>of(Component.text(lore)), null);
        when(block.getState(false)).thenReturn(state);
        when(data.read(state)).thenReturn(preserved);
        return preserved;
    }

    private Item spawn(ItemStack stack, Block block) {
        Item item = item(stack);
        Location location = mock(Location.class);
        World world = mock(World.class);
        when(location.getBlock()).thenReturn(block);
        when(item.getLocation()).thenReturn(location);
        when(item.getWorld()).thenReturn(world);
        when(item.isValid()).thenReturn(true);
        ItemSpawnEvent event = mock(ItemSpawnEvent.class);
        when(event.getEntity()).thenReturn(item);
        when(event.getLocation()).thenReturn(location);
        listener.onItemSpawn(event);
        return item;
    }

    private static BlockPlaceEvent place(ItemStack item, BlockState state) {
        Block block = mock(Block.class);
        BlockState replaced = mock(BlockState.class);
        when(block.getState()).thenReturn(state);
        when(replaced.getBlock()).thenReturn(block);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockReplacedState()).thenReturn(replaced);
        when(event.getItemInHand()).thenReturn(item);
        return event;
    }

    private static Item item(ItemStack stack) {
        Item item = mock(Item.class);
        when(item.getItemStack()).thenReturn(stack);
        return item;
    }

    private static ItemStack similar(ItemStack stack, ItemStack expected) {
        when(stack.isSimilar(expected)).thenReturn(true);
        return stack;
    }

    private static ItemStack chest(int amount) {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(stack.getType()).thenReturn(Material.CHEST);
        when(stack.getAmount()).thenReturn(amount);
        when(stack.getItemMeta()).thenReturn(meta);
        return stack;
    }
}

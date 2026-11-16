package sys.vitae.lorem.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

class ItemConsumptionTest {

    @Test
    void lastWaterBottleIsReplacedInItsSlotRatherThanAddedToTheInventory() {
        ItemStack water = water(1);
        Player player = mock(Player.class);
        try (var construction = mockConstruction(ItemStack.class, (_, context) ->
                assertEquals(List.of(Material.GLASS_BOTTLE), context.arguments()))) {
            ItemStack remaining = ItemConsumption.consumeOne(water, player);
            assertEquals(1, construction.constructed().size());
            assertSame(construction.constructed().getFirst(), remaining);
            verifyNoInteractions(player);
            verify(water, never()).setAmount(anyInt());
        }
    }

    @Test
    void stackedWaterBottlesKeepTheStackAndReturnOrDropExactlyOneEmptyBottle() {
        for (boolean full : List.of(false, true)) {
            ItemStack water = water(3), remaining = mock(ItemStack.class);
            when(water.clone()).thenReturn(remaining);
            Player player = mock(Player.class);
            PlayerInventory inventory = mock(PlayerInventory.class);
            World world = mock(World.class);
            Location location = new Location(world, 1, 2, 3);
            when(player.getInventory()).thenReturn(inventory);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(location);
            when(inventory.addItem(any(ItemStack.class))).thenAnswer(invocation -> {
                ItemStack bottle = Objects.requireNonNull(invocation.getArgument(0, ItemStack.class));
                return full ? new HashMap<>(Map.of(0, bottle)) : new HashMap<>();
            });

            try (var construction = mockConstruction(ItemStack.class, (_, context) ->
                    assertEquals(List.of(Material.GLASS_BOTTLE), context.arguments()))) {
                assertSame(remaining, ItemConsumption.consumeOne(water, player));
                assertEquals(1, construction.constructed().size());
                ItemStack bottle = Objects.requireNonNull(construction.constructed().getFirst());
                verify(remaining).setAmount(2);
                verify(water, never()).setAmount(anyInt());
                verify(inventory).addItem(bottle);
                if (full) {
                    verify(world).dropItemNaturally(location, bottle);
                } else {
                    verifyNoInteractions(world);
                }
            }
        }
    }

    @Test
    void ordinaryIngredientsAreConsumedWithoutCreatingBottles() {
        ItemStack ingredient = mock(ItemStack.class), remaining = mock(ItemStack.class);
        Player player = mock(Player.class);
        when(ingredient.getType()).thenReturn(Material.COAL);
        when(ingredient.getAmount()).thenReturn(1);
        assertNull(ItemConsumption.consumeOne(ingredient, player));
        when(ingredient.getAmount()).thenReturn(3);
        when(ingredient.clone()).thenReturn(remaining);
        assertSame(remaining, ItemConsumption.consumeOne(ingredient, player));
        verify(remaining).setAmount(2);
        verifyNoInteractions(player);
    }

    private static ItemStack water(int amount) {
        ItemStack item = mock(ItemStack.class);
        PotionMeta meta = mock(PotionMeta.class);
        when(item.getType()).thenReturn(Material.POTION);
        when(item.getAmount()).thenReturn(amount);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getBasePotionType()).thenReturn(PotionType.WATER);
        return item;
    }
}

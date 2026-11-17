package sys.vitae.lorem.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import sys.vitae.lorem.utils.PlacedItemData.Preserved;

class PlacedItemDataTest {

    private PlacedItemData placed;
    private PersistentDataContainer data;
    private TileState tile;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.namespace()).thenReturn("loremipsum");
        placed = new PlacedItemData(plugin);
        Map<Object, Object> stored = new HashMap<>();
        data = Objects.requireNonNull(Mockito.mock(PersistentDataContainer.class,
                call -> switch (call.getMethod().getName()) {
                    case "set" -> stored.put(call.getArgument(0), call.getArgument(2));
                    case "get" -> stored.get(call.getArgument(0));
                    default -> null;
                }));
        tile = mock(TileState.class);
        BlockData block = mock(BlockData.class);
        when(tile.getPersistentDataContainer()).thenReturn(data);
        when(tile.getBlockData()).thenReturn(block);
        when(block.getPlacementMaterial()).thenReturn(Material.SHULKER_BOX);
    }

    @Test
    void restoresStoredLoreAndGlintOntoTheBlocksDropOnly() {
        List<Component> lore = List.<Component>of(Component.text("Tools", NamedTextColor.GOLD), Component.translatable("block.minecraft.chest"));
        ItemStack item = item(Material.SHULKER_BOX, false, false);
        when(item.getItemMeta().lore()).thenReturn(lore);
        when(item.getItemMeta().hasEnchantmentGlintOverride()).thenReturn(true);
        when(item.getItemMeta().getEnchantmentGlintOverride()).thenReturn(true);
        assertTrue(placed.store(item, data));

        Preserved preserved = placed.read(tile);
        assertNotNull(preserved);
        assertEquals(new Preserved(Material.SHULKER_BOX, lore, true), preserved);

        ItemStack drop = item(Material.SHULKER_BOX, false, false);
        ItemMeta dropMeta = drop.getItemMeta();
        assertTrue(preserved.isMissingFrom(drop));
        preserved.restoreOnto(drop);
        verify(dropMeta).lore(lore);
        verify(dropMeta).setEnchantmentGlintOverride(true);
        verify(drop).setItemMeta(dropMeta);

        assertFalse(preserved.isMissingFrom(item(Material.SHULKER_BOX, true, false)));
        assertFalse(preserved.isMissingFrom(item(Material.SHULKER_BOX, false, true)));
        assertFalse(preserved.isMissingFrom(item(Material.CHEST, false, false)));
    }

    @Test
    void storesNothingForAnItemWithoutLoreOrGlintOverride() {
        assertFalse(placed.store(item(Material.SHULKER_BOX, false, false), data));
        verify(data, never()).set(any(), any(), any());
        assertNull(placed.read(tile));
    }

    private static ItemStack item(Material type, boolean hasLore, boolean hasGlint) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.getType()).thenReturn(type);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.hasLore()).thenReturn(hasLore);
        when(meta.hasEnchantmentGlintOverride()).thenReturn(hasGlint);
        return item;
    }
}

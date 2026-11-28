package sys.vitae.lorem.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

class NameTagsTest {

    private static final @NonNull Component LORE = Objects.requireNonNull(Component.text("lore"));
    private static final @NonNull Component MARKER = TextStyles.CUSTOM_MARKER;
    private static final @NonNull Component PLAIN = Objects.requireNonNull(Component.text("Mob"));
    private static final @NonNull Component RED = Objects.requireNonNull(Component.text("Mob", NamedTextColor.RED));
    private static final @NonNull Component BOLD = Objects.requireNonNull(PLAIN.decorate(TextDecoration.BOLD));
    private static final @NonNull Component RED_BOLD = Objects.requireNonNull(RED.decorate(TextDecoration.BOLD));
    private static final @NonNull Component SWATCH = TextStyles.swatch(NamedTextColor.RED);

    @Test
    void formattedNamesGetTheirLegendAfterOtherLoreUnlessAlreadyThere() {
        // A shared color shows as its swatch, which only goes before the marker if there's other formatting.
        Map<@NonNull Component, List<@NonNull Component>> legends = Map.of(
                RED, List.of(SWATCH), BOLD, List.of(MARKER), RED_BOLD, List.of(SWATCH, MARKER));
        legends.forEach((name, legend) -> {
            ItemStack tag = nameTag(name, List.of(LORE));
            ItemStack marked = NameTags.withLegend(tag, null);
            assertSame(tag.clone(), marked);
            ItemMeta meta = marked.getItemMeta();
            verify(meta).lore(concat(List.of(LORE), legend));
            verify(marked).setItemMeta(meta);

            ItemStack alreadyMarked = nameTag(name, concat(List.of(LORE), legend));
            assertSame(alreadyMarked, NameTags.withLegend(alreadyMarked, name));
        });
    }

    @Test
    void renamesReplaceOnlyTheOldNamesLegend() {
        assertEquals(List.of(LORE, MARKER), synced(BOLD, List.of(LORE, SWATCH, MARKER), RED_BOLD));
        assertEquals(List.of(LORE, SWATCH), synced(RED, List.of(LORE, MARKER), BOLD));
        assertEquals(List.of(LORE), synced(PLAIN, List.of(LORE, SWATCH), RED));
        assertNull(synced(null, List.of(MARKER), BOLD));

        // A line like the marker that isn't last is lore, as is a swatch that isn't the old name's.
        assertEquals(List.of(MARKER, LORE), synced(PLAIN, List.of(MARKER, LORE), BOLD));
        assertEquals(List.of(LORE, SWATCH, SWATCH), synced(RED, List.of(LORE, SWATCH), PLAIN));
    }

    @Test
    void loreEndingLikeALegendKeepsThoseLines() {
        for (List<@NonNull Component> lore : List.of(List.of(LORE, MARKER), List.of(LORE, SWATCH),
                List.of(LORE, SWATCH, MARKER))) {
            for (Component name : List.of(PLAIN, RED, BOLD, RED_BOLD)) {
                ItemMeta meta = mock(ItemMeta.class);
                when(meta.customName()).thenReturn(name);
                NameTags.setLore(meta, lore);
                ArgumentCaptor<List<Component>> stored = ArgumentCaptor.captor();
                verify(meta).lore(stored.capture());
                when(meta.lore()).thenReturn(stored.getValue());
                assertEquals(lore, NameTags.lore(meta));

                // Syncing that lore, as the anvil does next, keeps the lines, whatever the new name.
                for (Component renamed : List.of(PLAIN, RED, BOLD, RED_BOLD)) {
                    List<Component> syncedLore = synced(renamed, stored.getValue(), name);
                    ItemMeta synced = mock(ItemMeta.class);
                    when(synced.customName()).thenReturn(renamed);
                    when(synced.lore()).thenReturn(syncedLore);
                    assertEquals(lore, NameTags.lore(synced));
                }
            }
        }
    }

    @Test
    void otherItemsAreLeftAlone() {
        ItemStack paper = mock(ItemStack.class);
        when(paper.getType()).thenReturn(Material.PAPER);
        assertSame(paper, NameTags.withLegend(paper, BOLD));
        verify(paper, never()).clone();
    }

    @Test
    void namedCreatesAnOrdinaryNameTagWithItsNamesLegend() {
        Map<@NonNull Component, List<@NonNull Component>> legends = Map.of(PLAIN, List.of(), RED, List.of(SWATCH), BOLD, List.of(MARKER));
        legends.forEach((name, legend) -> {
            ItemMeta meta = mock(ItemMeta.class);
            try (var construction = mockConstruction(ItemStack.class, (item, context) -> {
                assertEquals(List.of(Material.NAME_TAG), context.arguments());
                when(item.getItemMeta()).thenReturn(meta);
            })) {
                ItemStack tag = NameTags.named(name);
                verify(meta).customName(name);
                if (legend.isEmpty()) {
                    verify(meta, never()).lore(any());
                } else {
                    verify(meta).lore(legend);
                }
                verify(tag).setItemMeta(meta);
                assertEquals(List.of(tag), construction.constructed());
            }
        });
    }

    /** The lore of a name tag with the name and lore once its legend is synced after a rename from the old name. */
    private static @Nullable List<Component> synced(@Nullable Component name, List<? extends Component> lore,
            @Nullable Component oldName) {
        ItemStack tag = nameTag(name, lore);
        ItemStack result = NameTags.withLegend(tag, oldName);
        if (result == tag) {
            return new ArrayList<>(lore);
        }
        ArgumentCaptor<List<Component>> stored = ArgumentCaptor.captor();
        verify(result.getItemMeta()).lore(stored.capture());
        return stored.getValue();
    }

    private static List<@NonNull Component> concat(List<@NonNull Component> first, List<@NonNull Component> second) {
        List<@NonNull Component> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    /** A name tag whose (single) clone has the given name and lore. */
    private static ItemStack nameTag(@Nullable Component name, @Nullable List<? extends Component> lore) {
        ItemStack tag = mock(ItemStack.class), copy = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(tag.getType()).thenReturn(Material.NAME_TAG);
        when(tag.clone()).thenReturn(copy);
        when(copy.getItemMeta()).thenReturn(meta);
        when(meta.customName()).thenReturn(name);
        when(meta.lore()).thenReturn(lore == null ? null : new ArrayList<Component>(lore));
        return tag;
    }
}

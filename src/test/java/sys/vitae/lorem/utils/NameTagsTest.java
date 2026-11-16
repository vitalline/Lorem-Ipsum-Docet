package sys.vitae.lorem.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

class NameTagsTest {

    private static final Component LORE = Component.text("lore");

    @Test
    void formattedNamesGetTheMarkerAfterOtherLoreUnlessAlreadyMarked() {
        ItemStack tag = nameTag(Component.text("Mob", NamedTextColor.RED), List.of(LORE));
        ItemStack marked = NameTags.withMarker(tag);
        assertSame(tag.clone(), marked);
        ItemMeta meta = marked.getItemMeta();
        verify(meta).lore(List.of(LORE, NameTags.MARKER));
        verify(marked).setItemMeta(meta);

        ItemStack alreadyMarked = nameTag(Component.text("Mob", NamedTextColor.RED), List.of(LORE, NameTags.MARKER));
        assertSame(alreadyMarked, NameTags.withMarker(alreadyMarked));
    }

    @Test
    void plainOrRemovedNamesDropOnlyTheMarker() {
        ItemStack plain = nameTag(Component.text("Mob"), List.of(NameTags.MARKER, LORE));
        verify(NameTags.withMarker(plain).getItemMeta()).lore(List.of(LORE));

        ItemStack unnamed = nameTag(null, List.of(NameTags.MARKER));
        verify(NameTags.withMarker(unnamed).getItemMeta()).lore(null);
    }

    @Test
    void otherItemsAreLeftAlone() {
        ItemStack paper = mock(ItemStack.class);
        when(paper.getType()).thenReturn(Material.PAPER);
        assertSame(paper, NameTags.withMarker(paper));
        verify(paper, never()).clone();
    }

    /** A name tag whose (single) clone has the given name and lore. */
    private static ItemStack nameTag(@Nullable Component name, @Nullable List<Component> lore) {
        ItemStack tag = mock(ItemStack.class), copy = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(tag.getType()).thenReturn(Material.NAME_TAG);
        when(tag.clone()).thenReturn(copy);
        when(copy.getItemMeta()).thenReturn(meta);
        when(meta.customName()).thenReturn(name);
        when(meta.lore()).thenReturn(lore);
        return tag;
    }
}

package sys.vitae.lorem.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.List;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.NonNull;
import org.mockito.ArgumentCaptor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sys.vitae.lorem.utils.Templates.Kind;
import sys.vitae.lorem.utils.Templates.Type;

class TemplatesTest {

    private Templates templates;
    private final @NonNull NamespacedKey idKey = new NamespacedKey("loremipsum", "template_id");

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.namespace()).thenReturn("loremipsum");
        templates = new Templates(plugin);
    }

    @Test
    void allTypesAndResetsAreIdentifiedOnlyByTheirPersistentId() {
        for (Type type : Type.values()) {
            for (boolean reset : new boolean[] {false, true}) {
                Kind kind = new Kind(type, reset);
                ItemStack paper = paper(kind.id());
                assertEquals(kind, templates.kind(paper));
                assertFalse(templates.isSheet(paper));
            }
        }
        ItemStack untagged = paper(null);
        when(untagged.getItemMeta().hasLore()).thenReturn(true);
        assertNull(templates.kind(untagged));
        assertFalse(templates.isSheet(untagged));
        assertNull(templates.kind(paper("unknown")));
    }

    @Test
    void createsTaggedResetWithTranslatableHeaderRatherThanCustomName() {
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        try (var construction = mockConstruction(ItemStack.class,
                (item, context) -> when(item.getItemMeta()).thenReturn(meta))) {
            templates.create(new Kind(Type.LORE, true));

            verify(data).set(idKey, PersistentDataType.STRING, "reset_lore");
            ArgumentCaptor<@NonNull Component> name = ArgumentCaptor.forClass(Component.class);
            verify(meta).itemName(name.capture());
            TranslatableComponent header = Objects.requireNonNull(
                    assertInstanceOf(TranslatableComponent.class, name.getValue()));
            assertEquals("options.generic_value", header.key());
            assertEquals("mco.template.name", ((TranslatableComponent) header.arguments().getFirst().value()).key());
            Component label = (Component) header.arguments().get(1).value();
            assertEquals("mco.backup.entry.description", ((TranslatableComponent) label).key());
            assertEquals("controls.reset", ((TranslatableComponent) label.children().getLast()).key());
            verify(meta, never()).customName(any());
            assertEquals(1, construction.constructed().size());
        }
    }

    @Test
    void nameTextPreviewsLikeACustomNameButAppliesUnchanged() {
        Component plain = Component.text("a name");
        ItemStack template = paper("name"), copy = paper("name");
        when(template.clone()).thenReturn(copy);
        ItemMeta copyMeta = copy.getItemMeta();
        templates.withText(template, List.of(plain));

        ArgumentCaptor<List<Component>> lore = ArgumentCaptor.captor();
        verify(copyMeta).lore(lore.capture());
        Component preview = Objects.requireNonNull(lore.getValue().getFirst());
        assertEquals(TextDecoration.State.NOT_SET, preview.decoration(TextDecoration.ITALIC));
        assertEquals(NamedTextColor.WHITE, preview.color());

        when(copyMeta.lore()).thenReturn(lore.getValue());
        assertEquals(List.of(plain), templates.text(copy));

        // Lore templates show their text just as it will look once applied.
        ItemStack loreTemplate = paper("lore"), loreCopy = paper("lore");
        when(loreTemplate.clone()).thenReturn(loreCopy);
        templates.withText(loreTemplate, List.of(plain));
        verify(loreCopy.getItemMeta()).lore(List.of(plain));
    }

    @Test
    void nameAndLoreApplyOnlyTheirOwnProperty() {
        ItemStack input = mock(ItemStack.class), result = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(input.getItemMeta()).thenReturn(meta);
        when(input.clone()).thenReturn(result);
        ItemStack name = paper("name");
        Component text = Component.text("a name");
        when(name.getItemMeta().lore()).thenReturn(List.of(text));

        assertSame(result, templates.apply(input, name));
        verify(meta).customName(text);
        verify(meta, never()).lore(any());
        verify(meta, never()).setEnchantmentGlintOverride(any());

        clearInvocations(meta);
        ItemStack lore = paper("lore");
        when(lore.getItemMeta().lore()).thenReturn(new java.util.ArrayList<Component>(List.of(text, Component.empty())));
        assertSame(result, templates.apply(input, lore));
        verify(meta).lore(List.of(text, Component.empty()));
        verify(meta, never()).customName(any());
    }

    @Test
    void resetsRemoveOnlyTheCorrespondingOverride() {
        ItemStack input = mock(ItemStack.class), result = mock(ItemStack.class);
        LeatherArmorMeta meta = mock(LeatherArmorMeta.class);
        when(input.getItemMeta()).thenReturn(meta);
        when(input.clone()).thenReturn(result);

        templates.apply(input, paper("reset_name"));
        verify(meta).customName(null);
        templates.apply(input, paper("reset_lore"));
        verify(meta).lore(null);
        templates.apply(input, paper("reset_glint"));
        verify(meta).setEnchantmentGlintOverride(null);
        templates.apply(input, paper("reset_color"));
        verify(meta).setColor(null);
        verify(meta, never()).removeEnchant(any());
    }

    @Test
    void colorReplaysStepsAgainstExistingDyeAndRejectsOtherItems() {
        ColorRecipe recipe = new ColorRecipe(List.of(List.of(Color.RED), List.of(Color.BLUE)));
        ItemStack color = paper("color");
        when(color.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey("loremipsum", "color_recipe"), Objects.requireNonNull(PersistentDataType.INTEGER_ARRAY)))
                .thenReturn(recipe.encode());
        ItemStack input = mock(ItemStack.class), result = mock(ItemStack.class);
        LeatherArmorMeta meta = mock(LeatherArmorMeta.class);
        when(input.getItemMeta()).thenReturn(meta);
        when(input.clone()).thenReturn(result);
        when(meta.isDyed()).thenReturn(true);
        when(meta.getColor()).thenReturn(Color.WHITE);

        assertSame(result, templates.apply(input, color));
        verify(meta).setColor(Color.fromRGB(169, 84, 255));

        when(input.getItemMeta()).thenReturn(mock(ItemMeta.class));
        assertNull(templates.apply(input, color));
    }

    private ItemStack paper(String id) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(item.getType()).thenReturn(Material.PAPER);
        when(item.getAmount()).thenReturn(1);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(idKey, Objects.requireNonNull(PersistentDataType.STRING))).thenReturn(id);
        when(data.has(idKey)).thenReturn(id != null);
        return item;
    }
}

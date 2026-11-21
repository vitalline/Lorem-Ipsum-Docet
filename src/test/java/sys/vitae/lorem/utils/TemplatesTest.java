package sys.vitae.lorem.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.ArrayList;
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
import sys.vitae.lorem.utils.Templates.Variant;

class TemplatesTest {

    private Templates templates;
    private final @NonNull NamespacedKey idKey = new NamespacedKey("loremipsum", "template_id");
    private final @NonNull NamespacedKey nameTagKey = new NamespacedKey("loremipsum", "name_tag_id");

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.namespace()).thenReturn("loremipsum");
        templates = new Templates(plugin);
    }

    @Test
    void allTypesAndVariantsAreIdentifiedOnlyByTheirPersistentId() {
        for (Type type : Type.values()) {
            for (Variant variant : Variant.values()) {
                if (variant == Variant.COPY && !type.isCopyable()) {
                    continue;
                }
                Kind kind = new Kind(type, variant);
                ItemStack paper = paper(kind.id());
                assertEquals(kind, templates.kind(paper));
                assertFalse(templates.isSheet(paper));
            }
        }
        assertEquals("copy_name", new Kind(Type.NAME, Variant.COPY).id());
        ItemStack untagged = paper(null);
        when(untagged.getItemMeta().hasLore()).thenReturn(true);
        assertNull(templates.kind(untagged));
        assertFalse(templates.isSheet(untagged));
        assertNull(templates.kind(paper("unknown")));
        assertNull(templates.kind(paper("copy_glint")));
    }

    @Test
    void createsTaggedResetWithTranslatableHeaderRatherThanCustomName() {
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        try (var construction = mockConstruction(ItemStack.class,
                (item, _) -> when(item.getItemMeta()).thenReturn(meta))) {
            templates.create(new Kind(Type.LORE, Variant.RESET));

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
    void everyTemplateGlints() {
        for (Type type : Type.values()) {
            for (Variant variant : Variant.values()) {
                if (variant == Variant.COPY && !type.isCopyable()) {
                    continue;
                }
                ItemMeta meta = mock(ItemMeta.class);
                when(meta.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
                try (var _ = mockConstruction(ItemStack.class,
                        (item, _) -> when(item.getItemMeta()).thenReturn(meta))) {
                    templates.create(new Kind(type, variant));
                }
                verify(meta).setEnchantmentGlintOverride(true);
            }
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
    void formattedTextIsMarkedAfterItsLinesButTheMarkerIsNotPartOfTheText() {
        @NonNull Component plain = Component.text("text"), styled = Component.text("text", NamedTextColor.WHITE);
        for (String id : List.of("name", "lore")) {
            ItemStack template = paper(id), copy = paper(id);
            when(template.clone()).thenReturn(copy);
            ItemMeta meta = copy.getItemMeta();
            ArgumentCaptor<List<Component>> lore = ArgumentCaptor.captor();

            templates.withText(template, List.of(plain));
            verify(meta).lore(lore.capture());
            assertFalse(lore.getValue().contains(TextStyles.CUSTOM_MARKER));

            // Text that ends like the marker is formatted too, so the marker still follows it.
            for (List<@NonNull Component> text : List.of(List.of(plain, styled), List.of(plain, TextStyles.CUSTOM_MARKER))) {
                clearInvocations(meta);
                templates.withText(template, text);
                verify(meta).lore(lore.capture());
                assertEquals(text.size() + 1, lore.getValue().size());
                assertEquals(TextStyles.CUSTOM_MARKER, lore.getValue().getLast());
                when(meta.lore()).thenReturn(lore.getValue());
                assertEquals(text, templates.text(copy));
            }
        }
    }

    @Test
    void retextingKeepsTheStackSize() {
        ItemStack template = paper("lore"), copy = paper("lore");
        when(template.getAmount()).thenReturn(5);
        when(template.clone()).thenReturn(copy);

        templates.retext(template, List.of(Component.text("new")));
        verify(copy.getItemMeta()).lore(List.of(Component.text("new")));
        verify(copy).setAmount(5);
    }

    @Test
    void craftingOrRenamingAnOldTemplateGivesItGlint() {
        ItemStack template = paper("lore"), copies = paper("lore"), styled = paper("lore"), renamed = paper("lore");
        when(template.clone()).thenReturn(copies, styled, renamed);

        assertSame(copies, templates.copies(template, 3));
        ItemMeta meta = copies.getItemMeta();
        verify(copies).setAmount(3);
        verify(meta).setEnchantmentGlintOverride(true);
        verify(copies).setItemMeta(meta);

        assertSame(styled, templates.withText(template, List.of(Component.text("styled"))));
        verify(styled.getItemMeta()).setEnchantmentGlintOverride(true);

        assertSame(renamed, templates.retext(template, List.of(Component.text("renamed"))));
        verify(renamed.getItemMeta()).setEnchantmentGlintOverride(true);
    }

    @Test
    void glowingTemplatesDontApplyToWhatAlwaysGlints() {
        Templates spy = spy(templates);
        ItemStack star = mock(ItemStack.class), tag = mock(ItemStack.class);
        when(star.getItemMeta()).thenReturn(mock(ItemMeta.class));
        doReturn(true).when(spy).glintsByDefault(star);
        ItemMeta tagMeta = mock(ItemMeta.class);
        PersistentDataContainer tagData = mock(PersistentDataContainer.class);
        when(tag.getType()).thenReturn(Material.NAME_TAG);
        when(tag.getAmount()).thenReturn(1);
        when(tag.getItemMeta()).thenReturn(tagMeta);
        when(tagMeta.getPersistentDataContainer()).thenReturn(tagData);
        when(tagData.get(nameTagKey, Objects.requireNonNull(PersistentDataType.STRING))).thenReturn("reset");

        for (ItemStack input : List.of(star, tag, paper("name"), paper("reset_glint"))) {
            for (String glint : List.of("glint", "reset_glint")) {
                assertNull(spy.apply(input, paper(glint)));
                verify(input.getItemMeta(), never()).setEnchantmentGlintOverride(any());
            }
        }
        // Other templates still apply to them.
        when(star.clone()).thenReturn(mock(ItemStack.class));
        assertNotNull(spy.apply(star, paper("reset_name")));
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
        when(lore.getItemMeta().lore()).thenReturn(new ArrayList<Component>(List.of(text, Component.empty())));
        assertSame(result, templates.apply(input, lore));
        verify(meta).lore(List.of(text, Component.empty()));
        verify(meta, never()).customName(any());

        // A name tag's lore stays followed by its formatting marker.
        clearInvocations(meta);
        when(input.getType()).thenReturn(Material.NAME_TAG);
        when(meta.customName()).thenReturn(Component.text("Mob", NamedTextColor.RED));
        templates.apply(input, lore);
        verify(meta).lore(List.of(text, Component.empty(), TextStyles.CUSTOM_MARKER));
    }

    @Test
    void resetsRemoveOnlyTheCorrespondingOverride() {
        Templates templates = spy(this.templates);
        ItemStack input = mock(ItemStack.class), result = mock(ItemStack.class);
        LeatherArmorMeta meta = mock(LeatherArmorMeta.class);
        when(input.getItemMeta()).thenReturn(meta);
        when(input.clone()).thenReturn(result);
        doReturn(false).when(templates).glintsByDefault(input);

        templates.apply(input, paper("reset_name"));
        verify(meta).customName(null);
        templates.apply(input, paper("reset_lore"));
        verify(meta).lore(null);
        templates.apply(input, paper("reset_glint"));
        verify(meta).setEnchantmentGlintOverride(null);
        // Color templates are crafted onto items instead.
        assertNull(templates.apply(input, paper("reset_color")));
        assertNull(templates.apply(input, paper("color")));
        verify(meta, never()).setColor(any());
        verify(meta, never()).removeEnchant(any());

        // A Copy template has nothing to apply until it copied a property.
        clearInvocations(meta);
        for (String id : List.of("copy_name", "copy_lore", "copy_color")) {
            assertNull(templates.apply(input, paper(id)));
        }
        verifyNoInteractions(meta);
    }

    @Test
    void copiesTheCorrespondingPropertyOffASourceItem() {
        Templates spy = spy(templates);
        ItemStack copy = mock(ItemStack.class);
        LeatherArmorMeta meta = mock(LeatherArmorMeta.class);
        ItemStack source = mock(ItemStack.class);
        when(source.getType()).thenReturn(Material.LEATHER_HELMET);
        when(source.getItemMeta()).thenReturn(meta);
        assertNull(spy.copyFrom(Type.NAME, source));
        assertNull(spy.copyFrom(Type.LORE, source));
        assertNull(spy.copyFrom(Type.COLOR, source));
        assertNull(spy.copyFrom(Type.GLINT, source));

        Component name = Component.text("Helmet", NamedTextColor.RED);
        when(meta.customName()).thenReturn(name);
        doReturn(copy).when(spy).create(Type.NAME, List.of(name));
        assertSame(copy, spy.copyFrom(Type.NAME, source));

        when(meta.lore()).thenReturn(new ArrayList<Component>(List.of(Component.text("line"), TextStyles.CUSTOM_MARKER)));
        doReturn(copy).when(spy).create(Type.LORE, List.of(Component.text("line"), TextStyles.CUSTOM_MARKER));
        assertSame(copy, spy.copyFrom(Type.LORE, source));
        // A name tag's formatting marker is presentation, not description.
        when(source.getType()).thenReturn(Material.NAME_TAG);
        doReturn(copy).when(spy).create(Type.LORE, List.of(Component.text("line")));
        assertSame(copy, spy.copyFrom(Type.LORE, source));
        when(meta.lore()).thenReturn(new ArrayList<Component>(List.of(TextStyles.CUSTOM_MARKER)));
        assertNull(spy.copyFrom(Type.LORE, source));

        when(meta.isDyed()).thenReturn(true);
        when(meta.getColor()).thenReturn(Color.TEAL);
        doReturn(copy).when(spy).createColor(ColorRecipe.of(Color.TEAL));
        assertSame(copy, spy.copyFrom(Type.COLOR, source));
    }

    @Test
    void colorCopyTakesTheColorAllOfATextTemplateShares() {
        Templates spy = spy(templates);
        ItemStack copy = mock(ItemStack.class), lore = paper("lore");
        doReturn(copy).when(spy).createColor(ColorRecipe.of(Color.fromRGB(NamedTextColor.RED.value())));
        when(lore.getItemMeta().lore()).thenReturn(new ArrayList<Component>(List.of(
                Component.text("a", NamedTextColor.RED).decorate(TextDecoration.BOLD),
                Component.empty(),
                Component.text().color(NamedTextColor.RED).append(Component.text("b")).build(),
                TextStyles.CUSTOM_MARKER)));
        assertSame(copy, spy.copyFrom(Type.COLOR, lore));
        assertNull(spy.copyFrom(Type.LORE, lore));

        for (List<@NonNull Component> refused : List.<List<@NonNull Component>>of(
                List.of(Component.text("a", NamedTextColor.RED), Component.text("b", NamedTextColor.BLUE)),
                List.of(Component.text("a", NamedTextColor.RED), Component.text("b")))) {
            when(lore.getItemMeta().lore()).thenReturn(new ArrayList<Component>(refused));
            assertNull(spy.copyFrom(Type.COLOR, lore));
        }
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

        assertSame(result, templates.dye(input, color));
        verify(meta).setColor(Color.fromRGB(169, 84, 255));
        verify(result).setAmount(1);

        assertSame(result, templates.dye(input, paper("reset_color")));
        verify(meta).setColor(null);
        when(result.isSimilar(input)).thenReturn(true);
        assertNull(templates.dye(input, paper("reset_color")));
        assertNull(templates.dye(input, paper("copy_color")));
        assertNull(templates.dye(input, paper("glint")));

        when(input.getItemMeta()).thenReturn(mock(ItemMeta.class));
        assertNull(templates.dye(input, color));
    }

    @Test
    void resetAndCopyTagsAreLabeledByItemNameSoVanillaNeverAppliesThem() {
        for (Variant variant : List.of(Variant.RESET, Variant.COPY)) {
            ItemMeta meta = mock(ItemMeta.class);
            PersistentDataContainer data = mock(PersistentDataContainer.class);
            when(meta.getPersistentDataContainer()).thenReturn(data);
            try (var construction = mockConstruction(ItemStack.class, (item, context) -> {
                assertEquals(List.of(Material.NAME_TAG), context.arguments());
                when(item.getItemMeta()).thenReturn(meta);
            })) {
                templates.createNameTag(variant);

                String id = variant == Variant.RESET ? "reset" : "copy";
                verify(data).set(nameTagKey, PersistentDataType.STRING, id);
                ArgumentCaptor<@NonNull Component> name = ArgumentCaptor.forClass(Component.class);
                verify(meta).itemName(name.capture());
                TranslatableComponent label = Objects.requireNonNull(
                        assertInstanceOf(TranslatableComponent.class, name.getValue()));
                assertEquals("item.minecraft.name_tag", label.key());
                assertEquals(variant == Variant.RESET ? "controls.reset" : "key.keyboard.copy",
                        ((TranslatableComponent) label.children().getLast()).key());
                verify(meta, never()).customName(any());
                assertEquals(1, construction.constructed().size());
            }
        }
    }

    @Test
    void renamedResetOrCopyTagIsAnOrdinaryNameTag() {
        for (Variant variant : List.of(Variant.RESET, Variant.COPY)) {
            String id = variant == Variant.RESET ? "reset" : "copy";
            ItemStack tag = nameTag(id, false);
            assertEquals(variant, templates.nameTagVariant(tag));
            assertSame(tag, templates.asOrdinaryNameTag(tag));
            assertNull(templates.nameTagVariant(paper(id)));

            ItemStack renamed = nameTag(id, true), copy = nameTag(id, true);
            when(renamed.clone()).thenReturn(copy);
            assertNull(templates.nameTagVariant(renamed));
            assertSame(copy, templates.asOrdinaryNameTag(renamed));
            ItemMeta meta = copy.getItemMeta();
            verify(meta.getPersistentDataContainer()).remove(nameTagKey);
            verify(meta).itemName(null);
            verify(meta).setEnchantmentGlintOverride(null);
            verify(copy).setItemMeta(meta);
        }
        assertNull(templates.nameTagVariant(nameTag(null, false)));
        assertNull(templates.nameTagVariant(nameTag("plain", false)));
    }

    private ItemStack nameTag(String id, boolean named) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(item.getType()).thenReturn(Material.NAME_TAG);
        when(item.getAmount()).thenReturn(1);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(meta.hasCustomName()).thenReturn(named);
        when(data.get(nameTagKey, Objects.requireNonNull(PersistentDataType.STRING))).thenReturn(id);
        return item;
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

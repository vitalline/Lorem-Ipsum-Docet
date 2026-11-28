package sys.vitae.lorem.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static sys.vitae.lorem.NonNullMockito.mock;

import java.util.List;
import java.util.Objects;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

class TextStylesTest {

    @Test
    void swatchesAreUprightSoTheirBlockIsSquare() {
        Component swatch = TextStyles.swatch(NamedTextColor.RED);
        assertEquals(TextDecoration.State.FALSE, swatch.decoration(TextDecoration.ITALIC));
        assertEquals(NamedTextColor.RED, swatch.color());
    }

    @ParameterizedTest
    @EnumSource(DyeColor.class)
    void dyeReplacesColorThroughoutNestedTextWithoutLosingOtherProperties(@NonNull DyeColor dye) {
        ItemStack ingredient = ingredient(Material.valueOf(dye.name() + "_DYE"));
        TextColor expected = TextColor.color(dye.getColor().asRGB());
        Component original = Component.text("red", NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .append(Component.text("blue", NamedTextColor.BLUE).font(Key.key("alt")));
        assertTrue(TextStyles.isIngredient(ingredient));
        Component styled = Objects.requireNonNull(TextStyles.apply(List.of(original), ingredient, true).getFirst());

        assertEquals(expected, styled.color());
        assertEquals(expected, styled.children().getFirst().color());
        assertEquals(TextDecoration.State.TRUE, styled.decoration(TextDecoration.BOLD));
        assertEquals(Key.key("alt"), styled.children().getFirst().font());
        assertEquals("redblue", FormatCodes.plainText(styled));
    }

    @ParameterizedTest
    @CsvSource({
            "END_ROD, OBFUSCATED",
            "COAL, BOLD",
            "CHARCOAL, BOLD",
            "STRING, STRIKETHROUGH",
            "STICK, UNDERLINED",
            "FEATHER, ITALIC"
    })
    void decorationNormalizesNestedTextAndLinesThenTogglesOff(@NonNull Material material, @NonNull TextDecoration decoration) {
        ItemStack ingredient = ingredient(material);
        TextDecoration retained = decoration == TextDecoration.BOLD ? TextDecoration.ITALIC : TextDecoration.BOLD;
        var mixed = List.of(Component.text("styled", NamedTextColor.RED).decorate(decoration).decorate(retained)
                .append(Component.text("nested").decoration(decoration, false).font(Key.key("alt"))),
                Component.text("plain"));
        assertTrue(TextStyles.isIngredient(ingredient));
        var on = TextStyles.apply(mixed, ingredient, false);
        assertTrue(on.stream().allMatch(line -> line.decoration(decoration) == TextDecoration.State.TRUE));
        Component first = Objects.requireNonNull(on.getFirst());
        assertEquals(TextDecoration.State.TRUE, first.children().getFirst().decoration(decoration));
        assertEquals(TextDecoration.State.TRUE, first.decoration(retained));
        assertEquals(NamedTextColor.RED, first.color());
        assertEquals(Key.key("alt"), first.children().getFirst().font());
        assertEquals("stylednested", FormatCodes.plainText(first));

        var off = TextStyles.apply(on, ingredient, false);
        assertTrue(off.stream().allMatch(line -> line.decoration(decoration) == TextDecoration.State.FALSE));
        Component firstOff = Objects.requireNonNull(off.getFirst());
        assertEquals(TextDecoration.State.FALSE, firstOff.children().getFirst().decoration(decoration));
        assertEquals(TextDecoration.State.TRUE, firstOff.decoration(retained));
    }

    @Test
    void respectsContextDefaultWhenTogglingItalic() {
        var plain = List.of(Component.text("text"));
        assertEquals(TextDecoration.State.FALSE,
                Objects.requireNonNull(TextStyles.apply(plain, ingredient(Material.FEATHER), true).getFirst())
                        .decoration(TextDecoration.ITALIC));
        assertEquals(TextDecoration.State.TRUE,
                Objects.requireNonNull(TextStyles.apply(plain, ingredient(Material.FEATHER), false).getFirst())
                        .decoration(TextDecoration.ITALIC));
    }

    @ParameterizedTest
    @CsvSource({
            "EXPERIENCE_BOTTLE, alt",
            "BOOK, uniform",
            "OMINOUS_BOTTLE, illageralt",
            "POTION, default"
    })
    void fontReplacesAllOverridesAndStylesTranslationArguments(@NonNull Material material, @NonNull String font) {
        ItemStack ingredient = material == Material.POTION ? potion(material, PotionType.WATER) : ingredient(material);
        Key expected = Key.key(font);
        Component argument = Component.text("name").font(Key.key("illageralt"));
        Component original = Component.translatable("options.generic_value", argument, Component.text("value"))
                .font(Key.key("alt")).color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .append(Component.text("nested").font(Key.key("uniform")));
        assertTrue(TextStyles.isIngredient(ingredient));
        TranslatableComponent styled = (TranslatableComponent) TextStyles.apply(List.of(original), ingredient, true)
                .getFirst();

        assertEquals(expected, styled.font());
        assertEquals(expected, styled.children().getFirst().font());
        assertEquals(expected, ((Component) styled.arguments().getFirst().value()).font());
        assertEquals(expected, ((Component) styled.arguments().get(1).value()).font());
        assertEquals(NamedTextColor.RED, styled.color());
        assertEquals(TextDecoration.State.TRUE, styled.decoration(TextDecoration.BOLD));
        assertEquals("options.generic_value", styled.key());
    }

    @Test
    void formattingIncludesNestedTextTranslationArgumentsAndExplicitDefaults() {
        Component plain = Component.translatable("options.generic_value", Component.text("argument"), Component.text("value"))
                .append(Component.text("tail"));
        assertFalse(TextStyles.isFormatted(plain));
        assertTrue(TextStyles.isFormatted(plain.append(Component.text("styled").font(Key.key("alt")))));
        assertTrue(TextStyles.isFormatted(Component.translatable("options.generic_value",
                Component.text("argument", NamedTextColor.RED), Component.text("value"))));
        // Explicit default formatting looks unformatted in item tooltips, but not on a mob.
        assertTrue(TextStyles.isFormatted(Component.text("name", NamedTextColor.WHITE).decorate(TextDecoration.ITALIC)));
    }

    @Test
    void uniformSignStyleUsesInheritedAppearanceAndIgnoresEmptyLines() {
        Component line = Component.text("first", NamedTextColor.RED)
                .append(Component.text("second").color(NamedTextColor.RED));
        Style uniform = TextStyles.uniformStyle(List.of(line, Component.empty(), Component.text("third", NamedTextColor.RED)));
        assertNotNull(uniform);
        assertEquals(NamedTextColor.RED, uniform.color());
        assertNull(TextStyles.uniformStyle(List.of(line, Component.text("different", NamedTextColor.BLUE))));
        assertNull(TextStyles.uniformStyle(List.of(Component.text("a").decorate(TextDecoration.BOLD), Component.text("b"))));
    }

    @Test
    void rejectsOtherPotionsAndWaterWithCustomEffects() {
        ItemStack water = potion(Material.POTION, PotionType.WATER);
        assertFalse(TextStyles.isIngredient(potion(Material.POTION, PotionType.STRENGTH)));
        assertFalse(TextStyles.isIngredient(potion(Material.POTION, null)));
        assertFalse(TextStyles.isIngredient(potion(Material.SPLASH_POTION, PotionType.WATER)));
        assertFalse(TextStyles.isIngredient(potion(Material.LINGERING_POTION, PotionType.WATER)));
        when(((PotionMeta) water.getItemMeta()).hasCustomEffects()).thenReturn(true);
        assertFalse(TextStyles.isIngredient(water));
        assertThrows(IllegalArgumentException.class, () -> TextStyles.apply(List.of(Component.text("text")), water, true));
    }

    private static @NonNull ItemStack ingredient(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }

    private static @NonNull ItemStack potion(Material material, @Nullable PotionType type) {
        ItemStack item = ingredient(material);
        PotionMeta meta = mock(PotionMeta.class);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getBasePotionType()).thenReturn(type);
        return item;
    }
}

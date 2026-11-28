package sys.vitae.lorem.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class TextStyles {

    /**
     * Vanilla's "Custom", styled like vanilla's own tooltip lines. It ends the legend of text with explicit formatting
     * other than a shared color, as item tooltips may show it the same way as the default formatting (see
     * {@link #legend}).
     */
    public static final @NonNull Component CUSTOM_MARKER = Objects.requireNonNull(
            Component.translatable("generator.custom", "Custom")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

    private TextStyles() {}

    /** A block of the color and its hexadecimal value, upright so that the block is square. */
    public static @NonNull Component swatch(TextColor color) {
        return Objects.requireNonNull(Component.text("\u2588 ", color).decoration(TextDecoration.ITALIC, false)
                .append(Component.translatable("item.color", "Color: %s", Component.text(color.asHexString()))));
    }

    public static @Nullable DyeColor dye(Material material) {
        String name = material.name();
        return name.endsWith("_DYE") ? DyeColor.valueOf(name.substring(0, name.length() - 4)) : null;
    }

    public static boolean isIngredient(ItemStack ingredient) {
        Material material = ingredient.getType();
        return decoration(material) != null || font(ingredient) != null || dye(material) != null;
    }

    public static @Nullable TextDecoration decoration(Material material) {
        return switch (material) {
            case END_ROD -> TextDecoration.OBFUSCATED;
            case COAL, CHARCOAL -> TextDecoration.BOLD;
            case STRING -> TextDecoration.STRIKETHROUGH;
            case STICK -> TextDecoration.UNDERLINED;
            case FEATHER -> TextDecoration.ITALIC;
            default -> null;
        };
    }

    public static boolean isWaterBottle(ItemStack ingredient) {
        return ingredient.getType() == Material.POTION
                && ingredient.getItemMeta() instanceof PotionMeta potion
                && potion.getBasePotionType() == PotionType.WATER && !potion.hasCustomEffects();
    }

    private static @Nullable Key font(ItemStack ingredient) {
        return switch (ingredient.getType()) {
            case EXPERIENCE_BOTTLE -> Key.key("alt");
            case BOOK -> Key.key("uniform");
            case OMINOUS_BOTTLE -> Key.key("illageralt");
            case POTION -> isWaterBottle(ingredient) ? Key.key("default") : null;
            default -> null;
        };
    }

    public static @NonNull List<@NonNull Component> apply(List<? extends Component> lines,
            ItemStack ingredient, boolean italicDefault) {
        DyeColor dye = dye(ingredient.getType());
        if (dye != null) {
            return color(lines, TextColor.color(dye.getColor().asRGB()));
        }
        TextDecoration decoration = decoration(ingredient.getType());
        if (decoration != null) {
            boolean enabled = !allDecorated(lines, decoration, italicDefault);
            return map(lines, style -> style.decoration(decoration, enabled));
        }
        Key font = font(ingredient);
        if (font == null) {
            throw new IllegalArgumentException("Not a text styling ingredient: " + ingredient);
        }
        return map(lines, style -> style.font(font));
    }

    public static @NonNull List<@NonNull Component> color(List<? extends Component> lines,
            @Nullable TextColor color) {
        return map(lines, style -> style.color(color));
    }

    /** Whether any part of the text has explicit styling, even if it matches the context's defaults. */
    public static boolean isFormatted(Component component) {
        return !map(component, _ -> Style.empty()).equals(component);
    }

    /**
     * The lines telling apart explicit formatting, which tooltips may show the same way as the default: the
     * {@link #swatch} of a color all of the text shares, followed by the {@link #CUSTOM_MARKER} if it has any other
     * formatting.
     */
    public static @NonNull List<Component> legend(List<? extends Component> text) {
        List<Component> legend = new ArrayList<>();
        TextColor color = sharedColor(text);
        if (color != null) {
            legend.add(swatch(color));
        }
        if ((color == null ? text : color(text, null)).stream().anyMatch(TextStyles::isFormatted)) {
            legend.add(CUSTOM_MARKER);
        }
        return legend;
    }

    /**
     * The lines followed by the legend of the text they show or describe. Lines already ending in the marker get
     * another even if the legend is empty, so that {@link #unmarked} always restores them.
     */
    public static @NonNull List<Component> marked(List<? extends Component> lines,
            List<? extends Component> described) {
        List<Component> result = new ArrayList<>(lines);
        List<Component> legend = legend(described);
        result.addAll(legend.isEmpty() && endsWithMarker(lines) ? List.of(CUSTOM_MARKER) : legend);
        return result;
    }

    /** The text without its legend, as {@link #marked} with itself. */
    public static @NonNull List<Component> unmarked(List<? extends Component> text) {
        return unmarked(text, TextStyles::sharedColor);
    }

    /** The lines without the legend of the text they describe, as {@link #marked} with it. */
    public static @NonNull List<Component> unmarked(List<? extends Component> lines,
            List<? extends Component> described) {
        TextColor color = sharedColor(described);
        return unmarked(lines, _ -> color);
    }

    /**
     * The lines without a final marker, then without a final swatch of the described text's color. Text ending like
     * a swatch of its own color shares that color, so it is followed by a swatch of the same color and keeps the line.
     */
    private static @NonNull List<Component> unmarked(List<? extends Component> lines,
            Function<List<Component>, @Nullable TextColor> describedColor) {
        List<Component> result = new ArrayList<>(lines);
        if (endsWithMarker(result)) {
            result.removeLast();
        }
        if (!result.isEmpty() && result.getLast() instanceof TextComponent last
                && last.color() instanceof TextColor shown
                && describedColor.apply(result.subList(0, result.size() - 1)) instanceof TextColor color
                && shown.value() == color.value() && swatch(shown).equals(last)) {
            result.removeLast();
        }
        return result;
    }

    private static boolean endsWithMarker(List<? extends Component> lines) {
        return !lines.isEmpty() && CUSTOM_MARKER.equals(lines.getLast());
    }

    /** A visual style shared by all non-empty text, or null for mixed formatting. */
    public static @Nullable Style uniformStyle(List<? extends Component> lines) {
        List<@NonNull Style> styles = new ArrayList<>();
        for (Component line : lines) {
            collectStyles(line, Style.empty(), styles, false);
        }
        return shared(styles);
    }

    /**
     * Like {@link #uniformStyle}, but unset properties stay unset, so they keep showing the context's defaults
     * (e.g. a custom name's italics).
     */
    public static @Nullable Style sharedStyle(Component component) {
        List<@NonNull Style> styles = new ArrayList<>();
        collectStyles(component, Style.empty(), styles, true);
        return shared(styles);
    }

    /** The color explicitly shared by all non-empty text, or null if any of it is uncolored or colored differently. */
    public static @Nullable TextColor sharedColor(List<? extends Component> lines) {
        List<Style> styles = new ArrayList<>();
        for (Component line : lines) {
            collectStyles(line, Style.empty(), styles, true);
        }
        TextColor color = styles.isEmpty() ? null : styles.getFirst().color();
        return color != null && styles.stream().allMatch(style -> style.color() instanceof TextColor other
                && other.value() == color.value()) ? color : null;
    }

    private static @Nullable Style shared(List<@NonNull Style> styles) {
        if (styles.isEmpty()) {
            return Style.empty();
        }
        Style first = Objects.requireNonNull(styles.getFirst());
        return styles.stream().allMatch(first::equals) ? first : null;
    }

    private static void collectStyles(Component component, @NonNull Style inherited, List<@NonNull Style> styles,
            boolean keepUnset) {
        Style resolved = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (!(component instanceof net.kyori.adventure.text.TextComponent text) || !text.content().isEmpty()) {
            Key font = resolved.font();
            Style.Builder visual = Style.style().color(resolved.color()).font(keepUnset
                    ? (Key.key("default").equals(font) ? null : font)
                    : (font == null ? Key.key("default") : font));
            for (TextDecoration decoration : TextDecoration.values()) {
                TextDecoration.State state = resolved.decoration(decoration);
                visual.decoration(decoration, keepUnset ? state : TextDecoration.State.byBoolean(state == TextDecoration.State.TRUE));
            }
            styles.add(visual.build());
        }
        for (Component child : component.children()) {
            collectStyles(child, resolved, styles, keepUnset);
        }
        if (component instanceof TranslatableComponent translated) {
            for (var argument : translated.arguments()) {
                if (argument.value() instanceof Component text) {
                    collectStyles(text, resolved, styles, keepUnset);
                }
            }
        }
    }

    private static @NonNull List<@NonNull Component> map(List<? extends Component> lines,
            UnaryOperator<@NonNull Style> operation) {
        return Objects.requireNonNull(lines.stream().map(line -> map(line, operation)).toList());
    }

    private static @NonNull Component map(Component component, UnaryOperator<@NonNull Style> operation) {
        Component result = component.style(Objects.requireNonNull(operation.apply(component.style())))
                .children(map(component.children(), operation));
        if (result instanceof TranslatableComponent translated) {
            result = translated.arguments(Objects.requireNonNull(translated.arguments().stream()
                    .map(argument -> argument.value() instanceof Component text
                            ? net.kyori.adventure.text.TranslationArgument.component(map(text, operation)) : argument)
                    .toList()));
        }
        return result;
    }

    private static boolean allDecorated(List<? extends Component> lines,
            @NonNull TextDecoration decoration, boolean italicDefault) {
        List<@NonNull Style> styles = new ArrayList<>();
        Style defaults = Style.style().decoration(TextDecoration.ITALIC, italicDefault).build();
        for (Component line : lines) {
            collectStyles(line, defaults, styles, false);
        }
        return !styles.isEmpty() && styles.stream()
                .allMatch(style -> style.decoration(decoration) == TextDecoration.State.TRUE);
    }
}

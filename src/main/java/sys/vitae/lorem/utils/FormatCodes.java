package sys.vitae.lorem.utils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.NonNull;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Converts between {@code \}-escaped text, as typed by players, and text components.
 *
 * <p>Escape sequences (specifier letters and hex digits are case-insensitive, like vanilla's {@code §} codes):
 * <ul>
 *   <li>{@code \0}-{@code \9}, {@code \a}-{@code \f} - colors; {@code \xRRGGBB} - a hex color</li>
 *   <li>{@code \k}-{@code \o} - obfuscated, bold, strikethrough, underlined, italic</li>
 *   <li>{@code \g} - enchanting table (SGA) font, {@code \h} - Unifont, {@code \i} - illager runes,
 *       {@code \j} - the regular font</li>
 *   <li>{@code \r} - reset: no color, decorations or font</li>
 *   <li>{@code \sXX}, {@code \tXXX}, <code>&#92;uXXXX</code>, {@code \vXXXXX}, {@code \wXXXXXX} -
 *       the Unicode character with that (hexadecimal) code point; {@code \sA7} is a literal {@code §}</li>
 *   <li>{@code \\} - a literal {@code \}</li>
 * </ul>
 * Like legacy {@code §} codes, a color (or reset) turns off all decorations. Fonts are only changed by font codes and reset.
 * A {@code \} that doesn't start a valid sequence is kept as-is.
 */
public final class FormatCodes {

    public static final char SECTION = '§';
    public static final char ESCAPE = '\\';

    /** Decorations shown when not explicitly turned off: custom item names are italic. */
    public static final Set<@NonNull TextDecoration> NAME_DEFAULTS = Set.of(TextDecoration.ITALIC);
    /** Decorations shown when not explicitly turned off: none (e.g. signs). */
    public static final Set<@NonNull TextDecoration> PLAIN_DEFAULTS = Set.of();

    private static final String COLOR_CODES = "0123456789abcdef";
    private static final List<@NonNull NamedTextColor> COLORS = List.of(
            NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN, NamedTextColor.DARK_AQUA,
            NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE, NamedTextColor.GOLD, NamedTextColor.GRAY,
            NamedTextColor.DARK_GRAY, NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW, NamedTextColor.WHITE);
    private static final String FONT_CODES = "ghi";
    private static final List<@NonNull Key> FONTS = List.of(Key.key("alt"), Key.key("uniform"), Key.key("illageralt"));
    private static final char DEFAULT_FONT_CODE = 'j';
    private static final Key DEFAULT_FONT = Key.key("default");
    private static final String DECORATION_CODES = "klmno";
    private static final List<@NonNull TextDecoration> DECORATIONS = List.of(
            TextDecoration.OBFUSCATED, TextDecoration.BOLD, TextDecoration.STRIKETHROUGH,
            TextDecoration.UNDERLINED, TextDecoration.ITALIC);
    private static final char RESET = 'r';
    /** Unicode escape specifiers, by digit count: {@code s} takes 2 hex digits, ..., {@code w} takes 6. */
    private static final String UNICODE_SPECIFIERS = "stuvw";
    private static final int UNICODE_MIN_DIGITS = 2;
    private static final char HEX_COLOR = 'x';
    private static final int HEX_COLOR_DIGITS = 6;
    private static final String SECTION_ESCAPE = "\\sA7";

    private FormatCodes() {}

    private sealed interface Code {}
    private record ColorCode(TextColor color) implements Code {}
    private record DecorationCode(TextDecoration decoration) implements Code {}
    private record FontCode(Key font) implements Code {} // null: the regular font
    private record ResetCode() implements Code {}

    /** A recognized escape sequence: either a {@link Code} or the text it stands for. */
    private record Escape(Object value, int length) {}

    /** A piece of text with its style, all inherited properties resolved. */
    private record Run(String text, Style style) {}

    /** The formatting in effect while parsing. */
    private record State(TextColor color, Set<TextDecoration> on, boolean explicit, Key font) {
        static final State INITIAL = new State(null, Set.of(), false, null);

        State apply(Code code) {
            return switch (code) {
                case ColorCode c -> new State(c.color(), Set.of(), true, font);
                case DecorationCode d -> new State(color, with(on, d.decoration()), explicit, font);
                case FontCode f -> new State(color, on, explicit, f.font());
                case ResetCode _ -> new State(null, Set.of(), true, null);
            };
        }

        /** Before any color or reset, decorations are left unset, so the context's defaults (e.g. italic) apply. */
        @NonNull Style style() {
            Style.Builder style = Style.style().color(color).font(font);
            for (@NonNull TextDecoration decoration : DECORATIONS) {
                style.decoration(
                    decoration,
                    on.contains(decoration) ? TextDecoration.State.TRUE : explicit ? TextDecoration.State.FALSE : TextDecoration.State.NOT_SET
                );
            }
            return style.build();
        }
    }

    /** What a run looks like: its color, the decorations shown, and its font (null for the regular one). */
    private record Look(TextColor color, Set<@NonNull TextDecoration> on, Key font) {
        static Look of(Style style, Set<@NonNull TextDecoration> defaults) {
            Set<@NonNull TextDecoration> on = EnumSet.noneOf(TextDecoration.class);
            for (TextDecoration decoration : DECORATIONS) {
                TextDecoration.State state = style.decoration(decoration);
                if (state == TextDecoration.State.TRUE || (state == TextDecoration.State.NOT_SET && defaults.contains(decoration))) {
                    on.add(decoration);
                }
            }
            return new Look(style.color(), on, normalizeFont(style.font()));
        }
    }

    /** True if the text contains any escape sequence (including {@code \\}). */
    public static boolean hasEscapes(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == ESCAPE && (text.charAt(i + 1) == ESCAPE || escapeAt(text, i) != null)) {
                return true;
            }
        }
        return false;
    }

    /** Parses escaped text into a component. Text without escapes becomes a plain, unstyled text component. */
    public static Component toComponent(String text) {
        State state = State.INITIAL;
        List<Component> parts = new ArrayList<>();
        for (Object piece : tokenize(text)) {
            if (piece instanceof Code code) {
                state = state.apply(code);
            } else if (piece instanceof String string) {
                parts.add(Component.text(string, state.style()));
            }
        }
        return switch (parts.size()) {
            case 0 -> Component.empty();
            case 1 -> parts.getFirst();
            default -> Component.text().append(parts).build();
        };
    }

    /**
     * Translates escapes into a legacy {@code §}-coded string, for places that only take plain strings
     * (like book titles). Font codes are dropped, as legacy text can't express them.
     */
    public static String toLegacy(String text) {
        return render(tokenize(text), true);
    }

    /**
     * The inverse of {@link #toComponent}: escaped text that parses back to a component that looks the same.
     * Formatting becomes escape codes (lowercase specifiers, uppercase hex digits), {@code §} becomes {@code \sA7},
     * a {@code \} is doubled only where it would otherwise start an escape, and all other characters are kept as-is.
     *
     * @param defaults the decorations the context shows when they're not set, e.g. {@link #NAME_DEFAULTS}
     * @return the escaped text, or null if the component can't be expressed with escapes
     *         (e.g. it has click events, translatable parts or a custom font)
     */
    public static String toBuffer(Component component, Set<@NonNull TextDecoration> defaults) {
        List<Run> runs = new ArrayList<>();
        if (!flatten(component, Style.empty(), runs)) {
            return null;
        }
        List<Object> pieces = new ArrayList<>();
        Look current = new Look(null, defaults, null);
        for (Run run : runs) {
            Look target = Look.of(run.style(), defaults);
            addTransition(current, target, pieces);
            pieces.add(run.text());
            current = target;
        }
        return render(pieces, false);
    }

    /** True if the escaped text is non-empty and made up solely of formatting codes. */
    public static boolean isOnlyFormatting(String text) {
        List<Object> pieces = tokenize(text);
        return !pieces.isEmpty() && pieces.stream().allMatch(Code.class::isInstance);
    }

    /** Removes the run of formatting codes at the start of escaped text. */
    public static String stripLeadingCodes(String text) {
        int i = 0;
        Escape escape;
        while ((escape = escapeAt(text, i)) != null && escape.value() instanceof Code) {
            i += escape.length();
        }
        return text.substring(i);
    }

    /** True if both are absent, or both are present and look the same. */
    public static boolean sameText(Component a, Component b) {
        return a == null ? b == null : b != null && a.compact().equals(b.compact());
    }

    public static String plainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(component));
    }

    private static List<Object> tokenize(String text) {
        List<Object> pieces = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == ESCAPE && i + 1 < text.length() && text.charAt(i + 1) == ESCAPE) {
                literal.append(ESCAPE);
                i += 2;
                continue;
            }
            Escape escape = escapeAt(text, i);
            if (escape == null) {
                literal.append(c);
                i++;
                continue;
            }
            if (escape.value() instanceof Code code) {
                if (!literal.isEmpty()) {
                    pieces.add(literal.toString());
                    literal.setLength(0);
                }
                pieces.add(code);
            } else {
                literal.append((String) escape.value());
            }
            i += escape.length();
        }
        if (!literal.isEmpty()) {
            pieces.add(literal.toString());
        }
        return pieces;
    }

    /** The escape sequence (other than {@code \\}) starting at the given index, or null if there's none. */
    private static Escape escapeAt(String text, int i) {
        if (i + 1 >= text.length() || text.charAt(i) != ESCAPE) {
            return null;
        }
        char specifier = Character.toLowerCase(text.charAt(i + 1));
        int index;
        if ((index = COLOR_CODES.indexOf(specifier)) >= 0) {
            return new Escape(new ColorCode(COLORS.get(index)), 2);
        } else if ((index = DECORATION_CODES.indexOf(specifier)) >= 0) {
            return new Escape(new DecorationCode(DECORATIONS.get(index)), 2);
        } else if ((index = FONT_CODES.indexOf(specifier)) >= 0) {
            return new Escape(new FontCode(FONTS.get(index)), 2);
        } else if (specifier == DEFAULT_FONT_CODE) {
            return new Escape(new FontCode(null), 2);
        } else if (specifier == RESET) {
            return new Escape(new ResetCode(), 2);
        } else if (specifier == HEX_COLOR) {
            Integer rgb = hexValue(text, i + 2, HEX_COLOR_DIGITS);
            return rgb == null ? null : new Escape(new ColorCode(TextColor.color(rgb)), 2 + HEX_COLOR_DIGITS);
        } else if ((index = UNICODE_SPECIFIERS.indexOf(specifier)) >= 0) {
            int digits = index + UNICODE_MIN_DIGITS;
            Integer codePoint = hexValue(text, i + 2, digits);
            if (codePoint == null || !Character.isValidCodePoint(codePoint)
                    || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE)) {
                return null;
            }
            return new Escape(Character.toString(codePoint), 2 + digits);
        }
        return null;
    }

    /** Collects the text runs of a component; false if it has anything escapes can't express. */
    private static boolean flatten(Component component, @NonNull Style inherited, List<Run> runs) {
        if (!(component instanceof TextComponent text)) {
            return false;
        }
        Style style = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (style.clickEvent() != null || style.hoverEvent() != null || style.insertion() != null) {
            return false;
        }
        Key font = normalizeFont(style.font());
        if (font != null && !FONTS.contains(font)) {
            return false;
        }
        if (!text.content().isEmpty()) {
            runs.add(new Run(text.content(), style));
        }
        for (Component child : component.children()) {
            if (!flatten(child, style, runs)) {
                return false;
            }
        }
        return true;
    }

    /** Adds the codes that turn the {@code from} look into the {@code to} look. */
    private static void addTransition(Look from, Look to, List<Object> pieces) {
        Look current = from;
        // Decorations can only be turned off by a color or reset code, which turns off all of them.
        if (!sameColor(from.color(), to.color()) || !to.on().containsAll(from.on())) {
            if (to.color() != null) {
                pieces.add(new ColorCode(to.color()));
                current = new Look(to.color(), Set.of(), from.font());
            } else {
                pieces.add(new ResetCode());
                current = new Look(null, Set.of(), null);
            }
        }
        if (!Objects.equals(current.font(), to.font())) {
            pieces.add(new FontCode(to.font()));
        }
        for (TextDecoration decoration : DECORATIONS) {
            if (to.on().contains(decoration) && !current.on().contains(decoration)) {
                pieces.add(new DecorationCode(decoration));
            }
        }
    }

    private static String render(List<Object> pieces, boolean legacy) {
        StringBuilder raw = new StringBuilder();
        BitSet literalEscapes = new BitSet();
        for (Object piece : pieces) {
            if (piece instanceof Code code) {
                appendCode(raw, code, legacy);
                continue;
            }
            for (char c : ((String) piece).toCharArray()) {
                if (!legacy && c == SECTION) {
                    raw.append(SECTION_ESCAPE);
                } else {
                    if (c == ESCAPE) {
                        literalEscapes.set(raw.length());
                    }
                    raw.append(c);
                }
            }
        }
        if (legacy) {
            return raw.toString();
        }
        // Double a literal \ only where it would otherwise be read as (part of) an escape.
        String text = raw.toString();
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            out.append(text.charAt(i));
            if (literalEscapes.get(i) && i + 1 < text.length()
                    && (text.charAt(i + 1) == ESCAPE || escapeAt(text, i) != null)) {
                out.append(ESCAPE);
            }
        }
        return out.toString();
    }

    private static void appendCode(StringBuilder out, Code code, boolean legacy) {
        char prefix = legacy ? SECTION : ESCAPE;
        switch (code) {
            case ColorCode(TextColor color) when color instanceof NamedTextColor named ->
                    out.append(prefix).append(COLOR_CODES.charAt(COLORS.indexOf(named)));
            case ColorCode(TextColor color) -> {
                String hex = String.format(Locale.ROOT, "%06X", color.value());
                out.append(prefix).append(HEX_COLOR);
                if (legacy) {
                    for (char digit : hex.toLowerCase(Locale.ROOT).toCharArray()) {
                        out.append(SECTION).append(digit);
                    }
                } else {
                    out.append(hex);
                }
            }
            case DecorationCode(TextDecoration decoration) ->
                    out.append(prefix).append(DECORATION_CODES.charAt(DECORATIONS.indexOf(decoration)));
            case FontCode(Key font) -> {
                if (!legacy) {
                    out.append(prefix).append(font == null ? DEFAULT_FONT_CODE : FONT_CODES.charAt(FONTS.indexOf(font)));
                }
            }
            case ResetCode() -> out.append(prefix).append(RESET);
        }
    }

    private static boolean sameColor(TextColor a, TextColor b) {
        return a == null ? b == null : b != null && a.value() == b.value();
    }

    private static Key normalizeFont(Key font) {
        return DEFAULT_FONT.equals(font) ? null : font;
    }

    private static Set<TextDecoration> with(Set<TextDecoration> set, TextDecoration decoration) {
        Set<TextDecoration> result = EnumSet.of(decoration);
        result.addAll(set);
        return result;
    }

    /** The value of the {@code count} hex digits at the given index, or null if there aren't that many. */
    private static Integer hexValue(String text, int start, int count) {
        if (start + count > text.length()) {
            return null;
        }
        for (int i = start; i < start + count; i++) {
            char c = text.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return null;
            }
        }
        return Integer.parseInt(text, start, start + count, 16);
    }
}

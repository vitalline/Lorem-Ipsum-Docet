package sys.vitae.lorem.utils;

import java.util.Locale;

/**
 * Helpers for {@code \}-escaped formatting codes and Minecraft's legacy {@code §} codes.
 *
 * <p>Specifier letters are case-insensitive, as are hex digits,
 * mirroring vanilla's and CraftBukkit's handling of {@code §} codes.
 * <p>Escape sequences:
 * <ul>
 *   <li>{@code \\} - a literal {@code \}</li>
 *   <li>{@code \xRRGGBB} - a hex color, i.e. {@code §x§R§R§G§G§B§B}</li>
 *   <li>{@code \sXX}, {@code \tXXX}, <code>&#92;uXXXX</code>, {@code \vXXXXX}, {@code \wXXXXXX} - the Unicode
 *       character with that (hexadecimal) code point</li>
 *   <li>any other {@code \} - a {@code §}, so {@code \c} is <code>&#167;c</code></li>
 * </ul>
 * A {@code \x} or Unicode specifier that isn't followed by enough hex digits (or names an invalid code point)
 * falls back to the last rule, so the old {@code \x\R\R\G\G\B\B} form still works.
 */
public final class FormatCodes {

    public static final char SECTION = '§';
    public static final char ESCAPE = '\\';

    private static final char HEX_COLOR = 'x';
    private static final int HEX_COLOR_DIGITS = 6;
    /** {@code §x} followed by six {@code §}-prefixed digits. */
    private static final int HEX_COLOR_LEGACY_LENGTH = 2 + 2 * HEX_COLOR_DIGITS;

    /** Unicode escape specifiers, by digit count: {@code s} takes 2 hex digits, ..., {@code w} takes 6. */
    private static final String UNICODE_SPECIFIERS = "stuvw";
    private static final int UNICODE_MIN_DIGITS = 2;

    /** A literal {@code §}, which can't be typed in game. */
    private static final String SECTION_ESCAPE = "\\sA7";

    private FormatCodes() {}

    public static boolean containsEscape(String text) {
        return text != null && text.indexOf(ESCAPE) >= 0;
    }

    /** Expands every escape sequence in the text (see the class docs). */
    public static String translate(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c != ESCAPE) {
                out.append(c);
                i++;
                continue;
            }
            char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (next == ESCAPE) {
                out.append(ESCAPE);
                i += 2;
                continue;
            }

            char specifier = Character.toLowerCase(next);
            if (specifier == HEX_COLOR) {
                String hex = hexDigits(text, i + 2, HEX_COLOR_DIGITS);
                if (hex != null) {
                    out.append(SECTION).append(HEX_COLOR);
                    for (char digit : hex.toLowerCase(Locale.ROOT).toCharArray()) {
                        out.append(SECTION).append(digit);
                    }
                    i += 2 + HEX_COLOR_DIGITS;
                    continue;
                }
            } else if (UNICODE_SPECIFIERS.indexOf(specifier) >= 0) {
                int digits = UNICODE_SPECIFIERS.indexOf(specifier) + UNICODE_MIN_DIGITS;
                String hex = hexDigits(text, i + 2, digits);
                if (hex != null) {
                    int codePoint = Integer.parseInt(hex, 16);
                    if (Character.isValidCodePoint(codePoint)) {
                        out.appendCodePoint(codePoint);
                        i += 2 + digits;
                        continue;
                    }
                }
            }
            out.append(SECTION);
            i++;
        }
        return out.toString();
    }

    /**
     * The inverse of {@link #translate}: turns legacy text into an editable "buffer" string that translates
     * back to it. Formatting codes become escapes (lowercase specifiers, uppercase hex digits), {@code \}
     * becomes {@code \\}, and a {@code §} that isn't part of a code becomes {@code \sA7}. Everything else,
     * including any other Unicode character, is kept as-is.
     */
    public static String toBuffer(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int length = 1;
            if (c == ESCAPE) {
                out.append(ESCAPE).append(ESCAPE);
            } else if (c == SECTION && isLegacyHexColor(text, i)) {
                out.append(ESCAPE).append(HEX_COLOR);
                for (int digit = 0; digit < HEX_COLOR_DIGITS; digit++) {
                    out.append(Character.toUpperCase(text.charAt(i + 3 + 2 * digit)));
                }
                length = HEX_COLOR_LEGACY_LENGTH;
            } else if (c == SECTION && i + 1 < text.length() && isSimpleCode(text.charAt(i + 1))) {
                out.append(ESCAPE).append(Character.toLowerCase(text.charAt(i + 1)));
                length = 2;
            } else if (c == SECTION) {
                out.append(SECTION_ESCAPE);
            } else {
                out.append(c);
            }
            i += length;
        }
        return out.toString();
    }

    /** True if the (already translated) text is non-empty and made up solely of {@code §} codes. */
    public static boolean isOnlyFormatting(String text) {
        return !text.isEmpty() && leadingCodesLength(text) == text.length();
    }

    /** Removes the run of {@code §} codes at the start of the text. */
    public static String stripLeadingCodes(String text) {
        return text.substring(leadingCodesLength(text));
    }

    private static int leadingCodesLength(String text) {
        int i = 0;
        while (i + 1 < text.length() && text.charAt(i) == SECTION && isFormatCode(text.charAt(i + 1))) {
            i += 2;
        }
        return i;
    }

    /** Colors {@code 0-9a-f}, decorations {@code k-o}, reset {@code r}, and the hex marker {@code x}. */
    private static boolean isFormatCode(char c) {
        return isSimpleCode(c) || Character.toLowerCase(c) == HEX_COLOR;
    }

    /** The format codes that stand on their own: everything but the hex marker. */
    private static boolean isSimpleCode(char c) {
        c = Character.toLowerCase(c);
        return isHexDigit(c) || (c >= 'k' && c <= 'o') || c == 'r';
    }

    /** {@code §x§R§R§G§G§B§B} at the given index. */
    private static boolean isLegacyHexColor(String text, int start) {
        if (start + HEX_COLOR_LEGACY_LENGTH > text.length()
                || Character.toLowerCase(text.charAt(start + 1)) != HEX_COLOR) {
            return false;
        }
        for (int i = start + 2; i < start + HEX_COLOR_LEGACY_LENGTH; i += 2) {
            if (text.charAt(i) != SECTION || !isHexDigit(text.charAt(i + 1))) {
                return false;
            }
        }
        return true;
    }

    /** The {@code count} hex digits at the given index, or null if there aren't that many. */
    private static String hexDigits(String text, int start, int count) {
        if (start + count > text.length()) {
            return null;
        }
        for (int i = start; i < start + count; i++) {
            if (!isHexDigit(text.charAt(i))) {
                return null;
            }
        }
        return text.substring(start, start + count);
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}

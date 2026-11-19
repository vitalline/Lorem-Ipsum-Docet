package sys.vitae.lorem.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

class FormatCodesTest {

    @Test
    void styleResetClearsOnlyDecorations() {
        Component formatted = FormatCodes.toComponent("\\3\\l\\hbold\\pplain");
        Component before = formatted.children().getFirst();
        Component after = formatted.children().get(1);

        assertEquals(NamedTextColor.DARK_AQUA, before.color());
        assertEquals(Key.key("uniform"), before.font());
        assertEquals(TextDecoration.State.TRUE, before.decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.DARK_AQUA, after.color());
        assertEquals(Key.key("uniform"), after.font());
        assertEquals(TextDecoration.State.FALSE, after.decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.FALSE, after.decoration(TextDecoration.ITALIC));
    }

    @Test
    void colorResetClearsOnlyColor() {
        Component formatted = FormatCodes.toComponent("\\3\\l\\hred\\qplain");
        Component before = formatted.children().getFirst();
        Component after = formatted.children().get(1);

        assertEquals(NamedTextColor.DARK_AQUA, before.color());
        assertEquals(Key.key("uniform"), before.font());
        assertEquals(TextDecoration.State.TRUE, before.decoration(TextDecoration.BOLD));
        assertNull(after.color());
        assertEquals(Key.key("uniform"), after.font());
        assertEquals(TextDecoration.State.TRUE, after.decoration(TextDecoration.BOLD));
    }

    @Test
    void independentResetsRoundTripAndTranslateToLegacyFormatting() {
        String styleReset = "\\3\\l\\hbold\\pplain";
        String colorReset = "\\3\\l\\hred\\qplain";

        for (String input : new String[] {styleReset, colorReset}) {
            Component parsed = FormatCodes.toComponent(input);
            String buffer = FormatCodes.toBuffer(parsed, FormatCodes.PLAIN_DEFAULTS);
            assertNotNull(buffer);
            assertTrue(FormatCodes.sameText(parsed, FormatCodes.toComponent(buffer)));
        }

        assertEquals("\u00a73\u00a7lbold\u00a7r\u00a73plain", FormatCodes.toLegacy(styleReset));
        assertEquals("\u00a73\u00a7lred\u00a7r\u00a7lplain", FormatCodes.toLegacy(colorReset));
    }
}

package sys.vitae.lorem.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class FormattingAccessTest {

    @Test
    void creativeAndOperatorsHaveAccessButOrdinarySurvivalDoesNot() {
        Player player = mock(Player.class);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        assertFalse(FormattingAccess.canFormat(player));
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertTrue(FormattingAccess.canFormat(player));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.isOp()).thenReturn(true);
        assertTrue(FormattingAccess.canFormat(player));
    }

    @Test
    void explicitPermissionOverridesBothDefaults() {
        Player player = mock(Player.class);
        when(player.isPermissionSet(FormattingAccess.FORMAT_PERMISSION)).thenReturn(true);
        when(player.hasPermission(FormattingAccess.FORMAT_PERMISSION)).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        assertTrue(FormattingAccess.canFormat(player));

        when(player.hasPermission(FormattingAccess.FORMAT_PERMISSION)).thenReturn(false);
        when(player.isOp()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertFalse(FormattingAccess.canFormat(player));
    }
}

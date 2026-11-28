package sys.vitae.lorem.listeners;

import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.any;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.NonNull;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import sys.vitae.lorem.utils.FormatCodes;
import sys.vitae.lorem.utils.FormattingAccess;

class ChatListenerTest {

    private final ChatListener listener = new ChatListener();

    @Test
    void authorizedPlayersGetEscapesTranslated() {
        AsyncChatEvent event = chat(true, "\\cRed \\lbold");
        listener.onChat(event);
        verify(event).message(FormatCodes.toComponent("\\cRed \\lbold"));
    }

    @Test
    void chatPermissionIsSeparateFromItemFormattingAccess() {
        AsyncChatEvent event = chat(false, "\\cRed");
        Player player = event.getPlayer();
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.isPermissionSet(FormattingAccess.FORMAT_PERMISSION)).thenReturn(true);
        when(player.hasPermission(FormattingAccess.FORMAT_PERMISSION)).thenReturn(true);
        listener.onChat(event);
        verify(event, never()).message(any(Component.class, Component.empty()));
    }

    @Test
    void messagesWithoutEscapesAreLeftUntouched() {
        AsyncChatEvent event = chat(true, "just \\z text");
        listener.onChat(event);
        verify(event, never()).message(any(Component.class, Component.empty()));
    }

    private static AsyncChatEvent chat(boolean permitted, @NonNull String text) {
        Player player = mock(Player.class);
        when(player.hasPermission(FormattingAccess.CHAT_PERMISSION)).thenReturn(permitted);
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.message()).thenReturn(Component.text(text));
        return event;
    }
}

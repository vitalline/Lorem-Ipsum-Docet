package sys.vitae.lorem.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import sys.vitae.lorem.utils.FormatCodes;
import sys.vitae.lorem.utils.FormattingAccess;

/** Permission-gated escape substitutions in chat messages. */
public class ChatListener implements Listener {

    /** Runs early, so chat formatting plugins listening later see the styled message. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!event.getPlayer().hasPermission(FormattingAccess.CHAT_PERMISSION)) {
            return;
        }
        String text = FormatCodes.plainText(event.message());
        if (FormatCodes.hasEscapes(text)) {
            event.message(FormatCodes.toComponent(text));
        }
    }
}
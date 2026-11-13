package sys.vitae.lorem.listeners;

import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerSignOpenEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.Plugin;

import sys.vitae.lorem.utils.FormatCodes;

/**
 * Escape sequences in signs and books. Text fields are client-side and the server
 * only receives the finished text, so (unlike the anvil) this can't be live.
 *
 * <ul>
 *   <li><b>Signs:</b> escapes are translated when the editor is closed. When a sign is (re-)edited,
 *       the editing player is shown its text as a {@link FormatCodes#toBuffer buffer} string first,
 *       so itsformatting survives the edit.</li>
 *   <li><b>Books:</b> a book and quill keeps the raw text as its buffer;
 *       the title and pages are translated when the book is signed.</li>
 * </ul>
 */
public class TextFieldListener implements Listener {

    private final Plugin plugin;

    public TextFieldListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Fired just before the editor is opened, so the client fills it with the buffer text sent here. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignOpen(PlayerSignOpenEvent event) {
        // A fresh snapshot, so the event's sign stays untouched.
        if (!(event.getSign().getBlock().getState() instanceof Sign sign)) {
            return;
        }
        SignSide side = sign.getSide(event.getSide());
        boolean changed = false;
        for (int i = 0; i < side.getLines().length; i++) {
            String line = side.getLine(i);
            String buffer = FormatCodes.toBuffer(line);
            if (!buffer.equals(line)) {
                side.setLine(i, buffer);
                changed = true;
            }
        }
        if (changed) {
            event.getPlayer().sendBlockUpdate(sign.getLocation(), sign);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        for (int i = 0; i < event.getLines().length; i++) {
            String line = event.getLine(i);
            if (FormatCodes.containsEscape(line)) {
                event.setLine(i, FormatCodes.translate(line));
            }
        }
    }

    /** Makes sure the editor ends up seeing the real text, even if the change was rejected. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && block.getState() instanceof Sign sign) {
                player.sendBlockUpdate(sign.getLocation(), sign);
            }
        });
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBookSign(PlayerEditBookEvent event) {
        if (!event.isSigning()) {
            return;
        }
        BookMeta meta = event.getNewBookMeta();
        boolean changed = false;
        if (meta.hasTitle() && FormatCodes.containsEscape(meta.getTitle())) {
            // Fails (leaving the raw title) if the translation exceeds the title length limit.
            changed = meta.setTitle(FormatCodes.translate(meta.getTitle()));
        }
        List<String> pages = meta.getPages();
        if (pages.stream().anyMatch(FormatCodes::containsEscape)) {
            meta.setPages(pages.stream().map(FormatCodes::translate).toList());
            changed = true;
        }
        if (changed) {
            event.setNewBookMeta(meta);
        }
    }
}

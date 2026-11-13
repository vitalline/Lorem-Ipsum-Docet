package sys.vitae.lorem.listeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import net.kyori.adventure.text.Component;
import sys.vitae.lorem.utils.FormatCodes;

/**
 * Escape sequences in signs and books. Text fields are client-side and the server only receives the finished
 * text, so (unlike the anvil) this can't be live.
 *
 * <ul>
 *   <li><b>Signs:</b> escapes are translated when the editor is closed. When a sign is (re-)edited, the
 *       editing player is shown its lines as {@link FormatCodes#toBuffer escaped text} first, so their formatting
 *       survives the edit (and removing a code removes its formatting). Lines that escapes can't express (e.g.
 *       with click events) are left to vanilla.</li>
 *   <li><b>Books:</b> a book and quill keeps the raw text; the pages are translated when the book is signed. The
 *       title can only hold legacy formatting, so fonts are dropped there.</li>
 * </ul>
 */
public class TextFieldListener implements Listener {

    /** A sign being edited, and which of its lines the editor was shown as escaped text. */
    private record PendingEdit(Location location, Side side, boolean[] escaped) {}

    private final Plugin plugin;
    private final Map<UUID, PendingEdit> pendingEdits = new HashMap<>();

    public TextFieldListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Fired just before the editor is opened, so the client fills it with the escaped text sent here. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignOpen(PlayerOpenSignEvent event) {
        // A fresh snapshot, so the event's sign stays untouched.
        if (!(event.getSign().getBlock().getState() instanceof Sign sign)) {
            return;
        }
        SignSide side = sign.getSide(event.getSide());
        List<Component> lines = side.lines();
        boolean[] escaped = new boolean[lines.size()];
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            String text = FormatCodes.toBuffer(lines.get(i), FormatCodes.PLAIN_DEFAULTS);
            if (text == null) {
                continue;
            }
            escaped[i] = true;
            Component shown = Component.text(text);
            if (!FormatCodes.sameText(shown, lines.get(i))) {
                side.line(i, shown);
                changed = true;
            }
        }
        Player player = event.getPlayer();
        pendingEdits.put(player.getUniqueId(), new PendingEdit(sign.getLocation(), event.getSide(), escaped));
        if (changed) {
            player.sendBlockUpdate(Objects.requireNonNull(sign.getLocation()), sign);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        PendingEdit edit = pendingEdits.get(event.getPlayer().getUniqueId());
        boolean[] escaped = edit != null && edit.side() == event.getSide()
                && edit.location().equals(event.getBlock().getLocation()) ? edit.escaped() : new boolean[0];
        for (int i = 0; i < event.lines().size(); i++) {
            String text = FormatCodes.plainText(event.line(i));
            if ((i < escaped.length && escaped[i]) || FormatCodes.hasEscapes(text)) {
                event.line(i, FormatCodes.toComponent(text));
            }
        }
    }

    /** Makes sure the editor ends up seeing the real text, even if the change was rejected. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        pendingEdits.remove(player.getUniqueId());
        Block block = event.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && block.getState() instanceof Sign sign) {
                player.sendBlockUpdate(Objects.requireNonNull(sign.getLocation()), sign);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingEdits.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBookSign(PlayerEditBookEvent event) {
        if (!event.isSigning()) {
            return;
        }
        BookMeta meta = event.getNewBookMeta();
        boolean changed = false;
        if (meta.hasTitle() && FormatCodes.hasEscapes(meta.getTitle())) {
            // Fails (leaving the raw title) if the translation exceeds the title length limit.
            changed = meta.setTitle(FormatCodes.toLegacy(meta.getTitle()));
        }
        List<Component> pages = new ArrayList<>(meta.pages());
        boolean pagesChanged = false;
        for (int i = 0; i < pages.size(); i++) {
            String text = FormatCodes.plainText(pages.get(i));
            if (FormatCodes.hasEscapes(text)) {
                pages.set(i, FormatCodes.toComponent(text));
                pagesChanged = true;
            }
        }
        if (pagesChanged) {
            meta.pages(pages);
        }
        if (changed || pagesChanged) {
            event.setNewBookMeta(meta);
        }
    }
}

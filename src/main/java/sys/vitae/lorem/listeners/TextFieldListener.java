package sys.vitae.lorem.listeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import sys.vitae.lorem.utils.FormatCodes;
import sys.vitae.lorem.utils.FormattingAccess;
import sys.vitae.lorem.utils.ItemConsumption;
import sys.vitae.lorem.utils.TextStyles;

/**
 * Permission-gated substitutions in signs and signed books, and survival-friendly sign styling.
 * The sign editor receives a client-only snapshot; the real sign is never stripped before a successful edit.
 */
public class TextFieldListener implements Listener {

    private record PendingEdit(Location location, Side side, List<Component> original, List<String> shown,
            boolean[] escaped, boolean formatting, @Nullable Style uniform) {}

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
        Player player = event.getPlayer();
        PendingEdit previous = pendingEdits.remove(player.getUniqueId());
        if (previous != null) {
            resync(player, previous.location());
        }
        boolean formatting = FormattingAccess.canFormat(player) && !player.isSneaking();
        SignSide side = sign.getSide(event.getSide());
        List<Component> lines = List.copyOf(side.lines());
        List<String> shown = new ArrayList<>();
        boolean[] escaped = new boolean[lines.size()];
        boolean changed = false;
        for (int i = 0; i < lines.size(); i++) {
            String text = formatting ? FormatCodes.toBuffer(lines.get(i), FormatCodes.PLAIN_DEFAULTS) : null;
            escaped[i] = text != null;
            if (text == null) {
                text = FormatCodes.plainText(lines.get(i));
            }
            shown.add(text);
            Component preview = Component.text(text);
            if (!FormatCodes.sameText(preview, lines.get(i))) {
                side.line(i, preview);
                changed = true;
            }
        }
        pendingEdits.put(player.getUniqueId(), new PendingEdit(sign.getLocation(), event.getSide(), lines,
                List.copyOf(shown), escaped, formatting, TextStyles.uniformStyle(lines)));
        if (changed) {
            player.sendBlockUpdate(Objects.requireNonNull(sign.getLocation()), sign);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        PendingEdit edit = pendingEdits.get(event.getPlayer().getUniqueId());
        if (edit != null && (edit.side() != event.getSide() || !edit.location().equals(event.getBlock().getLocation()))) {
            edit = null;
        }
        List<@NonNull String> submitted = event.lines().stream().map(FormatCodes::plainText).toList();
        if (edit != null && submitted.equals(edit.shown())) {
            for (int i = 0; i < edit.original().size(); i++) {
                event.line(i, edit.original().get(i));
            }
            return;
        }
        boolean formatting = FormattingAccess.canFormat(event.getPlayer())
                && (edit != null ? edit.formatting() : !event.getPlayer().isSneaking());
        Style uniform = edit == null ? null : edit.uniform();
        for (int i = 0; i < event.lines().size(); i++) {
            String text = Objects.requireNonNull(submitted.get(i));
            if (formatting && ((edit != null && edit.escaped()[i]) || FormatCodes.hasEscapes(text))) {
                event.line(i, FormatCodes.toComponent(text));
            } else {
                event.line(i, Component.text(text, !formatting && uniform != null ? uniform : Style.empty()));
            }
        }
    }

    /** Makes sure the editor ends up seeing the real text, even if the change was rejected. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        PendingEdit edit = pendingEdits.get(player.getUniqueId());
        if (edit != null && edit.location().equals(event.getBlock().getLocation()) && edit.side() == event.getSide()) {
            pendingEdits.remove(player.getUniqueId());
        }
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
        if (!event.isSigning() || !FormattingAccess.canFormat(event.getPlayer())) {
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStyleSign(PlayerInteractEvent event) {
        ItemStack ingredient = event.getItem();
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null || ingredient == null
                || !TextStyles.isIngredient(ingredient) || block == null
                || event.useInteractedBlock() == Result.DENY || event.useItemInHand() == Result.DENY
                || (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.CREATIVE)
                || !(block.getState() instanceof Sign sign) || sign.isWaxed()
                || sign.getAllowedEditorUniqueId() != null) {
            return;
        }
        SignSide side = sign.getTargetSide(player);
        List<Component> original = List.copyOf(side.lines());
        if (original.stream().allMatch(line -> FormatCodes.plainText(line).isEmpty())) {
            return;
        }
        var dye = TextStyles.dye(ingredient.getType());
        List<@NonNull Component> styled = dye == null ? TextStyles.apply(original, ingredient, false)
                : TextStyles.color(original, null);
        boolean changed = !styled.equals(original) || (dye != null && side.getColor() != dye);
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        if (!changed) {
            return;
        }
        for (int i = 0; i < styled.size(); i++) {
            side.line(i, Objects.requireNonNull(styled.get(i)));
        }
        if (dye != null) {
            side.setColor(dye);
        }
        if (!sign.update()) {
            plugin.getLogger().warning("Could not update styled sign at " + sign.getLocation());
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItem(event.getHand(), ItemConsumption.consumeOne(ingredient, player));
        }
        player.getWorld().playSound(sign.getLocation(), Sound.ITEM_DYE_USE, 1, 1);
    }

    private static void resync(Player player, Location location) {
        if (location.getBlock().getState() instanceof Sign sign) {
            player.sendBlockUpdate(Objects.requireNonNull(location), sign);
        }
    }
}

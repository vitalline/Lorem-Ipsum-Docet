package sys.vitae.lorem.listeners;

import java.util.List;
import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import sys.vitae.lorem.utils.FormatCodes;
import sys.vitae.lorem.utils.LoreTemplates;

/**
 * Anvil handling.
 * The anvil re-computes its output (and fires {@link PrepareAnvilEvent}) every time the rename text changes,
 * so the output slot updates in real time.
 *
 * <p><b>Renaming:</b> escape sequences in the rename text are translated (see {@link FormatCodes}).
 * A rename consisting solely of formatting codes replaces the leading formatting codes of the item's existing custom name.
 * Items without a custom name aren't renamed at all in that case, just like renaming an item to its current name in vanilla.
 *
 * <p><b>Lore:</b> a lore template in the second slot copies its lore onto the item; a blank sheet clears the item's lore.
 * Either can be combined with renaming, and uses up one sheet. It's priced like a rename.
 */
public class AnvilListener implements Listener {

    private static final int RESULT_SLOT = 2;

    private final Plugin plugin;

    public AnvilListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack input = event.getInventory().getItem(0);
        if (input == null || input.getType().isAir()) {
            return;
        }
        ItemStack addition = event.getInventory().getItem(1);
        if (LoreTemplates.isTemplate(addition) || LoreTemplates.isBlankSheet(addition)) {
            applyLore(event, input, addition);
        } else {
            translateRename(event, input);
        }
    }

    /** Fixes up the name of vanilla's output. */
    private static void translateRename(PrepareAnvilEvent event, ItemStack input) {
        ItemStack result = event.getResult();
        String renameText = event.getView().getRenameText();
        if (result == null || result.getType().isAir() || !FormatCodes.hasEscapes(renameText)
                || !isRenamedTo(result, input, renameText)) {
            return;
        }

        Component oldName = customName(input);
        Component name = translateName(renameText, oldName);
        if (FormatCodes.sameText(name, oldName)) {
            cancelRename(event, result, input);
            return;
        }

        ItemStack out = result.clone();
        ItemMeta meta = out.getItemMeta();
        meta.customName(name);
        out.setItemMeta(meta);
        event.setResult(out);
    }

    /** Vanilla has no output for these ingredients, so the whole result (and its price) is built here. */
    private void applyLore(PrepareAnvilEvent event, ItemStack input, ItemStack sheet) {
        ItemMeta meta = input.getItemMeta();
        if (meta == null) {
            return;
        }
        List<Component> lore = LoreTemplates.isTemplate(sheet) ? sheet.getItemMeta().lore() : null;
        if (Objects.equals(meta.hasLore() ? meta.lore() : null, lore)) {
            return; // nothing to apply; leave vanilla's empty output
        }

        AnvilView view = event.getView();
        Component oldName = meta.hasCustomName() ? meta.customName() : null;
        Component name = resolveName(view.getRenameText(), oldName);
        boolean renamed = !FormatCodes.sameText(name, oldName);
        if (renamed) {
            meta.customName(name);
        }
        meta.lore(lore);
        ItemStack out = input.clone();
        out.setItemMeta(meta);

        // Like a rename: 1 level per change on top of the prior-work penalty, which isn't increased, and never "Too Expensive!".
        int cost = Math.max(1, Math.min(priorWork(input) + priorWork(sheet) + 1 + (renamed ? 1 : 0),
                view.getMaximumRepairCost() - 1));
        event.setResult(out);
        view.setRepairCost(cost);
        view.setRepairItemCountCost(1);
        // For an ingredient it doesn't accept, the server marks the output as denied (cost -1, so it can't be taken)
        // right after this event; re-apply the price once that's done.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (out.equals(view.getTopInventory().getItem(RESULT_SLOT))) {
                view.setRepairCost(cost);
                view.setRepairItemCountCost(1);
            }
        });
    }

    /** The custom name vanilla's rename rules give the output (null for none), with escapes translated. */
    private static Component resolveName(String renameText, Component oldName) {
        if (renameText == null || renameText.isBlank()) {
            return null;
        }
        // The rename field shows the current name without formatting; leaving it as-is keeps the name.
        if (oldName != null && renameText.equals(FormatCodes.plainText(oldName))) {
            return oldName;
        }
        return FormatCodes.hasEscapes(renameText) ? translateName(renameText, oldName) : Component.text(renameText);
    }

    /** Translates the rename text; returns {@code oldName} if that wouldn't rename the item. */
    private static Component translateName(String renameText, Component oldName) {
        if (!FormatCodes.isOnlyFormatting(renameText)) {
            return FormatCodes.toComponent(renameText);
        }
        String oldText = oldName == null ? null : FormatCodes.toBuffer(oldName, FormatCodes.NAME_DEFAULTS);
        if (oldText == null) {
            return oldName; // no custom name, or one that can't be restyled with codes
        }
        return FormatCodes.toComponent(renameText + FormatCodes.stripLeadingCodes(oldText));
    }

    /**
     * Vanilla only renames when the text differs from the item's current name; in that case the output's custom name
     * is exactly the literal rename text. Guards against re-translating an untouched name that merely contains a
     * literal {@code \} (e.g. while repairing such an item).
     */
    private static boolean isRenamedTo(ItemStack result, ItemStack input, String renameText) {
        Component resultName = customName(result);
        return resultName != null && FormatCodes.plainText(resultName).equals(renameText)
                && !resultName.equals(customName(input));
    }

    /**
     * Undoes vanilla's rename: restores the input's name and refunds the 1 level the rename cost.
     * If nothing else changed (no repair/enchant), the anvil shows no output, as in vanilla.
     */
    private static void cancelRename(PrepareAnvilEvent event, ItemStack result, ItemStack input) {
        ItemStack out = result.clone();
        ItemMeta meta = out.getItemMeta();
        meta.customName(customName(input));
        out.setItemMeta(meta);

        if (isUnchanged(out, input)) {
            event.setResult(null);
            return;
        }
        AnvilView view = event.getView();
        view.setRepairCost(Math.max(1, view.getRepairCost() - 1));
        event.setResult(out);
    }

    /** Compares the items, ignoring the prior-work penalty the anvil adds to every output. */
    private static boolean isUnchanged(ItemStack out, ItemStack input) {
        ItemStack normalized = out.clone();
        if (normalized.getItemMeta() instanceof Repairable repairable
                && input.getItemMeta() instanceof Repairable original) {
            repairable.setRepairCost(original.getRepairCost());
            normalized.setItemMeta(repairable);
        }
        return normalized.isSimilar(input);
    }

    private static Component customName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomName() ? meta.customName() : null;
    }

    private static int priorWork(ItemStack item) {
        return item.getItemMeta() instanceof Repairable repairable ? repairable.getRepairCost() : 0;
    }
}

package sys.vitae.lorem.listeners;

import java.util.List;
import java.util.Objects;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;

import sys.vitae.lorem.utils.FormatCodes;
import sys.vitae.lorem.utils.LoreTemplates;

/**
 * Anvil handling. The anvil re-computes its output (and fires {@link PrepareAnvilEvent})
 * every time the rename text changes, so the output slot updates in real time.
 *
 * <p><b>Renaming:</b> escape sequences in the rename text are translated (see {@link FormatCodes}).
 * A rename consisting solely of formatting codes replaces the leading formatting codes of the item's
 * existing custom name. Items without a custom name aren't renamed at all in that case(the default name
 * can't be expressed as a legacy string), just like renaming an item to its current name in vanilla.
 *
 * <p><b>Lore:</b> a lore template in the second slot copies its lore onto the item; a blank sheet clears
 * the item's lore. Either can be combined with renaming, and uses up one sheet. It's priced like a rename.
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
        if (result == null || result.getType().isAir() || !FormatCodes.containsEscape(renameText)
                || !isRenamedTo(result, input, renameText)) {
            return;
        }

        ItemMeta inputMeta = input.getItemMeta();
        String oldName = inputMeta != null && inputMeta.hasDisplayName() ? inputMeta.getDisplayName() : null;
        String name = translateName(renameText, oldName);
        if (Objects.equals(name, oldName)) {
            cancelRename(event, result, input);
            return;
        }

        ItemStack out = result.clone();
        ItemMeta meta = out.getItemMeta();
        meta.setDisplayName(name);
        out.setItemMeta(meta);
        event.setResult(out);
    }

    /** Vanilla has no output for these ingredients, so the whole result (and its price) is built here. */
    private void applyLore(PrepareAnvilEvent event, ItemStack input, ItemStack sheet) {
        ItemMeta meta = input.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = LoreTemplates.isTemplate(sheet) ? sheet.getItemMeta().getLore() : null;
        if (Objects.equals(meta.hasLore() ? meta.getLore() : null, lore)) {
            return; // nothing to apply; leave vanilla's empty output
        }

        AnvilView view = event.getView();
        String oldName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        String name = resolveName(view.getRenameText(), oldName);
        boolean renamed = !Objects.equals(name, oldName);
        if (renamed) {
            meta.setDisplayName(name);
        }
        meta.setLore(lore);
        ItemStack out = input.clone();
        out.setItemMeta(meta);

        // Like a rename: 1 level per change on top of the prior-work penalty, which isn't increased,
        // and never "Too Expensive!".
        int cost = Math.max(1, Math.min(priorWork(input) + priorWork(sheet) + 1 + (renamed ? 1 : 0),
                view.getMaximumRepairCost() - 1));
        event.setResult(out);
        view.setRepairCost(cost);
        view.setRepairItemCountCost(1);
        // For an ingredient it doesn't accept, CraftBukkit marks the output as denied
        // (cost -1, so it can't be taken) right after this event; re-apply the price once that's done.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (out.equals(view.getTopInventory().getItem(RESULT_SLOT))) {
                view.setRepairCost(cost);
                view.setRepairItemCountCost(1);
            }
        });
    }

    /** The custom name vanilla's rename rules give the output (null for none), with escapes translated. */
    private static String resolveName(String renameText, String oldName) {
        if (renameText == null || renameText.isBlank()) {
            return null;
        }
        // The rename field shows the current name without formatting; leaving it as-is keeps the name.
        if (oldName != null && renameText.equals(ChatColor.stripColor(oldName))) {
            return oldName;
        }
        return FormatCodes.containsEscape(renameText) ? translateName(renameText, oldName) : renameText;
    }

    /** Translates the rename text; returns {@code oldName} if that wouldn't rename the item. */
    private static String translateName(String renameText, String oldName) {
        String name = FormatCodes.translate(renameText);
        if (FormatCodes.isOnlyFormatting(name)) {
            return oldName == null ? null : name + FormatCodes.stripLeadingCodes(oldName);
        }
        return name;
    }

    /**
     * Vanilla only renames when the text differs from the item's current name; in that case
     * the output's custom name is exactly the literal rename text. Guards against re-translating
     * an untouched name that merely contains a literal {@code \} (e.g. while repairing such an item).
     */
    private static boolean isRenamedTo(ItemStack result, ItemStack input, String renameText) {
        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null || !resultMeta.hasDisplayName() || !resultMeta.getDisplayName().equals(renameText)) {
            return false;
        }
        ItemMeta inputMeta = input.getItemMeta();
        return inputMeta == null || !inputMeta.hasDisplayName() || !inputMeta.getDisplayName().equals(renameText);
    }

    /**
     * Undoes vanilla's rename: restores the input's name and refunds the 1 level the rename cost.
     * If nothing else changed (no repair/enchant), the anvil shows no output, as in vanilla.
     */
    private static void cancelRename(PrepareAnvilEvent event, ItemStack result, ItemStack input) {
        ItemStack out = result.clone();
        ItemMeta meta = out.getItemMeta();
        ItemMeta inputMeta = input.getItemMeta();
        meta.setDisplayName(inputMeta != null && inputMeta.hasDisplayName() ? inputMeta.getDisplayName() : null);
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

    private static int priorWork(ItemStack item) {
        return item.getItemMeta() instanceof Repairable repairable ? repairable.getRepairCost() : 0;
    }
}

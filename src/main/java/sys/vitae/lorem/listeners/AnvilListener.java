package sys.vitae.lorem.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import sys.vitae.lorem.utils.FormatCodes;
import sys.vitae.lorem.utils.FormattingAccess;
import sys.vitae.lorem.utils.NameTags;
import sys.vitae.lorem.utils.Templates;
import sys.vitae.lorem.utils.Templates.Kind;
import sys.vitae.lorem.utils.Templates.Type;
import sys.vitae.lorem.utils.Templates.Variant;
import sys.vitae.lorem.utils.TextStyles;

/**
 * Anvil handling.
 * The anvil re-computes its output (and fires {@link PrepareAnvilEvent}) every time the rename text changes,
 * so the output slot updates in real time.
 *
 * <p><b>Renaming:</b> escape sequences in the rename text are translated (see {@link FormatCodes}).
 * A rename consisting solely of formatting codes replaces the leading formatting codes of the item's existing custom name.
 * Items without a custom name aren't renamed at all in that case, just like renaming an item to its current name in vanilla.
 * Plain rename text keeps the style of a uniformly formatted name.
 * Name tags get a marker line while their name is formatted (see {@link NameTags}), and a renamed name-resetting or
 * name-copying tag becomes an ordinary name tag.
 * Renaming a Name or Description template changes the name or whole description it applies instead, by the same
 * rules.
 *
 * <p>Typed templates in the second slot apply just their property, consuming one sheet. Color templates are crafted
 * onto items instead (see {@link TemplateCraftListener}).
 * Other than a Name template, these can be combined with renaming. They are priced like a rename.
 */
public class AnvilListener implements Listener {

    private static final int RESULT_SLOT = 2;

    private final Plugin plugin;
    private final Templates templates;

    public AnvilListener(Plugin plugin, Templates templates) {
        this.plugin = plugin;
        this.templates = templates;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack input = event.getInventory().getItem(0);
        if (input == null || input.isEmpty()) {
            return;
        }
        ItemStack addition = event.getInventory().getItem(1);
        boolean formatting = event.getView().getPlayer() instanceof Player player && FormattingAccess.canFormat(player);
        Kind kind = templates.kind(addition);
        if (kind != null) {
            applyTemplate(event, input, addition, kind, formatting);
        } else if (isTextTemplate(input)) {
            renameTemplate(event, input, formatting);
        } else {
            fixRename(event, input, formatting);
            markNameTag(event);
        }
    }

    /**
     * Keeps the formatting marker of a renamed name tag in sync, also when the rename wasn't translated, and turns a
     * renamed name-resetting or name-copying tag into an ordinary one.
     */
    private void markNameTag(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.isEmpty()) {
            return;
        }
        ItemStack marked = templates.asOrdinaryNameTag(NameTags.withMarker(result));
        if (marked != result) {
            event.setResult(marked);
        }
    }

    /**
     * Fixes up the name of vanilla's output: translates escapes, or keeps the style of the old name for plain text
     * (see {@link #plainName}).
     */
    private static void fixRename(PrepareAnvilEvent event, ItemStack input, boolean formatting) {
        ItemStack result = event.getResult();
        String renameText = event.getView().getRenameText();
        if (result == null || result.isEmpty() || !isRenamedTo(result, input, renameText)) {
            return;
        }

        Component oldName = customName(input);
        Component name;
        if (formatting && FormatCodes.hasEscapes(renameText)) {
            name = translateName(renameText, oldName);
            if (FormatCodes.sameText(name, oldName)) {
                cancelRename(event, result, input);
                return;
            }
        } else {
            name = plainName(renameText, oldName);
            if (name.equals(customName(result))) {
                return;
            }
        }

        ItemStack out = result.clone();
        ItemMeta meta = out.getItemMeta();
        meta.customName(name);
        out.setItemMeta(meta);
        event.setResult(out);
    }

    private boolean isTextTemplate(ItemStack item) {
        Kind kind = templates.kind(item);
        return kind != null && kind.variant() == Variant.PLAIN && kind.type().hasText();
    }

    /**
     * Rewrites the name or description a renamed Name or Description template applies, instead of naming the sheet.
     * Vanilla's output is the base, so the rename keeps its price; one with a name from before templates could be
     * renamed loses it.
     */
    private void renameTemplate(PrepareAnvilEvent event, ItemStack template, boolean formatting) {
        ItemStack result = event.getResult();
        if (result == null || result.isEmpty() || customName(result) == null) {
            return; // not renamed, or vanilla removing an old name
        }
        List<@NonNull Component> text = templateText(event.getView().getRenameText(), template, formatting);
        if (text == null) {
            return;
        }
        ItemStack unnamed = result.clone();
        ItemMeta meta = unnamed.getItemMeta();
        meta.customName(null);
        unnamed.setItemMeta(meta);
        ItemStack out = templates.retext(unnamed, text);
        event.setResult(isUnchanged(out, template) ? null : out);
    }

    /**
     * The template's new text, following the rules for renaming an item, with all of a description taken as its
     * name: plain text keeps a style shared by all of it, and a formatting-only rename restyles each line. Null if
     * the field doesn't rename it. The field shows the template's label, which the client sends as blank while it's
     * unchanged.
     */
    private @Nullable List<@NonNull Component> templateText(String renameText, ItemStack template, boolean formatting) {
        Component oldName = customName(template);
        if (renameText == null || renameText.isBlank()
                || oldName != null && renameText.equals(FormatCodes.plainText(oldName))) {
            return null;
        }
        List<@NonNull Component> old = templates.text(template);
        if (!formatting || !FormatCodes.hasEscapes(renameText)) {
            return List.of(plainName(renameText, Component.text().append(old).build()));
        }
        if (!FormatCodes.isOnlyFormatting(renameText)) {
            return List.of(FormatCodes.toComponent(renameText));
        }
        List<@NonNull Component> restyled = new ArrayList<>();
        for (Component line : old) {
            String escaped = FormatCodes.toBuffer(line, FormatCodes.NAME_DEFAULTS);
            if (escaped == null) {
                return old; // a line that can't be restyled with codes
            }
            restyled.add(FormatCodes.toComponent(renameText + FormatCodes.stripLeadingCodes(escaped)));
        }
        return restyled;
    }

    private static boolean sameText(List<? extends Component> a, List<? extends Component> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!FormatCodes.sameText(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Vanilla has no output for these ingredients, so the whole result (and its price) is built here. */
    private void applyTemplate(PrepareAnvilEvent event, ItemStack input, ItemStack sheet, Kind kind, boolean formatting) {
        ItemStack applied = templates.apply(input, sheet);
        if (applied == null) {
            return;
        }
        AnvilView view = event.getView();
        boolean renamed;
        if (isTextTemplate(input)) {
            List<@NonNull Component> text = kind.type() == Type.NAME ? null : templateText(view.getRenameText(), input, formatting);
            renamed = text != null && !sameText(text, templates.text(input));
            if (renamed) {
                applied = templates.retext(applied, text);
            }
        } else {
            Component oldName = customName(input);
            Component name = resolveName(view.getRenameText(), oldName, formatting);
            renamed = kind.type() != Type.NAME && !FormatCodes.sameText(name, oldName);
            if (renamed) {
                ItemMeta meta = applied.getItemMeta();
                meta.customName(name);
                applied.setItemMeta(meta);
            }
        }
        ItemStack out = templates.asOrdinaryNameTag(NameTags.withMarker(applied));
        if (out.isSimilar(input)) {
            return; // e.g. a Lore reset on a name tag whose only lore is the marker
        }

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
    private static Component resolveName(String renameText, Component oldName, boolean formatting) {
        if (renameText == null || renameText.isBlank()) {
            return null;
        }
        // The rename field shows the current name without formatting; leaving it as-is keeps the name.
        if (oldName != null && renameText.equals(FormatCodes.plainText(oldName))) {
            return oldName;
        }
        return formatting && FormatCodes.hasEscapes(renameText) ? translateName(renameText, oldName) : plainName(renameText, oldName);
    }

    /**
     * Plain rename text in the style the whole old name shared, as the rename field can only show plain text.
     * Mixed formatting is dropped, as in vanilla.
     */
    private static @NonNull Component plainName(String renameText, Component oldName) {
        Style style = oldName == null ? null : TextStyles.sharedStyle(oldName);
        return Component.text(Objects.requireNonNull(renameText), style == null ? Style.empty() : style);
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

package sys.vitae.lorem.utils;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;

/**
 * Sheets of paper come in three kinds: <em>blank</em> (plain paper), <em>renamed</em>
 * (a custom name but no lore), and <em>lore templates</em> (any paper carrying lore).
 */
public final class LoreTemplates {

    /**
     * "Template: Description", built from vanilla strings so it's translated client-side:
     * the options screen's "%s: %s" format, and Realms' "Template" and "Description".
     */
    private static final Component NAME = Component.translatable("options.generic_value", "%s: %s",
            Component.translatable("mco.template.name", "Template"),
            Component.translatable("mco.backup.entry.description", "Description"));

    private LoreTemplates() {}

    public static boolean isTemplate(ItemStack item) {
        return isPaper(item) && item.getItemMeta().hasLore();
    }

    /** Paper without lore, renamed or not. */
    public static boolean isSheet(ItemStack item) {
        return isPaper(item) && !item.getItemMeta().hasLore();
    }

    public static boolean isRenamedSheet(ItemStack item) {
        return isSheet(item) && item.getItemMeta().hasCustomName();
    }

    public static boolean isBlankSheet(ItemStack item) {
        return isSheet(item) && !item.getItemMeta().hasCustomName();
    }

    /**
     * A new lore template carrying the given lines.
     * The name is an item name rather than a custom name, so it isn't italic and the template doesn't count as renamed.
     */
    public static ItemStack create(List<Component> lore) {
        ItemStack template = new ItemStack(Material.PAPER);
        ItemMeta meta = template.getItemMeta();
        meta.itemName(NAME);
        meta.lore(lore);
        template.setItemMeta(meta);
        return template;
    }

    private static boolean isPaper(ItemStack item) {
        return item != null && item.getType() == Material.PAPER && item.getAmount() > 0;
    }
}

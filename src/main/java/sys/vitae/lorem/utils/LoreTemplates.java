package sys.vitae.lorem.utils;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Sheets of paper come in three kinds: <em>blank</em> (plain paper),
 * <em>renamed</em> (a custom name but no lore),
 * and <em>lore templates</em> (any paper carrying lore).
 */
public final class LoreTemplates {

    /**
     * "Template: Description", built from vanilla strings so it's translated client-side:
     * the options screen's "%s: %s" format, and Realms' "Template" and "Description".
     */
    private static final String SNBT_NAME = (
            "{translate:\"options.generic_value\",fallback:\"%s: %s\",with:[" +
            "{translate:\"mco.template.name\",fallback:\"Template\"}," +
            "{translate:\"mco.backup.entry.description\",fallback:\"Description\"}]}"
    );
    private static final String JSON_NAME = (
            "{\"translate\":\"options.generic_value\",\"fallback\":\"%s: %s\"," +
            "\"with\":[{\"translate\":\"mco.template.name\",\"fallback\":\"Template\"}," +
            "{\"translate\":\"mco.backup.entry.description\",\"fallback\":\"Description\"}]}"
    );
    private static final String FALLBACK_NAME = "Template: Description";

    private static ItemStack base;

    private LoreTemplates() {}

    public static boolean isTemplate(ItemStack item) {
        return isPaper(item) && item.getItemMeta().hasLore();
    }

    /** Paper without lore, renamed or not. */
    public static boolean isSheet(ItemStack item) {
        return isPaper(item) && !item.getItemMeta().hasLore();
    }

    public static boolean isRenamedSheet(ItemStack item) {
        return isSheet(item) && item.getItemMeta().hasDisplayName();
    }

    public static boolean isBlankSheet(ItemStack item) {
        return isSheet(item) && !item.getItemMeta().hasDisplayName();
    }

    /** A new lore template carrying the given lines. */
    public static ItemStack create(List<String> lore) {
        ItemStack template = base().clone();
        ItemMeta meta = template.getItemMeta();
        meta.setLore(lore);
        template.setItemMeta(meta);
        return template;
    }

    private static boolean isPaper(ItemStack item) {
        return item != null && item.getType() == Material.PAPER && item.getAmount() > 0;
    }

    private static ItemStack base() {
        if (base == null) {
            base = createBase();
        }
        return base;
    }

    /**
     * Paper with a translatable item name (not a custom name, so it isn't italic and doesn't count as renamed).
     * The API only takes legacy strings, so the name is set through the item syntax.
     */
    private static ItemStack createBase() {
        String[] specs = {
            "minecraft:paper[minecraft:item_name=" + SNBT_NAME + "]", // SNBT text components (1.21.5+)
            "minecraft:paper[minecraft:item_name='" + JSON_NAME + "']", // JSON text components (1.20.5 - 1.21.4)
        };
        for (String spec : specs) {
            try {
                return Bukkit.getItemFactory().createItemStack(spec);
            } catch (IllegalArgumentException ignored) {
                // syntax not supported by this server version; try the next one
            }
        }
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.setItemName(FALLBACK_NAME);
        paper.setItemMeta(meta);
        return paper;
    }
}

package sys.vitae.lorem.utils;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.kyori.adventure.text.Component;

/**
 * Name tags get the {@link TextStyles#legend} of their name after their other lore. Item tooltips show unstyled custom
 * names in italics, so a name with explicit default formatting looks the same there, yet renders differently on a mob.
 */
public final class NameTags {

    private NameTags() {}

    /** An ordinary name tag with the name, followed by its legend. */
    public static @NonNull ItemStack named(Component name) {
        ItemStack tag = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = tag.getItemMeta();
        meta.customName(name);
        List<Component> legend = TextStyles.legend(List.of(name));
        if (!legend.isEmpty()) {
            meta.lore(legend);
        }
        tag.setItemMeta(meta);
        return tag;
    }

    /** Sets a name tag's lore, followed by the legend of its name. */
    public static void setLore(ItemMeta meta, @Nullable List<? extends Component> lore) {
        List<Component> marked = TextStyles.marked(lore == null ? List.of() : lore, name(meta.customName()));
        meta.lore(marked.isEmpty() ? null : marked);
    }

    /** A name tag's lore without the legend of its name. */
    public static @NonNull List<Component> lore(ItemMeta meta) {
        List<Component> lore = meta.lore();
        return TextStyles.unmarked(lore == null ? List.of() : lore, name(meta.customName()));
    }

    /**
     * The item with its legend matching its name, replacing that of the name it had before; the item itself if
     * that's already so or it isn't a name tag.
     */
    public static ItemStack withLegend(ItemStack item, @Nullable Component oldName) {
        if (item.getType() != Material.NAME_TAG) {
            return item;
        }
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return item;
        }
        List<Component> lore = meta.lore();
        List<Component> current = lore == null ? List.of() : lore;
        List<Component> updated = TextStyles.marked(TextStyles.unmarked(current, name(oldName)),
                name(meta.customName()));
        if (updated.equals(current)) {
            return item;
        }
        meta.lore(updated.isEmpty() ? null : updated);
        result.setItemMeta(meta);
        return result;
    }

    private static List<Component> name(@Nullable Component name) {
        return name == null ? List.of() : List.of(name);
    }
}
package sys.vitae.lorem.utils;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.kyori.adventure.text.Component;

/**
 * Name tags with a formatted name get the {@link TextStyles#CUSTOM_MARKER} after their other lore. Item tooltips show
 * unstyled custom names in italics, so a name with explicit default formatting looks the same there, yet renders
 * differently on a mob.
 */
public final class NameTags {

    private NameTags() {}

    /** An ordinary name tag with the name, marked if it's formatted. */
    public static @NonNull ItemStack named(Component name) {
        ItemStack tag = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = tag.getItemMeta();
        meta.customName(name);
        if (TextStyles.isFormatted(name)) {
            meta.lore(List.of(TextStyles.CUSTOM_MARKER));
        }
        tag.setItemMeta(meta);
        return tag;
    }

    /** Sets a name tag's lore, followed by the marker if its name is formatted. */
    public static void setLore(ItemMeta meta, @Nullable List<? extends Component> lore) {
        List<Component> marked = marked(meta, lore == null ? List.of() : lore);
        meta.lore(marked.isEmpty() ? null : marked);
    }

    /** The item with its marker matching its name; the item itself if that's already so or it isn't a name tag. */
    public static ItemStack withMarker(ItemStack item) {
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
        List<Component> updated = marked(meta, TextStyles.unmarked(current));
        if (updated.equals(current)) {
            return item;
        }
        meta.lore(updated.isEmpty() ? null : updated);
        result.setItemMeta(meta);
        return result;
    }

    private static @NonNull List<Component> marked(ItemMeta meta, List<? extends Component> lore) {
        Component name = meta.customName();
        return TextStyles.marked(lore, name != null && TextStyles.isFormatted(name));
    }
}
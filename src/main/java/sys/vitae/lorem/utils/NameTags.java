package sys.vitae.lorem.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Name tags with a formatted name get a marker lore line. Item tooltips show unstyled custom names in italics,
 * so a name with explicit default formatting looks the same there, yet renders differently on a mob.
 */
public final class NameTags {

    /** Vanilla's "Custom", styled like vanilla's own tooltip lines. */
    public static final @NonNull Component MARKER = Objects.requireNonNull(
            Component.translatable("generator.custom", "Custom")
                    .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

    private NameTags() {}

    /** An ordinary name tag with the name, marked if it's formatted. */
    public static @NonNull ItemStack named(Component name) {
        ItemStack tag = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = tag.getItemMeta();
        meta.customName(name);
        syncMarker(meta, name);
        tag.setItemMeta(meta);
        return tag;
    }

    /** The item with its marker matching its name; the item itself if that's already so or it isn't a name tag. */
    public static ItemStack withMarker(ItemStack item) {
        if (item.getType() != Material.NAME_TAG) {
            return item;
        }
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null || !syncMarker(meta, meta.customName())) {
            return item;
        }
        result.setItemMeta(meta);
        return result;
    }

    /** Adds or removes the marker (after any other lore) to match the name; returns whether the lore changed. */
    private static boolean syncMarker(ItemMeta meta, @Nullable Component name) {
        List<Component> lore = meta.lore();
        List<Component> updated = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        updated.removeIf(MARKER::equals);
        if (name != null && TextStyles.isFormatted(name)) {
            updated.add(MARKER);
        }
        if (updated.equals(lore == null ? List.of() : lore)) {
            return false;
        }
        meta.lore(updated.isEmpty() ? null : updated);
        return true;
    }
}

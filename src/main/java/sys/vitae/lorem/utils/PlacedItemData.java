package sys.vitae.lorem.utils;

import java.util.List;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

/**
 * An item's lore and glint override, kept on the block entity it placed. Vanilla keeps a placed container's name for
 * its drop, but its loot table drops these.
 */
public final class PlacedItemData {

    private static final @NonNull GsonComponentSerializer JSON = Objects.requireNonNull(GsonComponentSerializer.gson());
    private static final @NonNull PersistentDataType<?, List<String>> LORE_TYPE =
            Objects.requireNonNull(PersistentDataType.LIST.strings());
    private static final @NonNull PersistentDataType<Byte, Boolean> GLINT_TYPE =
            Objects.requireNonNull(PersistentDataType.BOOLEAN);

    private final @NonNull NamespacedKey loreKey;
    private final @NonNull NamespacedKey glintKey;

    public PlacedItemData(Plugin plugin) {
        loreKey = new NamespacedKey(plugin, "placed_lore");
        glintKey = new NamespacedKey(plugin, "placed_glint");
    }

    /** The properties the drop of a block entity placed from an item loses, as stored on the block. */
    public record Preserved(@NonNull Material item, @Nullable List<Component> lore, @Nullable Boolean glint) {

        /** Whether the stack is the block's drop, still missing all of these properties. */
        public boolean isMissingFrom(ItemStack drop) {
            ItemMeta meta = drop.getItemMeta();
            return drop.getType() == item && meta != null && (lore == null || !meta.hasLore())
                    && (glint == null || !meta.hasEnchantmentGlintOverride());
        }

        /** Restores these properties onto the stack, keeping any it has gained since. */
        public ItemStack restoreOnto(ItemStack drop) {
            ItemMeta meta = drop.getItemMeta();
            if (lore != null && !meta.hasLore()) {
                meta.lore(lore);
            }
            if (glint != null && !meta.hasEnchantmentGlintOverride()) {
                meta.setEnchantmentGlintOverride(glint);
            }
            drop.setItemMeta(meta);
            return drop;
        }
    }

    /** Stores the item's lore and glint override in a block's data; returns whether it had either. */
    public boolean store(ItemStack item, PersistentDataContainer data) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        boolean stored = false;
        List<Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            data.set(loreKey, LORE_TYPE, lore.stream().<String>map(JSON::serialize).toList());
            stored = true;
        }
        if (meta.hasEnchantmentGlintOverride()) {
            data.set(glintKey, GLINT_TYPE, meta.getEnchantmentGlintOverride());
            stored = true;
        }
        return stored;
    }

    /** What the block's drop would lose, or null if it was placed without lore or a glint override. */
    public @Nullable Preserved read(BlockState state) {
        if (!(state instanceof TileState tile)) {
            return null;
        }
        PersistentDataContainer data = tile.getPersistentDataContainer();
        List<String> lore = data.get(loreKey, LORE_TYPE);
        Boolean glint = data.get(glintKey, GLINT_TYPE);
        if (lore == null && glint == null) {
            return null;
        }
        return new Preserved(Objects.requireNonNull(state.getBlockData().getPlacementMaterial()),
                lore == null ? null : lore.stream().<Component>map(JSON::deserialize).toList(), glint);
    }
}

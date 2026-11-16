package sys.vitae.lorem.utils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;

public final class Templates {

    public enum Type {
        NAME("mco.backup.entry.name", "Name"),
        LORE("mco.backup.entry.description", "Description"),
        GLINT("effect.minecraft.glowing", "Glowing"),
        COLOR("options.chat.color", "Colors");

        private final @NonNull Component label;

        Type(@NonNull String key, @NonNull String fallback) {
            label = Component.translatable(key, fallback);
        }

        public boolean hasText() {
            return this == NAME || this == LORE;
        }
    }

    public record Kind(Type type, boolean reset) {
        public String id() {
            return (reset ? "reset_" : "") + type.name().toLowerCase(Locale.ROOT);
        }
    }

    /** Custom names are white unless styled; lore defaults to purple. Both are italic unless styled. */
    private static final @NonNull Style PREVIEW = Style.style(NamedTextColor.WHITE);
    private static final @NonNull PersistentDataType<String, String> STRING_TYPE =
            Objects.requireNonNull(PersistentDataType.STRING);
    private static final @NonNull PersistentDataType<int[], int[]> COLOR_RECIPE_TYPE =
            Objects.requireNonNull(PersistentDataType.INTEGER_ARRAY);

    private final @NonNull NamespacedKey idKey;
    private final @NonNull NamespacedKey colorKey;

    public Templates(Plugin plugin) {
        idKey = new NamespacedKey(plugin, "template_id");
        colorKey = new NamespacedKey(plugin, "color_recipe");
    }

    public @Nullable Kind kind(@Nullable ItemStack item) {
        if (item == null || !isPaper(item)) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(idKey, STRING_TYPE);
        for (Type type : Type.values()) {
            for (boolean reset : new boolean[] {false, true}) {
                Kind kind = new Kind(type, reset);
                if (kind.id().equals(id)) {
                    return kind;
                }
            }
        }
        return null;
    }

    public boolean isSheet(@Nullable ItemStack item) {
        return item != null && isPaper(item) && !item.getItemMeta().getPersistentDataContainer().has(idKey)
                && !item.getItemMeta().hasLore();
    }

    public boolean isRenamedSheet(@Nullable ItemStack item) {
        return item != null && isSheet(item) && item.getItemMeta().hasCustomName();
    }

    public boolean isBlankSheet(@Nullable ItemStack item) {
        return item != null && isSheet(item) && !item.getItemMeta().hasCustomName();
    }

    public @NonNull ItemStack create(Type type, List<? extends Component> text) {
        return withText(create(new Kind(type, false)), text);
    }

    public @NonNull ItemStack create(Kind kind) {
        ItemStack template = new ItemStack(Material.PAPER);
        ItemMeta meta = template.getItemMeta();
        Component label = kind.type().label;
        if (kind.reset()) {
            label = label.append(Component.text(" - ")).append(Component.translatable("controls.reset", "Reset"));
        }
        meta.itemName(Component.translatable("options.generic_value", "%s: %s",
                Component.translatable("mco.template.name", "Template"), label));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, kind.id());
        if (kind.type() == Type.GLINT && !kind.reset()) {
            meta.setEnchantmentGlintOverride(true);
        }
        template.setItemMeta(meta);
        return template;
    }

    public @NonNull ItemStack createColor(ColorRecipe recipe) {
        ItemStack template = create(new Kind(Type.COLOR, false));
        ItemMeta meta = template.getItemMeta();
        meta.getPersistentDataContainer().set(colorKey, PersistentDataType.INTEGER_ARRAY, recipe.encode());
        TextColor color = TextColor.color(recipe.apply(null).asRGB());
        meta.lore(List.of(Component.text("\u2588 ", color)
                .append(Component.translatable("item.color", "Color: %s", Component.text(color.asHexString())))));
        template.setItemMeta(meta);
        return template;
    }

    public @NonNull ColorRecipe colorRecipe(ItemStack template) {
        int[] data = template.getItemMeta().getPersistentDataContainer().get(colorKey, COLOR_RECIPE_TYPE);
        if (data == null) {
            throw new IllegalArgumentException("Color template is missing its color_recipe payload");
        }
        return ColorRecipe.decode(data);
    }

    public @NonNull List<@NonNull Component> text(ItemStack template) {
        List<Component> lines = template.getItemMeta().lore();
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Text template is missing its text payload");
        }
        List<@NonNull Component> text = Objects.requireNonNull(lines.stream().<@NonNull Component>map(Objects::requireNonNull).toList());
        return isName(template) ? Objects.requireNonNull(text.stream().map(Templates::unwrapPreview).toList()) : text;
    }

    public @NonNull ItemStack withText(ItemStack template, List<? extends Component> text) {
        boolean name = isName(template);
        ItemStack result = template.clone();
        result.setAmount(1);
        ItemMeta meta = result.getItemMeta();
        meta.lore(name ? text.stream().map(Templates::wrapPreview).toList() : text);
        result.setItemMeta(meta);
        return result;
    }

    private boolean isName(ItemStack template) {
        return Objects.requireNonNull(kind(template)).type() == Type.NAME;
    }

    /**
     * A Name template's text is wrapped so that it previews in its lore like an unstyled custom name, while the name
     * itself is applied without the wrapper.
     */
    private static @NonNull Component wrapPreview(Component line) {
        return Component.text().style(PREVIEW).append(Objects.requireNonNull(line)).build();
    }

    /** The name inside a preview wrapper; Name templates crafted before the wrapper was introduced store it directly. */
    private static @NonNull Component unwrapPreview(@NonNull Component line) {
        if (line instanceof TextComponent wrapper && wrapper.content().isEmpty() && wrapper.children().size() == 1
                && wrapper.color() instanceof TextColor color && color.value() == NamedTextColor.WHITE.value()) {
            return Objects.requireNonNull(wrapper.children().getFirst());
        }
        return line;
    }

    /** Null means the target is incompatible or no property would change. */
    public @Nullable ItemStack apply(ItemStack input, ItemStack template) {
        Kind kind = Objects.requireNonNull(kind(template));
        ItemMeta meta = input.getItemMeta();
        if (meta == null) {
            return null;
        }
        switch (kind.type()) {
            case NAME -> meta.customName(kind.reset() ? null : text(template).getFirst());
            case LORE -> meta.lore(kind.reset() ? null : text(template));
            case GLINT -> meta.setEnchantmentGlintOverride(kind.reset() ? null : true);
            case COLOR -> {
                if (!(meta instanceof LeatherArmorMeta dyed)) {
                    return null;
                }
                dyed.setColor(kind.reset() ? null : colorRecipe(template).apply(dyed.isDyed() ? dyed.getColor() : null));
            }
        }
        ItemStack result = input.clone();
        result.setItemMeta(meta);
        return result.isSimilar(input) ? null : result;
    }

    private static boolean isPaper(@NonNull ItemStack item) {
        return item.getType() == Material.PAPER && item.getAmount() > 0;
    }
}

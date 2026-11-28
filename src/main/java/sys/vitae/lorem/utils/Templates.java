package sys.vitae.lorem.utils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import io.papermc.paper.datacomponent.DataComponentTypes;
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

        /** Whether there is a Copy variant: glint is just on or off, so there's nothing to copy. */
        public boolean isCopyable() {
            return this != GLINT;
        }
    }

    /** Plain templates carry their property; Reset templates remove it; Copy templates take it off another item. */
    public enum Variant {
        PLAIN(null),
        RESET(Component.translatable("controls.reset", "Reset")),
        COPY(Component.translatable("key.keyboard.copy", "Copy"));

        private final @Nullable Component label;

        Variant(@Nullable Component label) {
            this.label = label;
        }

        private @NonNull Component label(Component base) {
            Component suffix = label;
            return suffix == null ? Objects.requireNonNull(base)
                    : Objects.requireNonNull(base.append(Component.text(" - ")).append(suffix));
        }

        private String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Kind(Type type, Variant variant) {
        public Kind {
            if (variant == Variant.COPY && !type.isCopyable()) {
                throw new IllegalArgumentException(type + " templates have no Copy variant");
            }
        }

        public String id() {
            String id = type.name().toLowerCase(Locale.ROOT);
            return variant == Variant.PLAIN ? id : variant.id() + "_" + id;
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
    private final @NonNull NamespacedKey nameTagKey;

    public Templates(Plugin plugin) {
        idKey = new NamespacedKey(plugin, "template_id");
        colorKey = new NamespacedKey(plugin, "color_recipe");
        nameTagKey = new NamespacedKey(plugin, "name_tag_id");
    }

    /**
     * A name tag that removes a mob's custom name (Reset) or copies it onto an ordinary name tag (Copy). It must not
     * have a custom name itself, or vanilla would apply that instead, so its label is an item name.
     */
    public @NonNull ItemStack createNameTag(Variant variant) {
        if (variant == Variant.PLAIN) {
            throw new IllegalArgumentException("Plain name tags are vanilla's");
        }
        ItemStack tag = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = tag.getItemMeta();
        meta.itemName(variant.label(Component.translatable("item.minecraft.name_tag", "Name Tag")));
        meta.getPersistentDataContainer().set(nameTagKey, PersistentDataType.STRING, variant.id());
        meta.setEnchantmentGlintOverride(true);
        tag.setItemMeta(meta);
        return tag;
    }

    /**
     * The variant of a resetting or copying name tag, or null for any other item. One that was renamed since is an
     * ordinary name tag.
     */
    public @Nullable Variant nameTagVariant(@Nullable ItemStack item) {
        if (item == null) {
            return null;
        }
        Variant variant = storedNameTagVariant(item);
        return variant != null && !item.getItemMeta().hasCustomName() ? variant : null;
    }

    /** A renamed resetting or copying tag as the ordinary name tag it now is; the item itself otherwise. */
    public ItemStack asOrdinaryNameTag(ItemStack item) {
        if (storedNameTagVariant(item) == null || !item.getItemMeta().hasCustomName()) {
            return item;
        }
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        meta.getPersistentDataContainer().remove(nameTagKey);
        meta.itemName(null);
        meta.setEnchantmentGlintOverride(null);
        result.setItemMeta(meta);
        return result;
    }

    private @Nullable Variant storedNameTagVariant(ItemStack item) {
        if (item.getType() != Material.NAME_TAG || item.getAmount() < 1) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(nameTagKey, STRING_TYPE);
        for (Variant variant : List.of(Variant.RESET, Variant.COPY)) {
            if (variant.id().equals(id)) {
                return variant;
            }
        }
        return null;
    }

    public @Nullable Kind kind(@Nullable ItemStack item) {
        if (item == null || !isPaper(item)) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(idKey, STRING_TYPE);
        for (Type type : Type.values()) {
            for (Variant variant : Variant.values()) {
                if (variant == Variant.COPY && !type.isCopyable()) {
                    continue;
                }
                Kind kind = new Kind(type, variant);
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
        return withText(create(new Kind(type, Variant.PLAIN)), text);
    }

    public @NonNull ItemStack create(Kind kind) {
        ItemStack template = new ItemStack(Material.PAPER);
        ItemMeta meta = template.getItemMeta();
        meta.itemName(Component.translatable("options.generic_value", "%s: %s",
                Component.translatable("mco.template.name", "Template"), kind.variant().label(kind.type().label)));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, kind.id());
        meta.setEnchantmentGlintOverride(true);
        template.setItemMeta(meta);
        return template;
    }

    public @NonNull ItemStack createColor(ColorRecipe recipe) {
        ItemStack template = create(new Kind(Type.COLOR, Variant.PLAIN));
        ItemMeta meta = template.getItemMeta();
        meta.getPersistentDataContainer().set(colorKey, PersistentDataType.INTEGER_ARRAY, recipe.encode());
        TextColor color = TextColor.color(recipe.apply(null).asRGB());
        meta.lore(List.of(TextStyles.swatch(color)));
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
        List<Component> lore = template.getItemMeta().lore();
        List<@NonNull Component> lines = lore == null ? List.of()
                : Objects.requireNonNull(lore.stream().<@NonNull Component>map(Objects::requireNonNull).toList());
        List<Component> text = TextStyles.unmarked(isName(template) ? lines.stream().map(Templates::unwrapPreview).toList() : lines);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Text template is missing its text payload");
        }
        return Objects.requireNonNull(text.stream().<@NonNull Component>map(Objects::requireNonNull).toList());
    }

    /**
     * Like name tags, text templates with any explicit formatting get its {@link TextStyles#legend}.
     * Templates from before every template glinted get their glint.
     */
    public @NonNull ItemStack withText(ItemStack template, List<? extends Component> text) {
        boolean name = isName(template);
        ItemStack result = template.clone();
        result.setAmount(1);
        ItemMeta meta = result.getItemMeta();
        meta.lore(TextStyles.marked(name ? text.stream().map(Templates::wrapPreview).toList() : text, text));
        meta.setEnchantmentGlintOverride(true);
        result.setItemMeta(meta);
        return result;
    }

    /** The template holding the text instead; unlike {@link #withText}, the stack keeps its size. */
    public @NonNull ItemStack retext(ItemStack template, List<? extends Component> text) {
        ItemStack result = withText(template, text);
        result.setAmount(template.getAmount());
        return result;
    }

    /** Copies of the template; templates from before every template glinted get their glint. */
    public @NonNull ItemStack copies(ItemStack template, int amount) {
        ItemStack copies = template.clone();
        copies.setAmount(amount);
        ItemMeta meta = copies.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        copies.setItemMeta(meta);
        return copies;
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

    /**
     * The item with the template's property applied in an anvil. Null means the target is incompatible or no property
     * would change. Glowing templates, like their Resets, don't apply to anything that always glints: templates,
     * resetting or copying name tags, and items that glint by default. Color templates are crafted onto items instead
     * (see {@link #dye}).
     */
    public @Nullable ItemStack apply(ItemStack input, ItemStack template) {
        Kind kind = Objects.requireNonNull(kind(template));
        ItemMeta meta = input.getItemMeta();
        if (meta == null || kind.variant() == Variant.COPY
                || kind.type() == Type.GLINT && (kind(input) != null || nameTagVariant(input) != null || glintsByDefault(input))) {
            return null;
        }
        boolean reset = kind.variant() == Variant.RESET;
        switch (kind.type()) {
            case NAME -> meta.customName(reset ? null : text(template).getFirst());
            case LORE -> {
                List<@NonNull Component> lore = reset ? null : text(template);
                if (input.getType() == Material.NAME_TAG) {
                    NameTags.setLore(meta, lore);
                } else {
                    meta.lore(lore);
                }
            }
            case GLINT -> meta.setEnchantmentGlintOverride(reset ? null : true);
            case COLOR -> {
                return null;
            }
        }
        ItemStack result = input.clone();
        result.setItemMeta(meta);
        return result.isSimilar(input) ? null : result;
    }

    /**
     * One of the dyeable items, dyed by a plain Color template (replaying its recipe on the existing dye) or undyed by
     * a Color Reset template. Null if it isn't dyeable or its color wouldn't change.
     */
    public @Nullable ItemStack dye(ItemStack input, ItemStack template) {
        Kind kind = Objects.requireNonNull(kind(template));
        if (kind.type() != Type.COLOR || kind.variant() == Variant.COPY
                || !(input.getItemMeta() instanceof LeatherArmorMeta dyed)) {
            return null;
        }
        dyed.setColor(kind.variant() == Variant.RESET
                ? null : colorRecipe(template).apply(dyed.isDyed() ? dyed.getColor() : null));
        ItemStack result = input.clone();
        result.setAmount(1);
        result.setItemMeta(dyed);
        return result.isSimilar(input) ? null : result;
    }

    /**
     * A plain template of the type holding the source's corresponding property, or null if the source has none.
     * A name tag's legend is not part of its description. A plain Name or Description template, or a name
     * tag's name, lends its text's color, if all of the text explicitly shares one.
     */
    public @Nullable ItemStack copyFrom(Type type, ItemStack source) {
        ItemMeta meta = source.getItemMeta();
        if (meta == null) {
            return null;
        }
        Kind kind = kind(source);
        if (kind != null) {
            TextColor color = type == Type.COLOR && kind.variant() == Variant.PLAIN && kind.type().hasText()
                    ? TextStyles.sharedColor(text(source)) : null;
            return color == null ? null : createColor(ColorRecipe.of(Objects.requireNonNull(Color.fromRGB(color.value()))));
        }
        return switch (type) {
            case NAME -> meta.customName() instanceof Component name ? create(Type.NAME, List.of(name)) : null;
            case LORE -> {
                List<Component> original = meta.lore();
                List<Component> lore = original == null ? List.of()
                        : source.getType() == Material.NAME_TAG ? NameTags.lore(meta) : original;
                yield lore.isEmpty() ? null : create(Type.LORE, lore);
            }
            case COLOR -> {
                if (meta instanceof LeatherArmorMeta dyed) {
                    yield dyed.isDyed() ? createColor(ColorRecipe.of(dyed.getColor())) : null;
                }
                TextColor color = source.getType() == Material.NAME_TAG && meta.customName() instanceof Component name
                        ? TextStyles.sharedColor(List.of(name)) : null;
                yield color == null ? null : createColor(ColorRecipe.of(Objects.requireNonNull(Color.fromRGB(color.value()))));
            }
            case GLINT -> null;
        };
    }

    /** Whether the item's type glints even without enchantments, like a nether star or an enchanted golden apple. */
    boolean glintsByDefault(ItemStack item) {
        return Boolean.TRUE.equals(item.getType().getDefaultData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE));
    }

    private static boolean isPaper(@NonNull ItemStack item) {
        return item.getType() == Material.PAPER && item.getAmount() > 0;
    }
}

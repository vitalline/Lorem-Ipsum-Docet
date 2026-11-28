package sys.vitae.lorem.listeners;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sys.vitae.lorem.utils.ColorRecipe;
import sys.vitae.lorem.utils.ItemConsumption;
import sys.vitae.lorem.utils.NameTags;
import sys.vitae.lorem.utils.Templates;
import sys.vitae.lorem.utils.Templates.Kind;
import sys.vitae.lorem.utils.Templates.Type;
import sys.vitae.lorem.utils.Templates.Variant;
import sys.vitae.lorem.utils.TextStyles;

/**
 * Template composition, copying, styling, resets and copy variants in either crafting grid, styling named name tags,
 * and Color templates (re)coloring dyeable items and text templates, like a dye.
 * There is no registered recipe behind these, so taking the result is handled manually: vanilla's
 * no-recipe path would hand back the ingredients instead of consuming them.
 */
public class TemplateCraftListener implements Listener {

    private static final int MAX_BULK_CRAFTS = 128;

    private final Plugin plugin;
    private final Templates templates;

    public TemplateCraftListener(Plugin plugin, Templates templates) {
        this.plugin = plugin;
        this.templates = templates;
    }

    /** The outcome of the current grid: the result item and the matrix indices to consume one item from. */
    record Plan(ItemStack result, List<Integer> consumed) {}

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        Plan plan = plan(inventory.getMatrix());
        if (plan != null) {
            inventory.setResult(plan.result());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onResultClick(InventoryClickEvent event) {
        handleResultClick(event);
    }

    // CraftItemEvent has its own HandlerList, so it isn't delivered to InventoryClickEvent listeners.
    // It's fired instead of InventoryClickEvent when the grid also matches a (e.g. datapack) recipe.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        handleResultClick(event);
    }

    private void handleResultClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory() instanceof CraftingInventory inventory)
                || event.getSlotType() != SlotType.RESULT
                || event.getRawSlot() != 0
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Plan plan = plan(inventory.getMatrix());
        if (plan == null) {
            return;
        }

        event.setCancelled(true);
        InventoryView view = event.getView();
        ItemStack result = plan.result();

        switch (event.getClick()) {
            case LEFT, RIGHT -> {
                ItemStack cursor = view.getCursor();
                if (isEmpty(cursor)) {
                    view.setCursor(result.clone());
                    consume(inventory, plan, player);
                } else if (canStack(cursor, result)) {
                    cursor.setAmount(cursor.getAmount() + result.getAmount());
                    view.setCursor(cursor);
                    consume(inventory, plan, player);
                }
            }
            case NUMBER_KEY -> {
                int slot = event.getHotbarButton();
                ItemStack target = player.getInventory().getItem(slot);
                if (isEmpty(target)) {
                    player.getInventory().setItem(slot, result.clone());
                    consume(inventory, plan, player);
                } else if (canStack(target, result)) {
                    target.setAmount(target.getAmount() + result.getAmount());
                    player.getInventory().setItem(slot, target);
                    consume(inventory, plan, player);
                }
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                for (int i = 0; i < MAX_BULK_CRAFTS && plan != null && plan.result().isSimilar(result); i++) {
                    if (!fits(player.getInventory(), plan.result())) {
                        break;
                    }
                    player.getInventory().addItem(plan.result().clone());
                    consume(inventory, plan, player);
                    plan = plan(inventory.getMatrix());
                }
            }
            case DROP -> {
                drop(player, result);
                consume(inventory, plan, player);
            }
            case CONTROL_DROP -> {
                for (int i = 0; i < MAX_BULK_CRAFTS && plan != null && plan.result().isSimilar(result); i++) {
                    drop(player, plan.result());
                    consume(inventory, plan, player);
                    plan = plan(inventory.getMatrix());
                }
            }
            case MIDDLE -> {
                // Creative's pick-a-stack, like vanilla's: a full stack, without crafting.
                if (player.getGameMode() == GameMode.CREATIVE && isEmpty(view.getCursor())) {
                    ItemStack stack = result.clone();
                    stack.setAmount(stack.getMaxStackSize());
                    view.setCursor(stack);
                }
            }
            default -> { } // other click types just do nothing
        }

        // The client predicted the click; resync it with what actually happened.
        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
    }

    Plan plan(ItemStack[] matrix) {
        List<Integer> used = new ArrayList<>();
        List<ItemStack> marked = new ArrayList<>();
        List<Integer> markedSlots = new ArrayList<>();
        List<ItemStack> sheets = new ArrayList<>();
        List<ItemStack> ingredients = new ArrayList<>();
        List<Integer> ingredientSlots = new ArrayList<>();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (isEmpty(item)) {
                continue;
            }
            used.add(i);
            if (templates.kind(item) != null) {
                marked.add(item);
                markedSlots.add(i);
            } else if (templates.isSheet(item)) {
                sheets.add(item);
            } else {
                ingredients.add(item);
                ingredientSlots.add(i);
            }
        }

        if (!marked.isEmpty() && marked.stream().allMatch(this::isPlainLore)
                && (marked.size() > 1 || sheets.stream().anyMatch(templates::isRenamedSheet))) {
            // Like the Description recipe, with each template contributing all its lines.
            List<Component> lore = new ArrayList<>();
            for (ItemStack item : matrix) {
                if (isEmpty(item)) {
                    continue;
                }
                if (templates.kind(item) != null) {
                    lore.addAll(templates.text(item));
                } else if (templates.isSheet(item)) {
                    lore.add(sheetLine(item));
                }
            }
            return createText(Type.LORE, lore, ingredients, used);
        }
        if (marked.size() == 1 && !sheets.isEmpty() && ingredients.isEmpty()) {
            return new Plan(templates.copies(marked.getFirst(), sheets.size() + 1), used);
        }
        if (marked.size() == 2 && sheets.isEmpty() && ingredients.isEmpty()) {
            return colorText(marked, markedSlots, used);
        }
        if (marked.size() == 1 && sheets.isEmpty() && !ingredients.isEmpty()) {
            ItemStack template = marked.getFirst();
            Kind kind = Objects.requireNonNull(templates.kind(template));
            if (kind.variant() == Variant.COPY) {
                // The source item only lends its property, so it stays in the grid.
                ItemStack copy = ingredients.size() == 1 ? templates.copyFrom(kind.type(), ingredients.getFirst()) : null;
                if (copy == null) {
                    return null;
                }
                List<Integer> consumed = new ArrayList<>(used);
                consumed.remove(ingredientSlots.getFirst());
                return new Plan(copy, consumed);
            }
            if (kind.type() == Type.COLOR && ingredients.size() == 1) {
                ItemStack dyed = templates.dye(ingredients.getFirst(), template);
                if (dyed == null) {
                    dyed = coloredNameTag(ingredients.getFirst(), template, kind.variant());
                }
                if (dyed != null) {
                    return new Plan(dyed, used);
                }
            }
            if (kind.variant() != Variant.PLAIN) {
                return null;
            }
            Variant variant = ingredients.size() == 1 ? variantOf(ingredients.getFirst()) : null;
            if (variant != null) {
                return variant == Variant.COPY && !kind.type().isCopyable()
                        ? null : new Plan(templates.create(new Kind(kind.type(), variant)), used);
            }
            List<Color> dyes = dyes(ingredients);
            if (kind.type() == Type.COLOR && dyes != null) {
                return new Plan(templates.createColor(templates.colorRecipe(template).append(dyes)), used);
            }
            if (kind.type().hasText()) {
                List<? extends Component> text = style(templates.text(template), ingredients, true);
                return text == null ? null : new Plan(templates.withText(template, text), used);
            }
            return null;
        }
        if (marked.isEmpty() && sheets.isEmpty()) {
            Variant variant = nameTagVariant(ingredients);
            if (variant != null) {
                return new Plan(templates.createNameTag(variant), used);
            }
            ItemStack styled = styledNameTag(ingredients);
            return styled == null ? null : new Plan(styled, used);
        }
        if (!marked.isEmpty() || sheets.isEmpty()) {
            return null;
        }
        if (ingredients.stream().anyMatch(item -> variantOf(item) != null)) {
            Kind kind = directVariant(sheets.size(), ingredients);
            return kind == null ? null : new Plan(templates.create(kind), used);
        }
        if (ingredients.stream().anyMatch(TemplateCraftListener::isNugget)) {
            // Mirrors vanilla's name tag recipe; a blank sheet is left to that recipe.
            List<ItemStack> styling = new ArrayList<>(ingredients);
            styling.removeIf(TemplateCraftListener::isNugget);
            ItemStack sheet = sheets.getFirst();
            return sheets.size() == 1 && styling.size() == ingredients.size() - 1 && templates.isRenamedSheet(sheet)
                    ? createText(Type.NAME, List.of(sheet.getItemMeta().customName()), styling, used)
                    : null;
        }
        if (sheets.stream().anyMatch(templates::isRenamedSheet)) {
            // Without a nugget, even a single renamed sheet is a (one-line) Description.
            List<Component> lore = new ArrayList<>();
            for (ItemStack sheet : sheets) {
                lore.add(sheetLine(sheet));
            }
            return createText(Type.LORE, lore, ingredients, used);
        }
        if (sheets.size() != 1 || !templates.isBlankSheet(sheets.getFirst())) {
            return null;
        }
        if (ingredients.size() == 1 && ingredients.getFirst().getType() == Material.GLOWSTONE_DUST) {
            return new Plan(templates.create(new Kind(Type.GLINT, Variant.PLAIN)), used);
        }
        List<Color> dyes = dyes(ingredients);
        return dyes == null ? null : new Plan(templates.createColor(new ColorRecipe(List.of(dyes))), used);
    }

    /** A sheet's description line: its name, or an empty line if it's blank. */
    private static Component sheetLine(ItemStack sheet) {
        ItemMeta meta = sheet.getItemMeta();
        return meta.hasCustomName() ? meta.customName() : Component.empty();
    }

    /**
     * A plain Name or Description template with a Color template: a plain one recolors all the text, a Reset clears
     * its color, and a Copy takes the color the text shares, leaving the text template in the grid.
     */
    private @Nullable Plan colorText(List<ItemStack> marked, List<Integer> slots, List<Integer> used) {
        int textIndex = -1;
        ItemStack color = null;
        Variant variant = null;
        for (int i = 0; i < marked.size(); i++) {
            Kind kind = Objects.requireNonNull(templates.kind(marked.get(i)));
            if (kind.variant() == Variant.PLAIN && kind.type().hasText()) {
                textIndex = i;
            } else if (kind.type() == Type.COLOR) {
                color = marked.get(i);
                variant = kind.variant();
            }
        }
        if (textIndex < 0 || color == null) {
            return null;
        }
        ItemStack text = marked.get(textIndex);
        return switch (Objects.requireNonNull(variant)) {
            case PLAIN -> new Plan(templates.withText(text, color(templates.text(text), templates.colorRecipe(color))), used);
            case RESET -> {
                List<@NonNull Component> original = templates.text(text);
                List<@NonNull Component> cleared = TextStyles.color(original, null);
                yield cleared.equals(original) ? null : new Plan(templates.withText(text, cleared), used);
            }
            case COPY -> {
                ItemStack copy = templates.copyFrom(Type.COLOR, text);
                List<Integer> consumed = new ArrayList<>(used);
                consumed.remove(slots.get(textIndex));
                yield copy == null ? null : new Plan(copy, consumed);
            }
        };
    }

    private boolean isPlainLore(ItemStack template) {
        return new Kind(Type.LORE, Variant.PLAIN).equals(templates.kind(template));
    }

    /** Bone meal bleaches into a Reset variant; an ink sac transcribes into a Copy variant. */
    private static @Nullable Variant variantOf(ItemStack ingredient) {
        return switch (ingredient.getType()) {
            case BONE_MEAL -> Variant.RESET;
            case INK_SAC -> Variant.COPY;
            default -> null;
        };
    }

    /**
     * A Reset or Copy template crafted directly: a plain template's recipe plus one bone meal or ink sac. Its text
     * doesn't matter, so renamed sheets count as blank.
     */
    private static @Nullable Kind directVariant(int sheets, List<ItemStack> ingredients) {
        Variant variant = null;
        List<ItemStack> base = new ArrayList<>();
        for (ItemStack ingredient : ingredients) {
            Variant added = variantOf(ingredient);
            if (added == null) {
                base.add(ingredient);
            } else if (variant != null) {
                return null;
            } else {
                variant = added;
            }
        }
        Type type = baseType(sheets, base);
        return type == null || variant == null || (variant == Variant.COPY && !type.isCopyable())
                ? null : new Kind(type, variant);
    }

    /** The type of plain template the sheets and other ingredients make, regardless of the sheets' names. */
    private static @Nullable Type baseType(int sheets, List<ItemStack> ingredients) {
        if (sheets == 2 && ingredients.isEmpty()) {
            return Type.LORE;
        }
        if (sheets != 1 || ingredients.isEmpty()) {
            return null;
        }
        if (ingredients.size() == 1 && isNugget(ingredients.getFirst())) {
            return Type.NAME;
        }
        if (ingredients.size() == 1 && ingredients.getFirst().getType() == Material.GLOWSTONE_DUST) {
            return Type.GLINT;
        }
        return dyes(ingredients) != null ? Type.COLOR : null;
    }

    /**
     * The variant an unnamed ordinary name tag crafts into with bone meal or an ink sac. Named tags are refused, so a
     * name is never wiped by accident.
     */
    private @Nullable Variant nameTagVariant(List<ItemStack> ingredients) {
        if (ingredients.size() != 2) {
            return null;
        }
        for (int i = 0; i < 2; i++) {
            ItemStack tag = ingredients.get(i);
            Variant variant = variantOf(ingredients.get(1 - i));
            if (variant != null && tag.getType() == Material.NAME_TAG && !tag.getItemMeta().hasCustomName()
                    && templates.nameTagVariant(tag) == null) {
                return variant;
            }
        }
        return null;
    }

    /**
     * One named name tag with its name styled like a Name template's, if all the other ingredients style text. Italics
     * toggle against a mob's upright nameplate rather than the tag's italic tooltip, since that's where the name shows.
     */
    private @Nullable ItemStack styledNameTag(List<ItemStack> ingredients) {
        List<ItemStack> tags = ingredients.stream().filter(item -> item.getType() == Material.NAME_TAG).toList();
        if (tags.size() != 1 || ingredients.size() < 2) {
            return null;
        }
        ItemStack tag = tags.getFirst();
        Component name = tag.getItemMeta().customName();
        if (name == null) {
            return null;
        }
        List<ItemStack> styling = new ArrayList<>(ingredients);
        styling.removeIf(item -> item == tag);
        List<? extends Component> styled = style(List.of(name), styling, false);
        return styled == null ? null : renamedTag(tag, styled.getFirst());
    }

    /**
     * One named name tag with its name recolored by a plain Color template or its color cleared by a Reset, like a
     * text template's. Null if it isn't a named name tag or its name wouldn't change.
     */
    private @Nullable ItemStack coloredNameTag(ItemStack tag, ItemStack template, Variant variant) {
        Component name = tag.getType() == Material.NAME_TAG ? tag.getItemMeta().customName() : null;
        if (name == null) {
            return null;
        }
        List<@NonNull Component> colored = variant == Variant.RESET
                ? TextStyles.color(List.of(name), null) : color(List.of(name), templates.colorRecipe(template));
        Component recolored = Objects.requireNonNull(colored.getFirst());
        return recolored.equals(name) ? null : renamedTag(tag, recolored);
    }

    /** One of the name tags with the new name, its legend following suit. */
    private ItemStack renamedTag(ItemStack tag, Component name) {
        ItemStack result = tag.clone();
        result.setAmount(1);
        ItemMeta meta = result.getItemMeta();
        List<Component> lore = NameTags.lore(meta);
        meta.customName(name);
        NameTags.setLore(meta, lore);
        result.setItemMeta(meta);
        return templates.asOrdinaryNameTag(result);
    }

    private static boolean isNugget(ItemStack item) {
        return switch (item.getType()) {
            case IRON_NUGGET, GOLD_NUGGET, COPPER_NUGGET -> true;
            default -> false;
        };
    }

    /** A plain Name or Description template with the text styled by the ingredients, if they all style text. */
    private @Nullable Plan createText(Type type, List<? extends Component> text, List<ItemStack> ingredients,
            List<Integer> used) {
        List<? extends Component> styled = style(text, ingredients, true);
        return styled == null ? null : new Plan(templates.create(type, styled), used);
    }

    /**
     * The text styled with all the ingredients at once (dyes mix into one color), or null unless they all style text.
     * Each decoration and the font may only be set by one ingredient, so the result never depends on their order.
     * Unset italics count as the given default when toggling.
     */
    private static @Nullable List<? extends Component> style(List<? extends Component> text,
            List<ItemStack> ingredients, boolean italicDefault) {
        List<? extends Component> result = text;
        List<Color> dyes = new ArrayList<>();
        Set<@NonNull TextDecoration> decorations = EnumSet.noneOf(TextDecoration.class);
        boolean font = false;
        for (ItemStack ingredient : ingredients) {
            DyeColor dye = TextStyles.dye(ingredient.getType());
            if (dye != null) {
                dyes.add(dye.getColor());
                continue;
            }
            if (!TextStyles.isIngredient(ingredient)) {
                return null;
            }
            TextDecoration decoration = TextStyles.decoration(ingredient.getType());
            if (decoration == null) {
                if (font) {
                    return null;
                }
                font = true;
            } else if (!decorations.add(decoration)) {
                return null;
            }
            result = TextStyles.apply(result, ingredient, italicDefault);
        }
        return dyes.isEmpty() ? result : color(result, new ColorRecipe(List.of(dyes)));
    }

    private static @NonNull List<@NonNull Component> color(List<? extends Component> text, ColorRecipe recipe) {
        return TextStyles.color(text, TextColor.color(recipe.apply(null).asRGB()));
    }

    private static List<Color> dyes(List<ItemStack> ingredients) {
        List<Color> colors = new ArrayList<>();
        for (ItemStack ingredient : ingredients) {
            var dye = TextStyles.dye(ingredient.getType());
            if (dye == null) {
                return null;
            }
            colors.add(dye.getColor());
        }
        return colors.isEmpty() ? null : colors;
    }

    private static void consume(CraftingInventory inventory, Plan plan, Player player) {
        ItemStack[] matrix = inventory.getMatrix();
        for (int index : plan.consumed()) {
            ItemStack item = matrix[index];
            if (isEmpty(item)) {
                matrix[index] = null;
            } else {
                matrix[index] = ItemConsumption.consumeOne(item, player);
            }
        }
        inventory.setMatrix(matrix); // triggers PrepareItemCraftEvent, refreshing the result slot
    }

    private static boolean canStack(ItemStack stack, ItemStack item) {
        return stack.isSimilar(item) && stack.getAmount() + item.getAmount() <= stack.getMaxStackSize();
    }

    /** Whether the whole item fits into the player's main inventory, so a craft is never split. */
    private static boolean fits(PlayerInventory inventory, ItemStack item) {
        int room = 0;
        for (ItemStack slot : inventory.getStorageContents()) {
            if (isEmpty(slot)) {
                room += item.getMaxStackSize();
            } else if (slot.isSimilar(item)) {
                room += Math.max(0, slot.getMaxStackSize() - slot.getAmount());
            }
            if (room >= item.getAmount()) {
                return true;
            }
        }
        return false;
    }

    private static void drop(Player player, ItemStack item) {
        Location eye = player.getEyeLocation();
        Item dropped = player.getWorld().dropItem(eye.clone().subtract(0, 0.3, 0), item.clone());
        dropped.setVelocity(eye.getDirection().multiply(0.3));
        dropped.setPickupDelay(40);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }
}

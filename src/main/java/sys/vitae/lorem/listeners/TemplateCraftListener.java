package sys.vitae.lorem.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Color;
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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import sys.vitae.lorem.utils.ColorRecipe;
import sys.vitae.lorem.utils.ItemConsumption;
import sys.vitae.lorem.utils.Templates;
import sys.vitae.lorem.utils.Templates.Kind;
import sys.vitae.lorem.utils.Templates.Type;
import sys.vitae.lorem.utils.TextStyles;

/**
 * Template composition, copying, styling and resets in either crafting grid.
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
        List<ItemStack> sheets = new ArrayList<>();
        List<ItemStack> ingredients = new ArrayList<>();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (isEmpty(item)) {
                continue;
            }
            used.add(i);
            if (templates.kind(item) != null) {
                marked.add(item);
            } else if (templates.isSheet(item)) {
                sheets.add(item);
            } else {
                ingredients.add(item);
            }
        }

        if (marked.size() == 1 && !sheets.isEmpty() && ingredients.isEmpty()) {
            ItemStack copies = marked.getFirst().clone();
            copies.setAmount(sheets.size() + 1);
            return new Plan(copies, used);
        }
        if (marked.size() == 2 && sheets.isEmpty() && ingredients.isEmpty()) {
            ItemStack text = null, color = null;
            for (ItemStack item : marked) {
                Kind kind = Objects.requireNonNull(templates.kind(item));
                if (!kind.reset() && kind.type().hasText()) {
                    text = item;
                } else if (!kind.reset() && kind.type() == Type.COLOR) {
                    color = item;
                }
            }
            return text != null && color != null
                    ? new Plan(templates.withText(text, color(templates.text(text), templates.colorRecipe(color))), used)
                    : null;
        }
        if (marked.size() == 1 && sheets.isEmpty() && !ingredients.isEmpty()) {
            ItemStack template = marked.getFirst();
            Kind kind = Objects.requireNonNull(templates.kind(template));
            if (!kind.reset() && ingredients.size() == 1 && ingredients.getFirst().getType() == Material.BONE_MEAL) {
                return new Plan(templates.create(new Kind(kind.type(), true)), used);
            }
            if (kind.reset()) {
                return null;
            }
            List<Color> dyes = dyes(ingredients);
            if (kind.type() == Type.COLOR && dyes != null) {
                return new Plan(templates.createColor(templates.colorRecipe(template).append(dyes)), used);
            }
            if (kind.type().hasText()) {
                List<@NonNull Component> text = style(templates.text(template), ingredients);
                return text == null ? null : new Plan(templates.withText(template, text), used);
            }
            return null;
        }
        if (!marked.isEmpty() || sheets.isEmpty()) {
            return null;
        }
        if (ingredients.isEmpty()) {
            if (sheets.stream().noneMatch(templates::isRenamedSheet)) {
                return null;
            }
            List<Component> lore = new ArrayList<>();
            for (ItemStack sheet : sheets) {
                ItemMeta meta = sheet.getItemMeta();
                lore.add(meta.hasCustomName() ? meta.customName() : Component.empty());
            }
            return new Plan(templates.create(Type.LORE, lore), used);
        }
        if (sheets.size() == 1 && ingredients.size() == 1 && isNugget(ingredients.getFirst())) {
            // Mirrors vanilla's name tag recipe; a blank sheet is left to that recipe.
            ItemStack sheet = sheets.getFirst();
            return templates.isRenamedSheet(sheet)
                    ? new Plan(templates.create(Type.NAME, List.of(sheet.getItemMeta().customName())), used)
                    : null;
        }
        if (sheets.size() != 1 || !templates.isBlankSheet(sheets.getFirst())) {
            return null;
        }
        if (ingredients.size() == 1 && ingredients.getFirst().getType() == Material.GLOWSTONE_DUST) {
            return new Plan(templates.create(new Kind(Type.GLINT, false)), used);
        }
        List<Color> dyes = dyes(ingredients);
        return dyes == null ? null : new Plan(templates.createColor(new ColorRecipe(List.of(dyes))), used);
    }

    private static boolean isNugget(ItemStack item) {
        return switch (item.getType()) {
            case IRON_NUGGET, GOLD_NUGGET, COPPER_NUGGET -> true;
            default -> false;
        };
    }

    /** The text styled with the ingredients (several dyes are mixed), or null if they aren't a styling. */
    private static List<@NonNull Component> style(List<? extends Component> text, List<ItemStack> ingredients) {
        List<Color> dyes = dyes(ingredients);
        if (dyes != null) {
            return color(text, new ColorRecipe(List.of(dyes)));
        }
        if (ingredients.size() == 1 && TextStyles.isIngredient(ingredients.getFirst())) {
            return TextStyles.apply(text, ingredients.getFirst(), true);
        }
        return null;
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

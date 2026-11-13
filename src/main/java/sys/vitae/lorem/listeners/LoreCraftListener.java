package sys.vitae.lorem.listeners;

import java.util.ArrayList;
import java.util.List;

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

import net.kyori.adventure.text.Component;
import sys.vitae.lorem.utils.LoreTemplates;

/**
 * Lore templates in the crafting grid (see {@link LoreTemplates} for the kinds of sheets):
 *
 * <ul>
 *   <li><b>Compose:</b> sheets only, at least one renamed, no template -> a new lore template
 *       whose lore is the sheets' names in reading order (blank sheets give blank lines).</li>
 *   <li><b>Copy:</b> one template plus any number of other sheets (renamed ones count as blank) ->
 *       that many copies of the template, the original included. Two or more templates don't combine.</li>
 *   <li><b>Revert:</b> one template plus a white dye -> a plain sheet of paper.</li>
 * </ul>
 *
 * There is no registered recipe behind this, so taking the result is handled manually: vanilla's
 * no-recipe path would hand back the ingredients instead of consuming them.
 */
public class LoreCraftListener implements Listener {

    private static final int MAX_BULK_CRAFTS = 128;

    private final Plugin plugin;

    public LoreCraftListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The outcome of the current grid: the result item and the matrix indices to consume one item from. */
    private record Plan(ItemStack result, List<Integer> consumed) {}

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
                    consume(inventory, plan);
                } else if (canStack(cursor, result)) {
                    cursor.setAmount(cursor.getAmount() + result.getAmount());
                    view.setCursor(cursor);
                    consume(inventory, plan);
                }
            }
            case NUMBER_KEY -> {
                int slot = event.getHotbarButton();
                ItemStack target = player.getInventory().getItem(slot);
                if (isEmpty(target)) {
                    player.getInventory().setItem(slot, result.clone());
                    consume(inventory, plan);
                } else if (canStack(target, result)) {
                    target.setAmount(target.getAmount() + result.getAmount());
                    player.getInventory().setItem(slot, target);
                    consume(inventory, plan);
                }
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                for (int i = 0; i < MAX_BULK_CRAFTS && plan != null && plan.result().isSimilar(result); i++) {
                    if (!fits(player.getInventory(), plan.result())) {
                        break;
                    }
                    player.getInventory().addItem(plan.result().clone());
                    consume(inventory, plan);
                    plan = plan(inventory.getMatrix());
                }
            }
            case DROP -> {
                drop(player, result);
                consume(inventory, plan);
            }
            case CONTROL_DROP -> {
                for (int i = 0; i < MAX_BULK_CRAFTS && plan != null && plan.result().isSimilar(result); i++) {
                    drop(player, plan.result());
                    consume(inventory, plan);
                    plan = plan(inventory.getMatrix());
                }
            }
            default -> { } // other click types just do nothing
        }

        // The client predicted the click; resync it with what actually happened.
        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
    }

    private static Plan plan(ItemStack[] matrix) {
        List<Integer> used = new ArrayList<>();
        int template = -1;
        int sheets = 0;
        boolean dye = false;
        boolean anyRenamed = false;
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (isEmpty(item)) {
                continue;
            }
            used.add(i);
            if (LoreTemplates.isTemplate(item)) {
                if (template >= 0) {
                    return null; // templates can't be copied onto each other
                }
                template = i;
            } else if (LoreTemplates.isSheet(item)) {
                sheets++;
                anyRenamed |= LoreTemplates.isRenamedSheet(item);
            } else if (item.getType() == Material.WHITE_DYE && !dye) {
                dye = true;
            } else {
                return null;
            }
        }

        if (dye) {
            return template >= 0 && sheets == 0 ? new Plan(new ItemStack(Material.PAPER), used) : null;
        }
        if (template >= 0) {
            if (sheets == 0) {
                return null;
            }
            ItemStack copies = matrix[template].clone();
            copies.setAmount(sheets + 1);
            return new Plan(copies, used);
        }
        if (!anyRenamed) {
            return null; // plain paper only: leave it to vanilla
        }
        List<Component> lore = new ArrayList<>();
        for (int index : used) {
            ItemMeta meta = matrix[index].getItemMeta();
            lore.add(meta.hasCustomName() ? meta.customName() : Component.empty());
        }
        return new Plan(LoreTemplates.create(lore), used);
    }

    private static void consume(CraftingInventory inventory, Plan plan) {
        ItemStack[] matrix = inventory.getMatrix();
        for (int index : plan.consumed()) {
            ItemStack item = matrix[index];
            if (isEmpty(item) || item.getAmount() <= 1) {
                matrix[index] = null;
            } else {
                item = item.clone();
                item.setAmount(item.getAmount() - 1);
                matrix[index] = item;
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
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }
}

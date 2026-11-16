package sys.vitae.lorem.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

public final class ItemConsumption {

    private ItemConsumption() {}

    /** Consumes one item and returns what belongs in its slot, retaining bottle remainders. */
    public static @Nullable ItemStack consumeOne(ItemStack ingredient, Player player) {
        int amount = ingredient.getAmount();
        if (amount < 1) {
            throw new IllegalArgumentException("Cannot consume an empty ingredient stack");
        }
        ItemStack bottle = TextStyles.isWaterBottle(ingredient) ? new ItemStack(Material.GLASS_BOTTLE) : null;
        if (amount == 1) {
            return bottle;
        }
        ItemStack remaining = ingredient.clone();
        remaining.setAmount(amount - 1);
        if (bottle != null) {
            give(player, bottle);
        }
        return remaining;
    }

    /** Puts the item into the player's inventory, dropping whatever doesn't fit nearby. */
    public static void give(Player player, ItemStack item) {
        for (ItemStack overflow : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }
}

package sys.vitae.lorem.listeners;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import sys.vitae.lorem.utils.ItemConsumption;
import sys.vitae.lorem.utils.NameTags;
import sys.vitae.lorem.utils.Templates;
import sys.vitae.lorem.utils.Templates.Variant;

/**
 * Name-resetting name tags remove a mob's custom name, consuming one tag like vanilla's name tags do.
 * The mob stays persistent, as there is no telling whether it despawned before it was named.
 * Name-copying name tags turn into an ordinary name tag carrying the mob's name, leaving the mob untouched.
 */
public class NameTagListener implements Listener {

    private static final @NonNull Sound PAGE_TURN = Objects.requireNonNull(
            Sound.sound(Key.key("item.book.page_turn"), Sound.Source.PLAYER, 1, 1));
    /** The cartography table's, which copies maps. */
    private static final @NonNull Sound COPY = Objects.requireNonNull(
            Sound.sound(Key.key("ui.cartography_table.take_result"), Sound.Source.PLAYER, 1, 1));

    private final Templates templates;

    public NameTagListener(Templates templates) {
        this.templates = templates;
    }

    // Cancelling also keeps the click from e.g. opening a villager's trades or making a pet sit.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack tag = player.getInventory().getItem(hand);
        Variant variant = templates.nameTagVariant(tag);
        if (variant == null || !(event.getRightClicked() instanceof LivingEntity entity) || entity instanceof Player
                || !(entity.customName() instanceof Component name)
                || (variant == Variant.RESET && !mayRename(player, entity))) {
            return;
        }
        event.setCancelled(true);
        boolean survival = player.getGameMode() != GameMode.CREATIVE;
        World world = entity.getWorld();
        if (variant == Variant.RESET) {
            entity.customName(null);
            if (survival) {
                player.getInventory().setItem(hand, ItemConsumption.consumeOne(tag, player));
            }
            world.playSound(PAGE_TURN, entity);
            world.spawnParticle(Particle.POOF, entity.getLocation().add(0, entity.getHeight(), 0), 5, 0.2, 0.1, 0.2, 0.01);
        } else {
            ItemStack named = NameTags.named(name);
            if (survival && tag.getAmount() == 1) {
                player.getInventory().setItem(hand, named);
            } else {
                if (survival) {
                    player.getInventory().setItem(hand, ItemConsumption.consumeOne(tag, player));
                }
                ItemConsumption.give(player, named);
            }
            world.playSound(COPY, entity);
        }
        player.swingHand(hand);
    }

    /** Only a tamed animal's owner may remove its name. */
    private static boolean mayRename(Player player, LivingEntity entity) {
        if (!(entity instanceof Tameable pet)) {
            return true;
        }
        UUID owner = pet.getOwnerUniqueId();
        return owner == null || owner.equals(player.getUniqueId());
    }
}

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
import sys.vitae.lorem.utils.ItemConsumption;
import sys.vitae.lorem.utils.Templates;

/**
 * Name-resetting name tags remove a mob's custom name, consuming one tag like vanilla's name tags do.
 * The mob stays persistent, as there is no telling whether it despawned before it was named.
 */
public class NameResetListener implements Listener {

    private static final @NonNull Sound PAGE_TURN = Objects.requireNonNull(
            Sound.sound(Key.key("item.book.page_turn"), Sound.Source.PLAYER, 1, 1));

    private final Templates templates;

    public NameResetListener(Templates templates) {
        this.templates = templates;
    }

    // Cancelling also keeps the click from e.g. opening a villager's trades or making a pet sit.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack tag = player.getInventory().getItem(hand);
        if (!templates.isNameReset(tag) || !(event.getRightClicked() instanceof LivingEntity entity)
                || entity instanceof Player || entity.customName() == null || !mayRename(player, entity)) {
            return;
        }
        event.setCancelled(true);
        entity.customName(null);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItem(hand, ItemConsumption.consumeOne(tag, player));
        }
        player.swingHand(hand);
        World world = entity.getWorld();
        world.playSound(PAGE_TURN, entity);
        world.spawnParticle(Particle.POOF, entity.getLocation().add(0, entity.getHeight(), 0), 5, 0.2, 0.1, 0.2, 0.01);
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

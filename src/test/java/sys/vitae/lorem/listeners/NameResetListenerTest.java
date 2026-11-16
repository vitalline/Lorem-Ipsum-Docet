package sys.vitae.lorem.listeners;

import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import sys.vitae.lorem.utils.Templates;

class NameResetListenerTest {

    private Templates templates;
    private NameResetListener listener;
    private Player player;
    private PlayerInventory inventory;
    private ItemStack tag;

    @BeforeEach
    void setUp() {
        templates = mock(Templates.class);
        listener = new NameResetListener(templates);
        player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        this.inventory = inventory;
        tag = mock(ItemStack.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(new UUID(0, 1));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(inventory.getItem(EquipmentSlot.OFF_HAND)).thenReturn(tag);
        when(tag.getAmount()).thenReturn(2);
        when(tag.clone()).thenReturn(tag);
        when(templates.isNameReset(tag)).thenReturn(true);
    }

    @Test
    void removesTheNameConsumingOneTagFromTheHandHoldingItInSurvivalOnly() {
        @NonNull Villager villager = named(Villager.class);
        PlayerInteractEntityEvent event = interact(villager);
        listener.onInteractEntity(event);

        verify(event).setCancelled(true);
        verify(villager).customName(null);
        verify(tag).setAmount(1);
        verify(inventory).setItem(EquipmentSlot.OFF_HAND, tag);
        verify(player).swingHand(EquipmentSlot.OFF_HAND);
        verify(villager.getWorld()).playSound(Objects.requireNonNull(
                Sound.sound(Key.key("item.book.page_turn"), Sound.Source.PLAYER, 1, 1)), villager);

        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        clearInvocations(inventory);
        listener.onInteractEntity(interact(villager));
        verify(inventory, never()).setItem(any(EquipmentSlot.class), any());
    }

    @Test
    void leavesUnnamedMobsPlayersOtherOwnersPetsAndOrdinaryTagsAlone() {
        LivingEntity unnamed = mock(LivingEntity.class);
        Player otherPlayer = named(Player.class);
        Wolf pet = named(Wolf.class);
        when(pet.getOwnerUniqueId()).thenReturn(new UUID(0, 2));
        for (Entity entity : new Entity[] {unnamed, otherPlayer, pet}) {
            assertUntouched(entity);
        }

        Wolf ownPet = named(Wolf.class);
        UUID self = player.getUniqueId();
        when(ownPet.getOwnerUniqueId()).thenReturn(self);
        when(templates.isNameReset(tag)).thenReturn(false);
        assertUntouched(ownPet);
        when(templates.isNameReset(tag)).thenReturn(true);
        listener.onInteractEntity(interact(ownPet));
        verify(ownPet).customName(null);
    }

    private void assertUntouched(Entity entity) {
        PlayerInteractEntityEvent event = interact(entity);
        listener.onInteractEntity(event);
        verify(event, never()).setCancelled(anyBoolean());
        verify(entity, never()).customName(any());
        verify(inventory, never()).setItem(any(EquipmentSlot.class), any());
    }

    private <T extends LivingEntity> @NonNull T named(Class<T> type) {
        T entity = mock(type);
        World world = mock(World.class);
        when(entity.customName()).thenReturn(Component.text("Name"));
        when(entity.getWorld()).thenReturn(world);
        when(entity.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        return entity;
    }

    private PlayerInteractEntityEvent interact(Entity entity) {
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        when(event.getRightClicked()).thenReturn(entity);
        return event;
    }
}

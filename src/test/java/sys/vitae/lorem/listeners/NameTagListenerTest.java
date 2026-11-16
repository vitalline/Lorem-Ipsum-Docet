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
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import sys.vitae.lorem.utils.Templates;
import sys.vitae.lorem.utils.Templates.Variant;

class NameTagListenerTest {

    private Templates templates;
    private NameTagListener listener;
    private Player player;
    private PlayerInventory inventory;
    private ItemStack tag;

    @BeforeEach
    void setUp() {
        templates = mock(Templates.class);
        listener = new NameTagListener(templates);
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
        when(templates.nameTagVariant(tag)).thenReturn(Variant.RESET);
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
        when(templates.nameTagVariant(tag)).thenReturn(null);
        assertUntouched(ownPet);
        when(templates.nameTagVariant(tag)).thenReturn(Variant.RESET);
        listener.onInteractEntity(interact(ownPet));
        verify(ownPet).customName(null);
    }

    @Test
    void copyTurnsOneTagIntoANameTagWithTheMobsNameLeavingTheMobAlone() {
        when(templates.nameTagVariant(tag)).thenReturn(Variant.COPY);
        Wolf pet = named(Wolf.class);
        when(pet.getOwnerUniqueId()).thenReturn(new UUID(0, 2)); // copying someone else's pet's name is fine
        ItemMeta namedMeta = mock(ItemMeta.class);
        try (var construction = mockConstruction(ItemStack.class,
                (item, _) -> when(item.getItemMeta()).thenReturn(namedMeta))) {
            PlayerInteractEntityEvent event = interact(pet);
            listener.onInteractEntity(event);
            verify(event).setCancelled(true);
            verify(namedMeta).customName(Component.text("Name"));
            verify(tag).setAmount(1);
            verify(inventory).setItem(EquipmentSlot.OFF_HAND, tag);
            verify(inventory).addItem(construction.constructed().getFirst());
            verify(pet, never()).customName(any());
            verify(player).swingHand(EquipmentSlot.OFF_HAND);

            // The last tag is replaced in hand.
            clearInvocations(inventory);
            when(tag.getAmount()).thenReturn(1);
            listener.onInteractEntity(interact(pet));
            verify(inventory).setItem(EquipmentSlot.OFF_HAND, construction.constructed().getLast());
            verify(inventory, never()).addItem(any());

            // Creative keeps the copying tag.
            clearInvocations(inventory);
            when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
            listener.onInteractEntity(interact(pet));
            verify(inventory, never()).setItem(any(EquipmentSlot.class), any());
            verify(inventory).addItem(construction.constructed().getLast());
        }
        assertUntouched(mock(LivingEntity.class));
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

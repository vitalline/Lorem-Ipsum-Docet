package sys.vitae.lorem.listeners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.NonNull;
import org.mockito.ArgumentCaptor;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sys.vitae.lorem.utils.FormatCodes;
import sys.vitae.lorem.utils.FormattingAccess;

class TextFieldListenerTest {

    private TextFieldListener listener;
    private final @NonNull Player player = Objects.requireNonNull(mock(Player.class));
    private Block block;
    private final @NonNull Sign sign = Objects.requireNonNull(mock(Sign.class));
    private final @NonNull SignSide side = Objects.requireNonNull(mock(SignSide.class));
    private BukkitScheduler scheduler;
    private Logger logger;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        logger = mock(Logger.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(server.getScheduler()).thenReturn(scheduler);
        listener = new TextFieldListener(plugin);
        when(player.getUniqueId()).thenReturn(new UUID(0, 1));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        block = mock(Block.class);
        Location location = new Location(mock(World.class), 1, 2, 3);
        when(block.getLocation()).thenReturn(location);
        when(block.getState()).thenReturn(sign);
        when(sign.getBlock()).thenReturn(block);
        when(sign.getLocation()).thenReturn(location);
        when(sign.getSide(Side.FRONT)).thenReturn(side);
    }

    @Test
    void survivalEditsStripMixedFormattingAndDoNotInterpretEscapes() {
        open(List.of(Component.text("red", NamedTextColor.RED).append(Component.text("blue", NamedTextColor.BLUE))));
        SignChangeEvent event = edit("changed \\c literal");
        listener.onSignChange(event);

        assertEquals(Component.text("changed \\c literal"), event.line(0));
        verify(side).line(0, Component.text("redblue"));
        verify(sign, never()).update();
    }

    @Test
    void shiftEditingByCreativeAndOperatorsUsesTheSamePlainEditor() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(player.isOp()).thenReturn(true);
        when(player.isSneaking()).thenReturn(true);
        open(List.of(Component.text("red", NamedTextColor.RED).append(Component.text("blue", NamedTextColor.BLUE))));
        SignChangeEvent event = edit("\\a not a code");
        listener.onSignChange(event);
        assertEquals(Component.text("\\a not a code"), event.line(0));
        verify(side).line(0, Component.text("redblue"));
    }

    @Test
    void plainEditsRestoreUniformWholeSignFormatting() {
        open(List.of(Component.text("old", NamedTextColor.RED).decorate(TextDecoration.BOLD)));
        SignChangeEvent event = edit("new");
        listener.onSignChange(event);
        assertEquals("new", FormatCodes.plainText(event.line(0)));
        assertEquals(NamedTextColor.RED, event.line(0).color());
        assertEquals(TextDecoration.State.TRUE, event.line(0).decoration(TextDecoration.BOLD));
    }

    @Test
    void unchangedEditingRestoresEvenMixedFormattingExactly() {
        Component mixed = Component.text("red", NamedTextColor.RED).append(Component.text("blue", NamedTextColor.BLUE));
        open(List.of(mixed));
        SignChangeEvent event = edit("redblue");
        listener.onSignChange(event);
        assertEquals(mixed, event.line(0));
        verify(sign, never()).update();
    }

    @Test
    void rejectedEditingResynchronizesTheUnmodifiedServerSign() {
        open(List.of(Component.text("red", NamedTextColor.RED).append(Component.text("blue", NamedTextColor.BLUE))));
        Sign serverSign = mock(Sign.class);
        Location location = block.getLocation();
        when(serverSign.getLocation()).thenReturn(location);
        when(block.getState()).thenReturn(serverSign);
        when(player.isOnline()).thenReturn(true);
        SignChangeEvent event = edit("rejected");
        event.setCancelled(true);

        listener.afterSignChange(event);
        ArgumentCaptor<@NonNull Runnable> update = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(any(Plugin.class), update.capture());
        Objects.requireNonNull(update.getValue()).run();
        verify(player).sendBlockUpdate(Objects.requireNonNull(location), serverSign);
        verify(serverSign, never()).update();
    }

    @Test
    void permissionGrantedSurvivalCanRoundTripAndChangeEscapeFormatting() {
        when(player.isPermissionSet(FormattingAccess.FORMAT_PERMISSION)).thenReturn(true);
        when(player.hasPermission(FormattingAccess.FORMAT_PERMISSION)).thenReturn(true);
        open(List.of(Component.text("red", NamedTextColor.RED)));
        verify(side).line(0, Component.text("\\cred"));
        SignChangeEvent event = edit("\\anew");
        listener.onSignChange(event);
        assertEquals(FormatCodes.toComponent("\\anew"), event.line(0));
    }

    @Test
    void signingBooksOnlyTranslatesForAuthorizedPlayers() {
        PlayerEditBookEvent event = mock(PlayerEditBookEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.isSigning()).thenReturn(true);
        listener.onBookSign(event);
        verify(event, never()).getNewBookMeta();

        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        BookMeta meta = mock(BookMeta.class);
        when(meta.pages()).thenReturn(new java.util.ArrayList<Component>(List.of(Component.text("\\cRed"))));
        when(event.getNewBookMeta()).thenReturn(meta);
        listener.onBookSign(event);
        verify(meta).pages(new java.util.ArrayList<Component>(List.of(FormatCodes.toComponent("\\cRed"))));
        verify(event).setNewBookMeta(meta);
    }

    @Test
    void signStylingHonorsWaxAndDoesNotConsumeAnIngredientWhenUpdatingFails() {
        when(side.lines()).thenReturn(new java.util.ArrayList<Component>(
                List.of(Component.text("text"), Component.empty(), Component.empty(), Component.empty())));
        when(sign.getTargetSide(player)).thenReturn(side);
        ItemStack ingredient = mock(ItemStack.class);
        when(ingredient.getType()).thenReturn(Material.COAL);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getItem()).thenReturn(ingredient);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.useInteractedBlock()).thenReturn(Result.DEFAULT);
        when(event.useItemInHand()).thenReturn(Result.DEFAULT);

        when(sign.isWaxed()).thenReturn(true);
        listener.onStyleSign(event);
        verifyNoInteractions(side);

        when(sign.isWaxed()).thenReturn(false);
        listener.onStyleSign(event);
        verify(side).line(0, Component.text("text").decorate(TextDecoration.BOLD));
        verify(event).setUseInteractedBlock(Result.DENY);
        verify(sign).update();
        verify(logger).warning(contains("Could not update styled sign"));
        verify(player, never()).getInventory();
    }

    private void open(List<@NonNull Component> text) {
        var lines = new java.util.ArrayList<Component>(
                List.of(Objects.requireNonNull(text.getFirst()), Component.empty(), Component.empty(), Component.empty()));
        when(side.lines()).thenReturn(Objects.requireNonNull(lines));
        listener.onSignOpen(new PlayerOpenSignEvent(player, sign, Side.FRONT, PlayerOpenSignEvent.Cause.INTERACT));
    }

    private SignChangeEvent edit(@NonNull String text) {
        return new SignChangeEvent(block, player,
                new java.util.ArrayList<Component>(List.of(Component.text(text), Component.empty(), Component.empty(), Component.empty())),
                Side.FRONT);
    }
}

package sys.vitae.lorem.listeners;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import sys.vitae.lorem.utils.FormatCodes;
import sys.vitae.lorem.utils.NameTags;
import sys.vitae.lorem.utils.Templates;
import sys.vitae.lorem.utils.Templates.Kind;
import sys.vitae.lorem.utils.Templates.Type;
import sys.vitae.lorem.utils.Templates.Variant;

class AnvilListenerTest {

    private AnvilListener listener;
    private Templates templates;
    private PrepareAnvilEvent event;
    private AnvilView view;
    private Player player;
    private ItemStack input;
    private ItemMeta inputMeta;
    private ItemStack addition;

    @BeforeEach
    void setUp() {
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        templates = mock(Templates.class);
        when(templates.asOrdinaryNameTag(any())).thenAnswer(returnsFirstArg());
        listener = new AnvilListener(plugin, templates);
        event = mock(PrepareAnvilEvent.class);
        view = mock(AnvilView.class);
        player = mock(Player.class);
        AnvilInventory inventory = mock(AnvilInventory.class);
        input = mock(ItemStack.class);
        inputMeta = mock(ItemMeta.class);
        addition = mock(ItemStack.class);
        when(input.getItemMeta()).thenReturn(inputMeta);
        when(addition.getItemMeta()).thenReturn(mock(ItemMeta.class));
        when(event.getInventory()).thenReturn(inventory);
        when(event.getView()).thenReturn(view);
        when(view.getPlayer()).thenReturn(player);
        when(view.getMaximumRepairCost()).thenReturn(40);
        when(inventory.getItem(0)).thenReturn(input);
        when(inventory.getItem(1)).thenReturn(addition);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
    }

    @Test
    void ordinarySurvivalKeepsLiteralEscapesButCreativeTranslatesThem() {
        listener.onPrepareAnvil(event);
        verify(event, never()).setResult(any());

        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(view.getRenameText()).thenReturn("\\cRed");
        ItemStack vanilla = mock(ItemStack.class), translated = mock(ItemStack.class);
        ItemMeta vanillaMeta = mock(ItemMeta.class), translatedMeta = mock(ItemMeta.class);
        when(event.getResult()).thenReturn(vanilla);
        when(vanilla.getItemMeta()).thenReturn(vanillaMeta);
        when(vanillaMeta.hasCustomName()).thenReturn(true);
        when(vanillaMeta.customName()).thenReturn(Component.text("\\cRed"));
        when(vanilla.clone()).thenReturn(translated);
        when(translated.getItemMeta()).thenReturn(translatedMeta);

        listener.onPrepareAnvil(event);
        verify(translatedMeta).customName(FormatCodes.toComponent("\\cRed"));
        verify(event).setResult(translated);
    }

    @Test
    void nameTemplateTakesPrecedenceOverTheRenameField() {
        ItemStack result = mock(ItemStack.class);
        when(templates.kind(addition)).thenReturn(new Kind(Type.NAME, Variant.PLAIN));
        when(templates.apply(input, addition)).thenReturn(result);
        when(view.getRenameText()).thenReturn("not the template name");

        listener.onPrepareAnvil(event);
        verify(event).setResult(result);
        verify(result, never()).getItemMeta();
        verify(view).setRepairCost(1);
        verify(view).setRepairItemCountCost(1);
    }

    @Test
    void otherTemplatesPreserveAnUntouchedFormattedName() {
        ItemStack result = mock(ItemStack.class);
        when(templates.kind(addition)).thenReturn(new Kind(Type.GLINT, Variant.PLAIN));
        when(templates.apply(input, addition)).thenReturn(result);
        when(inputMeta.hasCustomName()).thenReturn(true);
        when(inputMeta.customName()).thenReturn(Component.text("Old", NamedTextColor.RED));
        when(view.getRenameText()).thenReturn("Old");

        listener.onPrepareAnvil(event);
        verify(event).setResult(result);
        verify(result, never()).getItemMeta();
        verify(view).setRepairCost(1);
    }

    @Test
    void plainRenameRemovesTheNameTagMarkerEvenWithoutFormattingAccess() {
        Component lore = Component.text("lore");
        ItemStack vanilla = nameTag(Component.text("Plain"), List.of(lore, NameTags.MARKER));
        when(event.getResult()).thenReturn(vanilla);

        listener.onPrepareAnvil(event);
        ItemStack marked = vanilla.clone();
        verify(marked.getItemMeta()).lore(List.of(lore));
        verify(event).setResult(marked);
    }

    @Test
    void templatesSyncTheNameTagMarkerButSkipResultsThatOnlyRestoreIt() {
        when(templates.kind(addition)).thenReturn(new Kind(Type.NAME, Variant.PLAIN));
        ItemStack applied = nameTag(Component.text("Mob", NamedTextColor.RED), null);
        when(templates.apply(input, addition)).thenReturn(applied);

        listener.onPrepareAnvil(event);
        ItemStack marked = applied.clone();
        verify(marked.getItemMeta()).lore(List.of(NameTags.MARKER));
        verify(event).setResult(marked);

        clearInvocations(event, view);
        when(marked.isSimilar(input)).thenReturn(true);
        listener.onPrepareAnvil(event);
        verify(event, never()).setResult(any());
        verify(view, never()).setRepairCost(anyInt());
    }

    /** A name tag whose (single) clone has the given name and lore. */
    private static ItemStack nameTag(Component name, @Nullable List<? extends Component> lore) {
        ItemStack tag = mock(ItemStack.class), copy = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(tag.getType()).thenReturn(Material.NAME_TAG);
        when(tag.clone()).thenReturn(copy);
        when(copy.getItemMeta()).thenReturn(meta);
        when(meta.customName()).thenReturn(name);
        when(meta.lore()).thenReturn(lore == null ? null : new ArrayList<Component>(lore));
        return tag;
    }

    @Test
    void renamingANameResetTagMakesItAnOrdinaryNameTag() {
        ItemStack renamed = mock(ItemStack.class), ordinary = mock(ItemStack.class);
        when(event.getResult()).thenReturn(renamed);
        when(templates.asOrdinaryNameTag(renamed)).thenReturn(ordinary);

        listener.onPrepareAnvil(event);
        verify(event).setResult(ordinary);
    }

    @Test
    void plainRenameKeepsTheStyleOfAUniformlyFormattedNameWithoutFormattingAccess() {
        assertRenamed(FormatCodes.toComponent("\\a\\l\\hTest"), FormatCodes.toComponent("\\a\\l\\hTestificate"));
        // An unset italic stays unset, so it keeps following the context (italic in tooltips, upright on mobs).
        assertRenamed(Component.text("Test", NamedTextColor.GREEN), Component.text("Testificate", NamedTextColor.GREEN));
    }

    @Test
    void plainRenameOfAnUnstyledOrMixedFormattedNameIsLeftToVanilla() {
        for (Component oldName : List.of(Component.text("Test"),
                Component.text("Te", NamedTextColor.GREEN).append(Component.text("st", NamedTextColor.RED)))) {
            assertRenamed(oldName, null);
        }
    }

    /** Renames the input from {@code oldName} to "Testificate"; {@code expected} is null for vanilla's plain name. */
    private void assertRenamed(Component oldName, Component expected) {
        clearInvocations(event);
        when(inputMeta.hasCustomName()).thenReturn(true);
        when(inputMeta.customName()).thenReturn(oldName);
        when(view.getRenameText()).thenReturn("Testificate");
        ItemStack vanilla = mock(ItemStack.class), styled = mock(ItemStack.class);
        ItemMeta vanillaMeta = mock(ItemMeta.class), styledMeta = mock(ItemMeta.class);
        when(event.getResult()).thenReturn(vanilla);
        when(vanilla.getItemMeta()).thenReturn(vanillaMeta);
        when(vanillaMeta.hasCustomName()).thenReturn(true);
        when(vanillaMeta.customName()).thenReturn(Component.text("Testificate"));
        when(vanilla.clone()).thenReturn(styled);
        when(styled.getItemMeta()).thenReturn(styledMeta);

        listener.onPrepareAnvil(event);
        if (expected == null) {
            verify(event, never()).setResult(any());
        } else {
            verify(styledMeta).customName(expected);
            verify(event).setResult(styled);
        }
    }

    @Test
    void colorTemplateCannotBeConsumedForARenameOnAnIncompatibleItem() {
        when(templates.kind(addition)).thenReturn(new Kind(Type.COLOR, Variant.PLAIN));
        when(view.getRenameText()).thenReturn("New");
        listener.onPrepareAnvil(event);
        verify(event, never()).setResult(any());
    }
}

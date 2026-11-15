package sys.vitae.lorem.listeners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.List;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.jspecify.annotations.NonNull;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import sys.vitae.lorem.utils.ColorRecipe;
import sys.vitae.lorem.utils.FormatCodes;
import sys.vitae.lorem.utils.Templates;
import sys.vitae.lorem.utils.Templates.Kind;
import sys.vitae.lorem.utils.Templates.Type;
import sys.vitae.lorem.utils.TextStyles;

class TemplateCraftListenerTest {

    private Templates templates;
    private TemplateCraftListener listener;
    private final @NonNull ItemStack result = item(Material.PAPER);

    @BeforeEach
    void setUp() {
        templates = mock(Templates.class);
        Plugin plugin = mock(Plugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        listener = new TemplateCraftListener(plugin, templates);
    }

    @Test
    void loreCompositionPreservesReadingOrderAndBlankLines() {
        ItemStack first = sheet(Component.text("first")), blank = sheet(null), last = sheet(Component.text("last"));
        var expected = List.of(Component.text("first"), Component.empty(), Component.text("last"));
        when(templates.create(Type.LORE, expected)).thenReturn(result);

        var plan = listener.plan(new ItemStack[] {first, null, blank, last});
        assertSame(result, plan.result());
        assertEquals(List.of(0, 2, 3), plan.consumed());
        assertNull(listener.plan(new ItemStack[] {blank}));
    }

    @Test
    void createsNameAndGlintUsingTheirIngredients() {
        ItemStack paper = sheet(null), nameTag = item(Material.NAME_TAG);
        when(nameTag.getItemMeta().hasCustomName()).thenReturn(true);
        when(nameTag.getItemMeta().customName()).thenReturn(Component.text("Name"));
        when(templates.create(Type.NAME, List.of(Component.text("Name")))).thenReturn(result);
        assertSame(result, listener.plan(new ItemStack[] {paper, nameTag}).result());

        when(templates.create(new Kind(Type.GLINT, false))).thenReturn(result);
        assertSame(result, listener.plan(new ItemStack[] {paper, item(Material.GLOWSTONE_DUST)}).result());
    }

    @Test
    void whiteDyeProducesColorWhileBoneMealProducesTypedReset() {
        ItemStack paper = sheet(null);
        ColorRecipe white = new ColorRecipe(List.of(List.of(DyeColor.WHITE.getColor())));
        when(templates.createColor(white)).thenReturn(result);
        assertSame(result, listener.plan(new ItemStack[] {paper, item(Material.WHITE_DYE)}).result());

        for (Type type : Type.values()) {
            ItemStack template = template(type, false);
            when(templates.create(new Kind(type, true))).thenReturn(result);
            assertSame(result, listener.plan(new ItemStack[] {template, item(Material.BONE_MEAL)}).result());
            assertNull(listener.plan(new ItemStack[] {template(type, true), item(Material.BONE_MEAL)}));
        }
    }

    @Test
    void colorDyeingAppendsOneGroupedStepInsteadOfFlatteningTheHistory() {
        ItemStack color = template(Type.COLOR, false);
        ColorRecipe original = new ColorRecipe(List.of(List.of(Color.RED)));
        when(templates.colorRecipe(color)).thenReturn(original);
        when(templates.createColor(original.append(List.of(DyeColor.BLUE.getColor(), DyeColor.WHITE.getColor()))))
                .thenReturn(result);

        assertSame(result, listener.plan(new ItemStack[] {color, item(Material.BLUE_DYE), item(Material.WHITE_DYE)}).result());
    }

    @Test
    void colorTemplateStylesNameAndLoreInsteadOfCopyingItsDescription() {
        for (Type type : List.of(Type.NAME, Type.LORE)) {
            ItemStack text = template(type, false), color = template(Type.COLOR, false);
            List<@NonNull Component> original = List.of(
                    Component.text("text").append(Component.text("tail", TextColor.color(0xFFFFFF))));
            when(templates.text(text)).thenReturn(Objects.requireNonNull(original));
            when(templates.colorRecipe(color)).thenReturn(new ColorRecipe(List.of(List.of(Color.RED))));
            when(templates.withText(text, TextStyles.color(original, TextColor.color(Color.RED.asRGB())))).thenReturn(result);
            assertSame(result, listener.plan(new ItemStack[] {text, color}).result());
        }
    }

    @Test
    void fontIngredientReplacesOverridesThroughoutTemplateText() {
        ItemStack template = template(Type.LORE, false);
        var original = List.of(FormatCodes.toComponent("\\hfirst\\isecond"));
        when(templates.text(template)).thenReturn(Objects.requireNonNull(original));
        ItemStack ingredient = item(Material.EXPERIENCE_BOTTLE);
        when(templates.withText(template, TextStyles.apply(original, ingredient, true))).thenReturn(result);
        assertSame(result, listener.plan(new ItemStack[] {template, ingredient}).result());
        assertNull(listener.plan(new ItemStack[] {template, item(Material.ENCHANTING_TABLE)}));
    }

    @Test
    void copiesEachTemplateIncludingResetsButDoesNotCombineTwoOfTheSameType() {
        for (Type type : Type.values()) {
            for (boolean reset : new boolean[] {false, true}) {
                ItemStack template = template(type, reset);
                when(template.clone()).thenReturn(result);
                assertSame(result, listener.plan(new ItemStack[] {template, sheet(null), sheet(Component.text("ignored"))}).result());
                verify(result).setAmount(3);
                clearInvocations(result);
                assertNull(listener.plan(new ItemStack[] {template, template}));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = ClickType.class, names = {
            "LEFT", "RIGHT", "NUMBER_KEY", "SHIFT_LEFT", "SHIFT_RIGHT", "DROP", "CONTROL_DROP"})
    void takingWaterStyledTemplateLeavesOneEmptyBottleInTheGrid(ClickType click) {
        ItemStack template = template(Type.NAME, false);
        ItemStack water = item(Material.POTION);
        PotionMeta meta = mock(PotionMeta.class);
        when(water.getItemMeta()).thenReturn(meta);
        when(meta.getBasePotionType()).thenReturn(PotionType.WATER);
        var original = List.of(FormatCodes.toComponent("\\gName"));
        when(templates.text(template)).thenReturn(Objects.requireNonNull(original));
        when(templates.withText(template, TextStyles.apply(original, water, true))).thenReturn(result);
        when(result.clone()).thenReturn(result);
        when(result.isSimilar(result)).thenReturn(true);
        when(result.getMaxStackSize()).thenReturn(64);

        ItemStack[] matrix = {template, water};
        CraftingInventory inventory = mock(CraftingInventory.class);
        when(inventory.getMatrix()).thenReturn(matrix);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Player player = mock(Player.class);
        PlayerInventory playerInventory = mock(PlayerInventory.class);
        World world = mock(World.class);
        Item dropped = mock(Item.class);
        when(player.getInventory()).thenReturn(playerInventory);
        when(playerInventory.getStorageContents()).thenReturn(new ItemStack[36]);
        when(player.getWorld()).thenReturn(world);
        when(player.getEyeLocation()).thenReturn(new Location(world, 1, 2, 3));
        when(world.dropItem(any(Location.class), same(result))).thenReturn(dropped);
        when(event.getView()).thenReturn(view);
        when(event.getSlotType()).thenReturn(SlotType.RESULT);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(click);

        try (var construction = mockConstruction(ItemStack.class, (bottle, context) ->
                assertEquals(List.of(Material.GLASS_BOTTLE), context.arguments()))) {
            listener.onResultClick(event);
            verify(event).setCancelled(true);
            switch (click) {
                case LEFT, RIGHT -> verify(view).setCursor(result);
                case NUMBER_KEY -> verify(playerInventory).setItem(0, result);
                case SHIFT_LEFT, SHIFT_RIGHT -> verify(playerInventory).addItem(result);
                case DROP, CONTROL_DROP -> verify(world).dropItem(any(Location.class), same(result));
                default -> fail("Unexpected click type");
            }
            assertNull(matrix[0]);
            assertEquals(1, construction.constructed().size());
            assertSame(construction.constructed().getFirst(), matrix[1]);
            verify(inventory).setMatrix(matrix);
        }
    }

    private ItemStack template(Type type, boolean reset) {
        ItemStack item = item(Material.PAPER);
        when(templates.kind(item)).thenReturn(new Kind(type, reset));
        return item;
    }

    private ItemStack sheet(Component name) {
        ItemStack item = item(Material.PAPER);
        when(templates.isSheet(item)).thenReturn(true);
        when(templates.isRenamedSheet(item)).thenReturn(name != null);
        when(templates.isBlankSheet(item)).thenReturn(name == null);
        when(item.getItemMeta().hasCustomName()).thenReturn(name != null);
        when(item.getItemMeta().customName()).thenReturn(name);
        return item;
    }

    private static @NonNull ItemStack item(Material material) {
        ItemStack item = Objects.requireNonNull(mock(ItemStack.class));
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(1);
        when(item.getItemMeta()).thenReturn(mock(ItemMeta.class));
        return item;
    }
}

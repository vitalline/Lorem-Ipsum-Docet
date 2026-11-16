package sys.vitae.lorem.listeners;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.jspecify.annotations.NonNull;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import sys.vitae.lorem.utils.ColorRecipe;
import sys.vitae.lorem.utils.FormatCodes;
import sys.vitae.lorem.utils.Templates;
import sys.vitae.lorem.utils.Templates.Kind;
import sys.vitae.lorem.utils.Templates.Type;
import sys.vitae.lorem.utils.Templates.Variant;
import sys.vitae.lorem.utils.TextStyles;

class TemplateCraftListenerTest {

    /** Nested formatting, so every styling ingredient visibly changes something. */
    private static final @NonNull List<@NonNull Component> STYLED = Objects.requireNonNull(List.of(
            Component.text("Mob", NamedTextColor.BLUE).decorate(TextDecoration.BOLD)
                    .append(Component.text(" name", NamedTextColor.GREEN).font(Key.key("alt")))));

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
    void createsNameFromARenamedSheetAndAnyNuggetAndGlintFromGlowstone() {
        when(templates.create(Type.NAME, List.of(Component.text("Name")))).thenReturn(result);
        for (Material nugget : List.of(Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.COPPER_NUGGET)) {
            assertSame(result, listener.plan(new ItemStack[] {sheet(Component.text("Name")), item(nugget)}).result());
            // Left to vanilla's name tag recipe.
            assertNull(listener.plan(new ItemStack[] {sheet(null), item(nugget)}));
        }

        when(templates.create(new Kind(Type.GLINT, Variant.PLAIN))).thenReturn(result);
        assertSame(result, listener.plan(new ItemStack[] {sheet(null), item(Material.GLOWSTONE_DUST)}).result());
    }

    @Test
    void whiteDyeProducesColorWhileBoneMealAndInkSacProduceTypedResetAndCopy() {
        ItemStack paper = sheet(null);
        ColorRecipe white = new ColorRecipe(List.of(List.of(DyeColor.WHITE.getColor())));
        when(templates.createColor(white)).thenReturn(result);
        assertSame(result, listener.plan(new ItemStack[] {paper, item(Material.WHITE_DYE)}).result());

        for (Type type : Type.values()) {
            for (Variant variant : List.of(Variant.RESET, Variant.COPY)) {
                Material ingredient = variant == Variant.RESET ? Material.BONE_MEAL : Material.INK_SAC;
                ItemStack template = template(type, Variant.PLAIN);
                if (variant == Variant.COPY && !type.isCopyable()) {
                    assertNull(listener.plan(new ItemStack[] {template, item(ingredient)}));
                    continue;
                }
                ItemStack converted = item(Material.PAPER);
                when(templates.create(new Kind(type, variant))).thenReturn(converted);
                assertSame(converted, listener.plan(new ItemStack[] {template, item(ingredient)}).result());
                // Only plain templates convert; a Copy template takes the item as its source instead.
                assertNull(listener.plan(new ItemStack[] {template(type, Variant.RESET), item(ingredient)}));
            }
        }
    }

    @Test
    void plainRecipesWithBoneMealOrAnInkSacCraftResetAndCopyTemplatesDirectly() {
        record Recipe(Kind kind, ItemStack... grid) {}
        for (Variant variant : List.of(Variant.RESET, Variant.COPY)) {
            Material added = variant == Variant.RESET ? Material.BONE_MEAL : Material.INK_SAC;
            for (Recipe recipe : List.of(
                    new Recipe(new Kind(Type.NAME, variant), sheet(null), item(Material.GOLD_NUGGET), item(added)),
                    new Recipe(new Kind(Type.NAME, variant), item(added), sheet(Component.text("x")), item(Material.IRON_NUGGET)),
                    new Recipe(new Kind(Type.LORE, variant), sheet(null), sheet(Component.text("x")), item(added)),
                    new Recipe(new Kind(Type.COLOR, variant),
                            sheet(null), item(Material.RED_DYE), item(Material.BLUE_DYE), item(added)))) {
                ItemStack crafted = item(Material.PAPER);
                when(templates.create(recipe.kind())).thenReturn(crafted);
                var plan = listener.plan(recipe.grid());
                assertSame(crafted, plan.result());
                assertEquals(recipe.grid().length, plan.consumed().size());
            }
            for (ItemStack[] refused : List.of(
                    new ItemStack[] {sheet(null), item(added)},
                    new ItemStack[] {sheet(null), sheet(null), sheet(null), item(added)},
                    new ItemStack[] {sheet(null), item(Material.IRON_NUGGET), item(added), item(added)},
                    new ItemStack[] {sheet(null), item(Material.IRON_NUGGET), item(Material.BONE_MEAL), item(Material.INK_SAC)},
                    new ItemStack[] {sheet(null), item(Material.IRON_NUGGET), item(Material.STICK), item(added)})) {
                assertNull(listener.plan(refused));
            }
        }
        when(templates.create(new Kind(Type.GLINT, Variant.RESET))).thenReturn(result);
        assertSame(result, listener.plan(new ItemStack[] {sheet(null), item(Material.GLOWSTONE_DUST), item(Material.BONE_MEAL)}).result());
        assertNull(listener.plan(new ItemStack[] {sheet(null), item(Material.GLOWSTONE_DUST), item(Material.INK_SAC)}));
    }

    @Test
    void copyTemplateTakesItsPropertyOffTheSourceWithoutConsumingIt() {
        ItemStack template = template(Type.LORE, Variant.COPY), source = item(Material.DIAMOND_SWORD);
        when(templates.copyFrom(Type.LORE, source)).thenReturn(result);
        var plan = listener.plan(new ItemStack[] {source, null, template});
        assertSame(result, plan.result());
        assertEquals(List.of(2), plan.consumed());

        // Ingredients that would otherwise style or convert a template are sources too.
        ItemStack bare = item(Material.BONE_MEAL);
        assertNull(listener.plan(new ItemStack[] {template, bare}));
        verify(templates).copyFrom(Type.LORE, bare);
        verify(templates, never()).create(any(Kind.class));
        assertNull(listener.plan(new ItemStack[] {template, source, item(Material.STICK)}));
        // Two templates are never a copy, even if the other one holds the property.
        assertNull(listener.plan(new ItemStack[] {template, template(Type.LORE, Variant.PLAIN)}));
    }

    @Test
    void anUnnamedNameTagWithBoneMealOrAnInkSacCraftsAResetOrCopyTag() {
        when(templates.createNameTag(Variant.RESET)).thenReturn(result);
        ItemStack tag = item(Material.NAME_TAG);
        var plan = listener.plan(new ItemStack[] {item(Material.BONE_MEAL), null, tag});
        assertSame(result, plan.result());
        assertEquals(List.of(0, 2), plan.consumed());
        ItemStack copy = item(Material.NAME_TAG);
        when(templates.createNameTag(Variant.COPY)).thenReturn(copy);
        assertSame(copy, listener.plan(new ItemStack[] {tag, item(Material.INK_SAC)}).result());

        ItemStack named = item(Material.NAME_TAG), reset = item(Material.NAME_TAG), copier = item(Material.NAME_TAG);
        when(named.getItemMeta().hasCustomName()).thenReturn(true);
        when(templates.nameTagVariant(reset)).thenReturn(Variant.RESET);
        when(templates.nameTagVariant(copier)).thenReturn(Variant.COPY);
        for (ItemStack refused : List.of(named, reset, copier, item(Material.PAPER))) {
            for (Material ingredient : List.of(Material.BONE_MEAL, Material.INK_SAC)) {
                assertNull(listener.plan(new ItemStack[] {refused, item(ingredient)}));
            }
        }
        assertNull(listener.plan(new ItemStack[] {tag, item(Material.BONE_MEAL), item(Material.BONE_MEAL)}));
        assertNull(listener.plan(new ItemStack[] {item(Material.INK_SAC), item(Material.BONE_MEAL)}));
    }

    @Test
    void colorDyeingAppendsOneGroupedStepInsteadOfFlatteningTheHistory() {
        ItemStack color = template(Type.COLOR, Variant.PLAIN);
        ColorRecipe original = new ColorRecipe(List.of(List.of(Color.RED)));
        when(templates.colorRecipe(color)).thenReturn(original);
        when(templates.createColor(original.append(List.of(DyeColor.BLUE.getColor(), DyeColor.WHITE.getColor()))))
                .thenReturn(result);

        assertSame(result, listener.plan(new ItemStack[] {color, item(Material.BLUE_DYE), item(Material.WHITE_DYE)}).result());
    }

    @Test
    void colorTemplateStylesNameAndLoreInsteadOfCopyingItsDescription() {
        List<@NonNull Component> expected = TextStyles.color(STYLED, TextColor.color(Color.RED.asRGB()));
        for (Type type : List.of(Type.NAME, Type.LORE)) {
            ItemStack text = template(type, Variant.PLAIN), color = template(Type.COLOR, Variant.PLAIN);
            when(templates.text(text)).thenReturn(STYLED);
            when(templates.colorRecipe(color)).thenReturn(new ColorRecipe(List.of(List.of(Color.RED))));
            when(templates.withText(text, expected)).thenReturn(result);
            assertSame(result, listener.plan(new ItemStack[] {text, color}).result());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stylings")
    void ingredientsStyleNameAndLoreTemplatesAlike(List<Material> materials) {
        List<@NonNull ItemStack> ingredients = materials.stream().map(TemplateCraftListenerTest::ingredient).toList();
        List<Color> dyes = materials.stream().map(TextStyles::dye).filter(Objects::nonNull)
                .map(dye -> Objects.requireNonNull(dye).getColor()).toList();
        List<@NonNull Component> expected = dyes.isEmpty()
                ? TextStyles.apply(STYLED, ingredients.getFirst(), true)
                : TextStyles.color(STYLED, TextColor.color(new ColorRecipe(List.of(dyes)).apply(null).asRGB()));
        assertNotEquals(STYLED, expected);

        for (Type type : List.of(Type.NAME, Type.LORE)) {
            ItemStack template = template(type, Variant.PLAIN);
            when(templates.text(template)).thenReturn(STYLED);
            when(templates.withText(template, expected)).thenReturn(result);
            assertSame(result, listener.plan(grid(template, ingredients)).result());
        }
    }

    static Stream<List<Material>> stylings() {
        return Stream.concat(
                Arrays.stream(DyeColor.values()).map(dye -> List.of(Material.valueOf(dye.name() + "_DYE"))),
                Stream.of(List.of(Material.RED_DYE, Material.BLUE_DYE), List.of(Material.END_ROD),
                        List.of(Material.COAL), List.of(Material.CHARCOAL), List.of(Material.STRING), List.of(Material.STICK),
                        List.of(Material.FEATHER), List.of(Material.EXPERIENCE_BOTTLE), List.of(Material.BOOK),
                        List.of(Material.OMINOUS_BOTTLE), List.of(Material.POTION)));
    }

    @Test
    void rejectsOtherIngredients() {
        ItemStack template = template(Type.LORE, Variant.PLAIN);
        when(templates.text(template)).thenReturn(STYLED);
        assertNull(listener.plan(new ItemStack[] {template, item(Material.ENCHANTING_TABLE)}));
        assertNull(listener.plan(new ItemStack[] {template, item(Material.RED_DYE), item(Material.COAL)}));
    }

    @Test
    void copiesEachTemplateIncludingVariantsButDoesNotCombineTwoOfTheSameType() {
        for (Type type : Type.values()) {
            for (Variant variant : Variant.values()) {
                if (variant == Variant.COPY && !type.isCopyable()) {
                    continue;
                }
                ItemStack template = template(type, variant);
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
        ItemStack template = template(Type.NAME, Variant.PLAIN);
        ItemStack water = ingredient(Material.POTION);
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

        try (var construction = mockConstruction(ItemStack.class, (_, context) ->
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

    @Test
    void creativeMiddleClickPicksAFullStackWithoutCrafting() {
        ItemStack template = template(Type.NAME, Variant.PLAIN), copies = item(Material.PAPER), stack = item(Material.PAPER);
        when(template.clone()).thenReturn(copies);
        when(copies.clone()).thenReturn(stack);
        when(stack.getMaxStackSize()).thenReturn(64);
        ItemStack[] matrix = {template, sheet(null)};
        CraftingInventory inventory = mock(CraftingInventory.class);
        when(inventory.getMatrix()).thenReturn(matrix);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        Player player = mock(Player.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getSlotType()).thenReturn(SlotType.RESULT);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getClick()).thenReturn(ClickType.MIDDLE);

        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        listener.onResultClick(event);
        verify(view, never()).setCursor(any());

        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        listener.onResultClick(event);
        verify(stack).setAmount(64);
        verify(view).setCursor(stack);
        verify(inventory, never()).setMatrix(any());
    }

    private @NonNull ItemStack template(Type type, Variant variant) {
        ItemStack item = item(Material.PAPER);
        when(templates.kind(item)).thenReturn(new Kind(type, variant));
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

    private static ItemStack[] grid(@NonNull ItemStack first, List<@NonNull ItemStack> rest) {
        return Stream.concat(Stream.of(first), rest.stream()).toArray(ItemStack[]::new);
    }

    /** An item, with plain water for potions. */
    private static @NonNull ItemStack ingredient(Material material) {
        ItemStack item = item(material);
        if (material == Material.POTION) {
            PotionMeta meta = mock(PotionMeta.class);
            when(item.getItemMeta()).thenReturn(meta);
            when(meta.getBasePotionType()).thenReturn(PotionType.WATER);
        }
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

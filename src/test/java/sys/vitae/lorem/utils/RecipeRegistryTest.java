package sys.vitae.lorem.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static sys.vitae.lorem.NonNullMockito.any;
import static sys.vitae.lorem.NonNullMockito.mock;
import static sys.vitae.lorem.NonNullMockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.NonNull;
import org.mockito.ArgumentCaptor;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.kyori.adventure.text.Component;

class RecipeRegistryTest {

    private static final String NAMESPACE = "loremipsum";

    private final Server server = mock(Server.class);
    private final @NonNull ItemStack paper = stack(Material.PAPER);
    private final @NonNull ItemStack tag = stack(Material.NAME_TAG);
    private final Templates templates = mock(Templates.class);
    private final Player online = mock(Player.class);
    private RecipeRegistry registry;

    @BeforeEach
    void registerEntries() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.namespace()).thenReturn(NAMESPACE);
        when(plugin.getServer()).thenReturn(server);
        when(server.getOnlinePlayers()).thenAnswer(_ -> List.of(online));
        when(server.addRecipes(any())).thenReturn(true);
        var emptyInventory = mock(PlayerInventory.class);
        when(online.getInventory()).thenReturn(emptyInventory);
        when(emptyInventory.getContents()).thenReturn(new ItemStack[0]);

        when(paper.clone()).thenReturn(paper);
        when(tag.clone()).thenReturn(tag);
        when(templates.isSheet(paper)).thenReturn(true);
        when(templates.create(any(Templates.Type.class), anyList())).thenReturn(paper);
        when(templates.create(any(Templates.Kind.class))).thenReturn(paper);
        when(templates.createColor(any(ColorRecipe.class))).thenReturn(paper);
        when(templates.createNameTag(any(Templates.Variant.class))).thenReturn(tag);

        registry = new RecipeRegistry(plugin, templates);
        // MaterialChoice checks the live Paper registry; leave shape validation to the real ShapedRecipe.
        try (var items = mockStatic(ItemStack.class);
                var _ = mockConstruction(RecipeChoice.MaterialChoice.class, (choice, context) -> {
                    Object argument = context.arguments().getFirst();
                    List<Material> materials = argument instanceof List<?> list
                            ? list.stream().map(material -> (Material) material).toList() : List.of((Material) argument);
                    when(choice.getChoices()).thenReturn(Objects.requireNonNull(materials));
                    when(choice.validate(false)).thenReturn(choice);
                    when(choice.clone()).thenReturn(choice);
                })) {
            items.when(() -> ItemStack.of(Material.PAPER)).thenReturn(paper);
            items.when(() -> ItemStack.of(Material.NAME_TAG)).thenReturn(tag);
            registry.registerAll();
        }
    }

    @Test
    void registersGroupedPreviewsWithoutMatchingCraftingInputsOrUnlockingAnEmptyInventory() {
        ArgumentCaptor<Iterable<Recipe>> captured = ArgumentCaptor.captor();
        verify(server).addRecipes(captured.capture());
        List<ShapedRecipe> entries = new ArrayList<>();
        for (Recipe recipe : captured.getValue()) {
            entries.add(assertInstanceOf(ShapedRecipe.class, recipe));
        }
        Map<String, ShapedRecipe> byName = entries.stream()
                .collect(Collectors.toMap(recipe -> recipe.getKey().getKey(), recipe -> recipe));

        ShapedRecipe name = byName.get("template_name");
        ShapedRecipe nameReset = byName.get("template_name_reset");
        assertEquals(List.of(Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.COPPER_NUGGET),
                materials(name, 'I'));
        assertNotEquals(name.getGroup(), nameReset.getGroup());
        assertArrayEquals(new String[] {"PI"}, name.getShape());
        assertArrayEquals(new String[] {"PI", "M "}, nameReset.getShape());
        assertEquals(List.of(Material.BONE_MEAL), materials(nameReset, 'M'));
        assertEquals(List.of(Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.COPPER_NUGGET),
                materials(nameReset, 'I'));
        assertEquals(List.of(Material.INK_SAC), materials(byName.get("template_name_copy"), 'M'));

        // Both sheets of the Description example are renamed, so neither may match a real sheet.
        RecipeChoice secondSheet = byName.get("template_lore").getChoiceMap().get('I');
        assertInstanceOf(RecipeChoice.PredicateChoice.class, secondSheet);
        assertFalse(secondSheet.test(paper));
        assertEquals(List.of(Material.PAPER), materials(byName.get("template_lore_reset"), 'I'));
        verify(templates).create(Templates.Type.LORE, List.of(numbered(1), numbered(2)));

        ShapedRecipe color = byName.get("template_color_red_dye");
        ShapedRecipe colorReset = byName.get("template_color_reset_red_dye");
        ShapedRecipe colorCopy = byName.get("template_color_copy_red_dye");
        assertEquals(3, List.of(color.getGroup(), colorReset.getGroup(), colorCopy.getGroup()).stream()
                .distinct().count());
        for (ShapedRecipe example : List.of(color, colorReset, colorCopy)) {
            var group = entries.stream().filter(recipe -> recipe.getGroup().equals(example.getGroup())).toList();
            assertEquals(16, group.size());
            assertTrue(group.stream().allMatch(recipe -> recipe.getCategory() == example.getCategory()
                    && materials(recipe, 'I').size() == 1));
            assertEquals(16, group.stream().map(recipe -> materials(recipe, 'I').getFirst()).distinct().count());
        }
        for (ShapedRecipe entry : entries) {
            RecipeChoice first = entry.getChoiceMap().get('P');
            assertFalse(first.test(paper), entry.getKey().toString());
            assertFalse(first.test(tag), entry.getKey().toString());
        }

        verify(online, never()).discoverRecipes(Objects.requireNonNull(anyCollection()));
    }

    @Test
    void joiningWithBlankPaperUnlocksDescriptionAndANuggetUnlocksName() {
        ItemStack template = stack(Material.PAPER), nugget = stack(Material.GOLD_NUGGET);
        ItemStack boneMeal = stack(Material.BONE_MEAL);
        Player joining = mock(Player.class);
        var book = recipeBook(joining);
        var inventory = inventory(joining);
        when(inventory.getContents()).thenReturn(new ItemStack[] {template, nugget});
        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(joining);
        registry.onPlayerJoin(join);
        assertTrue(book.isEmpty());

        when(inventory.getContents()).thenReturn(new ItemStack[] {paper, boneMeal});
        registry.onPlayerJoin(join);
        assertEquals(keys("template_lore", "template_lore_reset"), book);

        when(inventory.getContents()).thenReturn(new ItemStack[] {paper, nugget, boneMeal});
        inventoryChange(joining, nugget);
        assertEquals(keys("template_lore", "template_lore_reset", "template_name", "template_name_reset"), book);
    }

    @Test
    void inventoryChangesUnlockOnlyHeldDyesAndRememberPreviouslyDiscoveredBases() {
        Player player = mock(Player.class);
        var book = recipeBook(player);
        var inventory = inventory(player);
        ItemStack glowstone = stack(Material.GLOWSTONE_DUST), red = stack(Material.RED_DYE);
        ItemStack ink = stack(Material.INK_SAC);
        when(inventory.getContents()).thenReturn(new ItemStack[] {red, glowstone, ink});
        inventoryChange(player, red);
        assertTrue(book.isEmpty());

        when(inventory.getContents()).thenReturn(new ItemStack[] {paper, red, glowstone});
        inventoryChange(player, paper);
        assertEquals(keys("template_color_red_dye", "template_glint", "template_lore"), book);

        when(inventory.getContents()).thenReturn(new ItemStack[] {ink});
        inventoryChange(player, ink);
        assertEquals(keys("template_color_red_dye", "template_glint", "template_lore",
                "template_color_copy_red_dye", "template_lore_copy"), book);
    }

    @Test
    void nameTagEntriesRequireAnOrdinaryUnnamedTagAndTheirOwnModifier() {
        Player player = mock(Player.class);
        var book = recipeBook(player);
        var inventory = inventory(player);
        ItemStack ink = stack(Material.INK_SAC);
        ItemMeta meta = mock(ItemMeta.class);
        when(tag.getItemMeta()).thenReturn(meta);
        when(inventory.getContents()).thenReturn(new ItemStack[] {tag, ink});
        when(meta.hasCustomName()).thenReturn(true);
        inventoryChange(player, ink);
        assertTrue(book.isEmpty());

        when(meta.hasCustomName()).thenReturn(false);
        inventoryChange(player, tag);
        assertEquals(keys("nametag_copy"), book);
    }

    private void inventoryChange(@NonNull Player player, @NonNull ItemStack added) {
        PlayerInventorySlotChangeEvent event = mock(PlayerInventorySlotChangeEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getNewItemStack()).thenReturn(added);
        registry.onInventoryChange(event);
    }

    private static PlayerInventory inventory(Player player) {
        var inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        return inventory;
    }

    private static Set<NamespacedKey> recipeBook(Player player) {
        var book = new HashSet<NamespacedKey>();
        when(player.hasDiscoveredRecipe(any(NamespacedKey.class, new NamespacedKey(NAMESPACE, "any")))).thenAnswer(
            call -> book.contains(call.<NamespacedKey>getArgument(0))
        );
        when(player.discoverRecipes(Objects.requireNonNull(anyCollection()))).thenAnswer(call -> {
            Collection<NamespacedKey> entries = call.getArgument(0);
            int before = book.size();
            book.addAll(entries);
            return book.size() - before;
        });
        return book;
    }

    private static Set<@NonNull NamespacedKey> keys(String... names) {
        return Arrays.stream(names).map(name -> new NamespacedKey(NAMESPACE, name)).collect(Collectors.toSet());
    }

    private static @NonNull ItemStack stack(Material material) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Objects.requireNonNull(material));
        return stack;
    }

    private static Component numbered(int number) {
        return Component.translatable("potion.withAmplifier", "%s %s",
                Component.translatable("mco.backup.entry.description", "Description"), Component.text(number));
    }

    private static List<Material> materials(ShapedRecipe recipe, char slot) {
        return Objects.requireNonNull(assertInstanceOf(RecipeChoice.MaterialChoice.class,
                recipe.getChoiceMap().get(slot))).getChoices();
    }
}

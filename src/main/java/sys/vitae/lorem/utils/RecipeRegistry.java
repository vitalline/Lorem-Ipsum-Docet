package sys.vitae.lorem.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import net.kyori.adventure.text.Component;
import sys.vitae.lorem.utils.Templates.Kind;
import sys.vitae.lorem.utils.Templates.Type;
import sys.vitae.lorem.utils.Templates.Variant;

public final class RecipeRegistry implements Listener {

    private static final List<Material> NUGGETS =
            List.of(Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.COPPER_NUGGET);
    private static final List<@NonNull Material> DYES = Arrays.stream(DyeColor.values())
            .map(dye -> Material.valueOf(dye.name() + "_DYE")).toList();

    /** A base recipe, unlocked by holding a sheet and any of its ingredients, and its Reset and Copy entries. */
    private record Base(String key, List<Material> ingredients, String reset, @Nullable String copy) {}

    private final Plugin plugin;
    private final Templates templates;
    private final List<ShapedRecipe> entries = new ArrayList<>();
    private final Map<String, NamespacedKey> registered = new HashMap<>();
    private final List<Base> bases = new ArrayList<>();
    private final EnumSet<Material> triggers =
            EnumSet.of(Material.PAPER, Material.BONE_MEAL, Material.INK_SAC, Material.NAME_TAG);

    public RecipeRegistry(Plugin plugin, Templates templates) {
        this.plugin = plugin;
        this.templates = templates;
    }

    public void registerAll() {
        registerBase("template_name", "", Type.NAME,
                templates.create(Type.NAME, List.of(Component.translatable("mco.backup.entry.name", "Name"))),
                NUGGETS, List.of(Component.translatable("mco.backup.entry.name", "Name")));
        List<@NonNull Component> lines = List.of(numbered(1), numbered(2));
        registerBase("template_lore", "", Type.LORE, templates.create(Type.LORE, lines), List.of(Material.PAPER),
                lines);
        registerBase("template_glint", "", Type.GLINT, variant(Type.GLINT, Variant.PLAIN),
                List.of(Material.GLOWSTONE_DUST), List.of());
        for (Material material : DYES) {
            var dye = Objects.requireNonNull(TextStyles.dye(material));
            registerBase("template_color", dyeSuffix(material), Type.COLOR,
                    templates.createColor(new ColorRecipe(List.of(List.of(dye.getColor())))), List.of(material),
                    List.of());
        }
        register("nametag_reset", "nametag_reset", templates.createNameTag(Variant.RESET),
                ItemStack.of(Material.NAME_TAG), new RecipeChoice.MaterialChoice(Material.BONE_MEAL), null);
        register("nametag_copy", "nametag_copy", templates.createNameTag(Variant.COPY),
                ItemStack.of(Material.NAME_TAG), new RecipeChoice.MaterialChoice(Material.INK_SAC), null);

        var server = plugin.getServer();
        if (server.addRecipes(new ArrayList<>(entries))) {
            for (ShapedRecipe entry : entries) {
                registered.put(entry.getKey().getKey(), entry.getKey());
            }
            server.updateRecipes();
            server.getOnlinePlayers().forEach(this::discover);
        } else {
            plugin.getLogger().warning("Could not register the plugin's recipe-book entries");
        }
        entries.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        discover(event.getPlayer());
    }

    @EventHandler
    public void onInventoryChange(PlayerInventorySlotChangeEvent event) {
        if (triggers.contains(event.getNewItemStack().getType())) {
            discover(event.getPlayer());
        }
    }

    private void discover(Player player) {
        if (registered.isEmpty()) {
            return;
        }
        var materials = EnumSet.noneOf(Material.class);
        boolean sheet = false;
        boolean unnamedTag = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            Material material = Objects.requireNonNull(item.getType());
            materials.add(material);
            if (material == Material.PAPER) {
                sheet |= templates.isSheet(item);
            } else if (material == Material.NAME_TAG) {
                unnamedTag |= !item.getItemMeta().hasCustomName() && templates.nameTagVariant(item) == null;
            }
        }

        @NonNull List<NamespacedKey> pending = new ArrayList<>();
        if (sheet) {
            // Whether the sheet is blank or renamed doesn't matter: the book's example shows which one to use.
            for (Base base : bases) {
                if (base.ingredients().stream().anyMatch(materials::contains)) {
                    queue(player, pending, base.key());
                }
            }
        }
        if (!pending.isEmpty()) {
            player.discoverRecipes(pending);
        }

        // Check the book after unlocking the bases: another plugin may veto an individual discovery.
        boolean reset = materials.contains(Material.BONE_MEAL);
        boolean copy = materials.contains(Material.INK_SAC);
        if (!reset && !copy) {
            return;
        }
        @NonNull List<NamespacedKey> variants = new ArrayList<>();
        for (Base base : bases) {
            if (knows(player, base.key())) {
                queueVariants(player, variants, base.reset(), base.copy(), reset, copy);
            }
        }
        if (unnamedTag) {
            queueVariants(player, variants, "nametag_reset", "nametag_copy", reset, copy);
        }
        if (!variants.isEmpty()) {
            player.discoverRecipes(variants);
        }
    }

    private boolean knows(Player player, String key) {
        NamespacedKey id = registered.get(key);
        return id != null && player.hasDiscoveredRecipe(id);
    }

    private void queue(Player player, List<NamespacedKey> pending, String key) {
        NamespacedKey id = registered.get(key);
        if (id != null && !player.hasDiscoveredRecipe(id)) {
            pending.add(id);
        }
    }

    private void queueVariants(Player player, List<NamespacedKey> pending, String resetKey, @Nullable String copyKey,
            boolean reset, boolean copy) {
        if (reset) {
            queue(player, pending, resetKey);
        }
        if (copy && copyKey != null) {
            queue(player, pending, copyKey);
        }
    }

    private static String dyeSuffix(Material dye) {
        return "_" + dye.getKey().getKey();
    }

    /** An example sheet name like "Description 1", using the "Strength II" pattern so translations order it. */
    private static @NonNull Component numbered(int number) {
        return Objects.requireNonNull(Component.translatable("potion.withAmplifier", "%s %s",
                Component.translatable("mco.backup.entry.description", "Description"), Component.text(number)));
    }

    /**
     * The base's example sheets are renamed to {@code names}: the first is the left sheet, a second one is a sheet
     * ingredient. Its Reset and Copy examples are blank.
     */
    private void registerBase(String group, String suffix, Type type, ItemStack result, List<Material> ingredients,
            List<@NonNull Component> names) {
        String key = group + suffix;
        String reset = group + "_reset" + suffix;
        String copy = type.isCopyable() ? group + "_copy" + suffix : null;
        registerTemplate(key, group, result, ingredients, null, names);
        registerTemplate(reset, group + "_reset", variant(type, Variant.RESET), ingredients, Material.BONE_MEAL,
                List.of());
        if (copy != null) {
            registerTemplate(copy, group + "_copy", variant(type, Variant.COPY), ingredients, Material.INK_SAC,
                    List.of());
        }
        bases.add(new Base(key, ingredients, reset, copy));
        triggers.addAll(ingredients);
    }

    private ItemStack variant(Type type, Variant variant) {
        return templates.create(new Kind(type, variant));
    }

    private void registerTemplate(String key, String group, ItemStack result, List<Material> ingredients,
            @Nullable Material modifier, List<@NonNull Component> names) {
        ItemStack paper = sheet(names.isEmpty() ? null : names.getFirst());
        RecipeChoice ingredient = names.size() > 1 ? example(sheet(names.get(1)))
                : new RecipeChoice.MaterialChoice(Objects.requireNonNull(ingredients));
        register(key, group, result, paper, ingredient, modifier);
    }

    private static ItemStack sheet(@Nullable Component name) {
        ItemStack paper = ItemStack.of(Material.PAPER);
        if (name != null) {
            paper.editMeta(meta -> meta.customName(name));
        }
        return paper;
    }

    /** Shows the example in the book without matching a real grid or shadowing vanilla recipes. */
    private static RecipeChoice example(ItemStack item) {
        return RecipeChoice.predicateChoice(_ -> false, Objects.requireNonNull(item));
    }

    private void register(String key, String group, ItemStack result, ItemStack first, RecipeChoice ingredient,
            @Nullable Material modifier) {
        NamespacedKey id = new NamespacedKey(plugin, key);
        ShapedRecipe recipe = new ShapedRecipe(id, result);
        recipe.setGroup(id.getNamespace() + ":" + group);
        recipe.setCategory(CraftingBookCategory.MISC);
        if (modifier == null) {
            recipe.shape("PI");
        } else {
            recipe.shape("PI", "M ");
            recipe.setIngredient('M', new RecipeChoice.MaterialChoice(modifier));
        }
        recipe.setIngredient('P', example(first));
        recipe.setIngredient('I', ingredient);
        entries.add(recipe);
    }
}

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

    private final Plugin plugin;
    private final Templates templates;
    private final List<ShapedRecipe> entries = new ArrayList<>();
    private final Map<String, NamespacedKey> registered = new HashMap<>();

    public RecipeRegistry(Plugin plugin, Templates templates) {
        this.plugin = plugin;
        this.templates = templates;
    }

    public void registerAll() {
        registerNameTemplates();
        registerTemplate("template_lore", "template_lore",
                templates.create(Type.LORE, List.of(Component.text("Description"), Component.empty())),
                List.of(Material.PAPER), null, true);
        registerVariants("template_lore", "", Type.LORE, List.of(Material.PAPER));
        registerTemplate("template_glint", "template_glint", variant(Type.GLINT, Variant.PLAIN),
                List.of(Material.GLOWSTONE_DUST), null, false);
        registerVariants("template_glint", "", Type.GLINT, List.of(Material.GLOWSTONE_DUST));
        registerColorTemplates();
        register("nametag_reset", "nametag_reset", templates.createNameTag(Variant.RESET),
                ItemStack.of(Material.NAME_TAG), List.of(Material.BONE_MEAL), null);
        register("nametag_copy", "nametag_copy", templates.createNameTag(Variant.COPY),
                ItemStack.of(Material.NAME_TAG), List.of(Material.INK_SAC), null);

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
        Material material = event.getNewItemStack().getType();
        if (switch (material) {
            case PAPER, GLOWSTONE_DUST, BONE_MEAL, INK_SAC, NAME_TAG -> true;
            default -> DYES.contains(material);
        }) {
            discover(event.getPlayer());
        }
    }

    private void discover(Player player) {
        if (registered.isEmpty()) {
            return;
        }
        var materials = EnumSet.noneOf(Material.class);
        boolean blankPaper = false;
        boolean renamedPaper = false;
        boolean unnamedTag = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            Material material = Objects.requireNonNull(item.getType());
            materials.add(material);
            if (material == Material.PAPER) {
                blankPaper |= templates.isBlankSheet(item);
                renamedPaper |= templates.isRenamedSheet(item);
            } else if (material == Material.NAME_TAG) {
                unnamedTag |= !item.getItemMeta().hasCustomName() && templates.nameTagVariant(item) == null;
            }
        }

        @NonNull List<NamespacedKey> pending = new ArrayList<>();
        if (renamedPaper) {
            for (Material nugget : NUGGETS) {
                queue(player, pending, nameKey(nugget));
            }
            queue(player, pending, "template_lore");
        }
        if (blankPaper) {
            if (materials.contains(Material.GLOWSTONE_DUST)) {
                queue(player, pending, "template_glint");
            }
            for (Material dye : DYES) {
                if (materials.contains(dye)) {
                    queue(player, pending, "template_color" + dyeSuffix(dye));
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
        if (NUGGETS.stream().anyMatch(nugget -> knows(player, nameKey(nugget)))) {
            queueVariants(player, variants, "template_name", "", reset, copy);
        }
        if (knows(player, "template_lore")) {
            queueVariants(player, variants, "template_lore", "", reset, copy);
        }
        if (knows(player, "template_glint")) {
            queueVariants(player, variants, "template_glint", "", reset, false);
        }
        for (Material dye : DYES) {
            String suffix = dyeSuffix(dye);
            if (knows(player, "template_color" + suffix)) {
                queueVariants(player, variants, "template_color", suffix, reset, copy);
            }
        }
        if (unnamedTag) {
            queueVariants(player, variants, "nametag", "", reset, copy);
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

    private void queueVariants(Player player, List<NamespacedKey> pending, String group, String suffix,
            boolean reset, boolean copy) {
        if (reset) {
            queue(player, pending, group + "_reset" + suffix);
        }
        if (copy) {
            queue(player, pending, group + "_copy" + suffix);
        }
    }

    private static String nameKey(Material nugget) {
        return "template_name_" + nugget.getKey().getKey().replace("_nugget", "");
    }

    private static String dyeSuffix(Material dye) {
        return "_" + dye.getKey().getKey();
    }

    private void registerNameTemplates() {
        for (Material nugget : NUGGETS) {
            registerTemplate(nameKey(nugget), "template_name",
                    templates.create(Type.NAME, List.of(Component.text("Name"))), List.of(nugget), null, true);
        }
        registerVariants("template_name", "", Type.NAME, NUGGETS);
    }

    private void registerColorTemplates() {
        for (Material material : DYES) {
            var dye = Objects.requireNonNull(TextStyles.dye(material));
            String suffix = dyeSuffix(material);
            registerTemplate("template_color" + suffix, "template_color",
                    templates.createColor(new ColorRecipe(List.of(List.of(dye.getColor())))),
                    List.of(material), null, false);
            registerVariants("template_color", suffix, Type.COLOR, List.of(material));
        }
    }

    private void registerVariants(String group, String suffix, Type type, List<Material> ingredients) {
        registerTemplate(group + "_reset" + suffix, group + "_reset", variant(type, Variant.RESET),
                ingredients, Material.BONE_MEAL, false);
        if (type.isCopyable()) {
            registerTemplate(group + "_copy" + suffix, group + "_copy", variant(type, Variant.COPY),
                    ingredients, Material.INK_SAC, false);
        }
    }

    private ItemStack variant(Type type, Variant variant) {
        return templates.create(new Kind(type, variant));
    }

    private void registerTemplate(String key, String group, ItemStack result, List<Material> ingredients,
            @Nullable Material modifier, boolean renamed) {
        ItemStack paper = ItemStack.of(Material.PAPER);
        if (renamed) {
            paper.editMeta(meta -> meta.customName(Component.translatable("mco.backup.entry.name", "Name")));
        }
        register(key, group, result, paper, ingredients, modifier);
    }

    private void register(String key, String group, ItemStack result, ItemStack first, List<Material> ingredients,
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
        // Show the example in the book without matching a real grid or shadowing vanilla recipes.
        recipe.setIngredient('P', RecipeChoice.predicateChoice(item -> false, Objects.requireNonNull(first)));
        recipe.setIngredient('I', new RecipeChoice.MaterialChoice(Objects.requireNonNull(ingredients)));
        entries.add(recipe);
    }
}

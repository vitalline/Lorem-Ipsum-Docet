# Player Guide

Lorem Ipsum Docet lets you make templates for item names, descriptions, glint,
and color; copy or clear those properties; style text and signs; and work with
formatted names. There are no commands.

## Quick start

1. Make a template using the recipe table below. Recipes are shapeless and work
   in either crafting grid.
2. Put the item you want to change in an anvil's first slot and a template in
   the second.
3. Take the result. One template is consumed, and the anvil charges levels
   like a rename.

Each occupied ingredient slot uses one item, no matter how many are in its
stack. A Name template takes precedence over the anvil's rename field; other
templates can be combined with a rename.

## Make templates

| Template | Recipe |
| --- | --- |
| **Name** | One renamed paper + an iron, gold, or copper nugget |
| **Description** | Two paper, at least one renamed |
| **Glowing** | One blank paper + glowstone dust |
| **Colors** | One blank paper + one or more dyes |

“Blank paper” means ordinary paper without a custom name. The Name recipe uses
renamed paper; with blank paper and a nugget, vanilla makes a name tag instead.
For a Description, each paper becomes a line in crafting-grid reading order;
blank paper in the recipe makes an empty line.

### Clear or copy a property

Add **bone meal** to a template recipe to make its **Reset** version, or craft
an existing plain template with bone meal. Use Reset templates in an anvil as
usual to remove that property. A Color reset restores leather armor's default
color; a Glowing reset removes the override (the item may still shimmer if it
has enchantments).

Add an **ink sac** to a Name, Description, or Colors template recipe to make a
**Copy** template. Glint cannot be copied. To use one, craft it with exactly
one item that has the property you want. The source stays in the grid; the
result is a plain template holding the copied property. Copy templates do not
work in an anvil.

### Duplicate a template

Put a template and additional paper in the grid: one template plus two paper
makes three. This works for plain, Reset, and Copy templates. Renamed paper
counts as blank for duplication.

## Color and style

### Leather armor colors

Put an existing Colors template and one or more dyes in the crafting grid to
append another dyeing step; its swatch and hex value preview the result. In an
anvil, the template applies those steps to the leather armor's existing color.
Reapplying it dyes the armor again. Color templates also work as an ingredient
with a Name or Description template to color all of that template's text.

### Name and Description text

Craft a Name or Description template with one styling ingredient. Each
ingredient changes only its own property across all the text. Decorations
toggle off if already enabled everywhere; otherwise they turn on everywhere.
Use multiple dyes together to mix a text color.

| Ingredient | Effect |
| --- | --- |
| Any dye | Set text color; multiple dyes mix |
| End rod | Toggle obfuscated |
| Coal or charcoal | Toggle bold |
| String | Toggle strikethrough |
| Stick | Toggle underline |
| Feather | Toggle italic |
| Bottle o' Enchanting | Enchanting-table font |
| Book | Unifont |
| Ominous bottle | Illager font |
| Ordinary water bottle | Regular font |

Water bottles must be ordinary drinkable water, not custom-effect, splash, or
lingering potions. The empty glass bottle remains after using one.

### Style a sign directly

Right-click a sign with one of the styling ingredients above. The ingredient
styles the side you're targeting; dyes set the sign's native text color.
Survival consumes one ingredient, Creative consumes none. Waxed signs and signs
currently being edited cannot be styled. Existing glow and dye are preserved
when applying other styles.

## Name tags and mobs

| Crafting recipe | Result and use |
| --- | --- |
| Unnamed name tag + bone meal | **Name Tag - Reset**; use on a named mob to remove its name |
| Unnamed name tag + ink sac | **Name Tag - Copy**; use on a named mob to get an ordinary name tag with its name |

Both tags shimmer. They do nothing to unnamed mobs. In Survival, a Reset tag is
consumed on use; a Copy tag is replaced by the named tag (or puts it in your
inventory). In Creative, the special tag is kept. Resetting another player's
tamed animal is not allowed; only its owner can do that. Renaming a special
tag in an anvil turns it into an ordinary name tag.

## Formatted names, signs, and books

In an anvil rename field, use backslash escape codes to add formatting. The
`loremipsum.format` permission is required: by default Creative players and
operators have it, while regular Survival players do not. Server permission
settings can override these defaults.

| Code | Effect |
| --- | --- |
| `\0`–`\9`, `\a`–`\f` | Minecraft colors |
| `\g` `\h` `\i` `\j` | Enchanting-table, Unifont, Illager, regular font |
| `\k` `\l` `\m` `\n` `\o` | Obfuscated, bold, strikethrough, underline, italic |
| `\p` | Reset decorations only |
| `\q` | Reset color only |
| `\r` | Reset color, decorations, and font |
| `\sXX` through `\wXXXXXX` | Unicode code point in hexadecimal |
| `\xRRGGBB` | Hex color |
| `\\` | A literal backslash |

The codes are case-insensitive. On signs, authorized players can use them while
editing; holding Shift opens a plain-text edit instead. Players without the
permission edit plain text. Signed books translate escapes in their pages and
title; fonts do not apply to book titles.

## Placed containers

Place a block-entity item (such as a chest, barrel, shulker box, or banner) that
has a Description or Glowing template applied, and those properties are
preserved when the block drops again. Vanilla already preserves custom names.

For detailed behavior, permissions, and technical notes, see the
[README](README.md).

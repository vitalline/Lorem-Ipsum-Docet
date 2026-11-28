# Player Guide

Lorem Ipsum Docet is a server plugin for customizing item text and appearance.
It adds templates for item names, descriptions, glint, and color; ways to copy
or clear those properties; ingredient-based styling for text and signs; and
escape codes for formatting item text, signs, and books. There are no commands.

The plugin's name plays on the *lorem ipsum* placeholder text. Reading *lorem*
as a mock-Latin form of "lore" (Minecraft's term for item descriptions), it
means roughly "it teaches lore itself".

## Quick start

1. Make a template using the recipe table below. Recipes are shapeless and work
   in either crafting grid.
2. Put the item you want to change in an anvil's first slot and a template in
   the second. Colors templates are the exception: craft them with the item
   instead, like a dye (see [Color and style](#color-and-style)).
3. Take the result. One template is consumed, and the anvil charges levels
   like a rename.

Each occupied ingredient slot uses one item, no matter how many are in its
stack. A Name template takes precedence over the anvil's rename field; other
templates can be combined with a rename. Glowing templates, including their
Resets, don't work on things that always shimmer: templates, Reset and Copy
name tags, and items that shimmer on their own, like nether stars.

## Make templates

The recipe book shows example layouts: paper on the left, the template
ingredient on the right, and any Reset or Copy modifier below the paper.
These are references: place the ingredients yourself in any order.
Mixed-dye and combined styling recipes aren't listed. Styling entries instead
show a Name template on the left and a decoration or font ingredient on the
right; they work the same on Description templates (see
[Name and Description text](#name-and-description-text)).

Recipes unlock as you collect ingredients: holding paper and an entry's
ingredient unlocks it, so paper alone unlocks Description, a nugget unlocks
Name, glowstone dust unlocks Glowing, and a dye unlocks that dye's Colors
entry. Once you know a base recipe, bone meal unlocks its Reset entry and an
ink sac unlocks its Copy entry, if it has one. Holding a Name or Description
template and a decoration or font ingredient unlocks that ingredient's styling
entry.

Unlocking only adds an entry to the recipe book: you can use any of the
recipes before unlocking their entries.

| Template | Recipe |
| --- | --- |
| **Name** | One renamed paper + an iron, gold, or copper nugget |
| **Description** | Two paper, at least one renamed |
| **Glowing** | One blank paper + glowstone dust |
| **Colors** | One blank paper + one or more dyes |

"Blank paper" means ordinary paper without a custom name. The Name recipe uses
renamed paper; with blank paper and a nugget, vanilla makes a name tag instead.
For a Description, each paper becomes a line in crafting-grid reading order;
blank paper in the recipe makes an empty line. Every template shimmers, so it's
easy to tell apart from ordinary paper.

### Clear or copy a property

Add **bone meal** to a template recipe to make its **Reset** version, or craft
an existing plain template with bone meal. Use Name, Description and Glowing
Reset templates in an anvil as usual to remove their property; a Glowing Reset
removes the forced glint (enchanted items may still shimmer). Craft a Colors
Reset with leather armor to restore its default color, or with a Name or
Description template to clear its text color.

Add an **ink sac** to a Name, Description, or Colors template recipe to make a
**Copy** template. Glowing cannot be copied. To use one, craft it with exactly
one item that has the property you want. The source stays in the grid; the
result is a plain template holding the copied property. A Colors Copy can also
take the color of a Name or Description template, or of a name tag's name,
if the corresponding text only has one color.
Copy templates do not work in an anvil.

### Duplicate a template

Put a template and additional paper in the grid: one template plus two paper
makes three. This works for plain, Reset, and Copy templates. Renamed paper
counts as blank for duplication, except with a Description template.

### Extend or edit text templates

Craft a Description template with renamed paper, or with other Description
templates, to join them into one: each template's lines, each renamed paper's
name, and an empty line for each blank paper, in crafting-grid reading order.
With only blank paper, the template is duplicated instead.

Rename a Name or Description template in an anvil to replace the name, or the
whole description, that it applies. This works like renaming an item, treating
all of a description as its name: plain text keeps a style shared by all of it,
a rename made only of escape codes restyles every line, and escape codes need
the `loremipsum.format` permission.

## Color and style

### Colors templates

Colors templates work like a complex dye: craft one with the thing to color,
and both are used up. With leather armor, leather horse armor, or wolf armor,
it dyes the armor, mixing with its existing color; crafting again dyes it
again. With a Name or Description template, or a renamed name tag, it sets all
of that text to the template's color; one tag from a stack is colored at a
time. A Colors Reset does the opposite, removing the armor's dye or the text's
color. Colors templates don't work in an anvil.

Put an existing Colors template and one or more dyes in the crafting grid to
append another dyeing step; its swatch and hex value preview the result.

### Name and Description text

Craft a Name or Description template with one or more styling ingredients.
Each ingredient changes only its own property across all the text, so they can
be combined freely, in any order. Decorations toggle off if already enabled
everywhere; otherwise they turn on everywhere. Each decoration and the font
can only be changed by one ingredient per craft.

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

Styling ingredients can also be added to the Name and Description recipes, so
the template comes out already styled. For example, renamed paper + a nugget +
dyes + a stick + a book + a feather makes a colored, underlined, Unifont,
non-italic Name template; without the nugget, the same ingredients make a
one-line Description. Adding them to a recipe that joins Description templates
styles all of the joined text.

Styling ingredients work on a renamed name tag too: craft one named tag with
them to style its name the same way. One tag from a stack is styled at a time.
A feather toggles italics as the name looks on a mob, where it's upright unless
styled, so one feather makes it italic there.

If all of the text shares one color, a swatch line with its hex value shows it.
A gray **Custom** line under the text indicates any other explicit text
formatting, to tell it apart from unformatted text that may look the same.
Name tags show these lines for their name. They are not applied with the text.

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

Having an ordinary unnamed name tag and the corresponding modifier unlocks
that entry in the recipe book.

Both tags shimmer. They do nothing to unnamed mobs. In Survival, a Reset tag is
consumed on use; a Copy tag is replaced by the named tag (or puts it in your
inventory). In Creative, the special tag is kept. Resetting another player's
tamed animal is not allowed; only its owner can do that. Renaming a special
tag in an anvil turns it into an ordinary name tag.

## Escape codes

In an anvil rename field, use backslash escape codes to add formatting. Since
Name and Description templates are made from renamed paper, that formatting
carries into the names and descriptions they apply. The `loremipsum.format`
permission is required: by default Creative players and operators have it,
while regular Survival players do not. Server permission settings can override
these defaults.

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
permission edit plain text. Signed books apply escape codes in their pages and
title; fonts do not apply to book titles.

## Placed blocks

Place a block-entity item (such as a chest, barrel, shulker box, or banner) that
has a Description or Glowing template applied, and those properties are
preserved when the block drops again. Vanilla already preserves custom names.

For detailed behavior, permissions, and technical notes, see the
[README](README.md).

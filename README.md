# Lorem Ipsum Docet

A Paper plugin for item templates and text customization. Build with Java 25 and
`mvn package`; the plugin JAR is written to `plugins`.

## Templates

All recipes below are shapeless and work in either crafting grid when they fit.
Each occupied ingredient slot contributes one item, regardless of stack size.

| Template | Recipe | Anvil effect |
| --- | --- | --- |
| Name | One renamed sheet of paper + any nugget (iron, gold or copper) | Copies the sheet's name, including formatting, as the item's name |
| Lore | Paper only, with at least one renamed sheet | Copies the sheets' names as description lines, in grid reading order; unrenamed sheets contribute empty lines |
| Glint | One unrenamed sheet of paper + glowstone dust | Forces enchantment shimmer without adding enchantments |
| Color | One unrenamed sheet of paper + one or more dyes | Dyes items supported by Paper's leather/dyeable-armour metadata |

The Name recipe mirrors vanilla's name tag recipe; an unrenamed sheet with a
nugget still crafts a vanilla name tag.

Put the target item in the anvil's first slot and a template in the second.
Applying a template consumes one template and costs levels like a rename. Other
properties are retained. Name templates take precedence over the rename field;
other templates can accompany a rename.

Craft any template with additional paper to copy it: one original plus two
sheets produces three copies. Renamed sheets count as blank when copying.
Craft a non-reset template with **bone meal** to produce its typed reset:

- Name reset removes the custom name.
- Lore reset removes the description.
- Glint reset removes the glint override; enchanted items may still shimmer.
- Color reset removes custom dye and restores the item's default colour.

Reset templates can be copied in the same way. White dye is a colour ingredient,
not a reset ingredient.

### Text colours, decorations, and fonts

Craft a Name or Lore template with one of these ingredients. The same ingredients
can be used on a sign's targeted side by right-clicking it.

| Ingredient | Text effect |
| --- | --- |
| Dye | Replace text colour (signs retain native sign-dye colours) |
| End rod | Toggle obfuscated text |
| Coal or charcoal | Toggle bold |
| String | Toggle strikethrough |
| Stick | Toggle underline |
| Feather | Toggle italic |
| Bottle o' Enchanting | Enchanting-table font (`minecraft:alt`) |
| Book | Unifont (`minecraft:uniform`) |
| Ominous bottle | Illager font (`minecraft:illageralt`) |
| Water bottle | Regular font (`minecraft:default`) |

Each operation replaces only its affected property **throughout** the text,
including nested overrides, while retaining the other properties. Decorations
turn off if already enabled everywhere; otherwise they turn on everywhere.
Template names/lore have the usual italic default; signs do not.

Several dyes can be mixed in one template craft. A Color template can also be
crafted with a Name/Lore template to apply its preview colour to all of that
template's text. Styling consumes the input template(s) and ingredients.

Item tooltips show unformatted names in italics, so a name with explicitly
default formatting (such as white italics) would look identical there, even
though mobs display them differently. Name tags with any explicit formatting
therefore get a grey `Custom` line (vanilla's translated label) after their other
lore. It's added and removed automatically when the tag is renamed or a template
is applied to it in an anvil.

Water bottles must be ordinary drinkable water, without custom potion effects;
other potions and splash/lingering water bottles are not accepted. The empty
glass bottle remains in the crafting grid, or in your hand when styling a sign
in Survival.
If water bottles are stacked by another plugin, extra empty bottles go into your
inventory, dropping nearby only when there is no room.

Sign styling consumes one ingredient in Survival and none in Creative. Waxed
signs, signs currently being edited, and denied interactions are not modified.
Vanilla glow-ink/ink-sac behavior remains available; text styling preserves
the side's glow and dye.

### Color recipes

A Color template shows a coloured block swatch and its hexadecimal RGB colour.
Craft it with more dyes to append another dyeing step.

The template stores the original dyes **and each recipe boundary**, not just the
preview colour. Applying it replays those recipes against the target's existing
dye, using vanilla brightness-preserving mixing and rounding. Repeated dyes
within a recipe retain their weight; applying the template repeatedly dyes the
item repeatedly. An undyed target contributes no initial colour.

### Identification and translation

Templates use the plugin-namespaced persistent string key `template_id`:
`name`, `lore`, `glint`, `color`, and `reset_` followed by the corresponding ID.
Color recipes additionally use the persistent integer-array key `color_recipe`.
Names and lore are presentation/payload, not template identification.

Display names use vanilla client translations, without a resource pack:
`Template: Name`, `Template: Description`, `Template: Glowing`, and
`Template: Colors`. Reset names append the translated `Reset` label.
These deliberately use existing vanilla synonyms. Stored Adventure components
are preserved, including translatable components; player-written text is not
automatically translated.

## Escape substitutions and permissions

`loremipsum.format` controls escape substitutions in anvil renames, signs,
and book signing. With no explicit permission setting, Creative players and
operators can use substitutions; ordinary Survival players cannot. Explicit
grants or denials override those defaults, including for Creative/operators.
Crafted templates and ingredient-based styling do not require this permission.

Existing escape syntax is as follows: `\0`-`\f` and `\xRRGGBB` for colours,
`\k`-`\o` for decorations, `\g`/`\h`/`\i`/`\j` for fonts, `\r` to reset,
`\\` for a literal backslash, and `\s` through `\w` for Unicode code points.
Book-and-quill text stays raw until signing; book titles cannot retain fonts.

### Editing formatted signs

- Authorized players normally edit escaped text, preserving mixed formatting.
- Other players, and authorized players holding Shift when opening the editor,
  edit plain text without escape substitution.
- A changed plain-text edit restores visual formatting only when it was uniform
  across all nonempty text on that side. Mixed formatting is stripped entirely.
  Native sign dye and glow are preserved.
- An unchanged submission restores all original components, including mixed
  formatting. Rejected edits leave the real sign untouched.

Only a client-side editing snapshot is stripped: opening the editor never changes
the stored sign. Minecraft normally submits sign edits when closing the editor,
including with Escape; Escape is not a separate server-visible cancel action.

## Tests

Run `mvn test` for template recipes, property isolation, resets, colour replay,
whole-text styling, permission overrides, and sign-editing regressions.

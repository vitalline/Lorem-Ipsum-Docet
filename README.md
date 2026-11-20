# Lorem Ipsum Docet

A Paper plugin for item templates and text customization. Its name plays on the
*lorem ipsum* placeholder text: reading *lorem* as a mock-Latin form of "lore"
(Minecraft's term for item descriptions), it means roughly "it teaches lore itself".

Build with Java 25 and `mvn package`; the development JAR is written to `target`
and overwritten on every build. Run `mvn package -Prelease` to publish a finished
version to `plugins`; this fails if that version's JAR already exists there,
so bump the version first.

For crafting recipes and player-facing instructions, see the
[Player Guide](PLAYER_GUIDE.md).

## Templates

All recipes below are shapeless and work in either crafting grid when they fit.
Each occupied ingredient slot contributes one item, regardless of stack size.

| Template | Recipe | Anvil effect |
| --- | --- | --- |
| Name | One renamed sheet of paper + any nugget (iron, gold or copper) | Copies the sheet's name, including formatting, as the item's name |
| Lore | Paper only, with at least one renamed sheet | Copies the sheets' names as description lines, in grid reading order; unrenamed sheets contribute empty lines |
| Glint | One unrenamed sheet of paper + glowstone dust | Forces enchantment shimmer without adding enchantments |
| Color | One unrenamed sheet of paper + one or more dyes | Dyes items supported by Paper's leather/dyeable-armor metadata |

(NB: In game, as well as in the Player Guide, Lore, Glint and Color templates
are called Description, Glowing and Colors. For further details, see
[Identification and translation](#identification-and-translation).)

The Name recipe mirrors vanilla's name tag recipe; an unrenamed sheet with a
nugget still crafts a vanilla name tag.

Put the target item in the anvil's first slot and a template in the second.
Applying a template consumes one template and costs levels like a rename. Other
properties are retained. Name templates take precedence over the rename field;
other templates can accompany a rename.

Craft any template, including reset and copy templates, with additional paper
to duplicate it: one original plus two sheets produces three. Renamed sheets
count as blank when duplicating, except with a plain Lore template (see below).

### Extending and renaming text templates

A plain Lore template crafted with at least one renamed sheet, or with other
plain Lore templates, is concatenated like the Lore recipe: in grid reading
order, each template contributes all its lines, each renamed sheet its name, and
each blank sheet an empty line. With only blank sheets, it's duplicated instead.

Renaming a plain Name or Lore template in an anvil replaces the name, or the
whole description, that it applies, rather than naming the sheet. This follows
the rules for renaming an item, taking all of a description as its name: escape
substitutions need `loremipsum.format`; a formatting-only rename restyles every
line, keeping all of them (unless a line can't be expressed with escapes); and
plain text keeps the style shared by all the non-empty text, if there is one (see
[Renaming formatted items](#renaming-formatted-items)). The rename field shows
the template's label; leaving it unchanged or clearing it changes nothing.

### Reset templates

Craft a plain template (neither a reset nor a copy) with **bone meal** to
produce its typed reset (`Template: Name - Reset`, and so on), or craft one
directly by adding bone meal to the plain template's recipe. As a reset has no
text, renamed and blank paper are interchangeable in direct recipes.

| Reset | Direct recipe | Anvil effect |
| --- | --- | --- |
| Name | One sheet of paper + any nugget + bone meal | Removes the custom name |
| Lore | Exactly two sheets of paper + bone meal | Removes the description |
| Glint | One sheet of paper + glowstone dust + bone meal | Removes the glint override; enchanted items may still shimmer |
| Color | One sheet of paper + one or more dyes + bone meal | Removes custom dye and restores the item's default color |

Reset templates are applied in an anvil like any other template. White dye is a
color ingredient, not a reset ingredient.

### Copy templates

Craft a plain Name, Lore or Color template with an **ink sac** to produce its
typed copy template (`Template: Name - Copy`, and so on), or craft one directly
by adding an ink sac to the plain template's recipe. As a copy has no text yet,
renamed and blank paper are interchangeable in direct recipes. There is no Glint
copy template: glint is just on or off.

| Copy | Direct recipe | Takes from the source item |
| --- | --- | --- |
| Name | One sheet of paper + any nugget + ink sac | Its custom name, including formatting |
| Lore | Exactly two sheets of paper + ink sac | Its description; a name tag's gray `Custom` marker isn't part of it |
| Color | One sheet of paper + one or more dyes + ink sac | Its exact dye color |

Craft a copy template with exactly one item that has the corresponding property
to turn it into a plain template holding that property. The source item isn't
used up; it stays in the grid, so shift-clicking converts every copy template in
the stack. A copied color is applied regardless of the target's dye; dyes
crafted onto the Color template afterwards are applied on top of it.

Items without the property (an unnamed item for Name, say) produce nothing.
Any single item counts as a source, even ingredients that would otherwise
style or convert a template. Renamed paper counts as blank paper, so it
duplicates the copy template instead; use the Name recipe to turn a renamed
sheet into a Name template. Copy templates have no effect in an anvil.

### Name reset tags

Craft an unnamed name tag with **bone meal** to get a shimmering name reset tag
(`Name Tag - Reset`). Use it on a named mob, as you would a name tag, to remove
the name. Like a vanilla name tag, it's consumed in Survival but not in Creative.
It does nothing to unnamed mobs, and the click doesn't open a villager's trades
or make a pet sit. Only the owner can clear a tamed animal's name.
Interactions denied by protection plugins are left alone.

- Named name tags aren't accepted, so a name is never wiped by accident.
- The mob stays persistent: vanilla naming prevents despawning, and there's no
  telling whether the mob despawned before it was named.
- Removing a name also ends its vanilla Easter eggs, such as `Dinnerbone`,
  `jeb_`, `Toast` and `Johnny`.
- Renaming a reset tag in an anvil turns it into an ordinary named tag.

### Name copy tags

Craft an unnamed name tag with an **ink sac** to get a shimmering name copy tag
(`Name Tag - Copy`). Use it on a named mob to turn one copy tag into an ordinary
name tag carrying that mob's name, formatting included. The mob keeps its name.
In Survival the copy tag is used up; the last one in hand becomes the named tag.
Otherwise the named tag goes into your inventory, or drops nearby if it's full.
In Creative the copy tag is kept.

Copy tags work on any named mob, including other players' pets. They do nothing
to unnamed mobs, and the click doesn't open a villager's trades or make a pet
sit. Interactions denied by protection plugins are left alone. Named name tags
and reset tags can't be made into copy tags, and renaming a copy tag in an anvil
turns it into an ordinary named tag.

### Text colors, decorations, and fonts

Craft a Name or Lore template with one of these ingredients. The same ingredients
can be used on a sign's targeted side by right-clicking it.

| Ingredient | Text effect |
| --- | --- |
| Dye | Replace text color (signs retain native sign-dye colors) |
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

Name templates preview their name in white, like an unstyled custom name,
instead of lore's usual purple.

Several dyes can be mixed in one template craft. A Color template can also be
crafted with a Name/Lore template to apply its preview color to all of that
template's text. Styling consumes the input template(s) and ingredients.

Explicit formatting can look exactly like the default in a tooltip, yet differ
once applied - for example, a name in explicit white italics previews just like
an unstyled one, but on a mob only the unstyled name shows upright. To tell them
apart, Name and Lore templates with text that has any explicit formatting get a
gray `Custom` line (vanilla's translated label) after their text. It only marks
the template; it isn't part of the text the template applies, joins or is
renamed from.

Name tags are marked the same way, after their other lore. Their marker is added
and removed automatically when the tag is renamed or a template is applied to it
in an anvil, and a Lore Copy template doesn't take it along.

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

A Color template shows a colored block swatch and its hexadecimal RGB color.
Craft it with more dyes to append another dyeing step.

The template stores the original dyes **and each recipe boundary**, not just the
preview color. Applying it replays those recipes against the target's existing
dye, using vanilla brightness-preserving mixing and rounding. Repeated dyes
within a recipe retain their weight; applying the template repeatedly dyes the
item repeatedly. An undyed target contributes no initial color.

### Placed blocks

Vanilla keeps a container's custom name when it's placed and broken, but drops
its description and glint override. Blocks with a block entity (shulker boxes,
chests, barrels, banners, and so on) placed from an item with either now drop
it back with them, whether broken by a player, a piston, a fluid, or an
explosion. Properties the drop already has are kept.

- Items spilling out of a broken container are untouched: they keep everything
  they had. Only an item identical to what the block would have dropped (same
  name, contents and other data) can get the block's properties.
- Creative breaks don't drop the block itself, so there is nothing to restore.
- Explosions merge identical drops into one stack, which is split into the
  restored items and the remainder. As explosions report no drops, the block's
  item is matched among the items spawned where the explosion's blocks were.
  Items identical to it that spill out of any exploded block (a plain chest
  burning in a furnace beside an exploding chest, say) are counted off first,
  so a block whose own drop was destroyed passes nothing on. Contents generated
  only on breaking, as in unopened loot chests, can't be counted.

### Identification and translation

Templates use the plugin-namespaced persistent string key `template_id`:
`name`, `lore`, `glint`, `color`, `reset_` followed by any of those, and `copy_`
followed by `name`, `lore` or `color`.
Color recipes additionally use the persistent integer-array key `color_recipe`.
Each dyeing step is stored as its dye count followed by the dyes' RGB values. A
copied color is stored first, as `0` followed by its RGB value.
Name reset and copy tags use the persistent string key `name_tag_id` with the
value `reset` or `copy`, and have no custom name, so vanilla never applies them
as a name.
Placed blocks keep their item's lore as Adventure JSON under the persistent
string-list key `placed_lore`, and its glint override under the boolean key
`placed_glint`.
Names and lore are presentation/payload, not template identification.

Display names use vanilla client translations, without a resource pack:
`Template: Name`, `Template: Description`, `Template: Glowing`, and
`Template: Colors`. Reset and copy names append the translated `Reset` or
`Copy` label.
These deliberately use existing vanilla synonyms. Stored Adventure components
are preserved, including translatable components; player-written text is not
automatically translated.

## Escape substitutions and permissions

`loremipsum.format` controls escape substitutions in anvil renames, signs,
and book signing. With no explicit permission setting, Creative players and
operators can use substitutions; ordinary Survival players cannot. Explicit
grants or denials override those defaults, including for Creative/operators.
Crafted templates and ingredient-based styling do not require this permission.

Existing escape syntax is as follows: `\0`-`\f` and `\xRRGGBB` for colors,
`\g`/`\h`/`\i`/`\j` for fonts, `\k`-`\o` for decorations, `\p` to reset
decorations only, `\q` to reset color only, and `\r` to reset all formatting.
Use `\s` through `\w` for Unicode code points, and `\\` for a literal backslash.
Book-and-quill text stays raw until signing; book titles cannot retain fonts.

### Renaming formatted items

The anvil's rename field only shows plain text. A plain-text rename (without
escape substitutions) keeps the style of the item's current name when the whole
name shares one style; this applies to all players. Mixed formatting is dropped,
as in vanilla. To get an unstyled name, remove the name first (clear the rename
field, or apply a Name reset), then rename the item.

### Editing formatted signs

- Authorized players normally edit escaped text, preserving mixed formatting.
- Other players, and authorized players holding Shift when opening the editor,
  edit plain text without escape substitution.
- A changed plain-text edit restores visual formatting only when it was uniform
  across all non-empty text on that side. Mixed formatting is stripped entirely.
  Native sign dye and glow are preserved.
- An unchanged submission restores all original components, including mixed
  formatting. Rejected edits leave the real sign untouched.

Only a client-side editing snapshot is stripped: opening the editor never changes
the stored sign. Minecraft normally submits sign edits when closing the editor,
including with Escape; Escape is not a separate server-visible cancel action.

## Tests

Run `mvn test` for template recipes, property isolation, resets, copies, color
replay, whole-text styling, name reset and copy tags, permission overrides,
sign-editing regressions, and placed-block drops.

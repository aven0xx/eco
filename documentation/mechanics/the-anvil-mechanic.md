---
title: The Anvil Mechanic
sidebar_position: 0
---

eco ships a custom anvil system that replaces vanilla's result-slot behaviour with one
that eco (and eco plugins such as EcoEnchants) fully control. This page explains how it
works end to end: the design, the event flow, the merge algorithm, the cost maths, and
how a plugin plugs its own enchant rules into it.

## Why eco replaces the vanilla anvil

Vanilla decides anvil results deep in the server internals, before most plugins get a say.
That makes it very hard to support custom enchantments, enchantment level caps, curses,
per-server enchant limits, or custom repair materials — the vanilla logic simply doesn't
know about them.

Instead of fighting the vanilla result, eco **suppresses it** and computes its own result
from scratch. This gives eco plugins complete control over how items combine, while eco
itself stays config-agnostic: eco owns the *mechanics*, and the plugin that registers a
handler owns the *rules*.

:::info
If no plugin registers an anvil handler, the eco anvil shell is completely inert and your
server's anvils behave exactly like vanilla. The mechanic only activates once something
calls `AnvilHandlers.register(...)`.
:::

## The "shell and handler" design

The system is split into two halves:

- **The shell** — owned by eco. Handles event listening, suppressing the vanilla result,
  renaming, durability repair, unit repair (e.g. iron ingots), XP cost, prior-work
  penalty, and rendering the final preview into the result slot.
- **The handler** — supplied by a plugin. Decides only the enchant-specific questions:
  *can this enchant be added?*, *what is this enchant's max level?*, and *should this
  combination be blocked entirely?*

The shell talks to the handler through three small API types in the
`com.willfp.eco.core.anvil` package.

### `AnvilHandler`

The interface a plugin implements to describe its enchant behaviour.

```kotlin
interface AnvilHandler {
    // Whether `enchant` at `level` may be added to `target`, given the enchants already present.
    fun canCombine(
        enchant: Enchantment,
        level: Int,
        target: ItemStack,
        existing: Set<Enchantment>
    ): Boolean

    // The maximum level the shell should clamp `enchant` to when two equal levels combine.
    fun maxLevel(enchant: Enchantment): Int = enchant.maxLevel

    // If true, the anvil produces no result at all (e.g. a curse that blocks combination).
    fun isBlocked(left: ItemStack?, right: ItemStack?): Boolean = false
}
```

Only `canCombine` is mandatory; `maxLevel` and `isBlocked` have sensible defaults.

### `AnvilSettings`

The numeric and behavioural knobs the shell reads. Because eco is config-agnostic, the
registering plugin owns these values (usually mapping them from its own config).

```kotlin
data class AnvilSettings(
    val costExponent: Double,        // how steeply XP cost scales with enchant-level difference (vanilla ≈ 1.0)
    val enchantLimit: Int,           // max enchants on an item; any value below 1 means unlimited
    val useReworkPenalty: Boolean,   // apply vanilla's escalating prior-work penalty
    val maxRepairCost: Int,          // the "Too Expensive!" ceiling
    val clampRepairCost: Boolean,    // clamp the cost to maxRepairCost instead of rejecting it
    val colorNameAllowed: (Player) -> Boolean // whether this player may use colour codes when renaming
)
```

### `AnvilHandlers`

The global registry the shell reads from. It holds a **single** handler + settings pair;
registering again replaces the previous registration.

```kotlin
AnvilHandlers.register(myHandler, mySettings) // activate the shell
AnvilHandlers.unregister()                    // deactivate; anvils return to vanilla
AnvilHandlers.handler()                        // current handler, or null
AnvilHandlers.settings()                       // current settings, or null
```

:::warning
Only one handler can be active at a time. If two plugins both register, the last one to
call `register` wins, silently. In practice a single enchantment plugin (e.g. EcoEnchants)
owns this.
:::

## The classes involved

| Class | Location | Responsibility |
| --- | --- | --- |
| `AnvilHandler` | `eco-api` · `core/anvil` | Interface plugins implement for enchant rules. |
| `AnvilSettings` | `eco-api` · `core/anvil` | Data class of numeric/behavioural knobs. |
| `AnvilHandlers` | `eco-api` · `core/anvil` | Global single-slot registry. |
| `AnvilRecipe` | `eco-api` · `core/recipe/workstation` | A custom "base + material → output" anvil recipe. |
| `AnvilMechanicsListener` | `core-plugin` · `internal/spigot/anvil` | The shell. Listens to anvil events and computes the result. |
| `AnvilRepair` | `core-plugin` · `internal/spigot/anvil` | Loads the unit-repair material table from `anvil/repair.json`. |
| `AnvilMath` | `core-plugin` · `internal/spigot/anvil` | Pure helper functions for cost and level maths. |
| `WorkstationRecipeListener` | `core-plugin` · `internal/spigot/recipes/workstation` | Applies custom `AnvilRecipe`s (and other workstation recipes). |
| `UnenchantablePatch` | `core-plugin` · `internal/spigot/eventlisteners` | Blocks anvil/enchant combining of items flagged unenchantable. |

## The event flow

All of the interesting work happens in `AnvilMechanicsListener` on the Bukkit
`PrepareAnvilEvent`, which fires whenever the contents of an anvil's input slots change.

The flow, step by step:

1. **Bail out if inactive.** If no handler is registered, the listener returns immediately
   and vanilla behaviour applies.

2. **Defer to custom recipes.** `WorkstationRecipeListener` handles `PrepareAnvilEvent` at
   `HIGH` priority; the mechanics shell runs later at `HIGHEST`. If a custom `AnvilRecipe`
   matches the current inputs, the shell recognises that (by re-matching against the
   registered recipes), clears any stale preview state, and returns — letting the recipe's
   result stand. See [Custom anvil recipes](#custom-anvil-recipes) below.

3. **Bump the preview generation.** Each player has a per-player counter. Every prepare
   event increments it. This is how the shell knows whether a result it computes later is
   still the *latest* one the player asked for (see [Stale-preview protection](#stale-preview-protection)).

4. **Check `isBlocked`.** If the handler blocks this combination, the result is cleared and
   the shell returns — no result, no cost.

5. **Skip AnvilGUI menus.** If the open inventory is an [AnvilGUI](https://github.com/WesJD/AnvilGUI)
   container (detected reflectively), the shell steps aside so the library's own text-input
   handling isn't disturbed.

6. **Suppress the vanilla result and compute the eco result.** The vanilla result is
   cleared immediately, then the merge is computed on the next tick via the scheduler (so
   the shell sees the fully-updated inventory). The heavy lifting happens in `doMerge`.

7. **Apply cost, prior-work penalty, and the "Too Expensive!" ceiling**, then render the
   result — but only if this is still the latest preview for the player.

### Stale-preview protection

Because the result is computed a tick after the event (to let the inventory settle), a
player editing the inputs quickly could end up with a result computed from *old* inputs
rendered into the slot. The shell guards against this with two per-player counters:

- `latestPreviewGeneration` — bumped on every prepare event.
- `renderedPreviewGeneration` — the generation actually rendered into the result slot.

The computed result is only rendered if its generation still equals `latestPreviewGeneration`,
and the check is repeated at every re-entry point. In addition, `onAnvilResultClick`
(at `HIGHEST` priority) cancels a click on the result slot if the rendered generation is
stale, preventing a player from grabbing a result that's about to be recomputed. Both
counters are cleared when the player closes the anvil (`onAnvilClose`) or disconnects
(`onQuit`).

## The merge algorithm

`doMerge(left, right, itemName, player, handler, settings)` computes the result of
combining the **left** slot (the item being upgraded/repaired) with the **right** slot (the
sacrifice, material, or enchanted book). It returns a result item and its base XP cost, or
a failure sentinel meaning "no valid result".

### 1. Empty left → no result

If the left slot is empty, there is nothing to produce and the merge fails.

### 2. Resolve the rename

The typed name is formatted first:

- If `colorNameAllowed(player)` is true, colour/format codes in the name are applied.
- Otherwise, any colour codes are stripped.
- If the resulting name is empty, the item's current display name is kept.

### 3. Rename-only (empty right slot)

If only the left slot is filled, the anvil is being used purely to rename:

- If the new name equals the current name, nothing changed → the merge fails (no result).
- Otherwise the new (italic) name is applied and the result is returned with an XP cost of `0`
  (plus the base repair cost handled later).

### 4. Two items — repair and combine

When both slots are filled:

**Unit repair (different materials).** If the two items are different types and the right
material can unit-repair the left (per the [repair table](#the-unit-repair-table)) — e.g.
iron ingots repairing an iron chestplate:

- Each unit repairs `ceil(maxDurability / 4)` durability.
- The number of units consumed is the smaller of "units needed to fully repair" and "units
  available in the stack".
- If no units are actually needed, the merge fails. Otherwise the damage is reduced and the
  consumed units are removed from the right stack. The number of units consumed becomes the
  `unitRepairCost`, which feeds into the XP cost.

If the items are different types and the right item *can't* unit-repair the left, the only
other valid combination is an **enchanted book**; anything else fails.

**Enchant merging.** Every enchant on the right item is merged into the left:

- If the enchant is **already present**, the two levels combine via `mergeEnchantLevel`:
  equal levels bump up by one (capped at the handler's `maxLevel`); otherwise the higher of
  the two levels wins.
- If the enchant is **new**, it is added only if `handler.canCombine(...)` allows it **and**
  the item hasn't hit the `enchantLimit` (values below 1 mean unlimited).

**Durability merge (same-material tool + tool).** If both items are damageable, no unit
repair happened, and the right item isn't an enchanted book, their remaining durabilities
are added together (capped at the item's maximum) — this is vanilla's "combine two of the
same tool" repair.

**Applying enchants.** The merged enchant set is written back onto the item — as *stored*
enchants for enchanted books (`EnchantmentStorageMeta`), or as normal enchants otherwise.

### 5. Compute the XP cost

The base XP cost is derived from how much the enchants changed plus any unit-repair cost:

```
enchantLevelDiff = |sum(levels before) − sum(levels after)|
xpCost           = round( enchantLevelDiff ^ costExponent + unitRepairCost )
```

`costExponent` (from `AnvilSettings`) controls how steeply cost scales — vanilla uses `1.0`
(linear).

## Cost, prior-work penalty, and the ceiling

Back in the event handler, the base repair cost reported by the anvil view is combined with
the merge's XP cost, and then:

- **Prior-work penalty.** If `useReworkPenalty` is enabled, the result item's stored repair
  cost is escalated using vanilla's formula `(cost + 1) * 2 − 1`. This is the familiar
  effect where an item becomes more expensive to work on each successive time
  (`0 → 1 → 3 → 7 → …`).
- **The "Too Expensive!" ceiling.** `maxRepairCost` sets the ceiling. With
  `clampRepairCost` enabled, costs above the ceiling are clamped down to it; with it
  disabled, a cost at or above the ceiling produces no result at all (the vanilla "Too
  Expensive!" behaviour).

A non-positive final cost produces no result.

## The unit-repair table

`AnvilRepair` loads a table from `anvil/repair.json` on the classpath describing which
materials can repair which items (iron ingots → iron gear, diamonds → diamond gear, planks →
wooden tools, and so on). Each entry maps a set of `units` (repair materials) to a set of
`repairable` item materials.

Loading this from a resource file rather than hard-coding it means:

- The table can be tweaked or extended without code changes.
- Material names unknown on the running server version (for example newer copper or spear
  items on an older server) are silently skipped, so the **same fixture works across every
  supported Minecraft version**.

A units entry may also reference a tag with the `TAG:` prefix (currently `TAG:PLANKS`),
which expands to every material in that tag.

## Custom anvil recipes

Separately from enchant merging, eco supports fully custom anvil *recipes* through
`AnvilRecipe` — "put **base** item (and optionally a **material** item) in the anvil to
produce this **output** at this repair cost". These are applied by
`WorkstationRecipeListener`, alongside grindstone, brewing, smelting, and villager recipes.

An `AnvilRecipe` is built with a builder:

```java
AnvilRecipe.builder(key, output, baseItem)
    .material(materialItem)     // optional right-slot requirement
    .baseDisplay(displayItem)   // optional display item for the base slot
    .materialDisplay(displayItem)
    .resultName("&aReforged Blade")   // optional name applied to the result
    .repairCost(5)              // XP level cost (default 1)
    .permission("myplugin.anvil.reforge")
    .build()
    .register();
```

Because a custom recipe is matched at `HIGH` priority and the enchant shell runs at
`HIGHEST`, a matching recipe always takes precedence: the shell detects the match and steps
aside so it never clobbers the recipe's result.

## Unenchantable items

`UnenchantablePatch` complements the shell by enforcing eco's *unenchantable* item flag. An
item marked unenchantable (via the item-lookup `unenchantable` argument) can't be enchanted
through the enchanting table, and any anvil result that would add or change its enchants is
cancelled at click time. The block is done at click time rather than on the prepare event
because plugins that recalculate results asynchronously (like EcoEnchants) may not have
finished by the time the prepare event fires.

## For plugin developers: putting it together

To take control of the eco anvil, register a handler and settings — typically once, when
your plugin enables:

```kotlin
AnvilHandlers.register(
    object : AnvilHandler {
        override fun canCombine(
            enchant: Enchantment,
            level: Int,
            target: ItemStack,
            existing: Set<Enchantment>
        ): Boolean {
            // your rules: conflicts, applicability, curses, custom enchants, ...
            return true
        }

        override fun maxLevel(enchant: Enchantment): Int =
            // e.g. look up your custom cap
            enchant.maxLevel

        override fun isBlocked(left: ItemStack?, right: ItemStack?): Boolean =
            // e.g. block if a curse forbids combining
            false
    },
    AnvilSettings(
        costExponent = 1.0,
        enchantLimit = 0,            // unlimited
        useReworkPenalty = true,
        maxRepairCost = 40,
        clampRepairCost = false,
        colorNameAllowed = { player -> player.hasPermission("myplugin.anvil.color") }
    )
)
```

From that point on, every anvil on the server routes through the eco shell, and your handler
decides how enchants combine while eco handles everything else. Call
`AnvilHandlers.unregister()` to hand anvils back to vanilla.

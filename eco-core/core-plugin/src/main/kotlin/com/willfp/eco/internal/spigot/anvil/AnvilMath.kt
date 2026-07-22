package com.willfp.eco.internal.spigot.anvil

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Treat any value below 1 as effectively unlimited. */
fun Int.infiniteIfNegative() = if (this < 1) Int.MAX_VALUE else this

/** Vanilla restores 25% of an item's max durability per repair unit (e.g. one ingot). */
const val VANILLA_REPAIR_FRACTION = 0.25

/**
 * Durability restored per repair unit consumed: `ceil(maxDurability * fraction)`, floored at 1.
 *
 * With [fraction] == [VANILLA_REPAIR_FRACTION] this is identical to vanilla's
 * `ceil(maxDurability / 4)`. The floor of 1 guards against a zero/negative [fraction] (or a
 * near-zero durability) producing a non-positive step, which would otherwise stall repair.
 *
 * Examples: repairPerUnit(1561, 0.25) -> 391. repairPerUnit(1000, 0.1) -> 100.
 */
fun repairPerUnit(maxDurability: Int, fraction: Double): Int =
    max(1, ceil(maxDurability * fraction).toInt())

/**
 * Vanilla-style prior-work penalty: (cost + 1) * 2 - 1.
 *
 * Applied each time an item is used as an input on an anvil, so the "repair cost"
 * (level cost shown in the anvil UI) escalates the more an item has been worked on.
 *
 * Examples: repairCost=0 -> 1, repairCost=1 -> 3, repairCost=3 -> 7.
 */
fun applyReworkPenalty(repairCost: Int): Int = (repairCost + 1) * 2 - 1

/**
 * XP cost = enchantLevelDiff^costExponent + unitRepairCost, rounded.
 *
 * [enchantLevelDiff] is the total enchant-level sum added/changed by the merge,
 * [unitRepairCost] is the number of repair units (e.g. ingots) consumed, and
 * [costExponent] controls how steeply cost scales with enchant level diff (vanilla uses 1.0).
 *
 * Examples: enchantLevelDiff=2, unitRepairCost=0, costExponent=1.0 -> 2.
 * enchantLevelDiff=3, unitRepairCost=1, costExponent=1.0 -> 4.
 */
fun computeXpCost(enchantLevelDiff: Int, unitRepairCost: Int, costExponent: Double): Int =
    (enchantLevelDiff.toDouble().pow(costExponent) + unitRepairCost).roundToInt()

/**
 * Per-enchant merge rule for an enchant already present on the target.
 * Equal levels bump by one (capped at [maxLevel]); otherwise take the higher.
 */
fun mergeEnchantLevel(existing: Int, incoming: Int, maxLevel: Int): Int =
    if (incoming == existing) min(maxLevel, incoming + 1) else max(incoming, existing)

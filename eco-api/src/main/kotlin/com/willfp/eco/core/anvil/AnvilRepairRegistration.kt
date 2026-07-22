package com.willfp.eco.core.anvil

import com.willfp.eco.core.items.Items
import com.willfp.eco.core.items.TestableItem

/**
 * A plugin-supplied anvil repair rule: placing a [material] item in the anvil alongside a
 * [repairable] item restores the repairable item's durability (it is **not** replaced by a new
 * item). Both sides are matched with the eco item lookup system, so custom / eco items are
 * supported on either side, in addition to vanilla materials.
 *
 * This is the extensible counterpart to eco's built-in vanilla unit-repair table (e.g. iron
 * ingots repairing iron tools): the vanilla table is [org.bukkit.Material]-only, whereas a
 * registration can target a specific eco item and be fed by a custom repair material.
 *
 * Register with [register] (or [AnvilRepairs.register]). Repairs work whether or not an
 * [AnvilHandler] is also registered — a repair-only plugin does not need to be an enchant
 * handler.
 *
 * @property repairable    The item that gets repaired (left anvil slot).
 * @property material       The item consumed to repair it (right anvil slot).
 * @property repairFraction Fraction of the repaired item's max durability restored per unit of
 *                          [material] consumed. `0.25` matches vanilla (25% per unit). Expected
 *                          range is `(0.0, 1.0]`; each unit always restores at least 1 durability.
 * @property xpCostPerUnit  Experience-level cost added per unit of [material] consumed.
 */
class AnvilRepairRegistration private constructor(
    val repairable: TestableItem,
    val material: TestableItem,
    val repairFraction: Double,
    val xpCostPerUnit: Int
) {
    /** Register this repair with the global [AnvilRepairs] registry. */
    fun register() {
        AnvilRepairs.register(this)
    }

    companion object {
        /** Vanilla restores 25% of an item's max durability per repair unit. */
        const val DEFAULT_REPAIR_FRACTION = 0.25

        /** Default experience-level cost per unit consumed. */
        const val DEFAULT_XP_COST_PER_UNIT = 1

        /**
         * Create a builder for a repair of [repairable] using [material].
         *
         * Both arguments accept any [TestableItem], so vanilla materials, eco items, and custom
         * items from other plugins can all be used — e.g.
         * `Items.lookup("diamond_sword")` or `Items.lookup("myplugin:reinforced_blade")`.
         */
        @JvmStatic
        fun builder(repairable: TestableItem, material: TestableItem): Builder =
            Builder(repairable, material)

        /**
         * Convenience builder overload accepting item-lookup keys directly, resolved via
         * [Items.lookup].
         */
        @JvmStatic
        fun builder(repairable: String, material: String): Builder =
            Builder(Items.lookup(repairable), Items.lookup(material))
    }

    /** Builder for [AnvilRepairRegistration]. */
    class Builder internal constructor(
        private val repairable: TestableItem,
        private val material: TestableItem
    ) {
        private var repairFraction = DEFAULT_REPAIR_FRACTION
        private var xpCostPerUnit = DEFAULT_XP_COST_PER_UNIT

        /**
         * Set the fraction of max durability restored per unit consumed. Defaults to
         * [DEFAULT_REPAIR_FRACTION] (vanilla-equivalent 25%).
         */
        fun repairFraction(repairFraction: Double): Builder = apply {
            this.repairFraction = repairFraction
        }

        /** Set the experience-level cost per unit consumed. Defaults to [DEFAULT_XP_COST_PER_UNIT]. */
        fun xpCostPerUnit(xpCostPerUnit: Int): Builder = apply {
            this.xpCostPerUnit = xpCostPerUnit
        }

        /** Build the [AnvilRepairRegistration]. */
        fun build(): AnvilRepairRegistration =
            AnvilRepairRegistration(repairable, material, repairFraction, xpCostPerUnit)
    }
}

package com.willfp.eco.internal.spigot.anvil

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class AnvilMathTests {
    @Test
    fun infiniteIfNegativeReturnsMaxForNonPositive() {
        Assertions.assertEquals(Int.MAX_VALUE, 0.infiniteIfNegative(), "0 -> infinite")
        Assertions.assertEquals(Int.MAX_VALUE, (-1).infiniteIfNegative(), "-1 -> infinite")
    }

    @Test
    fun infiniteIfNegativeKeepsPositive() {
        Assertions.assertEquals(5, 5.infiniteIfNegative(), "5 -> 5")
        Assertions.assertEquals(1, 1.infiniteIfNegative(), "1 -> 1")
    }

    @Test
    fun reworkPenaltyDoubles() {
        Assertions.assertEquals(1, applyReworkPenalty(0), "0 -> 1")
        Assertions.assertEquals(3, applyReworkPenalty(1), "1 -> 3")
        Assertions.assertEquals(7, applyReworkPenalty(3), "3 -> 7")
    }

    @Test
    fun xpCostUsesExponentPlusUnitRepair() {
        Assertions.assertEquals(5, computeXpCost(5, 0, 1.0), "linear diff")
        Assertions.assertEquals(7, computeXpCost(5, 2, 1.0), "plus unit repair")
        Assertions.assertEquals(0, computeXpCost(0, 0, 0.95), "no change -> 0")
    }

    @Test
    fun mergeLevelRules() {
        Assertions.assertEquals(3, mergeEnchantLevel(2, 2, 5), "equal -> +1")
        Assertions.assertEquals(5, mergeEnchantLevel(5, 5, 5), "equal at max stays")
        Assertions.assertEquals(4, mergeEnchantLevel(4, 2, 5), "existing higher")
        Assertions.assertEquals(4, mergeEnchantLevel(2, 4, 5), "incoming higher")
    }

    @Test
    fun repairPerUnitMatchesVanillaQuarter() {
        // ceil(maxDurability / 4) - identical to vanilla's unit-repair step.
        Assertions.assertEquals(391, repairPerUnit(1561, VANILLA_REPAIR_FRACTION), "25% of 1561, rounded up")
        Assertions.assertEquals(250, repairPerUnit(1000, VANILLA_REPAIR_FRACTION), "25% of 1000")
    }

    @Test
    fun repairPerUnitCustomFraction() {
        Assertions.assertEquals(100, repairPerUnit(1000, 0.1), "10% of 1000")
        Assertions.assertEquals(500, repairPerUnit(1000, 0.5), "50% of 1000")
    }

    @Test
    fun repairPerUnitFlooredAtOne() {
        Assertions.assertEquals(1, repairPerUnit(1, 0.25), "tiny durability floors at 1")
        Assertions.assertEquals(1, repairPerUnit(100, 0.0), "zero fraction floors at 1")
    }
}

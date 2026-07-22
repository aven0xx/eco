package com.willfp.eco.core.anvil

import org.bukkit.inventory.ItemStack
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Global registry of plugin-supplied [AnvilRepairRegistration]s the eco anvil shell reads from.
 *
 * Unlike [AnvilHandlers] (which holds a single enchant handler), any number of repairs may be
 * registered. Repairs are independent of the enchant handler: the anvil shell honours registered
 * repairs even when no [AnvilHandler] is registered, and — crucially — leaves all other anvil
 * behaviour to vanilla in that case, so registering a repair never disturbs normal anvil use.
 */
object AnvilRepairs {
    private val registrations = CopyOnWriteArrayList<AnvilRepairRegistration>()

    /** Register a repair. Multiple repairs may be active at once. */
    @JvmStatic
    fun register(registration: AnvilRepairRegistration) {
        registrations.add(registration)
    }

    /** Remove a previously registered repair. */
    @JvmStatic
    fun unregister(registration: AnvilRepairRegistration) {
        registrations.remove(registration)
    }

    /** Remove all registered repairs. */
    @JvmStatic
    fun clear() {
        registrations.clear()
    }

    /** Whether no repairs are registered. When true the shell's repair path is a no-op. */
    @JvmStatic
    fun isEmpty(): Boolean = registrations.isEmpty()

    /** A snapshot of all registered repairs. */
    @JvmStatic
    fun getAll(): List<AnvilRepairRegistration> = registrations.toList()

    /**
     * The first registered repair whose [AnvilRepairRegistration.repairable] matches [left] and
     * whose [AnvilRepairRegistration.material] matches [right], or null if none match (or either
     * item is absent).
     */
    @JvmStatic
    fun getMatch(left: ItemStack?, right: ItemStack?): AnvilRepairRegistration? {
        if (left == null || right == null) return null
        return registrations.firstOrNull {
            it.repairable.matches(left) && it.material.matches(right)
        }
    }
}

package dev.crossui.dsl

/**
 * Binds a slider while retaining a deterministic generated default for native
 * hosts that do not receive the KMP state's constructor defaults directly.
 */
fun slider(
    key: String,
    initial: Double,
    value: StateBinding<Double>,
    min: Double,
    max: Double,
    step: Double? = null,
    onChange: String,
) = slider(key, initial, min, max, step, onChange).withBinding("value", value)

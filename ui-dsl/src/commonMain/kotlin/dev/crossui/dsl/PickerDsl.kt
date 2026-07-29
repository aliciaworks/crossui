package dev.crossui.dsl

import dev.crossui.ir.PickerOption

/**
 * Binds a picker while retaining a deterministic generated default for native
 * hosts that do not receive the KMP state's constructor defaults directly.
 */
fun picker(
    key: String,
    initial: String,
    selected: StateBinding<String>,
    options: List<PickerOption>,
    onChange: String,
) = picker(key, initial, options, onChange).withBinding("selected", selected)

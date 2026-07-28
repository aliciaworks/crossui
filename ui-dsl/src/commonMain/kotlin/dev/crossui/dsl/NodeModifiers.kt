package dev.crossui.dsl

import dev.crossui.ir.*

fun Node.accessibility(
    label: String,
    role: SemanticRole,
    hint: String? = semantics.hint,
) = copy(semantics = semantics.copy(label = label, role = role, hint = hint))

fun Node.fromSource(file: String, line: Int? = null, column: Int? = null) =
    copy(source = SourceLocation(file, line, column))

fun Node.localized(field: LocalizedField, text: LocalizedText) =
    copy(localizedText = localizedText + (field to text))

fun Node.visibleWhen(binding: StateBinding<Boolean>) =
    withBinding("visible", binding)

fun Node.enabledWhen(binding: StateBinding<Boolean>) =
    withBinding("enabled", binding)

internal fun <T> Node.withBinding(field: String, binding: StateBinding<T>) =
    copy(bindings = bindings + (field to binding.reference))

fun Node.disabled() = copy(semantics = semantics.copy(enabled = false))

fun Node.irreversible() = copy(
    semantics = semantics.copy(
        traits = semantics.traits.copy(irreversible = true),
    ),
)

fun Node.frequent() = copy(
    semantics = semantics.copy(
        traits = semantics.traits.copy(frequency = ActionFrequency.Frequent),
    ),
)

fun Node.critical() = copy(
    semantics = semantics.copy(
        traits = semantics.traits.copy(importance = Importance.Critical),
    ),
)

private fun Node.extension(extension: PlatformExtension) =
    copy(extensions = extensions + extension)

fun Node.iosHaptic(type: HapticType) =
    extension(PlatformExtension.Ios(IosExtension.Haptic(type)))

fun Node.iosPresentation(style: PresentationStyle) =
    extension(PlatformExtension.Ios(IosExtension.Presentation(style)))

fun Node.iosSwipeAction(action: String) =
    extension(PlatformExtension.Ios(IosExtension.SwipeAction(action)))

fun Node.ipadosMulticolumn(columns: UInt) =
    extension(PlatformExtension.IpadOs(IpadOsExtension.MultiColumn(columns)))

fun Node.ipadosSidebar() =
    extension(PlatformExtension.IpadOs(IpadOsExtension.Sidebar))

fun Node.watchCrown(sensitivity: CrownSensitivity) =
    extension(PlatformExtension.WatchOs(WatchOsExtension.DigitalCrown(sensitivity)))

fun Node.watchGlance(priority: GlancePriority) =
    extension(PlatformExtension.WatchOs(WatchOsExtension.Glance(priority)))

fun Node.macShortcut(key: String, modifiers: List<KeyModifier>) =
    extension(PlatformExtension.MacOs(MacOsExtension.KeyboardShortcut(key, modifiers)))

fun Node.macToolbar(itemId: String) =
    extension(PlatformExtension.MacOs(MacOsExtension.ToolbarItem(itemId)))

fun Node.androidElevation(dp: Float) =
    extension(PlatformExtension.Android(AndroidExtension.Elevation(dp)))

fun Node.windowsCorner(radius: Float) =
    extension(PlatformExtension.Windows(WindowsExtension.CornerRadius(radius)))

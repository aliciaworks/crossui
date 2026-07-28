package dev.crossui.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PlatformExtension {
    val platform: PlatformIdentity

    @Serializable @SerialName("ios")
    data class Ios(val value: IosExtension) : PlatformExtension {
        override val platform = PlatformIdentity.Ios
    }

    @Serializable @SerialName("ipados")
    data class IpadOs(val value: IpadOsExtension) : PlatformExtension {
        override val platform = PlatformIdentity.IpadOs
    }

    @Serializable @SerialName("watchos")
    data class WatchOs(val value: WatchOsExtension) : PlatformExtension {
        override val platform = PlatformIdentity.WatchOs
    }

    @Serializable @SerialName("macos")
    data class MacOs(val value: MacOsExtension) : PlatformExtension {
        override val platform = PlatformIdentity.MacOs
    }

    @Serializable @SerialName("android")
    data class Android(val value: AndroidExtension) : PlatformExtension {
        override val platform = PlatformIdentity.Android
    }

    @Serializable @SerialName("windows")
    data class Windows(val value: WindowsExtension) : PlatformExtension {
        override val platform = PlatformIdentity.Windows
    }
}

@Serializable
sealed interface IosExtension {
    @Serializable @SerialName("haptic")
    data class Haptic(val hapticType: HapticType) : IosExtension
    @Serializable @SerialName("presentation")
    data class Presentation(val style: PresentationStyle) : IosExtension
    @Serializable @SerialName("swipe_action")
    data class SwipeAction(val action: String) : IosExtension
}

@Serializable enum class PresentationStyle { Sheet, FullScreenCover, Popover }
@Serializable enum class HapticType { Selection, Success, Warning, Error, Light, Medium, Heavy }

@Serializable
sealed interface IpadOsExtension {
    @Serializable @SerialName("multi_column")
    data class MultiColumn(val columns: UInt) : IpadOsExtension
    @Serializable @SerialName("sidebar")
    data object Sidebar : IpadOsExtension
}

@Serializable
sealed interface WatchOsExtension {
    @Serializable @SerialName("digital_crown")
    data class DigitalCrown(val sensitivity: CrownSensitivity) : WatchOsExtension
    @Serializable @SerialName("glance")
    data class Glance(val priority: GlancePriority) : WatchOsExtension
}

@Serializable enum class CrownSensitivity { Low, Medium, High }
@Serializable enum class GlancePriority { Low, Normal, High }

@Serializable
sealed interface MacOsExtension {
    @Serializable @SerialName("keyboard_shortcut")
    data class KeyboardShortcut(
        val key: String,
        val modifiers: List<KeyModifier> = emptyList(),
    ) : MacOsExtension
    @Serializable @SerialName("toolbar_item")
    data class ToolbarItem(val itemId: String) : MacOsExtension
}

@Serializable enum class KeyModifier { Command, Option, Control, Shift }

@Serializable
sealed interface AndroidExtension {
    @Serializable @SerialName("elevation")
    data class Elevation(val dp: Float) : AndroidExtension
}

@Serializable
sealed interface WindowsExtension {
    @Serializable @SerialName("corner_radius")
    data class CornerRadius(val radius: Float) : WindowsExtension
}

enum class UnsupportedExtensionPolicy { Reject, Strip }

data class ExtensionMismatch(
    val nodeKey: NodeKey,
    val extensionPlatform: PlatformIdentity,
    val targetPlatform: PlatformIdentity,
)

fun validateExtensions(
    document: UiDocument,
    target: TargetProfile,
    policy: UnsupportedExtensionPolicy = UnsupportedExtensionPolicy.Reject,
): List<ExtensionMismatch> = buildList {
    document.root.walk { node ->
        node.extensions
            .filterNot { extensionMatches(it.platform, target.platform) }
            .forEach { add(ExtensionMismatch(node.key, it.platform, target.platform)) }
    }
}.also {
    if (policy == UnsupportedExtensionPolicy.Reject && it.isNotEmpty()) {
        error(
            it.joinToString(
                prefix = "Extensions do not match target: ",
                transform = { mismatch ->
                    "${mismatch.nodeKey.value} (${mismatch.extensionPlatform} -> ${mismatch.targetPlatform})"
                },
            ),
        )
    }
}

private fun extensionMatches(extension: PlatformIdentity, target: PlatformIdentity): Boolean =
    extension == target ||
        (extension == PlatformIdentity.Ios && target == PlatformIdentity.IpadOs)

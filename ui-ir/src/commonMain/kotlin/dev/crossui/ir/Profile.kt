package dev.crossui.ir

import kotlinx.serialization.Serializable

@Serializable
enum class PlatformIdentity {
    Ios, IpadOs, WatchOs, MacOs, Android, Windows;

    val vendor: String
        get() = when (this) {
            Ios, IpadOs, WatchOs, MacOs -> "apple"
            Android -> "google"
            Windows -> "microsoft"
        }
}

@Serializable
data class TargetProfile(
    val platform: PlatformIdentity,
    val osVersion: String? = null,
    val capabilities: Capabilities = Capabilities(),
    val interaction: InteractionProfile = InteractionProfile(),
) {
    companion object {
        fun iphone() = TargetProfile(
            PlatformIdentity.Ios,
            "17.0",
            Capabilities(
                input = InputCapabilities(touch = true),
                display = DisplayCapabilities(DisplayClass.Phone, 390, 844),
                windowing = WindowingCapabilities(singleScene = true),
            ),
        )

        fun ipad() = TargetProfile(
            PlatformIdentity.IpadOs,
            "17.0",
            Capabilities(
                input = InputCapabilities(touch = true, pointer = true, keyboard = true),
                display = DisplayCapabilities(DisplayClass.Tablet, 1024, 1366, resizable = true),
                windowing = WindowingCapabilities(singleScene = false, multiWindow = true),
            ),
        )

        fun appleWatch() = TargetProfile(
            PlatformIdentity.WatchOs,
            "10.0",
            Capabilities(
                input = InputCapabilities(touch = true, digitalCrown = true),
                display = DisplayCapabilities(DisplayClass.Watch, 198, 242),
                windowing = WindowingCapabilities(singleScene = true),
            ),
            InteractionProfile(glanceable = true, typicalDuration = DurationClass.Seconds),
        )

        fun macDesktop() = TargetProfile(
            PlatformIdentity.MacOs,
            "14.0",
            Capabilities(
                input = InputCapabilities(pointer = true, keyboard = true),
                display = DisplayCapabilities(DisplayClass.Desktop, 1440, 900, resizable = true),
                windowing = WindowingCapabilities(
                    singleScene = false,
                    multiWindow = true,
                    menuBar = true,
                ),
            ),
            InteractionProfile(typicalDuration = DurationClass.Extended),
        )

        fun androidPhone() = TargetProfile(
            PlatformIdentity.Android,
            "14",
            Capabilities(
                input = InputCapabilities(touch = true),
                display = DisplayCapabilities(DisplayClass.Phone, 412, 915),
                windowing = WindowingCapabilities(singleScene = true),
            ),
        )

        fun windowsDesktop() = TargetProfile(
            PlatformIdentity.Windows,
            "11",
            Capabilities(
                input = InputCapabilities(touch = true, pointer = true, keyboard = true),
                display = DisplayCapabilities(DisplayClass.Desktop, 1440, 900, resizable = true),
                windowing = WindowingCapabilities(
                    singleScene = false,
                    multiWindow = true,
                    menuBar = true,
                ),
            ),
            InteractionProfile(typicalDuration = DurationClass.Extended),
        )
    }
}

@Serializable data class Capabilities(
    val input: InputCapabilities = InputCapabilities(),
    val display: DisplayCapabilities = DisplayCapabilities(),
    val windowing: WindowingCapabilities = WindowingCapabilities(),
)

@Serializable data class InputCapabilities(
    val touch: Boolean = false,
    val pointer: Boolean = false,
    val keyboard: Boolean = false,
    val digitalCrown: Boolean = false,
)

@Serializable data class DisplayCapabilities(
    val displayClass: DisplayClass = DisplayClass.Phone,
    val widthPoints: Int? = null,
    val heightPoints: Int? = null,
    val resizable: Boolean = false,
)

@Serializable enum class DisplayClass { Watch, Phone, Tablet, Desktop }

@Serializable data class WindowingCapabilities(
    val singleScene: Boolean = true,
    val multiWindow: Boolean = false,
    val menuBar: Boolean = false,
)

@Serializable data class InteractionProfile(
    val glanceable: Boolean = false,
    val typicalDuration: DurationClass = DurationClass.Minutes,
)

@Serializable enum class DurationClass { Seconds, Minutes, Extended }

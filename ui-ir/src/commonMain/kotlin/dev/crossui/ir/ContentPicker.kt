package dev.crossui.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A platform-neutral request for user-selected content. The selected content
 * itself stays behind a platform-owned handle; it is never represented as a
 * shared filesystem path.
 */
@Serializable
sealed interface ContentPickerRequest {
    @Serializable
    @SerialName("files")
    data class Files(
        val mimeTypes: List<String> = emptyList(),
        val allowMultiple: Boolean = false,
    ) : ContentPickerRequest

    @Serializable
    @SerialName("media")
    data class Media(
        val kinds: Set<MediaKind> = setOf(MediaKind.Image),
        val maxSelection: Int = 1,
    ) : ContentPickerRequest
}

@Serializable
enum class MediaKind { Image, Video }

internal fun ContentPickerRequest.validate(nodeKey: String) = when (this) {
    is ContentPickerRequest.Files -> mimeTypes.forEach { mimeType ->
        require(mimeType.isNotBlank() && '/' in mimeType) {
            "Invalid MIME type '$mimeType' for content picker: $nodeKey"
        }
    }
    is ContentPickerRequest.Media -> {
        require(kinds.isNotEmpty()) { "Media kinds cannot be empty: $nodeKey" }
        require(maxSelection > 0) { "Media maxSelection must be positive: $nodeKey" }
    }
}

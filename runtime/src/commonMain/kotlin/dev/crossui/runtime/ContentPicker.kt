package dev.crossui.runtime

import dev.crossui.ir.ContentPickerRequest

/**
 * Metadata plus an opaque platform-owned handle for user-selected content.
 * [handle] is intentionally not a file path: Android, Apple, and Windows each
 * have different access and lifetime rules for selected content.
 */
data class SelectedContent(
    val handle: String,
    val name: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
)

sealed interface ContentPickerResult {
    data class Selected(val content: List<SelectedContent>) : ContentPickerResult
    data object Cancelled : ContentPickerResult
    data class Unavailable(val message: String) : ContentPickerResult
    data class Failure(val error: UiError) : ContentPickerResult
}

/** Implement this at the native application boundary, where presentation lives. */
fun interface ContentPicker {
    suspend fun pick(request: ContentPickerRequest): ContentPickerResult
}

/**
 * A reducer can emit this effect after receiving a ContentPicker node's request
 * action. AsyncStore cancellation also cancels the in-flight platform request.
 */
data class PickContent(
    val request: ContentPickerRequest,
)

class ContentPickerEffectHandler<Action>(
    private val picker: ContentPicker,
    private val actionForResult: (ContentPickerResult) -> Action?,
) : AsyncEffectHandler<Action, PickContent> {
    override suspend fun execute(effect: PickContent): Action? =
        actionForResult(picker.pick(effect.request))
}

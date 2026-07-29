package dev.crossui.dsl

import dev.crossui.ir.ButtonVariant
import dev.crossui.ir.ContentPickerRequest
import dev.crossui.ir.LocalizedField
import dev.crossui.ir.LocalizedText
import dev.crossui.ir.MediaKind
import dev.crossui.ir.Node
import dev.crossui.ir.NodeKey
import dev.crossui.ir.NodeKind

fun filePicker(
    key: String,
    label: String,
    onRequest: String,
    mimeTypes: List<String> = emptyList(),
    allowMultiple: Boolean = false,
    variant: ButtonVariant = ButtonVariant.Primary,
) = Node(
    NodeKey(key),
    NodeKind.ContentPicker(
        label = label,
        request = ContentPickerRequest.Files(mimeTypes, allowMultiple),
        onRequest = onRequest,
        variant = variant,
    ),
)

fun filePicker(
    key: String,
    label: LocalizedText,
    onRequest: String,
    mimeTypes: List<String> = emptyList(),
    allowMultiple: Boolean = false,
    variant: ButtonVariant = ButtonVariant.Primary,
) = filePicker(key, label.fallback, onRequest, mimeTypes, allowMultiple, variant).copy(
    localizedText = mapOf(LocalizedField.Label to label),
)

fun mediaPicker(
    key: String,
    label: String,
    onRequest: String,
    kinds: Set<MediaKind> = setOf(MediaKind.Image),
    maxSelection: Int = 1,
    variant: ButtonVariant = ButtonVariant.Primary,
) = Node(
    NodeKey(key),
    NodeKind.ContentPicker(
        label = label,
        request = ContentPickerRequest.Media(kinds, maxSelection),
        onRequest = onRequest,
        variant = variant,
    ),
)

fun mediaPicker(
    key: String,
    label: LocalizedText,
    onRequest: String,
    kinds: Set<MediaKind> = setOf(MediaKind.Image),
    maxSelection: Int = 1,
    variant: ButtonVariant = ButtonVariant.Primary,
) = mediaPicker(key, label.fallback, onRequest, kinds, maxSelection, variant).copy(
    localizedText = mapOf(LocalizedField.Label to label),
)

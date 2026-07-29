package dev.crossui.ir

internal fun NodeKind.requiredBinding(): String? = when (this) {
    is NodeKind.Input -> "value"
    is NodeKind.Toggle, is NodeKind.Checkbox -> "checked"
    is NodeKind.Slider, is NodeKind.DatePicker -> "value"
    is NodeKind.Picker -> "selected"
    else -> null
}

internal fun LocalizedText.validate(location: String) {
    if (this is LocalizedText.Resource) {
        require(key.isNotBlank()) {
            "Localized resource key cannot be empty: $location"
        }
        require('?' !in key && '#' !in key) {
            "Localized resource key cannot contain '?' or '#': $location"
        }
        require(namespace == null || namespace.isNotBlank()) {
            "Localized resource namespace cannot be blank: $location"
        }
    }
}

internal fun NodeKind.supportedLocalizedFields(): Set<LocalizedField> = when (this) {
    is NodeKind.Text -> setOf(LocalizedField.Value)
    is NodeKind.Button -> setOf(LocalizedField.Label)
    is NodeKind.Input -> setOf(LocalizedField.Placeholder)
    is NodeKind.Loading -> setOf(LocalizedField.Label)
    is NodeKind.Route -> setOf(LocalizedField.Title)
    is NodeKind.Toggle -> setOf(LocalizedField.Label)
    is NodeKind.Image -> setOf(LocalizedField.Alt)
    is NodeKind.Dialog -> setOf(
        LocalizedField.Title,
        LocalizedField.ConfirmLabel,
        LocalizedField.CancelLabel,
    )
    is NodeKind.Checkbox -> setOf(LocalizedField.Label)
    is NodeKind.Chip -> setOf(LocalizedField.Label)
    is NodeKind.ContentPicker -> setOf(LocalizedField.Label)
    else -> emptySet()
}

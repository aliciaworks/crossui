# File and media pickers

CrossUI models the request and result contract, while the application presents
the native picker. This preserves the native host lifecycle and never exposes a
cross-platform filesystem path.

```kotlin
filePicker(
    key = "attachment",
    label = "Attach PDF",
    onRequest = "pick_attachment",
    mimeTypes = listOf("application/pdf"),
)

mediaPicker(
    key = "photos",
    label = "Choose photos",
    onRequest = "pick_photos",
    maxSelection = 3,
)
```

The generated SwiftUI, Compose, and WinUI control dispatches `onRequest`.
The reducer emits `PickContent`, and a host-owned `ContentPicker` implementation
returns `ContentPickerResult` through `ContentPickerEffectHandler`.

```kotlin
sealed interface UploadAction {
    data object PickAttachment : UploadAction
    data class Picked(val result: ContentPickerResult) : UploadAction
}

val pickerHandler = ContentPickerEffectHandler<UploadAction>(
    picker = nativePicker,
    actionForResult = UploadAction::Picked,
)

// The reducer turns PickAttachment into PickContent(request).
// AsyncStore owns cancellation when the screen closes.
```

`SelectedContent.handle` is opaque. Android commonly owns a content URI, Apple
owns a security-scoped URL/bookmark, and Windows owns a StorageFile token. Keep
all reading, copying, uploading, and permission lifetime handling in the
platform adapter.

Use the native host API that matches the request:

- Apple: `fileImporter` for documents and `PhotosPicker` for media.
- Android: Activity Result `OpenDocument` for files and the system Photo Picker
  for media.
- Windows: `FileOpenPicker`, attached to the host window.

Android applications can use the runtime adapter directly. Construct it in
`onCreate`, before the activity reaches `STARTED`:

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var contentPicker: AndroidContentPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentPicker = AndroidContentPicker(this)
    }
}
```

`ContentPickerRequest.Media` maps to `PickVisualMedia` or
`PickMultipleVisualMedia`. Image/video filters and `maxSelection` are passed to
the system request. Returned `content://` handles include display name, MIME
type, and size metadata when the provider exposes them. Read permission is
persisted when the provider supports persistent grants.

This boundary is deliberate: generated controls remain compile-time native UI,
while picker presentation keeps the activity, scene, or window ownership that
the platform requires.

This first capability covers opening existing files and media. Saving a new file,
camera capture, byte streaming, and upload progress are separate follow-up
contracts; they should not be overloaded into a picker result.

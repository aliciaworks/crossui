package dev.crossui.runtime

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import dev.crossui.ir.ContentPickerRequest
import dev.crossui.ir.MediaKind
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Activity-owned bridge to Android's system document and photo pickers.
 *
 * Construct this before the activity reaches STARTED so Activity Result
 * launchers can be registered safely.
 */
class AndroidContentPicker(
    private val activity: ComponentActivity,
    private val maxMediaSelection: Int = 50,
    private val persistReadPermission: Boolean = true,
) : ContentPicker {
    private val requestMutex = Mutex()
    private var pending: CancellableContinuation<List<Uri>>? = null

    private val singleMedia = activity.registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        complete(uri?.let(::listOf).orEmpty())
    }
    private val multipleMedia = activity.registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxMediaSelection),
        ::complete,
    )
    private val singleDocument = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        complete(uri?.let(::listOf).orEmpty())
    }
    private val multipleDocuments = activity.registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
        ::complete,
    )

    init {
        require(Looper.myLooper() == Looper.getMainLooper()) {
            "AndroidContentPicker must be constructed on the main thread."
        }
        require(maxMediaSelection >= 2) {
            "maxMediaSelection must be at least 2."
        }
    }

    override suspend fun pick(
        request: ContentPickerRequest,
    ): ContentPickerResult = requestMutex.withLock {
        try {
            val uris = withContext(Dispatchers.Main.immediate) {
                when (request) {
                    is ContentPickerRequest.Files -> pickFiles(request)
                    is ContentPickerRequest.Media -> pickMedia(request)
                }
            }
            if (uris.isEmpty()) {
                ContentPickerResult.Cancelled
            } else {
                ContentPickerResult.Selected(uris.map(::selectedContent))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ContentPickerResult.Failure(
                UiError(
                    code = "android_content_picker_failed",
                    message = error.message ?: "Android content picker failed.",
                    retryable = true,
                ),
            )
        }
    }

    private suspend fun pickFiles(
        request: ContentPickerRequest.Files,
    ): List<Uri> {
        val mimeTypes = request.mimeTypes.ifEmpty { listOf("*/*") }.toTypedArray()
        return if (request.allowMultiple) {
            await(multipleDocuments, mimeTypes)
        } else {
            await(singleDocument, mimeTypes)
        }
    }

    private suspend fun pickMedia(
        request: ContentPickerRequest.Media,
    ): List<Uri> {
        require(request.maxSelection <= maxMediaSelection) {
            "Requested ${request.maxSelection} media items, but this adapter " +
                "was registered for at most $maxMediaSelection."
        }
        val builder = PickVisualMediaRequest.Builder()
            .setMediaType(request.mediaType())
        if (request.maxSelection > 1) {
            builder.setMaxItems(request.maxSelection)
        }
        val pickerRequest = builder.build()
        return if (request.maxSelection == 1) {
            await(singleMedia, pickerRequest)
        } else {
            await(multipleMedia, pickerRequest)
        }
    }

    private suspend fun <Input> await(
        launcher: ActivityResultLauncher<Input>,
        input: Input,
    ): List<Uri> = suspendCancellableCoroutine { continuation ->
        check(pending == null) { "A content picker request is already active." }
        pending = continuation
        continuation.invokeOnCancellation {
            if (pending === continuation) pending = null
        }
        try {
            launcher.launch(input)
        } catch (error: Throwable) {
            pending = null
            continuation.resumeWithException(error)
        }
    }

    private fun complete(uris: List<Uri>) {
        val continuation = pending ?: return
        pending = null
        if (continuation.isActive) continuation.resume(uris)
    }

    private fun selectedContent(uri: Uri): SelectedContent {
        if (persistReadPermission) {
            try {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Some providers only grant access for the current process.
            }
        }
        val metadata = activity.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use(::readMetadata)
        return SelectedContent(
            handle = uri.toString(),
            name = metadata?.first ?: uri.lastPathSegment.orEmpty(),
            mimeType = activity.contentResolver.getType(uri),
            sizeBytes = metadata?.second,
        )
    }

    private fun readMetadata(cursor: Cursor): Pair<String, Long?>? {
        if (!cursor.moveToFirst()) return null
        val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            .takeIf { it >= 0 }
            ?.let(cursor::getString)
            .orEmpty()
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val size = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }
            ?.let(cursor::getLong)
        return name to size
    }
}

private fun ContentPickerRequest.Media.mediaType() = when (kinds) {
    setOf(MediaKind.Image) -> ActivityResultContracts.PickVisualMedia.ImageOnly
    setOf(MediaKind.Video) -> ActivityResultContracts.PickVisualMedia.VideoOnly
    else -> ActivityResultContracts.PickVisualMedia.ImageAndVideo
}

@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.example.prisma.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import com.example.prisma.cards.StatementUploadFile
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy
import kotlin.coroutines.cancellation.CancellationException

@Composable
actual fun rememberStatementPicker(
    onFilesSelected: (List<StatementUploadFile>) -> Unit,
    onError: (String) -> Unit,
): FilePickerState {
    val host = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val currentOnFilesSelected by rememberUpdatedState(onFilesSelected)
    val currentOnError by rememberUpdatedState(onError)

    val coordinator = remember(scope) {
        StatementIosCoordinator(
            scope,
            { currentOnFilesSelected(it) },
            { currentOnError(it) },
        )
    }

    return remember(host, coordinator) {
        FilePickerState(
            launchPdfPicker = { coordinator.pdf(host) },
            launchImagePicker = { coordinator.images(host) },
        )
    }
}

private class StatementIosCoordinator(
    private val scope: CoroutineScope,
    private val onFilesSelected: (List<StatementUploadFile>) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val documents = StatementDocumentDelegate { url ->
        readPdf(url)
    }

    private val photos = StatementPhotoDelegate(
        scope,
        onFilesSelected,
        onError,
    )

    fun pdf(host: UIViewController) {
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypePDF),
            asCopy = true,
        )

        picker.delegate = documents
        picker.allowsMultipleSelection = false
        present(picker, host)
    }

    fun images(host: UIViewController) {
        val configuration = PHPickerConfiguration()
        configuration.filter = PHPickerFilter.imagesFilter
        configuration.selectionLimit = 12

        val picker = PHPickerViewController(
            configuration = configuration,
        )

        picker.delegate = photos
        present(picker, host)
    }

    private fun readPdf(url: NSURL) {
        scope.launch {
            val file = try {
                withContext(Dispatchers.Default) {
                    val accessing =
                        url.startAccessingSecurityScopedResource()

                    try {
                        val path = url.path
                            ?: throw IllegalArgumentException(
                                "No se pudo abrir el PDF.",
                            )

                        val attributes =
                            NSFileManager.defaultManager.attributesOfItemAtPath(
                                path,
                                error = null,
                            )

                        val size = (
                                attributes?.get(NSFileSize) as? NSNumber
                                )?.longLongValue

                        require(
                            size != null &&
                                    size in 1L..STATEMENT_MAX_FILE_BYTES.toLong(),
                        ) {
                            "El PDF debe contener datos y pesar hasta 10 MB."
                        }

                        val bytes = NSData.dataWithContentsOfURL(url)
                            ?.toStatementBytes()
                            ?: throw IllegalArgumentException(
                                "No se pudo leer el PDF.",
                            )

                        StatementUploadFile(
                            url.lastPathComponent ?: "estado.pdf",
                            "application/pdf",
                            bytes,
                        )
                    } finally {
                        if (accessing) {
                            url.stopAccessingSecurityScopedResource()
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                onError(error.message ?: "El PDF no se pudo leer.")
                return@launch
            } catch (_: Exception) {
                onError("No se pudo abrir el PDF. Selecciona otra copia.")
                return@launch
            }

            deliverStatementBatch(
                listOf(file),
                onFilesSelected,
                onError,
            )
        }
    }

    private fun present(
        picker: UIViewController,
        host: UIViewController,
    ) {
        dispatch_async(dispatch_get_main_queue()) {
            if (scope.isActive) {
                topStatementController(host).presentViewController(
                    picker,
                    true,
                    null,
                )
            }
        }
    }
}

private fun topStatementController(
    host: UIViewController,
): UIViewController {
    var current = host

    while (true) {
        val next = current.presentedViewController ?: break
        if (next.isBeingDismissed()) break
        current = next
    }

    return current
}

private class StatementDocumentDelegate(
    private val onPicked: (NSURL) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onPicked)
    }

    override fun documentPickerWasCancelled(
        controller: UIDocumentPickerViewController,
    ) = Unit
}

private class StatementPhotoDelegate(
    private val scope: CoroutineScope,
    private val onFilesSelected: (List<StatementUploadFile>) -> Unit,
    private val onError: (String) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(
        picker: PHPickerViewController,
        didFinishPicking: List<*>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)

        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        if (results.isEmpty() || !scope.isActive) return

        if (results.size > STATEMENT_MAX_FILES) {
            onError("Selecciona hasta 12 imágenes del mismo estado.")
            return
        }

        val files = mutableListOf<StatementUploadFile>()
        var total = 0L

        fun readImage(index: Int) {
            if (!scope.isActive) return

            if (index == results.size) {
                deliverStatementBatch(files, onFilesSelected, onError)
                return
            }

            val provider = results[index].itemProvider

            val name = provider.suggestedName
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: "pagina-${index + 1}"

            provider.loadDataRepresentationForTypeIdentifier(
                "public.image",
            ) { data, _ ->
                val bytes = try {
                    data?.let { UIImage(data = it) }
                        ?.let { UIImageJPEGRepresentation(it, 0.85) }
                        ?.toStatementBytes()
                } catch (_: Exception) {
                    null
                }

                dispatch_async(dispatch_get_main_queue()) {
                    if (scope.isActive) {
                        if (
                            bytes == null ||
                            total + bytes.size > STATEMENT_MAX_TOTAL_BYTES
                        ) {
                            onError(
                                "No se pudo leer la selección completa. Cada imagen admite 10 MB y el lote 20 MB.",
                            )
                        } else {
                            files += StatementUploadFile(
                                "$name.jpg",
                                "image/jpeg",
                                bytes,
                            )
                            total += bytes.size
                            readImage(index + 1)
                        }
                    }
                }
            }
        }

        readImage(0)
    }
}

private fun NSData.toStatementBytes(): ByteArray {
    val count = length.toLong()

    require(count in 1L..STATEMENT_MAX_FILE_BYTES.toLong()) {
        "Cada archivo admite hasta 10 MB."
    }

    return ByteArray(count.toInt()).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}
@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.example.prisma.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
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

/**
 * Selector de archivos en iOS:
 *  - PDF: selector de Archivos (UIDocumentPickerViewController), copia el archivo a la app.
 *  - Imágenes: selector de Fotos (PHPickerViewController), varias a la vez. No requiere permiso
 *    de acceso a la fototeca porque el usuario solo comparte las fotos que elige.
 *
 * Antes esta función estaba vacía: el diálogo "Seleccionar documento" se cerraba y no pasaba nada.
 */
@Composable
actual fun rememberFilePicker(
    onFileSelected: (name: String, mimeType: String, fileBytes: ByteArray) -> Unit,
): FilePickerState {
    val hostController = LocalUIViewController.current
    val currentOnFileSelected by rememberUpdatedState(onFileSelected)
    // UIKit guarda los delegates como referencias débiles: el coordinador los mantiene vivos.
    val coordinator = remember { IosFilePickerCoordinator { name, mime, bytes -> currentOnFileSelected(name, mime, bytes) } }

    return remember(hostController, coordinator) {
        FilePickerState(
            launchPdfPicker = { coordinator.presentPdfPicker(hostController) },
            launchImagePicker = { coordinator.presentImagePicker(hostController) },
        )
    }
}

private class IosFilePickerCoordinator(
    private val onFileSelected: (name: String, mimeType: String, fileBytes: ByteArray) -> Unit,
) {
    private val documentDelegate = DocumentPickerDelegate { url -> readPdf(url) }
    private val photoDelegate = PhotoPickerDelegate { name, bytes -> onFileSelected(name, "image/jpeg", bytes) }

    fun presentPdfPicker(host: UIViewController) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF), asCopy = true)
        picker.delegate = documentDelegate
        picker.allowsMultipleSelection = false
        present(picker, host)
    }

    fun presentImagePicker(host: UIViewController) {
        val config = PHPickerConfiguration()
        config.filter = PHPickerFilter.imagesFilter
        config.selectionLimit = 0 // 0 = sin límite, igual que en Android
        val picker = PHPickerViewController(configuration = config)
        picker.delegate = photoDelegate
        present(picker, host)
    }

    private fun readPdf(url: NSURL) {
        val accessing = url.startAccessingSecurityScopedResource()
        val data = try {
            NSData.dataWithContentsOfURL(url)
        } finally {
            if (accessing) url.stopAccessingSecurityScopedResource()
        }
        val bytes = data?.toByteArray() ?: return
        onFileSelected(url.lastPathComponent ?: "estado_cuenta.pdf", "application/pdf", bytes)
    }

    /**
     * Se presenta en el siguiente ciclo del hilo principal para que el diálogo de Compose que
     * lanzó la acción termine de cerrarse, y desde el controlador visible más alto.
     */
    private fun present(picker: UIViewController, host: UIViewController) {
        dispatch_async(dispatch_get_main_queue()) {
            topMostController(host).presentViewController(picker, animated = true, completion = null)
        }
    }
}

private fun topMostController(host: UIViewController): UIViewController {
    var current = host
    while (true) {
        val next = current.presentedViewController ?: break
        if (next.isBeingDismissed()) break
        current = next
    }
    return current
}

private class DocumentPickerDelegate(
    private val onPicked: (NSURL) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onPicked)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}

private class PhotoPickerDelegate(
    private val onImage: (name: String, bytes: ByteArray) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {
    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        // A diferencia del selector de documentos, PHPicker no se cierra solo.
        picker.dismissViewControllerAnimated(true, completion = null)

        didFinishPicking.filterIsInstance<PHPickerResult>().forEachIndexed { index, result ->
            val provider = result.itemProvider
            val baseName = provider.suggestedName?.substringBeforeLast('.')?.ifBlank { null } ?: "comprobante_${index + 1}"
            provider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
                // Las fotos del iPhone suelen ser HEIC: se convierten a JPEG, que es lo que espera el servidor.
                val jpeg = data?.let { UIImage(data = it) }?.let { UIImageJPEGRepresentation(it, 0.85) }
                    ?: return@loadDataRepresentationForTypeIdentifier
                val bytes = jpeg.toByteArray()
                // El callback llega en un hilo de fondo; la UI se actualiza en el principal.
                dispatch_async(dispatch_get_main_queue()) { onImage("$baseName.jpg", bytes) }
            }
        }
    }
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}

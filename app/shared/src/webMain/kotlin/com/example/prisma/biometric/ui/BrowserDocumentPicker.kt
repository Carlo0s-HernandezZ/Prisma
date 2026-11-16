@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.example.prisma.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.FileReader
import kotlin.js.toDouble

private const val MAX_DOCUMENT_BYTES = 10 * 1024 * 1024

internal class BrowserDocumentPicker {
    private class Selection(
        val input: HTMLInputElement,
        val onSelected: (String, String, ByteArray) -> Unit,
    ) {
        var reader: FileReader? = null
        var onCancel: ((Event) -> Unit)? = null
    }

    private var pending: Selection? = null

    fun open(
        accept: String,
        extensions: Set<String>,
        onSelected: (String, String, ByteArray) -> Unit,
    ) {
        cancel()

        val input = (
                document.createElement("input") as HTMLInputElement
                ).apply {
                type = "file"
                this.accept = accept
                multiple = false
                style.display = "none"
            }

        val selection = Selection(input, onSelected)
        pending = selection

        selection.onCancel = {
            if (pending === selection) cancel()
        }

        input.addEventListener("cancel", selection.onCancel)

        input.onchange = {
            if (pending === selection) {
                val file = input.files?.item(0)

                if (file == null) {
                    cancel()
                } else {
                    read(selection, file, extensions)
                }
            }
        }

        document.body?.appendChild(input)

        try {
            input.click()
        } catch (_: Exception) {
            fail(selection, "No se pudo abrir el selector de archivos.")
        }
    }

    private fun read(
        selection: Selection,
        file: File,
        extensions: Set<String>,
    ) {
        val extension = file.name
            .substringAfterLast('.', "")
            .lowercase()

        if (extension !in extensions) {
            fail(
                selection,
                "Selecciona un archivo ${
                    extensions.joinToString(" o ") { it.uppercase() }
                }."
            )
            return
        }

        val size = file.size.toDouble()

        if (size <= 0) {
            fail(selection, "El documento seleccionado está vacío.")
            return
        }

        if (size > MAX_DOCUMENT_BYTES) {
            fail(selection, "El documento supera el límite de 10 MiB.")
            return
        }

        val name = file.name.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', '_')
            .take(90)
            .ifBlank { "documento" } + ".$extension"

        val mimeType = when (extension) {
            "pdf" -> "application/pdf"
            "xml" -> "application/xml"
            "png" -> "image/png"
            else -> "image/jpeg"
        }

        val reader = FileReader()
        selection.reader = reader

        reader.onerror = {
            fail(selection, "No se pudo leer el documento seleccionado.")
        }

        reader.onabort = {
            if (pending === selection) cancel()
        }

        reader.onload = {
            if (pending === selection) {
                val buffer = reader.result as? ArrayBuffer

                if (
                    buffer == null ||
                    buffer.byteLength == 0 ||
                    buffer.byteLength > MAX_DOCUMENT_BYTES
                ) {
                    fail(
                        selection,
                        "No se pudo leer el documento seleccionado " +
                                "(máximo 10 MiB)."
                    )
                } else {
                    val bytes = Int8Array(buffer).toByteArray()
                    val callback = selection.onSelected

                    cancel()
                    callback(name, mimeType, bytes)
                }
            }
        }

        try {
            reader.readAsArrayBuffer(file)
        } catch (_: Exception) {
            fail(selection, "No se pudo leer el documento seleccionado.")
        }
    }

    private fun fail(selection: Selection, message: String) {
        if (pending !== selection) return

        cancel()
        window.alert(message)
    }

    fun cancel() {
        val selection = pending ?: return
        pending = null

        selection.input.onchange = null
        selection.input.removeEventListener("cancel", selection.onCancel)
        selection.onCancel = null

        selection.reader?.let { reader ->
            reader.onload = null
            reader.onerror = null
            reader.onabort = null

            if (reader.readyState == FileReader.LOADING) {
                reader.abort()
            }
        }

        selection.reader = null
        selection.input.value = ""
        selection.input.remove()
    }
}

@Composable
internal fun rememberBrowserFilePicker(
    onFileSelected: (String, String, ByteArray) -> Unit,
): FilePickerState {
    val onSelected = rememberUpdatedState(onFileSelected)
    val picker = remember { BrowserDocumentPicker() }

    DisposableEffect(picker) {
        onDispose { picker.cancel() }
    }

    return remember(picker) {
        FilePickerState(
            launchPdfPicker = {
                picker.open(
                    accept = "application/pdf,.pdf",
                    extensions = setOf("pdf"),
                ) { name, mimeType, bytes ->
                    onSelected.value(name, mimeType, bytes)
                }
            },
            launchImagePicker = {
                picker.open(
                    accept = "image/png,image/jpeg,.png,.jpg,.jpeg",
                    extensions = setOf("png", "jpg", "jpeg"),
                ) { name, mimeType, bytes ->
                    onSelected.value(name, mimeType, bytes)
                }
            },
        )
    }
}
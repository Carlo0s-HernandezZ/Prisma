package com.example.prisma.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberFilePicker(
    onFileSelected: (name: String, mimeType: String, fileBytes: ByteArray) -> Unit,
): FilePickerState {
    val context = LocalContext.current

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = getFileName(context, uri) ?: "estado_cuenta.pdf"
            val bytes = getBytes(context, uri)
            if (bytes != null) {
                onFileSelected(name, "application/pdf", bytes)
            }
        }
    }

    // Permite seleccionar múltiples imágenes de la galería a la vez
    val imagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            for (uri in uris) {
                val name = getFileName(context, uri) ?: "comprobante.jpg"
                val bytes = getBytes(context, uri)
                if (bytes != null) {
                    onFileSelected(name, "image/jpeg", bytes)
                }
            }
        }
    }

    return remember {
        FilePickerState(
            launchPdfPicker = { pdfLauncher.launch(arrayOf("application/pdf")) },
            launchImagePicker = { imagesLauncher.launch("image/*") }
        )
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }
    }
    return name
}

private fun getBytes(context: Context, uri: Uri): ByteArray? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }
}

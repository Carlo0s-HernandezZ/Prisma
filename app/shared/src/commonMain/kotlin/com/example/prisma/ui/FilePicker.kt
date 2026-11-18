package com.example.prisma.ui

import androidx.compose.runtime.Composable

class FilePickerState(
    val launchPdfPicker: () -> Unit,
    val launchImagePicker: () -> Unit,
    /** PDF o XML (recibos de nómina y su CFDI). Por defecto, solo PDF. */
    val launchDocumentPicker: () -> Unit = launchPdfPicker,
)

@Composable
expect fun rememberFilePicker(
    onFileSelected: (name: String, mimeType: String, fileBytes: ByteArray) -> Unit,
): FilePickerState

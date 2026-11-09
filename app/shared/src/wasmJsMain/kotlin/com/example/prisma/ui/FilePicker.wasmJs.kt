package com.example.prisma.ui

import androidx.compose.runtime.Composable

@Composable
actual fun rememberFilePicker(
    onFileSelected: (name: String, mimeType: String, fileBytes: ByteArray) -> Unit,
): FilePickerState {
    return FilePickerState(
        launchPdfPicker = {},
        launchImagePicker = {},
    )
}

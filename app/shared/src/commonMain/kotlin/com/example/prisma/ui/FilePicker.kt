package com.example.prisma.ui

import androidx.compose.runtime.Composable

class FilePickerState(
    val launchPdfPicker: () -> Unit,
    val launchImagePicker: () -> Unit,
)

@Composable
expect fun rememberFilePicker(
    onFileSelected: (name: String, mimeType: String, fileBytes: ByteArray) -> Unit,
): FilePickerState

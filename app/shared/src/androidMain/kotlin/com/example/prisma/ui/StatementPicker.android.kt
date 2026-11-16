package com.example.prisma.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.prisma.cards.StatementUploadFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.cancellation.CancellationException

@Composable
actual fun rememberStatementPicker(
    onFilesSelected: (List<StatementUploadFile>) -> Unit,
    onError: (String) -> Unit,
): FilePickerState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnFilesSelected by rememberUpdatedState(onFilesSelected)
    val currentOnError by rememberUpdatedState(onError)

    fun selected(uris: List<Uri>, pdf: Boolean) {
        if (uris.isEmpty()) return

        if (uris.size > STATEMENT_MAX_FILES) {
            currentOnError("Selecciona hasta 12 archivos del mismo estado.")
            return
        }

        scope.launch {
            val files = try {
                withContext(Dispatchers.IO) {
                    var total = 0

                    uris.mapIndexed { index, uri ->
                        val name = statementFileName(context, uri)
                            ?: if (pdf) {
                                "estado.pdf"
                            } else "pagina-${index + 1}.jpg"

                        val mime = context.contentResolver.getType(uri)
                            ?.takeIf { it.isNotBlank() }
                            ?.lowercase()
                            ?: if (pdf) {
                                "application/pdf"
                            } else if (name.endsWith(".png", true)) {
                                "image/png"
                            } else "image/jpeg"

                        require(
                            mime in setOf(
                                "application/pdf",
                                "image/jpeg",
                                "image/png",
                            ),
                        ) {
                            "Selecciona un PDF o imágenes JPG/PNG."
                        }

                        val limit = minOf(
                            STATEMENT_MAX_FILE_BYTES,
                            STATEMENT_MAX_TOTAL_BYTES - total,
                        )

                        val bytes = statementFileBytes(context, uri, limit)
                        total += bytes.size

                        StatementUploadFile(name, mime, bytes)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalArgumentException) {
                currentOnError(
                    error.message
                        ?: "La selección supera los límites permitidos.",
                )
                return@launch
            } catch (_: Exception) {
                currentOnError(
                    "No se pudo leer uno de los archivos. Vuelve a seleccionar el lote completo.",
                )
                return@launch
            }

            deliverStatementBatch(
                files,
                currentOnFilesSelected,
                currentOnError,
            )
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) selected(listOf(uri), pdf = true)
    }

    val imagesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        selected(uris, pdf = false)
    }

    return remember(pdfLauncher, imagesLauncher) {
        FilePickerState(
            launchPdfPicker = {
                pdfLauncher.launch(arrayOf("application/pdf"))
            },
            launchImagePicker = {
                imagesLauncher.launch(arrayOf("image/jpeg", "image/png"))
            },
        )
    }
}

private fun statementFileName(
    context: Context,
    uri: Uri,
): String? =
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

        if (cursor.moveToFirst() && column >= 0) {
            cursor.getString(column)
        } else null
    }

private fun statementFileBytes(
    context: Context,
    uri: Uri,
    limit: Int,
): ByteArray {
    require(limit > 0) {
        "La selección supera 20 MB."
    }

    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException(
            "No se pudo abrir uno de los archivos.",
        )

    return input.use {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0

        while (true) {
            val count = it.read(buffer)
            if (count < 0) break

            total += count

            require(total <= limit) {
                "Cada archivo admite 10 MB y el conjunto hasta 20 MB."
            }

            output.write(buffer, 0, count)
        }

        output.toByteArray()
    }
}
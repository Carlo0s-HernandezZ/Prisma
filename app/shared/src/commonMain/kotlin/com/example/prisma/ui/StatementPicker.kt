package com.example.prisma.ui

import androidx.compose.runtime.Composable
import com.example.prisma.cards.StatementUploadFile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64

internal const val STATEMENT_MAX_FILES = 12
internal const val STATEMENT_MAX_FILE_BYTES = 10 * 1024 * 1024
internal const val STATEMENT_MAX_TOTAL_BYTES = 20 * 1024 * 1024

@Composable
expect fun rememberStatementPicker(
    onFilesSelected: (List<StatementUploadFile>) -> Unit,
    onError: (String) -> Unit,
): FilePickerState

internal fun deliverStatementBatch(
    files: List<StatementUploadFile>,
    onFilesSelected: (List<StatementUploadFile>) -> Unit,
    onError: (String) -> Unit,
) {
    val error = when {
        files.isEmpty() ->
            "No se pudieron leer los archivos seleccionados."

        files.size > STATEMENT_MAX_FILES ->
            "Selecciona hasta 12 archivos del mismo estado."

        files.any { it.bytes.isEmpty() } ->
            "Uno de los archivos está vacío. Selecciona el lote nuevamente."

        files.any { it.bytes.size > STATEMENT_MAX_FILE_BYTES } ->
            "Cada archivo puede pesar hasta 10 MB."

        files.sumOf { it.bytes.size.toLong() } > STATEMENT_MAX_TOTAL_BYTES ->
            "La selección supera 20 MB."

        files.any {
            it.mimeType !in setOf(
                "application/pdf",
                "image/jpeg",
                "image/png",
            )
        } -> "Selecciona un PDF o imágenes JPG/PNG."

        else -> null
    }

    if (error == null) {
        onFilesSelected(files.toList())
    } else {
        onError(error)
    }
}

internal fun deliverBrowserStatementBatch(
    payload: String,
    onFilesSelected: (List<StatementUploadFile>) -> Unit,
    onError: (String) -> Unit,
) {
    val files = try {
        Json.parseToJsonElement(payload).jsonArray.map { element ->
            val item = element.jsonObject

            StatementUploadFile(
                nombre = item.getValue("nombre").jsonPrimitive.content,
                mimeType = item.getValue("mimeType").jsonPrimitive.content,
                bytes = Base64.decode(
                    item.getValue("data").jsonPrimitive.content,
                ),
            )
        }
    } catch (_: Exception) {
        onError("No se pudo leer la selección. Vuelve a elegir los archivos.")
        return
    }

    deliverStatementBatch(files, onFilesSelected, onError)
}
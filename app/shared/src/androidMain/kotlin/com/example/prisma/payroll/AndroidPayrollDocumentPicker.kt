
package com.example.prisma.payroll

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.ByteArrayOutputStream

private const val MAX_FILE_BYTES = 10 * 1024 * 1024

@Composable
fun AndroidPayrollDocumentPickerProvider(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pendingCallback = remember {
        mutableStateOf<((String, ByteArray) -> Unit)?>(null)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->

        if (uri == null) {
            pendingCallback.value = null

        } else {
            scope.launch {
                try {
                    val (nombre, bytes) = withContext(Dispatchers.IO) {
                        leerDocumento(context, uri)
                    }

                    pendingCallback.value?.invoke(nombre, bytes)

                } catch (e: CancellationException) {
                    throw e

                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        e.message ?: "No se pudo leer el documento.",
                        Toast.LENGTH_LONG
                    ).show()

                } finally {
                    pendingCallback.value = null
                }
            }
        }
    }

    val picker = remember(launcher) {
        PayrollDocumentPicker { onSelected ->

            pendingCallback.value = onSelected

            launcher.launch(
                arrayOf(
                    "application/pdf",
                    "application/xml",
                    "text/xml"
                )
            )
        }
    }

    CompositionLocalProvider(
        LocalPayrollDocumentPicker provides picker
    ) {
        content()
    }
}

private fun leerDocumento(
    context: Context,
    uri: Uri
): Pair<String, ByteArray> {

    val resolver = context.contentResolver

    // Buscar el nombre proporcionado por el sistema.
    val nombreOriginal = resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->

        if (cursor.moveToFirst()) {
            val indice = cursor.getColumnIndex(
                OpenableColumns.DISPLAY_NAME
            )

            if (indice >= 0) {
                cursor.getString(indice)
            } else {
                null
            }
        } else {
            null
        }

    } ?: throw IllegalArgumentException(
        "No se pudo obtener el nombre del documento."
    )

    val extension = when {
        nombreOriginal.endsWith(".pdf", ignoreCase = true) ->
            ".pdf"

        nombreOriginal.endsWith(".xml", ignoreCase = true) ->
            ".xml"

        else -> throw IllegalArgumentException(
            "Selecciona un archivo PDF o XML."
        )
    }

    // Normalizar nombre para el repositorio.
    val nombreBase = nombreOriginal
        .dropLast(4)
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('.', '_')
        .take(90)
        .ifBlank { "nomina" }

    val nombreSeguro = nombreBase + extension

    val bytes = resolver.openInputStream(uri)?.use { entrada ->

        val salida = ByteArrayOutputStream()
        val buffer = ByteArray(8192)

        while (true) {
            val leidos = entrada.read(buffer)

            if (leidos == -1) {
                break
            }

            if (leidos == 0) {
                continue
            }

            if (salida.size() + leidos > MAX_FILE_BYTES) {
                throw IllegalArgumentException(
                    "El documento supera el límite de 10 MB."
                )
            }

            salida.write(buffer, 0, leidos)
        }

        salida.toByteArray()

    } ?: throw IllegalArgumentException(
        "No fue posible abrir el archivo."
    )

    if (bytes.isEmpty()) {
        throw IllegalArgumentException(
            "El documento seleccionado está vacío."
        )
    }

    return nombreSeguro to bytes
}

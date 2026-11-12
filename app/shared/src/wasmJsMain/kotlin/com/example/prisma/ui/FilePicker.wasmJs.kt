package com.example.prisma.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.io.encoding.Base64

/**
 * Abre el selector de archivos del navegador (<input type="file"> oculto) y entrega cada archivo
 * como (nombre, tipo MIME, contenido en Base64). El Base64 se arma por bloques para no rebasar
 * la pila con archivos grandes.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun openBrowserFileDialog(accept: String, multiple: Boolean, onFile: (String, String, String) -> Unit): Unit = js(
    """{
    var input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    input.multiple = multiple;
    input.style.display = 'none';
    input.onchange = function () {
        var files = Array.prototype.slice.call(input.files || []);
        files.forEach(function (file) {
            var reader = new FileReader();
            reader.onload = function () {
                var bytes = new Uint8Array(reader.result);
                var binary = '';
                for (var i = 0; i < bytes.length; i += 32768) {
                    binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 32768));
                }
                onFile(file.name, file.type || '', btoa(binary));
            };
            reader.readAsArrayBuffer(file);
        });
        input.remove();
    };
    document.body.appendChild(input);
    input.click();
}"""
)

@Composable
actual fun rememberFilePicker(
    onFileSelected: (name: String, mimeType: String, fileBytes: ByteArray) -> Unit,
): FilePickerState {
    val currentOnFileSelected by rememberUpdatedState(onFileSelected)
    return remember {
        FilePickerState(
            launchPdfPicker = {
                openBrowserFileDialog("application/pdf,.pdf", false) { name, mime, base64 ->
                    currentOnFileSelected(name, mime.ifBlank { "application/pdf" }, Base64.decode(base64))
                }
            },
            launchImagePicker = {
                openBrowserFileDialog("image/*", true) { name, mime, base64 ->
                    currentOnFileSelected(name, mime.ifBlank { "image/jpeg" }, Base64.decode(base64))
                }
            },
        )
    }
}

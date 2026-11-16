package com.example.prisma.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.example.prisma.cards.StatementUploadFile

@OptIn(ExperimentalWasmJsInterop::class)
private fun openStatementBrowserDialog(
    accept: String,
    multiple: Boolean,
    onBatch: (String) -> Unit,
    onError: (String) -> Unit,
): Unit = js(
    """{
    var input = document.createElement('input');
    input.type = 'file';
    input.accept = accept;
    input.multiple = multiple;
    input.style.display = 'none';
    var handled = false;

    input.oncancel = function () {
        handled = true;
        input.remove();
    };

    input.onchange = function () {
        if (handled) return;
        handled = true;

        var files = Array.prototype.slice.call(input.files || []);
        input.remove();
        if (!files.length) return;

        if (files.length > 12 || (!multiple && files.length !== 1)) {
            onError('Selecciona hasta 12 archivos del mismo estado.');
            return;
        }

        var total = files.reduce(function (sum, file) {
            return sum + file.size;
        }, 0);

        if (files.some(function (file) {
            return file.size === 0 || file.size > 10485760;
        }) || total > 20971520) {
            onError('Cada archivo debe contener datos, pesar hasta 10 MB y la selección hasta 20 MB.');
            return;
        }

        Promise.all(files.map(function (file) {
            return new Promise(function (resolve, reject) {
                var reader = new FileReader();

                reader.onerror = function () {
                    reject(new Error('read'));
                };

                reader.onabort = function () {
                    reject(new Error('read'));
                };

                reader.onload = function () {
                    try {
                        var bytes = new Uint8Array(reader.result);
                        var binary = '';

                        for (var i = 0; i < bytes.length; i += 32768) {
                            binary += String.fromCharCode.apply(
                                null,
                                bytes.subarray(i, i + 32768)
                            );
                        }

                        var mime = file.type || (
                            multiple
                                ? (
                                    file.name.toLowerCase().endsWith('.png')
                                        ? 'image/png'
                                        : 'image/jpeg'
                                  )
                                : 'application/pdf'
                        );

                        if (['application/pdf', 'image/jpeg', 'image/png'].indexOf(mime) < 0) {
                            throw new Error('type');
                        }

                        resolve({
                            nombre: file.name,
                            mimeType: mime,
                            data: btoa(binary)
                        });
                    } catch (error) {
                        reject(error);
                    }
                };

                reader.readAsArrayBuffer(file);
            });
        })).then(function (batch) {
            onBatch(JSON.stringify(batch));
        }, function () {
            onError('No se pudo leer la selección completa. Usa PDF, JPG o PNG y vuelve a intentarlo.');
        });
    };

    document.body.appendChild(input);
    input.click();
}""",
)

@Composable
actual fun rememberStatementPicker(
    onFilesSelected: (List<StatementUploadFile>) -> Unit,
    onError: (String) -> Unit,
): FilePickerState {
    val currentOnFilesSelected by rememberUpdatedState(onFilesSelected)
    val currentOnError by rememberUpdatedState(onError)

    return remember {
        FilePickerState(
            launchPdfPicker = {
                openStatementBrowserDialog(
                    "application/pdf,.pdf",
                    false,
                    {
                        deliverBrowserStatementBatch(
                            it,
                            currentOnFilesSelected,
                            currentOnError,
                        )
                    },
                    { currentOnError(it) },
                )
            },
            launchImagePicker = {
                openStatementBrowserDialog(
                    "image/jpeg,image/png,.jpg,.jpeg,.png",
                    true,
                    {
                        deliverBrowserStatementBatch(
                            it,
                            currentOnFilesSelected,
                            currentOnError,
                        )
                    },
                    { currentOnError(it) },
                )
            },
        )
    }
}
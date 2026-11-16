package com.example.prisma.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.StatementExtractionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatementReaderUiState(
    val files: List<StatementSelectedFile> = emptyList(),
    val isReading: Boolean = false,
    val result: StatementExtractionResponse? = null,
    val error: String? = null,
    val sessionExpired: Boolean = false,
)

class StatementReaderViewModel(
    private val cardId: String,
    private val repository: StatementExtractionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        StatementReaderUiState(),
    )

    val state = _state.asStateFlow()

    private var nextId = 0L

    fun addFile(
        nombre: String,
        bytes: ByteArray,
    ) {
        val current = _state.value

        if (current.isReading) return

        val message = when {
            bytes.isEmpty() ->
                "El archivo está vacío."

            bytes.size > 10 * 1024 * 1024 ->
                "Cada archivo puede pesar hasta 10 MB."

            current.files.any {
                it.bytes.contentEquals(bytes)
            } ->
                "Ese archivo ya está seleccionado."

            current.files.size >= 12 ->
                "Selecciona hasta 12 archivos del mismo estado."

            current.files.sumOf {
                it.bytes.size.toLong()
            } + bytes.size > 20L * 1024 * 1024 ->
                "La selección supera 20 MB."

            else -> null
        }

        if (message != null) {
            _state.update {
                it.copy(error = message)
            }
            return
        }

        nextId += 1

        _state.update {
            it.copy(
                files = it.files + StatementSelectedFile(
                    nextId,
                    nombre,
                    bytes,
                ),
                result = null,
                error = null,
            )
        }
    }

    fun removeFile(id: Long) {
        _state.update {
            if (it.isReading) {
                it
            } else {
                it.copy(
                    files = it.files.filterNot { file ->
                        file.id == id
                    },
                    result = null,
                    error = null,
                )
            }
        }
    }

    fun clear() {
        if (!_state.value.isReading) {
            _state.value = StatementReaderUiState()
        }
    }

    fun read() {
        val files = _state.value.files

        if (_state.value.isReading || files.isEmpty()) return

        _state.update {
            it.copy(
                isReading = true,
                result = null,
                error = null,
            )
        }

        viewModelScope.launch {
            repository.extract(cardId, files).fold(
                onSuccess = { result ->
                    _state.update {
                        it.copy(
                            isReading = false,
                            result = result,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isReading = false,
                            error = error.message
                                ?: "No se pudo leer el estado de cuenta.",
                            sessionExpired =
                                (error as? StatementExtractionException)
                                    ?.status == 401,
                        )
                    }
                },
            )
        }
    }
}
package com.example.prisma.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.CardDocumentDto
import com.example.prisma.api.CardDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CardDetailUiState(
    val card: CardDto? = null,
    val documents: List<CardDocumentDto> = emptyList(),
    val isLoading: Boolean = true,
    val isUploading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val sessionExpired: Boolean = false,
)

class CardDetailViewModel(
    private val card: CardDto,
    private val repository: CardRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CardDetailUiState(card = card))
    val state: StateFlow<CardDetailUiState> = _state.asStateFlow()

    init {
        loadDocuments()
    }

    fun loadDocuments() {
        _state.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            repository.listDocuments(card.id).fold(
                onSuccess = { docs ->
                    _state.update { it.copy(isLoading = false, documents = docs) }
                },
                onFailure = { e -> fail(e, "No se pudieron cargar los documentos.") }
            )
        }
    }

    /** Las subidas se encolan: al elegir varias fotos a la vez se suben una por una, sin perder ninguna. */
    private val uploadMutex = Mutex()
    private var pendingUploads = 0

    fun uploadDocument(fileName: String, mimeType: String, fileBytes: ByteArray) {
        pendingUploads++
        _state.update { it.copy(isUploading = true, message = null) }
        viewModelScope.launch {
            val result = uploadMutex.withLock {
                repository.uploadDocument(card.id, fileName, mimeType, fileBytes)
            }
            pendingUploads--
            val stillUploading = pendingUploads > 0
            result.fold(
                onSuccess = { doc ->
                    _state.update {
                        it.copy(
                            isUploading = stillUploading,
                            documents = listOf(doc) + it.documents,
                            message = "Estado de cuenta procesado y extraído exitosamente.",
                            isError = false
                        )
                    }
                },
                onFailure = { e ->
                    fail(e, "No se pudo subir \"$fileName\".")
                    if (stillUploading) _state.update { it.copy(isUploading = true) }
                }
            )
        }
    }

    fun deleteDocument(docId: String) {
        viewModelScope.launch {
            repository.deleteDocument(card.id, docId).fold(
                onSuccess = {
                    _state.update { state ->
                        state.copy(
                            documents = state.documents.filter { it.id != docId },
                            message = "Documento eliminado.",
                            isError = false
                        )
                    }
                },
                onFailure = { e -> fail(e, "No se pudo eliminar el documento.") }
            )
        }
    }

    private fun fail(e: Throwable, fallback: String) {
        val error = (e as? CardException)?.error ?: CardError.Unknown
        _state.update {
            it.copy(
                isLoading = false,
                isUploading = false,
                isError = true,
                sessionExpired = error == CardError.Unauthorized,
                message = when (error) {
                    CardError.Network -> "Sin conexión con el servidor."
                    CardError.Unauthorized -> "Tu sesión expiró."
                    else -> fallback
                }
            )
        }
    }
}

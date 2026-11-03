package com.example.prisma.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.prisma.api.PersonalData
import com.example.prisma.ui.todayIsoDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val nombre: String = "",
    val apellido: String = "",
    /** yyyy-MM-dd o vacío. */
    val fechaNacimiento: String = "",
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val confirmDelete: Boolean = false,
    /** El servidor rechazó el token: la app debe volver al login. */
    val sessionExpired: Boolean = false,
)

class ProfileViewModel(private val repository: ProfileRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onNombreChange(v: String) = _state.update { it.copy(nombre = v, message = null) }
    fun onApellidoChange(v: String) = _state.update { it.copy(apellido = v, message = null) }
    fun onFechaChange(v: String) = _state.update { it.copy(fechaNacimiento = v, message = null) }

    private fun load() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            repository.load().fold(
                onSuccess = { data ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            nombre = data?.nombre.orEmpty(),
                            apellido = data?.apellido.orEmpty(),
                            fechaNacimiento = data?.fechaNacimiento.orEmpty(),
                        )
                    }
                },
                onFailure = { e -> fail(e, "No se pudo cargar tu perfil.") },
            )
        }
    }

    fun save() {
        val s = _state.value
        if (s.isLoading) return
        if (s.nombre.isBlank() || s.apellido.isBlank()) {
            _state.update { it.copy(message = "Por favor ingresa nombre y apellido.", isError = true) }
            return
        }
        // Las fechas "yyyy-MM-dd" se comparan correctamente como texto.
        if (s.fechaNacimiento.isNotBlank() && s.fechaNacimiento > todayIsoDate()) {
            _state.update {
                it.copy(message = "La fecha de nacimiento no puede ser futura. Toca el campo de fecha y elige la correcta.", isError = true)
            }
            return
        }
        _state.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            val data = PersonalData(s.nombre.trim(), s.apellido.trim(), s.fechaNacimiento.ifBlank { null })
            repository.save(data).fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false, message = "¡Datos personales guardados con éxito!", isError = false) }
                },
                onFailure = { e -> fail(e, "Error al guardar los datos.") },
            )
        }
    }

    fun requestDelete() = _state.update { if (it.isLoading) it else it.copy(confirmDelete = true) }

    fun cancelDelete() = _state.update { it.copy(confirmDelete = false) }

    fun confirmDelete() {
        if (_state.value.isLoading) return
        _state.update { it.copy(confirmDelete = false, isLoading = true, message = null) }
        viewModelScope.launch {
            repository.delete().fold(
                onSuccess = {
                    _state.update {
                        ProfileUiState(message = "Datos personales eliminados.", isError = false)
                    }
                },
                onFailure = { e -> fail(e, "No se pudieron eliminar los datos.") },
            )
        }
    }

    private fun fail(e: Throwable, fallback: String) {
        val error = (e as? ProfileException)?.error ?: ProfileError.Unknown
        _state.update {
            it.copy(
                isLoading = false,
                isError = true,
                sessionExpired = error == ProfileError.Unauthorized,
                message = when (error) {
                    ProfileError.Network -> "Sin conexión con el servidor. Inténtalo de nuevo."
                    ProfileError.NotFound -> "No se encontraron datos para eliminar."
                    ProfileError.Invalid -> (e as? ProfileException)?.serverMessage
                        ?: "Revisa los datos: nombre, apellido y fecha deben ser válidos."
                    ProfileError.Unauthorized -> "Tu sesión expiró."
                    ProfileError.Unknown -> "$fallback El servidor respondió con un error; si continúa, verifica que esté actualizado y reiniciado."
                },
            )
        }
    }
}

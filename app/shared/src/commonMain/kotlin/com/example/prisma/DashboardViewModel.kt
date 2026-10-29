package com.example.prisma.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(repository: DashboardRepository) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = repository.observeDashboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}

package dev.tether.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tether.core.data.NetworkStatus
import dev.tether.core.data.Tether
import dev.tether.core.model.ProviderSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreUiState(
    val loading: Boolean = false,
    val sections: List<ProviderSection> = emptyList(),
    val onlineEnabled: Boolean = true,
    val offline: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

class ExploreViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = Tether.graph(application)
    private val appContext = application.applicationContext
    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val settings = graph.settings.settings.first()
            if (!settings.onlineDiscoveryEnabled) {
                _state.value = ExploreUiState(
                    loading = false,
                    onlineEnabled = false,
                    loaded = true,
                    sections = emptyList(),
                )
                return@launch
            }
            if (!NetworkStatus.isOnline(appContext)) {
                _state.value = ExploreUiState(
                    loading = false,
                    onlineEnabled = true,
                    offline = true,
                    loaded = true,
                )
                return@launch
            }
            runCatching { graph.search.discoverySections() }
                .onSuccess { sections ->
                    _state.value = ExploreUiState(
                        loading = false,
                        sections = sections,
                        onlineEnabled = true,
                        loaded = true,
                        error = if (sections.isEmpty()) "The provider returned no discoverable music right now." else null,
                    )
                }
                .onFailure { error ->
                    _state.value = ExploreUiState(
                        loading = false,
                        onlineEnabled = true,
                        loaded = true,
                        error = error.message ?: "Online discovery could not be reached.",
                    )
                }
        }
    }
}

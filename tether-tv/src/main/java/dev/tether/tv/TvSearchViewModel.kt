package dev.tether.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tether.core.data.SearchResults
import dev.tether.core.data.Tether
import dev.tether.core.model.ProviderTrack
import dev.tether.core.model.RecentSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TvSearchState(
    val query: String = "",
    val local: SearchResults = SearchResults(),
    val remoteTracks: List<ProviderTrack> = emptyList(),
    val providerNote: String? = null,
    val loadingLocal: Boolean = false,
    val loadingRemote: Boolean = false,
    val searchedOnline: Boolean = false,
) {
    val hasLocal: Boolean get() = local.hasLocal
    val hasRemote: Boolean get() = remoteTracks.isNotEmpty()
    val isEmpty: Boolean get() = query.isNotBlank() && !loadingLocal && !loadingRemote && !hasLocal && !hasRemote
}

class TvSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = Tether.graph(application)
    val recentSearches: StateFlow<List<RecentSearch>> = graph.search.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(TvSearchState())
    val state: StateFlow<TvSearchState> = _state
    private var job: Job? = null

    fun setQuery(query: String) {
        val normalized = query.take(80)
        _state.update { it.copy(query = normalized) }
        job?.cancel()
        if (normalized.isBlank()) {
            _state.value = TvSearchState()
            return
        }
        job = viewModelScope.launch {
            val current = withContext(Dispatchers.IO) { graph.settings.settings.first() }
            _state.update { it.copy(loadingLocal = true, loadingRemote = false, providerNote = null) }
            val local = withContext(Dispatchers.IO) { graph.search.searchLocal(normalized) }
            _state.update {
                it.copy(
                    local = local,
                    loadingLocal = false,
                    loadingRemote = current.onlineDiscoveryEnabled,
                )
            }
            if (!current.onlineDiscoveryEnabled) {
                _state.update { it.copy(searchedOnline = false, loadingRemote = false) }
                return@launch
            }
            val remote = withContext(Dispatchers.IO) { runCatching { graph.search.searchOnline(normalized) }.getOrNull() }
            _state.update {
                it.copy(
                    remoteTracks = remote?.remoteTracks.orEmpty(),
                    providerNote = remote?.providerNote,
                    loadingRemote = false,
                    searchedOnline = true,
                )
            }
        }
    }

    fun rememberCurrentQuery() {
        val query = _state.value.query.trim()
        if (query.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) { graph.search.recordSearch(query) }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) { graph.search.clearHistory() }
    }
}

package dev.tether.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.tether.core.data.SearchResults
import dev.tether.core.data.Tether
import dev.tether.core.model.RecentSearch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
    val searchingLocal: Boolean = false,
    val searchingOnline: Boolean = false,
    val error: String? = null,
)

@OptIn(FlowPreview::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = Tether.graph(application)
    private val _query = MutableStateFlow("")
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    val recentSearches: StateFlow<List<RecentSearch>> = graph.search.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _query
                .debounce(280)
                .distinctUntilChanged()
                .collect { query ->
                    val trimmed = query.trim()
                    if (trimmed.isEmpty()) {
                        _state.value = SearchUiState(query = query)
                        return@collect
                    }
                    _state.update {
                        it.copy(query = query, searchingLocal = true, searchingOnline = false, error = null)
                    }
                    val local = runCatching { graph.search.searchLocal(trimmed) }
                    if (local.isFailure) {
                        _state.value = SearchUiState(
                            query = query,
                            error = local.exceptionOrNull()?.message ?: "Local search failed.",
                        )
                        return@collect
                    }
                    _state.update {
                        it.copy(
                            results = local.getOrThrow(),
                            searchingLocal = false,
                            searchingOnline = true,
                        )
                    }
                    val online = runCatching { graph.search.searchOnline(trimmed) }
                    online.onSuccess { remote ->
                        _state.update { current ->
                            if (current.query.trim() != trimmed) {
                                current
                            } else {
                                current.copy(
                                    results = current.results.copy(
                                        remoteTracks = remote.remoteTracks,
                                        remoteReleases = remote.remoteReleases,
                                        providerNote = remote.providerNote,
                                        searchedOnline = remote.searchedOnline,
                                    ),
                                    searchingOnline = false,
                                )
                            }
                        }
                    }.onFailure { error ->
                        _state.update {
                            it.copy(
                                searchingOnline = false,
                                error = null,
                                results = it.results.copy(
                                    providerNote = "Online search could not be reached: ${error.message ?: "network error"}. Local results are still shown.",
                                    searchedOnline = true,
                                ),
                            )
                        }
                    }
                }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
        _state.update { it.copy(query = value) }
    }

    fun submit() {
        val query = _query.value.trim()
        if (query.isNotEmpty()) {
            viewModelScope.launch { graph.search.recordSearch(query) }
        }
    }

    fun clear() {
        _query.value = ""
        _state.value = SearchUiState()
    }

    fun chooseRecent(query: String) {
        setQuery(query)
        submit()
    }

    fun removeRecent(query: String) {
        viewModelScope.launch { graph.search.removeSearch(query) }
    }

    fun clearRecent() {
        viewModelScope.launch { graph.search.clearHistory() }
    }
}

package com.kite.zmusic.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kite.zmusic.data.PagedCommunityCatalog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommunityCatalogUiState<T>(
    val query: String = "",
    val entries: List<T> = emptyList(),
    val ready: Boolean = false,
    val failed: Boolean = false,
    val more: Boolean = false,
    val refreshing: Boolean = false,
)

class CommunityCatalogViewModel<T>(
    private val repository: PagedCommunityCatalog<T>,
    private val queryLimit: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(CommunityCatalogUiState<T>())
    val state: StateFlow<CommunityCatalogUiState<T>> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var refreshJob: Job? = null

    init {
        ensureRange()
    }

    fun onQueryChange(value: String) {
        val next = value.take(queryLimit)
        _state.update { it.copy(query = next) }
        searchJob?.cancel()
        if (next.isBlank()) {
            _state.update {
                it.copy(
                    entries = repository.rangeEntries,
                    ready = repository.rangeReady || repository.rangeFailed,
                    failed = repository.rangeFailed,
                    more = repository.more,
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SearchDebounceMs)
            val hits = repository.search(next)
            if (_state.value.query != next) return@launch
            if (hits == null) {
                _state.update { it.copy(entries = emptyList(), ready = true, failed = true, more = false) }
            } else {
                _state.update { it.copy(entries = hits, ready = true, failed = false, more = false) }
            }
        }
    }

    fun loadMore() {
        val snapshot = _state.value
        if (snapshot.query.isNotBlank() ||
            !snapshot.more ||
            !snapshot.ready ||
            snapshot.failed ||
            snapshot.refreshing
        ) {
            return
        }
        if (loadMoreJob?.isActive == true || refreshJob?.isActive == true) return
        loadMoreJob = viewModelScope.launch {
            repository.loadMore()
            if (_state.value.query.isNotBlank()) return@launch
            _state.update {
                it.copy(
                    entries = repository.rangeEntries,
                    more = repository.more,
                )
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        searchJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                val q = _state.value.query
                if (q.isBlank()) {
                    repository.refreshRange()
                    if (_state.value.query.isNotBlank()) return@launch
                    _state.update {
                        it.copy(
                            entries = repository.rangeEntries,
                            ready = true,
                            failed = repository.rangeFailed && repository.rangeEntries.isEmpty(),
                            more = repository.more,
                        )
                    }
                } else {
                    val hits = repository.refreshSearch(q)
                    if (_state.value.query != q) return@launch
                    if (hits == null) {
                        _state.update { it.copy(failed = it.entries.isEmpty(), more = false) }
                    } else {
                        _state.update {
                            it.copy(entries = hits, ready = true, failed = false, more = false)
                        }
                    }
                }
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    private fun ensureRange() {
        if (repository.rangeReady) {
            _state.update {
                it.copy(
                    entries = repository.rangeEntries,
                    ready = true,
                    failed = false,
                    more = repository.more,
                )
            }
            return
        }
        viewModelScope.launch {
            repository.ensureRange()
            if (_state.value.query.isNotBlank()) return@launch
            _state.update {
                it.copy(
                    entries = repository.rangeEntries,
                    ready = true,
                    failed = repository.rangeFailed,
                    more = repository.more,
                )
            }
        }
    }

    companion object {
        private const val SearchDebounceMs = 300L
    }
}

class CommunityCatalogViewModelFactory<T>(
    private val repository: PagedCommunityCatalog<T>,
    private val queryLimit: Int,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
        return CommunityCatalogViewModel(repository, queryLimit) as VM
    }
}

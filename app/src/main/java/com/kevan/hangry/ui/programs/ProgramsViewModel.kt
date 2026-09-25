package com.kevan.hangry.ui.programs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kevan.hangry.data.repository.ProgramsRepository
import com.kevan.hangry.domain.model.ProgramFeel
import com.kevan.hangry.domain.model.ProgramSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Shared by the Programs list and each program's screen. */
class ProgramsViewModel(private val repository: ProgramsRepository) : ViewModel() {

    val programs: StateFlow<List<ProgramSnapshot>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Exercises ticked in the session in progress, per program. */
    private val _checked = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val checked: StateFlow<Map<String, Set<String>>> = _checked.asStateFlow()

    fun toggle(programId: String, exerciseId: String) = _checked.update { all ->
        val current = all[programId].orEmpty()
        all + (programId to if (exerciseId in current) current - exerciseId else current + exerciseId)
    }

    fun setEnabled(programId: String, enabled: Boolean) = repository.setEnabled(programId, enabled)

    fun setLevel(programId: String, level: Int) {
        repository.setLevel(programId, level)
        _checked.update { it - programId }
    }

    fun finish(programId: String, feel: ProgramFeel) {
        viewModelScope.launch {
            repository.finishSession(programId, feel)
            _checked.update { it - programId }
        }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { repository.deleteSession(id) }
    }

    companion object {
        fun provideFactory(repository: ProgramsRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProgramsViewModel(repository) as T
        }
    }
}

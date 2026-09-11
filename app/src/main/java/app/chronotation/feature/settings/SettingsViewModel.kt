package app.chronotation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.chronotation.R
import app.chronotation.data.repository.PreferencesRepository
import app.chronotation.domain.ThemeMode
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: PreferencesRepository) : ViewModel() {
    // A null initial value lets the Activity wait for persisted appearance before drawing.
    val preferences = repository.preferences.stateIn(
        viewModelScope, SharingStarted.Eagerly, null,
    )
    private val errors = Channel<Int>(Channel.BUFFERED)
    val messages = errors.receiveAsFlow()

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch {
            try {
                repository.setTheme(mode)
            } catch (_: IOException) {
                errors.send(R.string.preferences_save_failed)
            }
        }
    }

    companion object {
        fun factory(repository: PreferencesRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
                    return SettingsViewModel(repository) as T
                }
            }
    }
}

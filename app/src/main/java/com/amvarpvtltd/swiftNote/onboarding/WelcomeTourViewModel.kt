package com.amvarpvtltd.swiftNote.onboarding

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reuses the existing flag. Never touches account, device, note or sync preferences. */
internal class WelcomeTourPreferences(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.getSharedPreferences("welcome_tour_prefs", Context.MODE_PRIVATE)
    )

    fun isComplete(): Boolean = preferences.getBoolean("completed", false)

    // Call on IO; only leave the welcome screen once completion is durable.
    fun complete(): Boolean = preferences.edit().putBoolean("completed", true).commit()
}

data class WelcomeTourState(
    val completed: Boolean = false,
    val saving: Boolean = false,
    val saveFailed: Boolean = false
)

class WelcomeTourViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = WelcomeTourPreferences(application)
    private val _state = MutableStateFlow(WelcomeTourState(completed = preferences.isComplete()))
    val state = _state.asStateFlow()

    fun complete() {
        if (_state.value.completed || _state.value.saving) return
        _state.value = _state.value.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching { preferences.complete() }.getOrDefault(false)
            }
            _state.value = WelcomeTourState(completed = saved, saveFailed = !saved)
        }
    }
}

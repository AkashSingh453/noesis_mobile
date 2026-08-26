package com.noesis.app

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val supabaseClient: SupabaseClient
) : ViewModel() {
    val sessionStatus: StateFlow<SessionStatus> = supabaseClient.auth.sessionStatus
}

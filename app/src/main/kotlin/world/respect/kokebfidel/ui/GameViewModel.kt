package world.respect.kokebfidel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import world.respect.kokebfidel.data.database.GameEntity
import world.respect.kokebfidel.data.repository.GameRepository
import world.respect.kokebfidel.data.models.RespectLaunchInfo

import kotlinx.coroutines.flow.*

    class GameViewModel(private val repository: GameRepository) : ViewModel() {
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val games: StateFlow<List<GameEntity>> = repository.getGames()
        .onEach { if (it.isNotEmpty()) _loading.value = false }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val progress: StateFlow<Map<String, Int>> = repository.getProgressFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    init {
        refreshGames()
    }

    fun refreshGames() {
        viewModelScope.launch {
            _loading.value = true
            try {
                repository.syncGameList()
            } finally {
                _loading.value = false
            }
        }
    }

    private val xapiReporter = world.respect.kokebfidel.data.network.XapiReporter(okhttp3.OkHttpClient())

    fun saveStars(
        gameId: String, 
        gameTitle: String, 
        activityId: String, 
        stars: Int, 
        maxStars: Int = 3, 
        launchInfo: world.respect.kokebfidel.data.models.RespectLaunchInfo? = null
    ) {
        viewModelScope.launch {
            repository.saveProgress(gameId, activityId, stars)
            
            // RESPECT: Cloud Synchronization Reporting
            launchInfo?.let { info ->
                if (!info.xapiEndpoint.isNullOrEmpty() && !info.auth.isNullOrEmpty()) {
                    xapiReporter.reportMissionProgress(
                        endpoint = info.xapiEndpoint,
                        auth = info.auth,
                        givenName = info.givenName,
                        gameId = gameId,
                        gameTitle = gameTitle,
                        score = stars,
                        maxScore = maxStars,
                        success = stars >= 2
                    )
                }
            }
        }
    }

    fun loadGameDetails(gameId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                repository.fetchGameDetails(gameId)
            } finally {
                _loading.value = false
            }
        }
    }
}

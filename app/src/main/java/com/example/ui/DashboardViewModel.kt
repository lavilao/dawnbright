package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.model.GameApp
import com.example.data.model.ServerStatus
import com.example.data.model.StreamConfig
import com.example.service.ScreenCastService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepo = AppRepository(application)

    val serverStatus: StateFlow<ServerStatus> = ScreenCastService.serverStatus

    private val _streamConfig = MutableStateFlow(ScreenCastService.currentConfig)
    val streamConfig: StateFlow<StreamConfig> = _streamConfig.asStateFlow()

    private val _installedApps = MutableStateFlow<List<GameApp>>(emptyList())
    val installedApps: StateFlow<List<GameApp>> = _installedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val filteredApps: StateFlow<List<GameApp>> = combine(
        _installedApps,
        _searchQuery,
        _selectedCategory
    ) { apps, query, category ->
        apps.filter { app ->
            val matchesQuery = query.isEmpty() ||
                    app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)

            val matchesCategory = when (category) {
                "Emulators" -> app.isEmulator
                "Games" -> app.category == "Game" || app.isEmulator
                else -> true
            }

            matchesQuery && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            val apps = appRepo.getInstalledGamesAndApps()
            _installedApps.value = apps
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateConfig(config: StreamConfig) {
        _streamConfig.value = config
        ScreenCastService.currentConfig = config
    }

    fun launchApp(packageName: String) {
        appRepo.launchPackage(packageName)
    }

    fun stopServer(context: Context) {
        val stopIntent = Intent(context, ScreenCastService::class.java).apply {
            action = ScreenCastService.ACTION_STOP
        }
        context.startService(stopIntent)
    }
}

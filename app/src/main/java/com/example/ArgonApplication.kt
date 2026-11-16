package com.example

import android.app.Application
import com.example.argon.data.local.ArgonDatabase
import com.example.argon.data.local.PreferencesManager
import com.example.argon.data.local.RestrictionRepository
import com.example.argon.data.remote.ArgonNavigationRepository
import com.example.argon.data.remote.OfflineMapStorage
import com.example.argon.location.TruckLocationController
import com.example.argon.ui.screens.hasConfiguredMapsApiKey
import com.example.argon.voice.TruckVoiceNavigator
import com.google.android.gms.maps.MapsInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ArgonApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: ArgonDatabase by lazy { ArgonDatabase.getInstance(this) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
    val restrictionRepository: RestrictionRepository by lazy {
        RestrictionRepository(
            restrictionDao = database.roadRestrictionDao(),
            crossingDao = database.restrictionCrossingDao(),
            poiDao = database.truckPoiDao()
        )
    }
    val navigationRepository: ArgonNavigationRepository by lazy {
        ArgonNavigationRepository(this, restrictionRepository)
    }
    val offlineMapStorage: OfflineMapStorage by lazy { OfflineMapStorage(this, database.offlineRegionDao()) }
    val locationController: TruckLocationController by lazy { TruckLocationController(this) }
    val voiceNavigator: TruckVoiceNavigator by lazy { TruckVoiceNavigator(this) }

    override fun onCreate() {
        super.onCreate()
        if (hasConfiguredMapsApiKey()) {
            runCatching {
                MapsInitializer.initialize(this, MapsInitializer.Renderer.LATEST) { }
            }
        }
        appScope.launch {
            database.ensureSeeded()
        }
    }
}

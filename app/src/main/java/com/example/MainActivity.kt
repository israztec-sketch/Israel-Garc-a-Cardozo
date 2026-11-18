package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.argon.domain.model.NavigationStatus
import com.example.argon.ui.MainViewModel
import com.example.argon.ui.screens.ArgonDiagnosticsScreen
import com.example.argon.ui.screens.CabinaScreen
import com.example.argon.ui.screens.MexicoOfflineScreen
import com.example.argon.ui.screens.RoutesScreen
import com.example.argon.ui.screens.VehicleProfileScreen
import com.example.ui.theme.ArgonTruckTheme

const val ROUTE_CABINA = "cabina_gps"
const val ROUTE_VEHICULO = "vehiculo"
const val ROUTE_RUTAS = "rutas"
const val ROUTE_MEXICO = "mexico_32"
const val ROUTE_ARGON_API = "argon_api"

private data class ArgonNavDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val testTag: String
)

private val TopDestinations = listOf(
    ArgonNavDestination(ROUTE_CABINA, R.string.nav_map, Icons.Default.Navigation, "nav_cabina"),
    ArgonNavDestination(ROUTE_VEHICULO, R.string.nav_vehicle, Icons.Default.LocalShipping, "nav_vehiculo"),
    ArgonNavDestination(ROUTE_RUTAS, R.string.nav_routes, Icons.Default.Route, "nav_rutas"),
    ArgonNavDestination(ROUTE_MEXICO, R.string.nav_mexico, Icons.Default.Map, "nav_mexico"),
    ArgonNavDestination(ROUTE_ARGON_API, R.string.nav_argon, Icons.Default.Api, "nav_argon")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val profile by viewModel.activeProfile.collectAsStateWithLifecycle()
            val profiles by viewModel.profiles.collectAsStateWithLifecycle()
            val regions by viewModel.regions.collectAsStateWithLifecycle()
            val pois by viewModel.pois.collectAsStateWithLifecycle()
            val restrictions by viewModel.restrictions.collectAsStateWithLifecycle()
            val recentCrossings by viewModel.recentCrossings.collectAsStateWithLifecycle()
            val preferences by viewModel.preferences.collectAsStateWithLifecycle()
            val location by viewModel.currentLocation.collectAsStateWithLifecycle()
            val lastVoice by viewModel.lastVoiceAnnouncement.collectAsStateWithLifecycle()

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { result ->
                val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
                val coarseGranted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                val canShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                viewModel.onLocationPermissionResult(
                    fineGranted = fineGranted,
                    coarseGranted = coarseGranted,
                    shouldShowRationaleAfterDeny = canShowRationale
                )
            }

            fun launchFineAndCoarsePermissionPrompt() {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            fun requestLocationWithAndroidGuideFlow() {
                val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                if (shouldShowRationale) {
                    viewModel.requestLocationRationale(true)
                } else {
                    launchFineAndCoarsePermissionPrompt()
                }
            }

            fun openAppLocationSettings() {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
                startActivity(intent)
            }

            fun openDeviceLocationSettings() {
                runCatching {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }.onFailure {
                    openAppLocationSettings()
                }
            }

            // Al iniciar la app, activar GPS real de inmediato o solicitar el permiso automáticamente
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (uiState.hasLocationPermission) {
                    viewModel.refreshRealGpsFix()
                } else {
                    requestLocationWithAndroidGuideFlow()
                }
            }

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshRealGpsFix()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            // Mantener pantalla activa durante conducción o modo pantalla atenuada
            val keepAwake = uiState.navigationStatus == NavigationStatus.NAVIGATING || uiState.screenOffMode
            DisposableEffect(keepAwake) {
                if (keepAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            ArgonTruckTheme(
                nightMode = preferences.nightMode,
                darkTheme = preferences.darkCockpit
            ) {
                ArgonRootShell(
                    hideChromeForScreenOff = uiState.screenOffMode,
                    content = { navRoute, navigateTo ->
                        when (navRoute) {
                            ROUTE_CABINA -> CabinaScreen(
                                uiState = uiState,
                                profile = profile,
                                location = location,
                                preferences = preferences,
                                lastVoiceAnnouncement = lastVoice,
                                restrictions = restrictions,
                                pois = pois,
                                onRequestGpsPermission = { requestLocationWithAndroidGuideFlow() },
                                onConfirmPermissionRationale = {
                                    viewModel.requestLocationRationale(false)
                                    launchFineAndCoarsePermissionPrompt()
                                },
                                onDismissPermissionRationale = { viewModel.requestLocationRationale(false) },
                                onOpenAppSettings = { openAppLocationSettings() },
                                onOpenDeviceLocationSettings = { openDeviceLocationSettings() },
                                onRefreshRealGps = viewModel::refreshRealGpsFix,
                                onDismissProximityWarning = viewModel::dismissProximityWarning,
                                onRerouteAroundRestrictedPoi = viewModel::rerouteAroundRestrictedPoi,
                                onStartNavigation = viewModel::startNavigation,
                                onStopNavigation = viewModel::stopNavigation,
                                onRecalculateFromGps = viewModel::recalculateFromCurrentGps,
                                onToggleNightMode = viewModel::toggleNightMode,
                                onToggleVoice = viewModel::toggleVoice,
                                onToggleScreenOffMode = viewModel::setScreenOffMode,
                                onOpenRoutes = { navigateTo(ROUTE_RUTAS) },
                                loadTile = viewModel::loadMapTile
                            )
                            ROUTE_VEHICULO -> VehicleProfileScreen(
                                profiles = profiles,
                                activeProfile = profile,
                                onSelectProfile = viewModel::selectProfile,
                                onSaveProfile = viewModel::saveTruckProfile
                            )
                            ROUTE_RUTAS -> RoutesScreen(
                                uiState = uiState,
                                profile = profile,
                                preferences = preferences,
                                onSetCorridor = { origin, dest ->
                                    viewModel.setCorridor(origin, dest, useGpsOrigin = false)
                                },
                                onSetDestinationFromGps = viewModel::setDestinationFromCurrentGps,
                                onUseCurrentGpsAsOrigin = viewModel::setUseCurrentGpsAsOrigin,
                                onSearchQueryChange = viewModel::updateSearchQuery,
                                onSelectRoute = viewModel::selectRouteOption,
                                onStartNavigationAndGoToCab = {
                                    viewModel.startNavigation()
                                    navigateTo(ROUTE_CABINA)
                                },
                                onToggleOfflineFirst = viewModel::toggleOfflineFirstMode
                            )
                            ROUTE_MEXICO -> MexicoOfflineScreen(
                                uiState = uiState,
                                location = location,
                                regions = regions,
                                pois = pois,
                                restrictions = restrictions,
                                recentCrossings = recentCrossings,
                                onDownloadState = viewModel::downloadOfflineState,
                                onDeleteState = viewModel::removeOfflineState,
                                onSetPoiFilter = viewModel::setPoiFilter,
                                onSetRestrictionFilter = viewModel::setRestrictionFilter,
                                onRegisterRestrictionPoint = viewModel::registerLocalRestrictionPoint,
                                onDeleteRestrictionPoint = viewModel::deleteLocalRestrictionPoint,
                                onClearCrossingHistory = viewModel::clearCrossingHistory
                            )
                            ROUTE_ARGON_API -> ArgonDiagnosticsScreen(
                                uiState = uiState,
                                onRefreshSnapshot = viewModel::refreshArgonSnapshotPreview,
                                onRunAcceptanceTests = viewModel::runAcceptanceSuite
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ArgonRootShell(
    hideChromeForScreenOff: Boolean,
    content: @Composable (route: String, navigateTo: (String) -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_CABINA

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail && !hideChromeForScreenOff) {
                    NavigationBar {
                        TopDestinations.forEach { dest ->
                            NavigationBarItem(
                                selected = currentRoute == dest.route,
                                onClick = { navigateTo(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                                label = { Text(stringResource(dest.labelRes), maxLines = 1) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !hideChromeForScreenOff) {
                    NavigationRail {
                        TopDestinations.forEach { dest ->
                            NavigationRailItem(
                                selected = currentRoute == dest.route,
                                onClick = { navigateTo(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_CABINA,
                    modifier = Modifier.weight(1f)
                ) {
                    TopDestinations.forEach { dest ->
                        composable(dest.route) {
                            content(dest.route, ::navigateTo)
                        }
                    }
                }
            }
        }
    }
}

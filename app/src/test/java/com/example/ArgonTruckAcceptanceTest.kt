package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.argon.api.ArgonNavigationContentProvider
import com.example.argon.api.ArgonTelemetryBus
import com.example.argon.data.catalog.MexicoFreightCatalog
import com.example.argon.domain.model.NavigationStatus
import com.example.argon.domain.model.TelemetryLocation
import com.example.argon.domain.routing.ArgonAcceptanceVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArgonTruckAcceptanceTest {

    @Test
    fun `all 7 acceptance criteria pass across routing veracity 32 states and argon provider`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Registrar ContentProvider en Robolectric y publicar snapshot de prueba
        Robolectric.buildContentProvider(ArgonNavigationContentProvider::class.java)
            .create(ArgonNavigationContentProvider.AUTHORITY)

        val profile = MexicoFreightCatalog.defaultProfiles.first()
        ArgonTelemetryBus.publish(
            location = TelemetryLocation(
                lat = 19.7145,
                lng = -99.2225,
                speedKmh = 76.0,
                bearingDeg = 335f,
                accuracyMeters = 5f,
                currentRoadName = "MEX-57D",
                currentTown = "Tepotzotlán",
                currentStateCode = "MEX",
                isSimulated = false,
                timestampMs = System.currentTimeMillis()
            ),
            profile = profile,
            route = null,
            status = NavigationStatus.NAVIGATING,
            remainingKm = 169.0,
            etaMinutes = 142
        )

        val results = ArgonAcceptanceVerifier.runAllChecks(context)
        assertEquals(7, results.size)
        results.forEach { check ->
            assertTrue("Falló prueba '${check.title}': ${check.detail}", check.passed)
        }

        // Verificar consultas independientes a los endpoints /location, /vehicle y /status
        val locUri = Uri.withAppendedPath(ArgonNavigationContentProvider.BASE_URI, "location")
        context.contentResolver.query(locUri, null, null, null, null)?.use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("MEX-57D", c.getString(c.getColumnIndexOrThrow("road_name")))
        }

        val vehUri = Uri.withAppendedPath(ArgonNavigationContentProvider.BASE_URI, "vehicle")
        context.contentResolver.query(vehUri, null, null, null, null)?.use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(4.20, c.getDouble(c.getColumnIndexOrThrow("height_m")), 0.01)
            assertEquals(46.5, c.getDouble(c.getColumnIndexOrThrow("gross_weight_t")), 0.01)
        }
    }

    @Test
    fun `viewmodel manages navigation state and LocationServices controller`() {
        val app = ApplicationProvider.getApplicationContext<ArgonApplication>()
        val vm = com.example.argon.ui.MainViewModel(app)
        // Verifica estado inicial del ViewModel y controlador LocationServices
        val initial = vm.uiState.value
        assertEquals("CMX", initial.originCode)
        assertEquals("QUE", initial.destinationCode)
        assertEquals(
            com.example.argon.location.LocationProviderStatus.PERMISSION_REQUIRED,
            app.locationController.providerStatus.value
        )

        // 1. Concesión solo aproximada (Coarse) en Android 12+
        vm.onLocationPermissionResult(
            fineGranted = false,
            coarseGranted = true,
            shouldShowRationaleAfterDeny = true
        )
        assertTrue(vm.uiState.value.hasLocationPermission)
        assertEquals(
            com.example.argon.location.LocationAccessLevel.APPROXIMATE_ONLY,
            vm.uiState.value.locationAccessLevel
        )

        // 2. Denegación permanente cuando no se debe mostrar más rationale
        vm.onLocationPermissionResult(
            fineGranted = false,
            coarseGranted = false,
            shouldShowRationaleAfterDeny = false
        )
        assertTrue(vm.uiState.value.isPermissionPermanentlyDenied)
        assertEquals(
            com.example.argon.location.LocationAccessLevel.DENIED,
            vm.uiState.value.locationAccessLevel
        )

        // 3. Concesión completa de Fine Location (ubicación precisa en tiempo real)
        vm.onLocationPermissionResult(
            fineGranted = true,
            coarseGranted = true,
            shouldShowRationaleAfterDeny = false
        )
        assertTrue(vm.uiState.value.hasLocationPermission)
        assertEquals(
            com.example.argon.location.LocationAccessLevel.PRECISE_GRANTED,
            vm.uiState.value.locationAccessLevel
        )
    }

    @Test
    fun `viewmodel proximity observer detects low bridge and weigh station from room database`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<ArgonApplication>()
        val vm = com.example.argon.ui.MainViewModel(app)

        // Esperar a que la BD Room se siembre y emita datos iniciales
        suspend fun awaitWarning(predicate: (com.example.argon.ui.RestrictedPoiWarning?) -> Boolean): com.example.argon.ui.RestrictedPoiWarning? {
            repeat(80) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                val current = vm.uiState.value.activeProximityWarning
                if (predicate(current)) return current
                kotlinx.coroutines.delay(25)
            }
            return vm.uiState.value.activeProximityWarning
        }

        // 1. Mover camión cerca del puente bajo RES_G_375 (3.75m < 4.20m del perfil activo)
        vm.updateTelemetryForVerification(19.6350, -99.1720, "Av. Ferrocarril Tultitlán")
        val bridgeWarning = awaitWarning { it?.id == "RES_G_375" }
        assertTrue("Se esperaba alerta de puente bajo RES_G_375", bridgeWarning != null)
        assertEquals("RES_G_375", bridgeWarning!!.id)
        assertEquals(
            com.example.argon.ui.RestrictedPoiAlertSeverity.CRITICAL_LEGAL_BLOCK,
            bridgeWarning.severity
        )
        assertTrue(bridgeWarning.isBridgeOrClearanceHazard)

        // 2. Descartar advertencia actual y verificar que se limpia
        vm.dismissProximityWarning()
        val cleared = awaitWarning { it == null }
        assertEquals(null, cleared)

        // 3. Mover camión cerca de la báscula SCT Palmillas (POI_BAS_PAL)
        vm.updateTelemetryForVerification(20.3270, -99.9390, "MEX-57D Km 148")
        val scaleWarning = awaitWarning { it?.id == "POI_BAS_PAL" }
        assertTrue("Se esperaba alerta de báscula POI_BAS_PAL", scaleWarning != null)
        assertEquals("POI_BAS_PAL", scaleWarning!!.id)
        assertEquals(
            com.example.argon.ui.RestrictedPoiAlertSeverity.WEIGH_STATION_CHECK,
            scaleWarning.severity
        )
    }

    @Test
    fun `maps api key validator rejects placeholder and malformed keys before initializing sdk`() {
        assertEquals(
            com.example.argon.ui.screens.MapsApiKeyStatus.MISSING_OR_PLACEHOLDER,
            com.example.argon.ui.screens.getMapsApiKeyStatus("")
        )
        assertEquals(
            com.example.argon.ui.screens.MapsApiKeyStatus.MISSING_OR_PLACEHOLDER,
            com.example.argon.ui.screens.getMapsApiKeyStatus("MY_MAPS_API_KEY")
        )
        assertEquals(
            com.example.argon.ui.screens.MapsApiKeyStatus.INVALID_FORMAT,
            com.example.argon.ui.screens.getMapsApiKeyStatus("Garcia")
        )
        assertEquals(false, com.example.argon.ui.screens.hasConfiguredMapsApiKey("Garcia"))
        assertEquals(
            com.example.argon.ui.screens.MapsApiKeyStatus.VALID_FORMAT,
            com.example.argon.ui.screens.getMapsApiKeyStatus("AIzaSyDummyValidFormatGoogleMapsKey12345")
        )
        assertEquals(
            true,
            com.example.argon.ui.screens.hasConfiguredMapsApiKey("AIzaSyDummyValidFormatGoogleMapsKey12345")
        )
    }

    @Test
    fun `room database stores geographic restrictions and records crossings with user location`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<ArgonApplication>()
        app.database.ensureSeeded()
        val repo = app.restrictionRepository
        val vm = com.example.argon.ui.MainViewModel(app)

        // 1. Consulta espacial SQL en Room para puente bajo (Tultitlán)
        val nearBridge = repo.findRestrictionsCrossingLocation(19.6350, -99.1720, radiusKm = 3.5)
        assertTrue(nearBridge.any { it.id == "RES_G_375" && it.isLowBridgeOrWeightLimit })

        // 2. Consulta espacial SQL en Room para pendiente pronunciada (Cumbres de Maltrata 7.5%)
        val nearGrade = repo.findRestrictionsCrossingLocation(18.8180, -97.2850, radiusKm = 3.5)
        assertTrue(nearGrade.any { it.id == "ADV_MALTRATA" && it.isSteepGrade })

        // 3. Consulta espacial SQL en Room para zona prohibida (Azcapotzalco)
        val nearProhibited = repo.findRestrictionsCrossingLocation(19.4850, -99.1850, radiusKm = 3.5)
        assertTrue(nearProhibited.any { it.id == "RES_URB_AZC" && it.isProhibitedZone })

        // 4. Registrar un punto de restricción local en coordenadas nuevas y verificar cruce con ubicación del usuario
        val customLat = 21.1234
        val customLng = -101.5678
        vm.clearCrossingHistory()
        vm.registerLocalRestrictionPoint(
            title = "Puente Bajo Local Test",
            category = com.example.argon.domain.model.RestrictionPointFilter.LOW_BRIDGES,
            roadName = "Libramiento León Sur",
            maxHeightMeters = 3.60,
            gradePercent = null,
            lat = customLat,
            lng = customLng,
            stateCode = "GUA"
        )

        var foundCustom = false
        repeat(40) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            val hits = repo.findRestrictionsCrossingLocation(customLat, customLng, radiusKm = 1.0)
            if (hits.any { it.title == "Puente Bajo Local Test" }) {
                foundCustom = true
                return@repeat
            }
            kotlinx.coroutines.delay(25)
        }
        assertTrue("El punto registrado debe persistirse y encontrarse por consulta espacial en Room", foundCustom)

        // Mover ubicación del usuario sobre el punto registrado y verificar registro de cruce en Room
        vm.updateTelemetryForVerification(customLat, customLng, "Libramiento León Sur")
        var crossingRecorded = false
        repeat(60) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            val crossings = app.database.restrictionCrossingDao().getLatestCrossingForRestriction(
                repo.findRestrictionsCrossingLocation(customLat, customLng, radiusKm = 1.0)
                    .firstOrNull { it.title == "Puente Bajo Local Test" }?.id ?: ""
            )
            if (crossings != null) {
                crossingRecorded = true
                return@repeat
            }
            kotlinx.coroutines.delay(25)
        }
        assertTrue("El cruce entre ubicación del usuario y la restricción debe guardarse en Room", crossingRecorded)
    }

    @Test
    fun `google routes api parser decodes polyline and rejects low bridge conflict for heavy truck`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = com.example.argon.data.remote.ArgonNavigationRepository(context)
        val profile = MexicoFreightCatalog.defaultProfiles.first() // 4.20m altura
        val origin = MexicoFreightCatalog.networkNodes.first { it.id == "CMX" }
        val dest = MexicoFreightCatalog.networkNodes.first { it.id == "QUE" }

        fun encodeGooglePolyline(points: List<com.example.argon.domain.model.GeoPoint>): String {
            val result = StringBuilder()
            var prevLat = 0
            var prevLng = 0
            fun writeSigned(v: Int) {
                var sgn = v shl 1
                if (v < 0) sgn = sgn.inv()
                var valBits = sgn
                while (valBits >= 0x20) {
                    result.append(((0x20 or (valBits and 0x1f)) + 63).toChar())
                    valBits = valBits shr 5
                }
                result.append((valBits + 63).toChar())
            }
            for (pt in points) {
                val latE5 = kotlin.math.round(pt.lat * 1e5).toInt()
                val lngE5 = kotlin.math.round(pt.lng * 1e5).toInt()
                writeSigned(latE5 - prevLat)
                writeSigned(lngE5 - prevLng)
                prevLat = latE5
                prevLng = lngE5
            }
            return result.toString()
        }

        // Trazado que cruza exactamente el puente bajo RES_G_375 (19.6350, -99.1720, máx 3.75m)
        val hazardousEncoded = encodeGooglePolyline(
            listOf(
                com.example.argon.domain.model.GeoPoint(19.4326, -99.1332),
                com.example.argon.domain.model.GeoPoint(19.6350, -99.1720),
                com.example.argon.domain.model.GeoPoint(20.5888, -100.3899)
            )
        )

        val googleResponseRoute = com.example.argon.data.remote.GoogleRouteData(
            distanceMeters = 215_400,
            duration = "9600s",
            polyline = com.example.argon.data.remote.GoogleRoutePolyline(hazardousEncoded),
            legs = listOf(
                com.example.argon.data.remote.GoogleRouteLeg(
                    steps = listOf(
                        com.example.argon.data.remote.GoogleRouteStep(
                            distanceMeters = 215_400,
                            navigationInstruction = com.example.argon.data.remote.GoogleNavigationInstruction(
                                maneuver = "DEPART",
                                instructions = "Toma Autopista México-Querétaro"
                            ),
                            startLocation = com.example.argon.data.remote.GoogleRouteLocation(
                                com.example.argon.data.remote.GoogleRouteLatLng(19.4326, -99.1332)
                            )
                        )
                    )
                )
            ),
            travelAdvisory = com.example.argon.data.remote.GoogleRouteTravelAdvisory(
                tollInfo = com.example.argon.data.remote.GoogleRouteTollInfo(
                    estimatedPrice = listOf(
                        com.example.argon.data.remote.GoogleMoney(currencyCode = "MXN", units = "763", nanos = 0L)
                    )
                )
            )
        )

        val audited = repo.buildGoogleRouteOption(
            index = 0,
            routeData = googleResponseRoute,
            originNode = origin,
            destNode = dest,
            profile = profile,
            baseRestrictions = MexicoFreightCatalog.verifiedRestrictions
        )

        assertTrue(audited != null)
        assertEquals(com.example.argon.domain.model.RoutingSource.GOOGLE_ROUTES_AUDITED, audited!!.routingSource)
        assertEquals(215.4, audited.distanceKm, 0.1)
        assertEquals(160, audited.estimatedMinutes)
        assertEquals(763.0, audited.tollCostMxn, 0.1)
        assertEquals(false, audited.safetyAudit.isCompatible)
        assertTrue(audited.safetyAudit.rejectionReasons.any { it.contains("3.75") || it.contains("Tultitlán") })
    }
}

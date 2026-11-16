package com.example.argon.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Valhalla Truck Routing DTOs ---

@JsonClass(generateAdapter = true)
data class ValhallaLocation(
    val lat: Double,
    val lon: Double,
    val type: String = "break"
)

@JsonClass(generateAdapter = true)
data class ValhallaTruckOptions(
    val height: Double,
    val width: Double,
    val length: Double,
    val weight: Double,
    @Json(name = "axle_load") val axleLoad: Double?,
    @Json(name = "axle_count") val axleCount: Int,
    val hazmat: Boolean,
    @Json(name = "use_tolls") val useTolls: Double = 0.85,
    @Json(name = "use_highways") val useHighways: Double = 1.0
)

@JsonClass(generateAdapter = true)
data class ValhallaCostingOptions(
    val truck: ValhallaTruckOptions
)

@JsonClass(generateAdapter = true)
data class ValhallaTruckRequest(
    val locations: List<ValhallaLocation>,
    val costing: String = "truck",
    @Json(name = "costing_options") val costingOptions: ValhallaCostingOptions,
    val alternates: Int = 1,
    val language: String = "es-ES",
    val units: String = "kilometers"
)

@JsonClass(generateAdapter = true)
data class ValhallaManeuver(
    val instruction: String,
    val length: Double,
    val time: Double,
    val type: Int = 0,
    @Json(name = "street_names") val streetNames: List<String>? = null,
    @Json(name = "begin_shape_index") val beginShapeIndex: Int = 0,
    @Json(name = "toll") val toll: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class ValhallaLeg(
    val shape: String,
    val maneuvers: List<ValhallaManeuver>,
    val summary: ValhallaSummary
)

@JsonClass(generateAdapter = true)
data class ValhallaSummary(
    val length: Double,
    val time: Double,
    @Json(name = "has_toll") val hasToll: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class ValhallaTrip(
    val legs: List<ValhallaLeg>,
    val summary: ValhallaSummary
)

@JsonClass(generateAdapter = true)
data class ValhallaRouteResponse(
    val trip: ValhallaTrip? = null,
    val alternates: List<ValhallaAlternate>? = null
)

@JsonClass(generateAdapter = true)
data class ValhallaAlternate(
    val trip: ValhallaTrip
)

// --- Overpass Restriction & POI Verification DTOs ---

@JsonClass(generateAdapter = true)
data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OverpassElement(
    val type: String,
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val tags: Map<String, String>? = null
)

// --- Nominatim Mexico Geocoding DTOs ---

@JsonClass(generateAdapter = true)
data class NominatimResult(
    @Json(name = "display_name") val displayName: String,
    val lat: String,
    val lon: String,
    val type: String? = null
)

// --- Google Routes API v2 DTOs ---

@JsonClass(generateAdapter = true)
data class GoogleRouteLatLng(
    val latitude: Double,
    val longitude: Double
)

@JsonClass(generateAdapter = true)
data class GoogleRouteLocation(
    val latLng: GoogleRouteLatLng
)

@JsonClass(generateAdapter = true)
data class GoogleRouteWaypoint(
    val location: GoogleRouteLocation
)

@JsonClass(generateAdapter = true)
data class GoogleVehicleInfo(
    val emissionType: String = "DIESEL"
)

@JsonClass(generateAdapter = true)
data class GoogleRouteModifiers(
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val vehicleInfo: GoogleVehicleInfo = GoogleVehicleInfo()
)

@JsonClass(generateAdapter = true)
data class GoogleRoutesRequest(
    val origin: GoogleRouteWaypoint,
    val destination: GoogleRouteWaypoint,
    val travelMode: String = "DRIVE",
    val routingPreference: String = "TRAFFIC_AWARE",
    val computeAlternativeRoutes: Boolean = true,
    val routeModifiers: GoogleRouteModifiers = GoogleRouteModifiers(),
    val languageCode: String = "es-419",
    val units: String = "METRIC",
    val polylineEncoding: String = "ENCODED_POLYLINE"
)

@JsonClass(generateAdapter = true)
data class GoogleRoutePolyline(
    val encodedPolyline: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleNavigationInstruction(
    val maneuver: String? = null,
    val instructions: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleRouteStep(
    val distanceMeters: Int? = null,
    val staticDuration: String? = null,
    val navigationInstruction: GoogleNavigationInstruction? = null,
    val startLocation: GoogleRouteLocation? = null
)

@JsonClass(generateAdapter = true)
data class GoogleRouteLeg(
    val distanceMeters: Int? = null,
    val duration: String? = null,
    val steps: List<GoogleRouteStep> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GoogleMoney(
    val currencyCode: String? = null,
    val units: String? = null,
    val nanos: Long? = null
)

@JsonClass(generateAdapter = true)
data class GoogleRouteTollInfo(
    val estimatedPrice: List<GoogleMoney> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GoogleRouteTravelAdvisory(
    val tollInfo: GoogleRouteTollInfo? = null
)

@JsonClass(generateAdapter = true)
data class GoogleRouteData(
    val distanceMeters: Int? = null,
    val duration: String? = null,
    val polyline: GoogleRoutePolyline? = null,
    val legs: List<GoogleRouteLeg> = emptyList(),
    val travelAdvisory: GoogleRouteTravelAdvisory? = null,
    val routeLabels: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GoogleRoutesResponse(
    val routes: List<GoogleRouteData> = emptyList()
)

interface GoogleRoutesApi {
    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Header("X-Goog-Api-Key") apiKey: String,
        @Header("X-Goog-FieldMask") fieldMask: String = DEFAULT_FIELD_MASK,
        @Body request: GoogleRoutesRequest
    ): GoogleRoutesResponse

    companion object {
        const val DEFAULT_FIELD_MASK =
            "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline," +
                "routes.legs.steps.navigationInstruction,routes.legs.steps.distanceMeters," +
                "routes.legs.steps.startLocation,routes.travelAdvisory.tollInfo,routes.routeLabels"
    }
}

interface ValhallaTruckApi {
    @POST("route")
    suspend fun calculateTruckRoute(@Body request: ValhallaTruckRequest): ValhallaRouteResponse
}

interface OverpassApi {
    @POST("api/interpreter")
    suspend fun queryRestrictions(@Body queryBody: RequestBody): OverpassResponse
}

interface NominatimApi {
    @GET("search")
    suspend fun searchMexico(
        @Query("q") query: String,
        @Query("countrycodes") countryCodes: String = "mx",
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 8,
        @Header("User-Agent") userAgent: String = "ArgonGpsTruck/1.0 (Android Heavy Freight)"
    ): List<NominatimResult>
}

object CartographicApiClient {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    val googleRoutesApi: GoogleRoutesApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://routes.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoogleRoutesApi::class.java)
    }

    val valhallaApi: ValhallaTruckApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://valhalla1.openstreetmap.de/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ValhallaTruckApi::class.java)
    }

    val overpassApi: OverpassApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://overpass-api.de/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OverpassApi::class.java)
    }

    val nominatimApi: NominatimApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NominatimApi::class.java)
    }
}

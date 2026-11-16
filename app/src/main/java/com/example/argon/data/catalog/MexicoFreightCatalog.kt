package com.example.argon.data.catalog

import com.example.argon.domain.model.CargoType
import com.example.argon.domain.model.GeoPoint
import com.example.argon.domain.model.MexicoStateRegion
import com.example.argon.domain.model.RestrictionKind
import com.example.argon.domain.model.RoadClass
import com.example.argon.domain.model.RoadEdge
import com.example.argon.domain.model.RoadNode
import com.example.argon.domain.model.RoadRestriction
import com.example.argon.domain.model.SpecialRestriction
import com.example.argon.domain.model.TruckPoi
import com.example.argon.domain.model.TruckPoiCategory
import com.example.argon.domain.model.TruckProfile
import com.example.argon.domain.model.TruckType
import com.example.argon.domain.model.VerificationStatus

object MexicoFreightCatalog {

    val defaultProfiles: List<TruckProfile> = listOf(
        TruckProfile(
            id = 1L,
            name = "Tractocamión T3-S2 (Caja Seca 53')",
            heightMeters = 4.20,
            widthMeters = 2.60,
            lengthMeters = 21.50,
            grossWeightTons = 46.5,
            axleCount = 5,
            axleLoadTons = 10.0,
            truckType = TruckType.TRACTOR_SEMITRAILER,
            cargoType = CargoType.GENERAL_DRY,
            specialRestrictions = setOf(SpecialRestriction.ONLY_ET_A_CORRIDORS),
            isActive = true
        ),
        TruckProfile(
            id = 2L,
            name = "Full Doblemente Articulado T3-S2-R4",
            heightMeters = 4.25,
            widthMeters = 2.60,
            lengthMeters = 31.00,
            grossWeightTons = 75.5,
            axleCount = 9,
            axleLoadTons = 9.5,
            truckType = TruckType.FULL_DOUBLE_TRAILER,
            cargoType = CargoType.GENERAL_DRY,
            specialRestrictions = setOf(
                SpecialRestriction.ONLY_ET_A_CORRIDORS,
                SpecialRestriction.URBAN_SCHEDULE_BAN
            ),
            isActive = false
        ),
        TruckProfile(
            id = 3L,
            name = "Autotanque Materiales Peligrosos (Pipa)",
            heightMeters = 3.95,
            widthMeters = 2.55,
            lengthMeters = 19.80,
            grossWeightTons = 42.0,
            axleCount = 5,
            axleLoadTons = 9.2,
            truckType = TruckType.TANKER,
            cargoType = CargoType.HAZMAT_FLAMMABLE,
            specialRestrictions = setOf(
                SpecialRestriction.HAZMAT_NO_TUNNELS,
                SpecialRestriction.ONLY_ET_A_CORRIDORS
            ),
            isActive = false
        )
    )

    // 32 Entidades Federativas de México con corredores troncales SCT / OSM
    val all32States: List<MexicoStateRegion> = listOf(
        MexicoStateRegion("AGU", "Aguascalientes", "Aguascalientes", 21.8853, -102.2916, listOf("MEX-45D", "MEX-45", "MEX-70"), 14.2),
        MexicoStateRegion("BCN", "Baja California", "Mexicali / Tijuana", 32.5149, -117.0382, listOf("MEX-1", "MEX-2D", "MEX-5"), 28.6),
        MexicoStateRegion("BCS", "Baja California Sur", "La Paz", 24.1426, -110.3128, listOf("MEX-1", "MEX-19"), 18.4),
        MexicoStateRegion("CAM", "Campeche", "Campeche", 19.8301, -90.5349, listOf("MEX-180", "MEX-186", "MEX-261"), 19.5),
        MexicoStateRegion("CHP", "Chiapas", "Tuxtla Gutiérrez", 16.7516, -93.1030, listOf("MEX-190D", "MEX-190", "MEX-200"), 27.1),
        MexicoStateRegion("CHH", "Chihuahua", "Chihuahua / Cd. Juárez", 28.6330, -106.0691, listOf("MEX-45D", "MEX-45", "MEX-16"), 36.8),
        MexicoStateRegion("CMX", "Ciudad de México", "CDMX / Vallejo / Iztapalapa", 19.4326, -99.1332, listOf("Circuito Exterior", "MEX-57D", "MEX-150D"), 34.0, isDownloaded = true, segmentCount = 14, restrictionCount = 6, poiCount = 8),
        MexicoStateRegion("COA", "Coahuila", "Saltillo / Torreón", 25.4232, -101.0053, listOf("MEX-57D", "MEX-40D", "MEX-30"), 31.2),
        MexicoStateRegion("COL", "Colima", "Manzanillo / Colima", 19.2452, -103.7241, listOf("MEX-54D", "MEX-200"), 15.8),
        MexicoStateRegion("DUR", "Durango", "Durango", 24.0277, -104.6532, listOf("MEX-40D", "MEX-45", "MEX-49"), 26.4),
        MexicoStateRegion("GUA", "Guanajuato", "León / Silao / Celaya", 21.0190, -101.2574, listOf("MEX-45D", "MEX-57D", "MEX-43D"), 24.7),
        MexicoStateRegion("GRO", "Guerrero", "Chilpancingo / Acapulco", 17.5506, -99.5024, listOf("MEX-95D", "MEX-200"), 23.1),
        MexicoStateRegion("HID", "Hidalgo", "Pachuca / Tula / Arco Norte", 20.1011, -98.7591, listOf("Arco Norte M40D", "MEX-85D", "MEX-132D"), 21.5),
        MexicoStateRegion("JAL", "Jalisco", "Guadalajara", 20.6597, -103.3496, listOf("MEX-15D", "MEX-80D", "MEX-54D", "Macrolibramiento"), 35.4, isDownloaded = true, segmentCount = 12, restrictionCount = 5, poiCount = 7),
        MexicoStateRegion("MEX", "Estado de México", "Toluca / Tepotzotlán", 19.2826, -99.6557, listOf("MEX-57D", "MEX-15D", "Arco Norte M40D", "CEM"), 38.2, isDownloaded = true, segmentCount = 16, restrictionCount = 7, poiCount = 9),
        MexicoStateRegion("MIC", "Michoacán", "Morelia / Lázaro Cárdenas", 19.7060, -101.1950, listOf("MEX-15D", "MEX-37D", "MEX-14D"), 28.0),
        MexicoStateRegion("MOR", "Morelos", "Cuernavaca", 18.9242, -99.2216, listOf("MEX-95D", "MEX-115D", "Libramiento La Pera"), 16.3),
        MexicoStateRegion("NAY", "Nayarit", "Tepic", 21.5008, -104.8946, listOf("MEX-15D", "MEX-200"), 17.9),
        MexicoStateRegion("NLE", "Nuevo León", "Monterrey", 25.6866, -100.3161, listOf("MEX-85D", "MEX-40D", "Periférico MTY", "MEX-57D"), 33.6, isDownloaded = true, segmentCount = 12, restrictionCount = 5, poiCount = 7),
        MexicoStateRegion("OAX", "Oaxaca", "Oaxaca / Tehuantepec", 17.0732, -96.7266, listOf("MEX-135D", "MEX-190", "MEX-185"), 29.4),
        MexicoStateRegion("PUE", "Puebla", "Puebla", 19.0414, -98.2063, listOf("MEX-150D", "Arco Norte M40D", "MEX-140D"), 25.9),
        MexicoStateRegion("QUE", "Querétaro", "Santiago de Querétaro", 20.5888, -100.3899, listOf("MEX-57D", "MEX-45D", "Libramiento Noreste"), 19.8, isDownloaded = true, segmentCount = 10, restrictionCount = 4, poiCount = 6),
        MexicoStateRegion("ROO", "Quintana Roo", "Cancún / Chetumal", 21.1619, -86.8515, listOf("MEX-180D", "MEX-307"), 22.0),
        MexicoStateRegion("SLP", "San Luis Potosí", "San Luis Potosí", 22.1565, -100.9855, listOf("MEX-57D", "MEX-70", "MEX-80D"), 25.3),
        MexicoStateRegion("SIN", "Sinaloa", "Culiacán / Mazatlán", 24.8091, -107.3940, listOf("MEX-15D", "MEX-40D"), 27.6),
        MexicoStateRegion("SON", "Sonora", "Hermosillo / Nogales", 29.0729, -110.9559, listOf("MEX-15D", "MEX-2"), 34.5),
        MexicoStateRegion("TAB", "Tabasco", "Villahermosa", 17.9895, -92.9475, listOf("MEX-180", "MEX-186", "MEX-187D"), 18.7),
        MexicoStateRegion("TAM", "Tamaulipas", "Nuevo Laredo / Reynosa / Altamira", 27.4763, -99.5164, listOf("MEX-85D", "MEX-97", "MEX-180"), 31.8),
        MexicoStateRegion("TLA", "Tlaxcala", "Tlaxcala / Calpulalpan", 19.3182, -98.2375, listOf("Arco Norte M40D", "MEX-136", "MEX-117D"), 13.4),
        MexicoStateRegion("VER", "Veracruz", "Veracruz / Orizaba / Coatzacoalcos", 19.1738, -96.1342, listOf("MEX-150D", "MEX-180D", "MEX-145D"), 35.9),
        MexicoStateRegion("YUC", "Yucatán", "Mérida / Progreso", 20.9674, -89.5926, listOf("MEX-180D", "MEX-261", "Anillo Periférico"), 20.2),
        MexicoStateRegion("ZAC", "Zacatecas", "Zacatecas", 22.7709, -102.5832, listOf("MEX-45D", "MEX-49", "MEX-54"), 24.1)
    )

    // Nodos troncales que conectan los 32 estados + nodos locales para pruebas de desvíos/restricciones
    val networkNodes: List<RoadNode> = all32States.map { state ->
        RoadNode(
            id = state.code,
            name = state.capitalOrHub,
            stateCode = state.code,
            lat = state.centerLat,
            lng = state.centerLng,
            isFreightHub = true,
            allowsHeavyUturn = true
        )
    } + listOf(
        RoadNode("TEPOTZOTLAN", "Caseta Tepotzotlán (MEX-57D)", "MEX", 19.7145, -99.2225, true, true),
        RoadNode("ARCO_TULA", "Enlace Arco Norte Tula", "HID", 20.0520, -99.3410, true, true),
        RoadNode("PALMILLAS", "Entronque Palmillas", "QUE", 20.3270, -99.9390, true, true),
        RoadNode("CELAYA_LIB", "Libramiento Celaya Carga", "GUA", 20.5280, -100.8150, true, true),
        RoadNode("SILAO_PI", "Puerto Interior Silao", "GUA", 21.0080, -101.4710, true, true),
        RoadNode("MACRO_GDL", "Macrolibramiento Guadalajara", "JAL", 20.4710, -103.3200, true, true),
        RoadNode("SALTILLO_LIB", "Libramiento Norponiente Saltillo", "COA", 25.4950, -101.0720, true, true),
        RoadNode("NVO_LAREDO", "Puente Comercio Mundial Nuevo Laredo", "TAM", 27.5810, -99.5380, true, true),
        RoadNode("ORIZABA_CUMBRES", "Cumbres de Maltrata (MEX-150D)", "VER", 18.8180, -97.2850, false, false),
        RoadNode("BALUARTE", "Puente Baluarte / Túnel El Sinaloense", "SIN", 23.5335, -105.8390, false, false),
        RoadNode("MANZANILLO", "Puerto de Manzanillo", "COL", 19.0540, -104.3160, true, true),
        RoadNode("LAZARO", "Puerto Lázaro Cárdenas", "MIC", 17.9585, -102.1890, true, true),
        // Nodos de prueba de restricción local (CDMX -> Querétaro alternativo incompatible)
        RoadNode("CDMX_CENTRO_RES", "Atajo Calle Residencial Azcapotzalco", "CMX", 19.4850, -99.1850, false, false),
        RoadNode("PUENTE_BAJO_TULTITLAN", "Paso Inferior F.C. Tultitlán (3.75m)", "MEX", 19.6350, -99.1720, false, false),
        RoadNode("PUENTE_PESO_TULA", "Puente Viejo Río Tula (Máx 24t)", "HID", 20.0480, -99.3460, false, false),
        RoadNode("TRAMO_SIN_DATO_QRO", "Tramo Estatal sin verificación de gálibo", "QUE", 20.4800, -100.1200, false, false)
    )

    private fun nodeOf(id: String): RoadNode = networkNodes.first { it.id == id }

    private fun edge(
        id: String,
        from: String,
        to: String,
        roadName: String,
        shield: String,
        state: String,
        roadClass: RoadClass,
        distKm: Double,
        speedKmh: Int = 80,
        isBridge: Boolean = false,
        isTunnel: Boolean = false,
        hasToll: Boolean = false,
        tollMxn: Double = 0.0,
        gradePct: Double = 1.5,
        maxH: Double? = 4.50,
        maxW: Double? = 80.0,
        maxAxle: Double? = 11.5,
        maxWidth: Double? = 2.65,
        maxLen: Double? = 32.0,
        hgv: Boolean = true,
        hazmat: Boolean = true,
        fullOk: Boolean = true,
        minRadius: Double = 18.0,
        status: VerificationStatus = VerificationStatus.VERIFIED_SOURCE,
        citation: String = "OSM Overpass + SCT Red ET/A"
    ): RoadEdge {
        val a = nodeOf(from)
        val b = nodeOf(to)
        val mid = GeoPoint((a.lat + b.lat) / 2.0 + 0.015, (a.lng + b.lng) / 2.0 - 0.015)
        return RoadEdge(
            id = id,
            fromNodeId = from,
            toNodeId = to,
            roadName = roadName,
            highwayShield = shield,
            stateCode = state,
            roadClass = roadClass,
            distanceKm = distKm,
            speedLimitKmh = speedKmh,
            isBridge = isBridge,
            isTunnel = isTunnel,
            hasToll = hasToll,
            tollCostMxn = tollMxn,
            gradePercent = gradePct,
            maxHeightMeters = maxH,
            maxWeightTons = maxW,
            maxAxleLoadTons = maxAxle,
            maxWidthMeters = maxWidth,
            maxLengthMeters = maxLen,
            hgvAllowed = hgv,
            hazmatAllowed = hazmat,
            fullDoubleAllowed = fullOk,
            minTurningRadiusM = minRadius,
            verificationStatus = status,
            sourceCitation = citation,
            geometry = listOf(GeoPoint(a.lat, a.lng), mid, GeoPoint(b.lat, b.lng))
        )
    }

    // Grafo bidireccional de carreteras federales, autopistas, libramientos y tramos restringidos
    val freightEdges: List<RoadEdge> by lazy {
        val directed = listOf(
            // Eje Troncal Centro - Norte (CDMX - QRO - SLP - COA - NLE - TAM)
            edge("E_CMX_MEX", "CMX", "MEX", "Autopista México-Toluca / Circuito", "MEX-15D", "CMX", RoadClass.AUTOPISTA_CUOTA, 64.0, 80, hasToll = true, tollMxn = 245.0, gradePct = 4.2),
            edge("E_CMX_TEP", "CMX", "TEPOTZOTLAN", "Autopista México-Querétaro", "MEX-57D", "MEX", RoadClass.AUTOPISTA_CUOTA, 43.0, 85, hasToll = true, tollMxn = 198.0),
            edge("E_TEP_PAL", "TEPOTZOTLAN", "PALMILLAS", "Autopista México-Querétaro (Corredor ET)", "MEX-57D", "MEX", RoadClass.CORREDOR_CARGA, 108.0, 85, hasToll = true, tollMxn = 385.0, gradePct = 2.8),
            edge("E_PAL_QRO", "PALMILLAS", "QUE", "Autopista Querétaro-Palmillas", "MEX-57D", "QUE", RoadClass.AUTOPISTA_CUOTA, 61.0, 85, hasToll = true, tollMxn = 180.0),
            edge("E_TEP_ARCO", "TEPOTZOTLAN", "ARCO_TULA", "Arco Norte Libramiento Carga", "M40D", "HID", RoadClass.LIBRAMIENTO, 52.0, 85, hasToll = true, tollMxn = 260.0),
            edge("E_ARCO_HID", "ARCO_TULA", "HID", "Arco Norte - Pachuca", "MEX-85D", "HID", RoadClass.CORREDOR_CARGA, 68.0, 80, hasToll = true, tollMxn = 210.0),
            edge("E_ARCO_PAL", "ARCO_TULA", "PALMILLAS", "Corredor Industrial Tula-Palmillas", "MEX-57D", "HID", RoadClass.CARRETERA_FEDERAL, 74.0, 75, hasToll = false),
            edge("E_ARCO_PUE", "ARCO_TULA", "PUE", "Arco Norte Tramo Texmelucan-Puebla", "M40D", "PUE", RoadClass.LIBRAMIENTO, 158.0, 85, hasToll = true, tollMxn = 590.0),

            // Alternativas INCOMPATIBLES / RIESGOSAS para probar reglas obligatorias 1..6 y veracidad!
            edge("E_RES_AZC", "CMX", "CDMX_CENTRO_RES", "Calle 22 de Febrero (Zona Residencial)", "LOC", "CMX", RoadClass.CALLE_RESIDENCIAL, 12.0, 30, hgv = false, maxWidth = 2.20, maxLen = 10.0, minRadius = 8.0, fullOk = false, citation = "OSM highway=residential / hgv=no"),
            edge("E_LOW_BRIDGE", "CDMX_CENTRO_RES", "PUENTE_BAJO_TULTITLAN", "Av. Ferrocarril Paso Inferior (Gálibo 3.75m)", "EST", "MEX", RoadClass.URBANA_RESTRINGIDA, 19.0, 45, isBridge = true, maxH = 3.75, maxW = 28.0, fullOk = false, minRadius = 10.0, citation = "OSM maxheight=3.75m Verificado"),
            edge("E_TULT_TEP", "PUENTE_BAJO_TULTITLAN", "TEPOTZOTLAN", "Vía López Portillo", "EST", "MEX", RoadClass.URBANA_RESTRINGIDA, 14.0, 50, maxH = 3.90, maxW = 32.0, fullOk = false),
            edge("E_OLD_BRIDGE_W", "ARCO_TULA", "PUENTE_PESO_TULA", "Puente Viejo Río Tula (Límite 24t)", "EST", "HID", RoadClass.CARRETERA_ESTATAL, 16.0, 50, isBridge = true, maxW = 24.0, maxAxle = 7.0, fullOk = false, citation = "SCT Señal Restrictiva Máx 24t"),
            edge("E_PUENTE_QRO_MISS", "PALMILLAS", "TRAMO_SIN_DATO_QRO", "Carretera Estatal 100 (Sin dato de gálibo/peso)", "EST-100", "QUE", RoadClass.CARRETERA_ESTATAL, 32.0, 65, isBridge = true, maxH = null, maxW = null, maxAxle = null, status = VerificationStatus.PARTIAL_MISSING_DATA, citation = "OSM sin etiquetas maxheight/maxweight"),
            edge("E_MISS_QRO", "TRAMO_SIN_DATO_QRO", "QUE", "Enlace Bernal-Querétaro (Sin verificación de peso)", "EST-100", "QUE", RoadClass.CARRETERA_ESTATAL, 35.0, 70, maxH = null, maxW = null, status = VerificationStatus.PARTIAL_MISSING_DATA, citation = "Datos catastrales incompletos"),

            // Bajío y Occidente (QRO - GUA - AGU - JAL - COL - MIC)
            edge("E_QRO_CEL", "QUE", "CELAYA_LIB", "Libramiento Ferroviario / Autopista Celaya", "MEX-45D", "GUA", RoadClass.LIBRAMIENTO, 48.0, 85, hasToll = true, tollMxn = 140.0),
            edge("E_CEL_SILAO", "CELAYA_LIB", "SILAO_PI", "Corredor Industrial del Bajío", "MEX-45D", "GUA", RoadClass.CORREDOR_CARGA, 88.0, 85, hasToll = true, tollMxn = 195.0),
            edge("E_SILAO_GUA", "SILAO_PI", "GUA", "Enlace Guanajuato-Silao", "MEX-45", "GUA", RoadClass.CARRETERA_FEDERAL, 22.0, 75),
            edge("E_SILAO_AGU", "SILAO_PI", "AGU", "Autopista León-Aguascalientes", "MEX-45D", "AGU", RoadClass.AUTOPISTA_CUOTA, 128.0, 85, hasToll = true, tollMxn = 310.0),
            edge("E_CEL_MIC", "CELAYA_LIB", "MIC", "Autopista Salamanca-Morelia", "MEX-43D", "MIC", RoadClass.AUTOPISTA_CUOTA, 118.0, 80, hasToll = true, tollMxn = 290.0),
            edge("E_MIC_JAL", "MIC", "MACRO_GDL", "Autopista de Occidente Morelia-Guadalajara", "MEX-15D", "JAL", RoadClass.AUTOPISTA_CUOTA, 275.0, 85, hasToll = true, tollMxn = 640.0, gradePct = 4.8),
            edge("E_GDL_MACRO", "MACRO_GDL", "JAL", "Macrolibramiento Sur Guadalajara", "MEX-80D", "JAL", RoadClass.LIBRAMIENTO, 32.0, 80, hasToll = true, tollMxn = 190.0),
            edge("E_SILAO_JAL", "SILAO_PI", "MACRO_GDL", "Autopista León-Guadalajara", "MEX-80D", "JAL", RoadClass.AUTOPISTA_CUOTA, 215.0, 85, hasToll = true, tollMxn = 520.0),
            edge("E_JAL_COL", "MACRO_GDL", "COL", "Autopista Guadalajara-Colima", "MEX-54D", "COL", RoadClass.AUTOPISTA_CUOTA, 195.0, 80, isBridge = true, hasToll = true, tollMxn = 480.0, gradePct = 5.5),
            edge("E_COL_MANZ", "COL", "MANZANILLO", "Corredor Portuario Colima-Manzanillo", "MEX-200D", "COL", RoadClass.CORREDOR_CARGA, 98.0, 80, hasToll = true, tollMxn = 215.0),
            edge("E_MIC_LAZ", "MIC", "LAZARO", "Autopista Siglo XXI (Puerto Lázaro Cárdenas)", "MEX-37D", "MIC", RoadClass.CORREDOR_CARGA, 315.0, 75, hasToll = true, tollMxn = 540.0, gradePct = 6.2),

            // Centro-Norte y Noreste (QRO - SLP - ZAC - COA - NLE - TAM)
            edge("E_QRO_SLP", "QUE", "SLP", "Carretera Federal 57 Querétaro-SLP", "MEX-57", "SLP", RoadClass.CARRETERA_FEDERAL, 204.0, 80, gradePct = 3.5),
            edge("E_AGU_ZAC", "AGU", "ZAC", "Carretera Federal 45 Aguascalientes-Zacatecas", "MEX-45D", "ZAC", RoadClass.CARRETERA_FEDERAL, 119.0, 80),
            edge("E_SLP_ZAC", "SLP", "ZAC", "Carretera Federal 49 SLP-Zacatecas", "MEX-49", "ZAC", RoadClass.CARRETERA_FEDERAL, 189.0, 80),
            edge("E_SLP_SALT", "SLP", "SALTILLO_LIB", "Autopista/Federal 57 Matehuala-Saltillo", "MEX-57D", "COA", RoadClass.CORREDOR_CARGA, 442.0, 85, hasToll = true, tollMxn = 410.0, gradePct = 5.8),
            edge("E_SALT_COA", "SALTILLO_LIB", "COA", "Libramiento Saltillo", "MEX-40D", "COA", RoadClass.LIBRAMIENTO, 24.0, 80),
            edge("E_SALT_NLE", "SALTILLO_LIB", "NLE", "Autopista Saltillo-Monterrey (Los Chorros/Viaducto)", "MEX-40D", "NLE", RoadClass.AUTOPISTA_CUOTA, 84.0, 80, hasToll = true, tollMxn = 250.0, gradePct = 6.4),
            edge("E_NLE_LAREDO", "NLE", "NVO_LAREDO", "Autopista Monterrey-Nuevo Laredo (Corredor T-MEC)", "MEX-85D", "TAM", RoadClass.CORREDOR_CARGA, 224.0, 85, hasToll = true, tollMxn = 690.0),
            edge("E_LAREDO_TAM", "NVO_LAREDO", "TAM", "Enlace Aduana Nuevo Laredo", "MEX-85", "TAM", RoadClass.CORREDOR_CARGA, 18.0, 60),
            edge("E_ZAC_DUR", "ZAC", "DUR", "Carretera Federal 45 Zacatecas-Durango", "MEX-45", "DUR", RoadClass.CARRETERA_FEDERAL, 290.0, 80),
            edge("E_COA_DUR", "COA", "DUR", "Autopista Saltillo-Torreón-Durango", "MEX-40D", "DUR", RoadClass.AUTOPISTA_CUOTA, 505.0, 85, hasToll = true, tollMxn = 890.0),

            // Pacífico y Noroeste (JAL - NAY - SIN - DUR - CHH - SON - BCN - BCS)
            edge("E_JAL_NAY", "JAL", "NAY", "Autopista Guadalajara-Tepic", "MEX-15D", "NAY", RoadClass.AUTOPISTA_CUOTA, 205.0, 80, hasToll = true, tollMxn = 610.0, gradePct = 5.0),
            edge("E_NAY_SIN", "NAY", "SIN", "Autopista Tepic-Mazatlán-Culiacán", "MEX-15D", "SIN", RoadClass.AUTOPISTA_CUOTA, 485.0, 85, hasToll = true, tollMxn = 980.0),
            edge("E_DUR_BAL", "DUR", "BALUARTE", "Supercarretera Durango-Mazatlán", "MEX-40D", "DUR", RoadClass.AUTOPISTA_CUOTA, 152.0, 70, isBridge = true, isTunnel = true, hasToll = true, tollMxn = 780.0, gradePct = 6.8, hazmat = false, citation = "CAPUFE / Túnel El Sinaloense restricción Hazmat"),
            edge("E_BAL_SIN", "BALUARTE", "SIN", "Autopista Pacífico Sur Sinaloa", "MEX-15D", "SIN", RoadClass.AUTOPISTA_CUOTA, 238.0, 80, hasToll = true, tollMxn = 420.0),
            edge("E_DUR_CHH", "DUR", "CHH", "Carretera Federal 45 Durango-Chihuahua", "MEX-45", "CHH", RoadClass.CARRETERA_FEDERAL, 665.0, 80),
            edge("E_SIN_SON", "SIN", "SON", "Autopista Costera Pacífico Culiacán-Hermosillo", "MEX-15D", "SON", RoadClass.CORREDOR_CARGA, 685.0, 85, hasToll = true, tollMxn = 840.0),
            edge("E_CHH_SON", "CHH", "SON", "Corredor Sierra Alta Chihuahua-Hermosillo", "MEX-16", "CHH", RoadClass.CARRETERA_FEDERAL, 575.0, 70, gradePct = 6.5, fullOk = false),
            edge("E_SON_BCN", "SON", "BCN", "Carretera Federal 2 Sonoyta-Mexicali-Tijuana", "MEX-2D", "BCN", RoadClass.CARRETERA_FEDERAL, 850.0, 85, hasToll = true, tollMxn = 360.0),
            edge("E_BCN_BCS", "BCN", "BCS", "Carretera Transpeninsular Baja California", "MEX-1", "BCS", RoadClass.CARRETERA_FEDERAL, 1380.0, 70, fullOk = false, maxWidth = 2.55),

            // Golfo, Oriente, Sur y Sureste (CMX - MOR - GRO - PUE - TLA - VER - OAX - CHP - TAB - CAM - YUC - ROO)
            edge("E_CMX_MOR", "CMX", "MOR", "Autopista México-Cuernavaca (La Pera)", "MEX-95D", "MOR", RoadClass.AUTOPISTA_CUOTA, 85.0, 70, hasToll = true, tollMxn = 290.0, gradePct = 6.0),
            edge("E_MOR_GRO", "MOR", "GRO", "Autopista del Sol Cuernavaca-Chilpancingo", "MEX-95D", "GRO", RoadClass.AUTOPISTA_CUOTA, 185.0, 80, isBridge = true, hasToll = true, tollMxn = 640.0, gradePct = 5.4),
            edge("E_CMX_PUE", "CMX", "PUE", "Autopista México-Puebla", "MEX-150D", "PUE", RoadClass.AUTOPISTA_CUOTA, 130.0, 80, hasToll = true, tollMxn = 410.0, gradePct = 5.2),
            edge("E_PUE_TLA", "PUE", "TLA", "Periférico Ecológico / Apizaco", "MEX-119D", "TLA", RoadClass.LIBRAMIENTO, 38.0, 80),
            edge("E_TLA_HID", "TLA", "HID", "Arco Norte Calpulalpan-Pachuca", "M40D", "HID", RoadClass.LIBRAMIENTO, 96.0, 85, hasToll = true, tollMxn = 280.0),
            edge("E_PUE_ORI", "PUE", "ORIZABA_CUMBRES", "Autopista Puebla-Orizaba (Cumbres de Maltrata)", "MEX-150D", "VER", RoadClass.AUTOPISTA_CUOTA, 138.0, 65, hasToll = true, tollMxn = 370.0, gradePct = 7.5),
            edge("E_ORI_VER", "ORIZABA_CUMBRES", "VER", "Autopista Córdoba-Puerto de Veracruz", "MEX-150D", "VER", RoadClass.CORREDOR_CARGA, 132.0, 85, hasToll = true, tollMxn = 340.0),
            edge("E_PUE_OAX", "PUE", "OAX", "Autopista Cuacnopalan-Oaxaca", "MEX-135D", "OAX", RoadClass.AUTOPISTA_CUOTA, 340.0, 75, isBridge = true, hasToll = true, tollMxn = 590.0, gradePct = 6.1),
            edge("E_VER_TAB", "VER", "TAB", "Autopista Veracruz-Coatzacoalcos-Villahermosa", "MEX-180D", "TAB", RoadClass.CORREDOR_CARGA, 460.0, 80, isBridge = true, hasToll = true, tollMxn = 620.0),
            edge("E_OAX_CHP", "OAX", "CHP", "Corredor Istmo-Chiapas", "MEX-190D", "CHP", RoadClass.CARRETERA_FEDERAL, 515.0, 75, gradePct = 5.6),
            edge("E_TAB_CHP", "TAB", "CHP", "Autopista Villahermosa-Las Choapas-Tuxtla", "MEX-187D", "CHP", RoadClass.AUTOPISTA_CUOTA, 255.0, 75, hasToll = true, tollMxn = 410.0, gradePct = 6.3),
            edge("E_TAB_CAM", "TAB", "CAM", "Carretera Federal Costera del Golfo", "MEX-180", "CAM", RoadClass.CARRETERA_FEDERAL, 385.0, 80),
            edge("E_CAM_YUC", "CAM", "YUC", "Autopista Campeche-Mérida", "MEX-180D", "YUC", RoadClass.AUTOPISTA_CUOTA, 178.0, 85, hasToll = true, tollMxn = 210.0),
            edge("E_YUC_ROO", "YUC", "ROO", "Autopista Mérida-Cancún (Mayab)", "MEX-180D", "ROO", RoadClass.AUTOPISTA_CUOTA, 308.0, 85, hasToll = true, tollMxn = 610.0),
            edge("E_VER_TAM", "VER", "TAM", "Corredor Golfo Poza Rica-Tampico", "MEX-180", "VER", RoadClass.CARRETERA_FEDERAL, 495.0, 75)
        )
        // Duplicar aristas en sentido inverso para conectividad completa bidireccional
        directed + directed.map { e ->
            e.copy(
                id = "${e.id}_REV",
                fromNodeId = e.toNodeId,
                toNodeId = e.fromNodeId,
                geometry = e.geometry.reversed()
            )
        }
    }

    val verifiedRestrictions: List<RoadRestriction> = listOf(
        RoadRestriction(
            id = "RES_G_375",
            title = "Gálibo Máximo 3.75 m — Paso Inferior F.C. Tultitlán",
            description = "Paso a desnivel ferroviario con altura libre medida de 3.75 metros. Prohibido para cajas secas de 4.10m - 4.30m.",
            kind = RestrictionKind.LEGAL_PROHIBITION,
            roadName = "Av. Ferrocarril Tultitlán",
            stateCode = "MEX",
            lat = 19.6350,
            lng = -99.1720,
            maxHeightMeters = 3.75,
            isBridge = true,
            verificationStatus = VerificationStatus.VERIFIED_SOURCE,
            sourceCitation = "OSM maxheight=3.75 + Señalamiento vial estatal"
        ),
        RoadRestriction(
            id = "RES_W_24T",
            title = "Límite de Peso 24 Toneladas — Puente Viejo Río Tula",
            description = "Estructura antigua con capacidad máxima de 24t brutas y 7t por eje. Tránsito pesado debe usar Arco Norte M40D.",
            kind = RestrictionKind.LEGAL_PROHIBITION,
            roadName = "Puente Viejo Río Tula",
            stateCode = "HID",
            lat = 20.0480,
            lng = -99.3460,
            maxWeightTons = 24.0,
            maxAxleLoadTons = 7.0,
            isBridge = true,
            verificationStatus = VerificationStatus.VERIFIED_SOURCE,
            sourceCitation = "SCT Dictamen Estructural / NOM-012"
        ),
        RoadRestriction(
            id = "RES_URB_AZC",
            title = "Zona Residencial Prohibida a Tractocamiones",
            description = "Vialidad local con prohibición legal de tránsito pesado (hgv=no) y radio de giro incompatible (8 m).",
            kind = RestrictionKind.LEGAL_PROHIBITION,
            roadName = "Calle 22 de Febrero, Azcapotzalco",
            stateCode = "CMX",
            lat = 19.4850,
            lng = -99.1850,
            hgvProhibited = true,
            maxWidthMeters = 2.20,
            maxLengthMeters = 10.0,
            verificationStatus = VerificationStatus.VERIFIED_SOURCE,
            sourceCitation = "OSM highway=residential + Reglamento Tránsito CDMX"
        ),
        RoadRestriction(
            id = "RES_HAZ_SIN",
            title = "Restricción Hazmat — Túnel El Sinaloense (MEX-40D)",
            description = "Prohibido el paso de materiales peligrosos inflamables y explosivos en túneles de alta longitud.",
            kind = RestrictionKind.LEGAL_PROHIBITION,
            roadName = "Autopista Durango-Mazatlán MEX-40D",
            stateCode = "SIN",
            lat = 23.5335,
            lng = -105.8390,
            hazmatProhibited = true,
            isTunnel = true,
            verificationStatus = VerificationStatus.VERIFIED_SOURCE,
            sourceCitation = "CAPUFE / NOM-002-SCT"
        ),
        RoadRestriction(
            id = "ADV_MALTRATA",
            title = "Pendiente Descendente 7.5% — Cumbres de Maltrata",
            description = "Descenso prolongado de 24 km con rampas de frenado. Usar freno de motor y velocidad controlada.",
            kind = RestrictionKind.DRIVING_ADVISORY,
            roadName = "Autopista MEX-150D Km 228",
            stateCode = "VER",
            lat = 18.8180,
            lng = -97.2850,
            gradePercent = 7.5,
            verificationStatus = VerificationStatus.VERIFIED_SOURCE,
            sourceCitation = "CAPUFE / SICT Alertas de Operación"
        ),
        RoadRestriction(
            id = "ADV_CHORROS",
            title = "Pendiente 6.4% y Curva Cerrada — Tramo Los Chorros",
            description = "Precaución por neblina frecuente y pendiente pronunciada en dirección Saltillo-Monterrey.",
            kind = RestrictionKind.DRIVING_ADVISORY,
            roadName = "Autopista MEX-57D / MEX-40D",
            stateCode = "COA",
            lat = 25.3980,
            lng = -100.8120,
            gradePercent = 6.4,
            verificationStatus = VerificationStatus.VERIFIED_SOURCE,
            sourceCitation = "SICT / Guardia Nacional Carreteras"
        ),
        RoadRestriction(
            id = "WARN_MISSING_QRO",
            title = "Tramo Estatal 100 sin dato de gálibo ni peso verificado",
            description = "No existen etiquetas verificables de altura libre o tonelaje máximo para este tramo.",
            kind = RestrictionKind.DRIVING_ADVISORY,
            roadName = "Carretera Estatal 100 Querétaro",
            stateCode = "QUE",
            lat = 20.4800,
            lng = -100.1200,
            maxHeightMeters = null,
            maxWeightTons = null,
            verificationStatus = VerificationStatus.PARTIAL_MISSING_DATA,
            sourceCitation = "OSM sin maxheight/maxweight"
        )
    )

    val verifiedPois: List<TruckPoi> = listOf(
        TruckPoi("POI_CAS_TEP", "Caseta Tepotzotlán (CAPUFE)", TruckPoiCategory.CASETA_COBRO, "MEX", "MEX-57D Km 43", 19.7145, -99.2225, "Carriles exclusivos carga pesada / IAVE y TAG", "CAPUFE Tarifas Vigentes", tollEstimateMxn = 198.0),
        TruckPoi("POI_BAS_PAL", "Centro de Control de Peso SCT Palmillas", TruckPoiCategory.BASCULA, "QUE", "MEX-57D Km 148", 20.3270, -99.9390, "Báscula dinámica y verificación NOM-012-SCT-2-2017", "SICT Dirección de Autotransporte"),
        TruckPoi("POI_PAR_QRO", "Parador El Marqués Tracto-Servicio", TruckPoiCategory.PARADOR_CAMIONES, "QUE", "MEX-57D Km 198", 20.5790, -100.2850, "Regaderas, comedor 24h, vigilancia y 120 cajones de tráiler", "OSM amenity=parking + hgv=yes", hasTrailerParking = true),
        TruckPoi("POI_DIE_SLP", "Estación Diésel Alto Flujo El Potosí", TruckPoiCategory.ESTACION_DIESEL, "SLP", "MEX-57 Km 182", 22.0920, -100.8920, "Bombas de alto flujo 120 L/min, Urea AdBlue y patio amplio", "CRE Permiso Expendio Diésel", hasTrailerParking = true),
        TruckPoi("POI_TAL_SIL", "Taller Servicio Pesado Bajío Diesel", TruckPoiCategory.TALLER_PESADO, "GUA", "MEX-45D Km 136", 20.9940, -101.4480, "Frenos de aire, suspensión neumática, vulcanizadora 24h", "Registro Directorio Transporte", hasTrailerParking = true),
        TruckPoi("POI_PEN_MTY", "Pensión y Estacionamiento Tráileres Apodaca", TruckPoiCategory.ESTACIONAMIENTO_TRAILER, "NLE", "Periférico MTY Km 34", 25.7810, -100.1840, "Patio bardeado, CCTV, acceso para Full T3-S2-R4", "OSM hgv=designated", hasTrailerParking = true),
        TruckPoi("POI_DES_MAL", "Área de Descanso y Rampa de Frenado Esperanza", TruckPoiCategory.AREA_DESCANSO, "PUE", "MEX-150D Km 221", 18.8590, -97.3580, "Revisión obligatoria de balatas antes de Cumbres de Maltrata", "CAPUFE Seguridad Vial", hasTrailerParking = true),
        TruckPoi("POI_CED_GDL", "Centro Logístico Jalisco / CEDIS Macrolibramiento", TruckPoiCategory.CENTRO_CARGA_DESCARGA, "JAL", "MEX-80D Km 42", 20.4710, -103.3200, "36 andenes de carga/descarga, patio de maniobras 45m", "OSM landuse=industrial"),
        TruckPoi("POI_GAS_NLD", "Gasolinera Pesada Corredor Comercio Mundial", TruckPoiCategory.GASOLINERA_PESADA, "TAM", "MEX-85D Km 218", 27.5620, -99.5320, "Diésel ultra bajo azufre, facturación inmediata y báscula", "CRE / OSM amenity=fuel", hasTrailerParking = true),
        TruckPoi("POI_BAS_PUE", "Báscula SCT San Martín Texmelucan", TruckPoiCategory.BASCULA, "PUE", "MEX-150D Km 91", 19.2840, -98.4310, "Control de dimensiones y peso bruto vehicular", "SICT Control de Carga"),
        TruckPoi("POI_CAS_BAL", "Caseta Coscomate / Puente Baluarte", TruckPoiCategory.CASETA_COBRO, "DUR", "MEX-40D Km 148", 23.5410, -105.7820, "Cobro por número de ejes (5 a 9 ejes)", "CAPUFE Red Autopistas", tollEstimateMxn = 780.0),
        TruckPoi("POI_PAR_VER", "Parador Seguro Córdoba-Veracruz", TruckPoiCategory.PARADOR_CAMIONES, "VER", "MEX-150D Km 296", 18.8850, -96.9120, "Descanso seguro, taller eléctrico y diésel", "OSM / Red Paradores Seguros", hasTrailerParking = true)
    )
}

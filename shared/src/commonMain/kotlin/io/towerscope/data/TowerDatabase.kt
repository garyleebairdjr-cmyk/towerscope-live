package io.towerscope.data

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.towerscope.calc.Geo
import io.towerscope.model.CellIdentity
import io.towerscope.model.DatabaseTower
import io.towerscope.model.RadioType
import io.towerscope.model.TowerLocation
import io.towerscope.model.TowerLookup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Client for the OpenCelliD open cell-tower database (https://opencellid.org).
 * A free API key is required; it is stored only on the device.
 */
class TowerDatabase(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun lookup(id: CellIdentity, apiKey: String): TowerLookup {
        if (apiKey.isBlank()) return TowerLookup.NoApiKey
        if (!id.isLookupable) return TowerLookup.NotLookupable
        return try {
            val text = http.get("$BASE/cell/get") {
                parameter("key", apiKey.trim())
                parameter("mcc", id.mcc)
                parameter("mnc", id.mnc)
                parameter("lac", id.area)
                parameter("cellid", id.cellId)
                if (id.radio.openCellIdName.isNotEmpty()) parameter("radio", id.radio.openCellIdName)
                parameter("format", "json")
            }.bodyAsText()
            val obj = json.parseToJsonElement(text).jsonObject
            obj.errorMessage()?.let { msg ->
                return if (msg.contains("not found", ignoreCase = true) || obj.num("code") == 1.0) TowerLookup.NotFound
                else TowerLookup.Failed(msg)
            }
            val lat = obj.num("lat")
            val lon = obj.num("lon")
            if (lat == null || lon == null || (lat == 0.0 && lon == 0.0)) TowerLookup.NotFound
            else TowerLookup.Found(
                TowerLocation(
                    lat, lon,
                    rangeM = obj.num("range"),
                    samples = obj.num("samples")?.toInt(),
                    source = "OpenCelliD",
                    updatedEpochSec = obj.num("updated")?.toLong(),
                ),
            )
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            TowerLookup.Failed(e.message ?: "Network error")
        }
    }

    /** Towers within a ~2 × 2 km square (the API's area limit) around a point. */
    suspend fun nearby(lat: Double, lon: Double, apiKey: String): Result<List<DatabaseTower>> {
        if (apiKey.isBlank()) return Result.failure(IllegalStateException("No API key"))
        return try {
            val box = Geo.boundingBox(lat, lon, 990.0)
            val text = http.get("$BASE/cell/getInArea") {
                parameter("key", apiKey.trim())
                parameter("BBOX", box.joinToString(","))
                parameter("format", "json")
                parameter("limit", 50)
            }.bodyAsText()
            val obj = json.parseToJsonElement(text).jsonObject
            obj.errorMessage()?.let { return Result.failure(IllegalStateException(it)) }
            val cells = (obj["cells"] as? JsonArray).orEmpty().mapNotNull { el ->
                val c = el as? JsonObject ?: return@mapNotNull null
                val la = c.num("lat") ?: return@mapNotNull null
                val lo = c.num("lon") ?: return@mapNotNull null
                val radio = RadioType.fromOpenCellId(c.str("radio"))
                DatabaseTower(
                    identity = CellIdentity(
                        radio = radio,
                        mcc = c.num("mcc")?.toInt(),
                        mnc = c.num("mnc")?.toInt(),
                        area = c.num("lac")?.toLong(),
                        cellId = c.num("cellid")?.toLong(),
                    ),
                    location = TowerLocation(la, lo, c.num("range"), c.num("samples")?.toInt(), "OpenCelliD", c.num("updated")?.toLong()),
                    averageSignalDbm = c.num("averageSignalStrength")?.toInt()?.takeIf { it != 0 },
                )
            }
            Result.success(cells)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(k: String): Double? = str(k)?.toDoubleOrNull()
    private fun JsonObject.errorMessage(): String? = str("error")?.takeIf { it.isNotBlank() }

    private companion object {
        const val BASE = "https://opencellid.org"
    }
}

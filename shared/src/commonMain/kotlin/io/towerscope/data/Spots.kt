package io.towerscope.data

import io.towerscope.platform.KeyValueStore

/** A location the user measured in the spot finder. */
data class SavedSpot(
    val name: String,
    /** Averaged signal strength (RSRP for LTE/NR, RSCP or dBm otherwise). */
    val signalDbm: Double,
    val sinrDb: Double?,
    val radioLabel: String,
    val timestampMs: Long,
)

/** Persists saved spots as simple tab-separated lines (names are sanitised). */
class SpotStore(private val store: KeyValueStore) {
    fun load(): List<SavedSpot> = store.getString(KEY)?.lines()?.mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 5) return@mapNotNull null
        SavedSpot(p[0], p[1].toDoubleOrNull() ?: return@mapNotNull null, p[2].toDoubleOrNull(), p[3], p[4].toLongOrNull() ?: 0L)
    } ?: emptyList()

    fun save(spots: List<SavedSpot>) {
        store.putString(
            KEY,
            spots.joinToString("\n") { s ->
                listOf(clean(s.name), s.signalDbm.toString(), s.sinrDb?.toString() ?: "", clean(s.radioLabel), s.timestampMs.toString()).joinToString("\t")
            }.ifEmpty { null },
        )
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').trim().take(40)

    private companion object { const val KEY = "saved_spots" }
}

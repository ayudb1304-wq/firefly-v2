package com.firefly.app.venue

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.firefly.app.core.geo.LatLon
import com.firefly.app.core.geo.MapProjection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/** A named point on the venue map (PRD H1). */
data class Poi(val index: Int, val name: String, val lat: Double, val lon: Double)

/** An optional venue: map image + calibration + POIs. Without one, the app draws a free map. */
class VenuePack(
    val name: String,
    val image: Bitmap,
    val projection: MapProjection,
    val pois: List<Poi>,
)

/**
 * Venue packs are optional and never bundled. An organiser hands out a zip
 * containing `venue.json` and the image it names (see docs/VENUE_PACK.md); the
 * user loads it from the map screen and it lives in app-private storage until removed.
 */
class VenueRepository(private val context: Context) {
    private val dir = File(context.filesDir, "venue")

    private val _venue = MutableStateFlow(loadInstalled())
    val venue: StateFlow<VenuePack?> = _venue.asStateFlow()

    suspend fun importZip(uri: Uri): Result<VenuePack> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = File(context.cacheDir, "venue-import").apply { deleteRecursively(); mkdirs() }
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = File(entry.name).name // flatten; ignore directories
                        if (!entry.isDirectory && name.isNotEmpty() && !name.startsWith(".")) {
                            File(staging, name).outputStream().use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("cannot open $uri")
            val pack = load(staging) // validate before replacing the installed one
            dir.deleteRecursively()
            staging.copyRecursively(dir, overwrite = true)
            staging.deleteRecursively()
            _venue.value = pack
            pack
        }.onFailure { Log.w(TAG, "venue import failed", it) }
    }

    fun remove() {
        dir.deleteRecursively()
        _venue.value = null
    }

    private fun loadInstalled(): VenuePack? =
        if (File(dir, "venue.json").exists()) runCatching { load(dir) }.onFailure { Log.w(TAG, "installed venue unreadable", it) }.getOrNull() else null

    private fun load(folder: File): VenuePack {
        val json = JSONObject(File(folder, "venue.json").readText())
        val imageName = json.optString("image", "map.png")
        val image = BitmapFactory.decodeFile(File(folder, imageName).path) ?: error("could not decode $imageName")
        val corners = json.getJSONObject("corners")
        fun corner(key: String): LatLon = corners.getJSONArray(key).let { LatLon(it.getDouble(0), it.getDouble(1)) }
        val projection = MapProjection(
            imageWidth = image.width,
            imageHeight = image.height,
            topLeft = corner("tl"),
            topRight = corner("tr"),
            bottomLeft = corner("bl"),
            bottomRight = corner("br"),
        )
        val poisJson = json.optJSONArray("pois")
        val pois = buildList {
            if (poisJson != null) for (i in 0 until poisJson.length()) {
                val p = poisJson.getJSONObject(i)
                add(Poi(p.getInt("i"), p.getString("name"), p.getDouble("lat"), p.getDouble("lon")))
            }
        }
        return VenuePack(json.optString("name", "Venue"), image, projection, pois)
    }

    private companion object {
        const val TAG = "Firefly/Venue"
    }
}

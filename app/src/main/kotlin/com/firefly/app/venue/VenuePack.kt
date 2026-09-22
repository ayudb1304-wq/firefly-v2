package com.firefly.app.venue

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.firefly.app.core.geo.LatLon
import com.firefly.app.core.geo.MapProjection
import org.json.JSONObject

/** A named point on the venue map (PRD H1). */
data class Poi(val index: Int, val name: String, val lat: Double, val lon: Double)

/** A loaded venue: map image + calibration + POIs. Bundled in assets/venue for v1. */
class VenuePack(
    val name: String,
    val image: Bitmap,
    val projection: MapProjection,
    val pois: List<Poi>,
)

object VenueLoader {
    private const val DIR = "venue"

    fun load(context: Context): VenuePack {
        val json = JSONObject(context.assets.open("$DIR/venue.json").bufferedReader().use { it.readText() })
        val imageName = json.optString("image", "map.png")
        val image = context.assets.open("$DIR/$imageName").use { BitmapFactory.decodeStream(it) }
            ?: error("could not decode $DIR/$imageName")
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
}

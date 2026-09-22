package com.firefly.app.core.geo

/**
 * Maps WGS84 coordinates onto pixel coordinates of a static venue image
 * calibrated by the lat/lon of its four corners (ARCHITECTURE.md §4).
 *
 * A least-squares affine fit through the four corners absorbs small rotation
 * and non-square pixels. Venues are small enough that lat/lon can be treated
 * as a flat plane.
 */
class MapProjection(
    val imageWidth: Int,
    val imageHeight: Int,
    topLeft: LatLon,
    topRight: LatLon,
    bottomLeft: LatLon,
    bottomRight: LatLon,
) {
    // Coordinates are centred on the corner centroid before fitting so the
    // normal equations stay well conditioned (raw lon² sums lose precision).
    private val lon0: Double
    private val lat0: Double

    // px = a*(lon-lon0) + b*(lat-lat0) + c ; py = d*(lon-lon0) + e*(lat-lat0) + f
    private val a: Double
    private val b: Double
    private val c: Double
    private val d: Double
    private val e: Double
    private val f: Double

    init {
        require(imageWidth > 0 && imageHeight > 0) { "image size must be positive" }
        val w = imageWidth.toDouble()
        val h = imageHeight.toDouble()
        val corners = listOf(topLeft, topRight, bottomLeft, bottomRight)
        lon0 = corners.sumOf { it.lon } / 4
        lat0 = corners.sumOf { it.lat } / 4
        val geo = corners.map { LatLon(it.lat - lat0, it.lon - lon0) }
        val px = doubleArrayOf(0.0, w, 0.0, w)
        val py = doubleArrayOf(0.0, 0.0, h, h)
        val x = solveAffine(geo, px)
        val y = solveAffine(geo, py)
        a = x[0]; b = x[1]; c = x[2]
        d = y[0]; e = y[1]; f = y[2]
        require(!(a * e - b * d).isNaN() && a * e - b * d != 0.0) { "degenerate corner calibration" }
    }

    fun toPixel(lat: Double, lon: Double): Pixel {
        val dl = lon - lon0
        val dp = lat - lat0
        return Pixel(a * dl + b * dp + c, d * dl + e * dp + f)
    }

    fun toGeo(px: Double, py: Double): LatLon {
        val det = a * e - b * d
        val u = px - c
        val v = py - f
        val lon = (e * u - b * v) / det
        val lat = (-d * u + a * v) / det
        return LatLon(lat + lat0, lon + lon0)
    }

    /** True if the point falls inside the image bounds. */
    fun contains(lat: Double, lon: Double): Boolean {
        val p = toPixel(lat, lon)
        return p.x >= 0 && p.y >= 0 && p.x <= imageWidth && p.y <= imageHeight
    }

    /** Geographic centre of the image. */
    fun centre(): LatLon = toGeo(imageWidth / 2.0, imageHeight / 2.0)

    /** Approximate image pixels per metre at the map centre (for accuracy rings). */
    fun pixelsPerMetre(): Double {
        val centre = toGeo(imageWidth / 2.0, imageHeight / 2.0)
        val east = toGeo(imageWidth / 2.0 + 100.0, imageHeight / 2.0)
        val metres = GeoMath.distanceMetres(centre.lat, centre.lon, east.lat, east.lon)
        return if (metres > 0) 100.0 / metres else 0.0
    }

    companion object {
        private const val METRES_PER_DEGREE_LAT = 111_195.0

        /**
         * Synthetic north-up calibration: [widthMetres] across, centred on a point,
         * height scaled to the image aspect. Used for walk tests away from the real venue.
         */
        fun centredOn(lat: Double, lon: Double, widthMetres: Double, imageWidth: Int, imageHeight: Int): MapProjection {
            require(widthMetres > 0)
            val halfLon = (widthMetres / 2) / (METRES_PER_DEGREE_LAT * kotlin.math.cos(Math.toRadians(lat)))
            val halfLat = (widthMetres / 2) * imageHeight / imageWidth / METRES_PER_DEGREE_LAT
            return MapProjection(
                imageWidth, imageHeight,
                topLeft = LatLon(lat + halfLat, lon - halfLon),
                topRight = LatLon(lat + halfLat, lon + halfLon),
                bottomLeft = LatLon(lat - halfLat, lon - halfLon),
                bottomRight = LatLon(lat - halfLat, lon + halfLon),
            )
        }
    }

    /** Least squares for p = k0*lon + k1*lat + k2 over the four corners (normal equations, 3×3). */
    private fun solveAffine(geo: List<LatLon>, p: DoubleArray): DoubleArray {
        val m = Array(3) { DoubleArray(3) }
        val r = DoubleArray(3)
        for (i in geo.indices) {
            val row = doubleArrayOf(geo[i].lon, geo[i].lat, 1.0)
            for (j in 0..2) {
                r[j] += row[j] * p[i]
                for (k in 0..2) m[j][k] += row[j] * row[k]
            }
        }
        return gaussianSolve(m, r)
    }

    private fun gaussianSolve(m: Array<DoubleArray>, r: DoubleArray): DoubleArray {
        val n = 3
        val aug = Array(n) { i -> DoubleArray(n + 1).also { row -> for (j in 0 until n) row[j] = m[i][j]; row[n] = r[i] } }
        for (col in 0 until n) {
            var pivot = col
            for (row in col + 1 until n) if (kotlin.math.abs(aug[row][col]) > kotlin.math.abs(aug[pivot][col])) pivot = row
            val tmp = aug[col]; aug[col] = aug[pivot]; aug[pivot] = tmp
            val p = aug[col][col]
            require(p != 0.0) { "singular calibration matrix" }
            for (j in col until n + 1) aug[col][j] /= p
            for (row in 0 until n) if (row != col) {
                val factor = aug[row][col]
                for (j in col until n + 1) aug[row][j] -= factor * aug[col][j]
            }
        }
        return DoubleArray(n) { aug[it][n] }
    }
}

data class LatLon(val lat: Double, val lon: Double)

data class Pixel(val x: Double, val y: Double)

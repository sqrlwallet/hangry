package com.kevan.hangry.domain.calculation

import com.kevan.hangry.data.datasource.RoutePoint
import java.time.Duration
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Splits and climb from a workout's GPS route. */
object RouteStats {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** One kilometre (the last can be shorter) and how long it took. */
    data class Split(val index: Int, val meters: Double, val duration: Duration) {
        /** Seconds per kilometre. */
        val paceSecondsPerKm: Double get() = duration.seconds / (meters / 1000.0)
    }

    fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /**
     * Kilometre splits, the time at each kilometre found by interpolating between the two
     * points either side. A last part under 100 m is dropped - its pace would be noise.
     */
    fun splits(points: List<RoutePoint>, splitMeters: Double = 1000.0): List<Split> {
        val sorted = points.sortedBy { it.time }
        if (sorted.size < 2) return emptyList()
        val result = mutableListOf<Split>()
        var covered = 0.0
        var splitStartTime = sorted.first().time
        var splitStartDistance = 0.0
        for (i in 1 until sorted.size) {
            val step = distanceMeters(sorted[i - 1], sorted[i])
            val stepMillis = Duration.between(sorted[i - 1].time, sorted[i].time).toMillis()
            while (step > 0 && covered + step >= splitStartDistance + splitMeters) {
                val boundary = splitStartDistance + splitMeters
                val fraction = (boundary - covered) / step
                val boundaryTime = sorted[i - 1].time.plusMillis((stepMillis * fraction).toLong())
                result += Split(result.size + 1, splitMeters, Duration.between(splitStartTime, boundaryTime))
                splitStartTime = boundaryTime
                splitStartDistance = boundary
            }
            covered += step
        }
        val rest = covered - splitStartDistance
        if (rest >= 100.0) result += Split(result.size + 1, rest, Duration.between(splitStartTime, sorted.last().time))
        return result
    }

    /** (distance from start in metres, altitude) pairs, when the route recorded altitude. */
    fun elevationProfile(points: List<RoutePoint>): List<Pair<Double, Double>> {
        val sorted = points.sortedBy { it.time }
        if (sorted.count { it.altitudeMeters != null } < 2) return emptyList()
        var covered = 0.0
        return sorted.mapIndexedNotNull { i, p ->
            if (i > 0) covered += distanceMeters(sorted[i - 1], p)
            p.altitudeMeters?.let { covered to it }
        }
    }

    /** Total climb, ignoring wobbles under 2 m (GPS altitude is noisy). */
    fun climbMeters(profile: List<Pair<Double, Double>>): Double {
        var climb = 0.0
        var anchor = profile.firstOrNull()?.second ?: return 0.0
        for ((_, alt) in profile) {
            if (alt - anchor >= 2.0) { climb += alt - anchor; anchor = alt } else if (alt < anchor) anchor = alt
        }
        return climb
    }
}

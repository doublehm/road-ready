package com.roadready.util

import kotlin.math.*

data class GeoPoint(val lat: Double, val lon: Double)

object GeometryUtils {
    /**
     * Simplifies a polyline using the Douglas-Peucker algorithm.
     * @param points The list of coordinates to simplify.
     * @param epsilon The maximum perpendicular distance (in degrees/units) to allow.
     * @return A simplified list of coordinates.
     */
    fun simplify(points: List<GeoPoint>, epsilon: Double): List<GeoPoint> {
        if (points.size < 3) return points

        var dmax = 0.0
        var index = 0
        val end = points.size - 1

        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points[0], points[end])
            if (d > dmax) {
                index = i
                dmax = d
            }
        }

        return if (dmax > epsilon) {
            val left = simplify(points.subList(0, index + 1), epsilon)
            val right = simplify(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(points[0], points[end])
        }
    }

    private fun perpendicularDistance(p: GeoPoint, start: GeoPoint, end: GeoPoint): Double {
        val x = p.lat
        val y = p.lon
        val x1 = start.lat
        val y1 = start.lon
        val x2 = end.lat
        val y2 = end.lon

        val area = abs(0.5 * (x1 * (y2 - y) + x2 * (y - y1) + x * (y1 - y2)))
        val bottom = sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
        
        return if (bottom == 0.0) {
            sqrt((x - x1).pow(2) + (y - y1).pow(2))
        } else {
            area / bottom * 2.0
        }
    }
}

package com.roadready.util

import dev.jamesyox.kastro.sol.SolarEvent
import dev.jamesyox.kastro.sol.SolarEventSequence
import kotlinx.datetime.*

class SafetyZoneCalculator(private val latitude: Double, private val longitude: Double) {

    /**
     * Determines if a playground zone speed limit (30 km/h) is currently active.
     * In BC, playground zones are active daily from dawn to dusk (sunrise to sunset).
     */
    fun isPlaygroundZoneActive(now: Instant = Clock.System.now()): Boolean {
        val sequence = SolarEventSequence(
            start = now,
            latitude = latitude,
            longitude = longitude,
            requestedSolarEvents = listOf(SolarEvent.Sunrise, SolarEvent.Sunset)
        )
        
        // Find the most recent event before 'now'
        // This is a simplified logic: if the next upcoming event is Sunset, 
        // we are currently between Sunrise and Sunset.
        val nextEvent = sequence.firstOrNull() ?: return false
        return nextEvent == SolarEvent.Sunset
    }

    /**
     * Determines if a school zone speed limit (30 km/h) is currently active.
     * Standard BC: 8 AM to 5 PM on school days (Mon-Fri).
     */
    fun isSchoolZoneActive(now: Instant = Clock.System.now()): Boolean {
        val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = localDateTime.hour
        val dayOfWeek = localDateTime.dayOfWeek

        // Mon-Fri
        val isSchoolDay = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
        // 8:00 AM to 5:00 PM (17:00)
        val isSchoolHours = hour in 8..16 

        // Note: Future refinement could include a holiday calendar check
        return isSchoolDay && isSchoolHours
    }

    /**
     * Returns the effective speed limit based on zone rules.
     */
    fun getEffectiveLimit(baseLimit: Int, zoneType: String, now: Instant = Clock.System.now()): Int {
        return when (zoneType) {
            "school" -> if (isSchoolZoneActive(now)) 30 else baseLimit
            "playground" -> if (isPlaygroundZoneActive(now)) 30 else baseLimit
            else -> baseLimit
        }
    }
}

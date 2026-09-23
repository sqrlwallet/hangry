package com.kevan.hangry.domain.model

/**
 * Stand-in for HRV on days a device doesn't report it. Defaults to [EXCELLENT] so recovery
 * reads at its best unless the user says otherwise; they can pick how they actually feel.
 * Never used when a real HRV reading exists. [score] replaces the HRV component (0-100).
 */
enum class HrvFeeling(val label: String, val description: String, val score: Double) {
    EXCELLENT("Excellent", "Rested and full of energy", 90.0),
    GOOD("Good", "Feeling solid", 75.0),
    OKAY("Okay", "A bit flat", 60.0),
    TIRED("Tired", "Low on energy", 45.0),
    DRAINED("Drained", "Run down or unwell", 30.0);

    companion object {
        val DEFAULT = EXCELLENT
        fun fromName(name: String?): HrvFeeling? = entries.firstOrNull { it.name == name }
    }
}

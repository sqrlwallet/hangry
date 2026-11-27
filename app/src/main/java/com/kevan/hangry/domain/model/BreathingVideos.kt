package com.kevan.hangry.domain.model

/**
 * Guided breathing videos played in an embedded YouTube player. [key] is used in deep links
 * (widget, coach, Today card). Old pattern ids ("box_4", "bpm_5" ...) still resolve via [fromKey].
 */
data class BreathingVideo(
    val key: String,
    val youtubeId: String,
    val title: String,
    val shortLabel: String,
    val minutes: Int,
    val description: String
) {
    val thumbnailUrl: String get() = "https://i.ytimg.com/vi/$youtubeId/hqdefault.jpg"
}

object BreathingVideos {

    val all: List<BreathingVideo> = listOf(
        BreathingVideo(
            key = "box_4", youtubeId = "56N5WM1SxRQ",
            title = "Box Breathing 4-4-4-4", shortLabel = "Box", minutes = 10,
            description = "For calm, focus and stress relief"
        ),
        BreathingVideo(
            key = "478", youtubeId = "0L44kyA6S0g",
            title = "4-7-8 Breathing for Sleep", shortLabel = "4-7-8", minutes = 15,
            description = "Dark screen, for falling asleep"
        ),
        BreathingVideo(
            key = "bpm_3", youtubeId = "EIPd4c-M8m8",
            title = "3 Breaths Per Minute", shortLabel = "3 BPM", minutes = 12,
            description = "10s in, 10s out. Dark screen, for deep sleep"
        ),
        BreathingVideo(
            key = "bpm_4", youtubeId = "31QAaOI5ilw",
            title = "4 Breaths Per Minute", shortLabel = "4 BPM", minutes = 12,
            description = "7.5s in, 7.5s out. Dark screen, for calming down before sleep"
        ),
        BreathingVideo(
            key = "bpm_5", youtubeId = "p8hX40WkcG0",
            title = "5 Breaths Per Minute", shortLabel = "5 BPM", minutes = 12,
            description = "6s in, 6s out. Coherent breathing for sleep and calm"
        ),
        BreathingVideo(
            key = "bpm_6", youtubeId = "e6RwGHZFa0o",
            title = "6 Breaths Per Minute", shortLabel = "6 BPM", minutes = 12,
            description = "5s in, 5s out. Good for beginners"
        )
    )

    fun fromKey(key: String?): BreathingVideo? = all.firstOrNull { it.key == key }
}

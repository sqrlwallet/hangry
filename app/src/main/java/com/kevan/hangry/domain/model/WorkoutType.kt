package com.kevan.hangry.domain.model

/** Broad families of workouts - drives the icon and how hard the training-load estimate rates them. */
enum class WorkoutCategory(val intensity: Double) {
    RUN(1.4),
    HIIT(1.4),
    CYCLE(1.2),
    SWIM(1.2),
    SPORT(1.2),
    WINTER(1.2),
    STRENGTH(1.1),
    WATER(1.1),
    OTHER(1.0),
    WALK(0.7),
    MIND_BODY(0.7)
}

/**
 * Every Health Connect exercise type, stored by the name Health Connect uses (e.g.
 * "WEIGHTLIFTING"), with a friendly label. Kept free of Health Connect classes so the
 * rest of the app - and tests - can use it without the SDK.
 */
enum class WorkoutType(val label: String, val category: WorkoutCategory) {
    OTHER_WORKOUT("Workout", WorkoutCategory.OTHER),
    BADMINTON("Badminton", WorkoutCategory.SPORT),
    BASEBALL("Baseball", WorkoutCategory.SPORT),
    BASKETBALL("Basketball", WorkoutCategory.SPORT),
    BIKING("Cycling", WorkoutCategory.CYCLE),
    BIKING_STATIONARY("Indoor cycling", WorkoutCategory.CYCLE),
    BOOT_CAMP("Boot camp", WorkoutCategory.HIIT),
    BOXING("Boxing", WorkoutCategory.HIIT),
    CALISTHENICS("Calisthenics", WorkoutCategory.STRENGTH),
    CRICKET("Cricket", WorkoutCategory.SPORT),
    DANCING("Dancing", WorkoutCategory.OTHER),
    ELLIPTICAL("Elliptical", WorkoutCategory.CYCLE),
    EXERCISE_CLASS("Exercise class", WorkoutCategory.HIIT),
    FENCING("Fencing", WorkoutCategory.SPORT),
    FOOTBALL_AMERICAN("American football", WorkoutCategory.SPORT),
    FOOTBALL_AUSTRALIAN("Australian football", WorkoutCategory.SPORT),
    FRISBEE_DISC("Frisbee", WorkoutCategory.SPORT),
    GOLF("Golf", WorkoutCategory.WALK),
    GUIDED_BREATHING("Guided breathing", WorkoutCategory.MIND_BODY),
    GYMNASTICS("Gymnastics", WorkoutCategory.STRENGTH),
    HANDBALL("Handball", WorkoutCategory.SPORT),
    HIGH_INTENSITY_INTERVAL_TRAINING("HIIT", WorkoutCategory.HIIT),
    HIKING("Hiking", WorkoutCategory.WALK),
    ICE_HOCKEY("Ice hockey", WorkoutCategory.WINTER),
    ICE_SKATING("Ice skating", WorkoutCategory.WINTER),
    MARTIAL_ARTS("Martial arts", WorkoutCategory.HIIT),
    PADDLING("Paddling", WorkoutCategory.WATER),
    PARAGLIDING("Paragliding", WorkoutCategory.OTHER),
    PILATES("Pilates", WorkoutCategory.MIND_BODY),
    RACQUETBALL("Racquetball", WorkoutCategory.SPORT),
    ROCK_CLIMBING("Rock climbing", WorkoutCategory.STRENGTH),
    ROLLER_HOCKEY("Roller hockey", WorkoutCategory.SPORT),
    ROWING("Rowing", WorkoutCategory.WATER),
    ROWING_MACHINE("Rowing machine", WorkoutCategory.CYCLE),
    RUGBY("Rugby", WorkoutCategory.SPORT),
    RUNNING("Running", WorkoutCategory.RUN),
    RUNNING_TREADMILL("Treadmill run", WorkoutCategory.RUN),
    SAILING("Sailing", WorkoutCategory.WATER),
    SCUBA_DIVING("Scuba diving", WorkoutCategory.WATER),
    SKATING("Skating", WorkoutCategory.OTHER),
    SKIING("Skiing", WorkoutCategory.WINTER),
    SNOWBOARDING("Snowboarding", WorkoutCategory.WINTER),
    SNOWSHOEING("Snowshoeing", WorkoutCategory.WINTER),
    SOCCER("Soccer", WorkoutCategory.SPORT),
    SOFTBALL("Softball", WorkoutCategory.SPORT),
    SQUASH("Squash", WorkoutCategory.SPORT),
    STAIR_CLIMBING("Stair climbing", WorkoutCategory.RUN),
    STAIR_CLIMBING_MACHINE("Stair machine", WorkoutCategory.RUN),
    STRENGTH_TRAINING("Strength training", WorkoutCategory.STRENGTH),
    STRETCHING("Stretching", WorkoutCategory.MIND_BODY),
    SURFING("Surfing", WorkoutCategory.WATER),
    SWIMMING_OPEN_WATER("Open water swim", WorkoutCategory.SWIM),
    SWIMMING_POOL("Pool swim", WorkoutCategory.SWIM),
    TABLE_TENNIS("Table tennis", WorkoutCategory.SPORT),
    TENNIS("Tennis", WorkoutCategory.SPORT),
    VOLLEYBALL("Volleyball", WorkoutCategory.SPORT),
    WALKING("Walking", WorkoutCategory.WALK),
    WATER_POLO("Water polo", WorkoutCategory.SWIM),
    WEIGHTLIFTING("Weightlifting", WorkoutCategory.STRENGTH),
    WHEELCHAIR("Wheelchair", WorkoutCategory.OTHER),
    YOGA("Yoga", WorkoutCategory.MIND_BODY);

    /** Whether a distance makes sense to show as pace (min/km) rather than speed. */
    val showsPace: Boolean get() = category == WorkoutCategory.RUN || category == WorkoutCategory.WALK || category == WorkoutCategory.SWIM

    companion object {
        /** Ids saved before every type was recognised. */
        private val LEGACY = mapOf(
            "CYCLING" to BIKING,
            "SWIMMING" to SWIMMING_POOL,
            "HIIT" to HIGH_INTENSITY_INTERVAL_TRAINING,
            "OTHER" to OTHER_WORKOUT
        )

        fun fromId(id: String?): WorkoutType {
            val key = id?.uppercase() ?: return OTHER_WORKOUT
            return entries.firstOrNull { it.name == key } ?: LEGACY[key] ?: OTHER_WORKOUT
        }
    }
}

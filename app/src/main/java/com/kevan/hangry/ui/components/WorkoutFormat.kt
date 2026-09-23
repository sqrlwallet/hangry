package com.kevan.hangry.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Kayaking
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.domain.model.WorkoutCategory
import com.kevan.hangry.domain.model.WorkoutText

/** Icon for a workout, by its family of exercise. Text lives in [WorkoutText]. */
object WorkoutFormat {
    fun icon(workout: ExerciseSessionEntity): ImageVector = when (WorkoutText.type(workout).category) {
        WorkoutCategory.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
        WorkoutCategory.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
        WorkoutCategory.CYCLE -> Icons.AutoMirrored.Filled.DirectionsBike
        WorkoutCategory.SWIM -> Icons.Default.Pool
        WorkoutCategory.HIIT -> Icons.Default.Whatshot
        WorkoutCategory.SPORT -> Icons.Default.SportsTennis
        WorkoutCategory.WINTER -> Icons.Default.DownhillSkiing
        WorkoutCategory.WATER -> Icons.Default.Kayaking
        WorkoutCategory.MIND_BODY -> Icons.Default.SelfImprovement
        WorkoutCategory.STRENGTH, WorkoutCategory.OTHER -> Icons.Default.FitnessCenter
    }
}

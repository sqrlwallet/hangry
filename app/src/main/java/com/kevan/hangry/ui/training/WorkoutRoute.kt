package com.kevan.hangry.ui.training

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.contracts.ExerciseRouteRequestContract
import com.kevan.hangry.R
import com.kevan.hangry.data.datasource.RealHealthConnectDataSource
import com.kevan.hangry.data.datasource.RoutePoint
import com.kevan.hangry.data.datasource.RouteResult
import com.kevan.hangry.data.datasource.toRoutePoints
import com.kevan.hangry.data.local.entity.ExerciseSessionEntity
import com.kevan.hangry.ui.theme.LocalHangryTokens
import kotlinx.coroutines.launch
import kotlin.math.cos

/**
 * "Show route" for distance workouts: reads the GPS route from Health Connect only when asked.
 * Another app's route needs the user's OK per workout, so Health Connect's consent screen opens
 * if needed. Drawn as an outline - no map tiles, so nothing leaves the phone.
 */
@Composable
fun WorkoutRouteSection(workout: ExerciseSessionEntity) {
    val recordId = workout.sourceRecordId ?: return
    if ((workout.distanceMeters ?: 0.0) <= 0.0) return
    val context = LocalContext.current
    val tokens = LocalHangryTokens.current
    val scope = rememberCoroutineScope()
    val dataSource = remember { RealHealthConnectDataSource(context.applicationContext) }
    var points by remember(recordId) { mutableStateOf<List<RoutePoint>?>(null) }
    var noRoute by remember(recordId) { mutableStateOf(false) }

    val consent = rememberLauncherForActivityResult(ExerciseRouteRequestContract()) { route ->
        val shared = route?.toRoutePoints().orEmpty()
        if (shared.size >= 2) points = shared else noRoute = true
    }

    Column(modifier = Modifier.padding(start = 36.dp)) {
        val shown = points
        if (shown != null) {
            RouteOutline(shown)
            TextButton(onClick = { points = null }) { Text(stringResource(R.string.metrics_training_hide_route)) }
        } else if (noRoute) {
            Text(stringResource(R.string.metrics_training_no_route), style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
        } else {
            TextButton(onClick = {
                scope.launch {
                    when (val result = dataSource.readExerciseRoute(recordId)) {
                        is RouteResult.Points -> if (result.points.size >= 2) points = result.points else noRoute = true
                        RouteResult.ConsentRequired -> consent.launch(recordId)
                        RouteResult.None -> noRoute = true
                    }
                }
            }) { Text(stringResource(R.string.metrics_training_show_route)) }
        }
    }
}

@Composable
private fun RouteOutline(points: List<RoutePoint>) {
    val tokens = LocalHangryTokens.current
    val lineColor = tokens.chartColors.trainingLoad
    val startColor = tokens.scoreColors.primed
    val endColor = tokens.scoreColors.rebuild
    val description = stringResource(R.string.metrics_training_route_description)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(vertical = 8.dp)
            .semantics { contentDescription = description }
    ) {
        // Flat projection is plenty at workout scale; longitude shrinks with latitude.
        val midLat = Math.toRadians(points.map { it.latitude }.average())
        val xs = points.map { it.longitude * cos(midLat) }
        val ys = points.map { it.latitude }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val span = maxOf(maxX - minX, maxY - minY).takeIf { it > 0 } ?: return@Canvas
        val pad = 12.dp.toPx()
        val scale = minOf(size.width - 2 * pad, size.height - 2 * pad) / span
        // Centre the route in the box.
        val offX = (size.width - (maxX - minX) * scale) / 2
        val offY = (size.height - (maxY - minY) * scale) / 2
        fun at(i: Int) = Offset(
            (offX + (xs[i] - minX) * scale).toFloat(),
            (offY + (maxY - ys[i]) * scale).toFloat() // north up
        )
        val path = Path().apply {
            moveTo(at(0).x, at(0).y)
            for (i in 1 until points.size) lineTo(at(i).x, at(i).y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(startColor, radius = 5.dp.toPx(), center = at(0))
        drawCircle(endColor, radius = 5.dp.toPx(), center = at(points.lastIndex))
    }
}

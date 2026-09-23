package com.kevan.hangry.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kevan.hangry.R
import com.kevan.hangry.data.local.entity.UserProfileEntity
import com.kevan.hangry.domain.model.AgeMath
import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyUnits
import com.kevan.hangry.ui.bodyage.BirthdayPickerDialog
import com.kevan.hangry.ui.coach.DashExpression
import com.kevan.hangry.ui.coach.DashMood
import com.kevan.hangry.ui.components.HangryCard
import com.kevan.hangry.ui.theme.BackgroundDark
import com.kevan.hangry.ui.theme.BlueRibbon
import com.kevan.hangry.ui.theme.HangryTokens
import com.kevan.hangry.ui.theme.LocalHangryTokens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The onboarding steps, in order - the step indicator on every screen counts from this. */
object OnboardingSteps {
    const val ABOUT_YOU = 1
    const val EXTRAS = 2
    const val CONNECT = 3
    const val HISTORY = 4
    const val SYNC = 5
    const val TOTAL = 5
}

/** What "About you" collects; everything is already metric. */
data class ProfileBasics(
    val dateOfBirth: LocalDate,
    val sex: BiologicalSex,
    val heightCm: Double,
    val weightKg: Double
)

/** The optional step; null / empty means the user left it out. */
data class ProfileExtras(
    val weightGoalKg: Double? = null,
    val goalTargetDate: LocalDate? = null,
    val sleepGoalMinutes: Int? = null,
    val dailyStepGoal: Long? = null,
    val neckCm: Double? = null,
    val waistCm: Double? = null,
    val hipCm: Double? = null,
    val allergies: List<String> = emptyList(),
    val conditions: List<String> = emptyList()
)

/**
 * Step 1: the four things calorie burn, body composition, heart zones and Body Age can't be
 * accurate without. Units can be typed either way; they're stored metric.
 */
@Composable
fun AboutYouScreen(
    profile: UserProfileEntity?,
    initialWeightKg: Double?,
    imperial: Boolean,
    onImperialChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onContinue: (ProfileBasics) -> Unit,
    onSkip: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    var dateOfBirth by rememberSaveable { mutableStateOf(profile?.dateOfBirth) }
    var sex by rememberSaveable { mutableStateOf(profile?.biologicalSex?.let { runCatching { BiologicalSex.valueOf(it) }.getOrNull() }) }
    val startHeight = profile?.heightCm
    var heightCm by rememberSaveable { mutableStateOf(startHeight?.let { "${it.toInt()}" } ?: "") }
    var heightFt by rememberSaveable { mutableStateOf(startHeight?.let { "${BodyUnits.cmToFeetInches(it).first}" } ?: "") }
    var heightIn by rememberSaveable { mutableStateOf(startHeight?.let { "${BodyUnits.cmToFeetInches(it).second}" } ?: "") }
    var weight by rememberSaveable {
        mutableStateOf(initialWeightKg?.let { "${BodyUnits.round1(if (imperial) BodyUnits.kgToLb(it) else it)}" } ?: "")
    }
    var pickingBirthday by remember { mutableStateOf(false) }

    val parsedHeight = if (imperial) {
        heightFt.toIntOrNull()?.let { ft -> BodyUnits.feetInchesToCm(ft, heightIn.toDoubleOrNull() ?: 0.0) }
    } else heightCm.toDoubleOrNull()
    val parsedWeight = weight.toDoubleOrNull()?.let { if (imperial) BodyUnits.lbToKg(it) else it }
    val validHeight = BodyUnits.plausibleHeight(parsedHeight)
    val validWeight = BodyUnits.plausibleWeight(parsedWeight)
    val heightTyped = if (imperial) heightFt.isNotEmpty() else heightCm.isNotEmpty()

    if (pickingBirthday) {
        BirthdayPickerDialog(initial = dateOfBirth, onDismiss = { pickingBirthday = false }, onPicked = {
            dateOfBirth = it
            pickingBirthday = false
        })
    }

    OnboardingProfileScaffold(
        title = "About You",
        step = OnboardingSteps.ABOUT_YOU,
        onNavigateBack = onNavigateBack,
        primaryLabel = "Continue",
        primaryEnabled = dateOfBirth != null && sex != null && validHeight != null && validWeight != null,
        onPrimary = { onContinue(ProfileBasics(dateOfBirth!!, sex!!, validHeight!!, BodyUnits.round1(validWeight!!))) },
        secondaryLabel = "I'll add these later",
        onSecondary = onSkip,
        dashMood = DashMood.WAVE,
        heading = "Let's get to know you",
        subheading = "Your burn, body metrics and Body Age are worked out from these - so they're about you, not an average person. On this device only."
    ) {
        HangryCard {
            Column(verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)) {
                FieldLabel("Date of birth")
                OutlinedButton(onClick = { pickingBirthday = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Cake, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        dateOfBirth?.let { "${it.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} · ${AgeMath.years(it)} years old" }
                            ?: "Choose your birthday"
                    )
                }

                FieldLabel("Biological sex", "Resting metabolism and body-fat formulas differ by sex.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BiologicalSex.entries.forEach { option ->
                        FilterChip(
                            selected = sex == option,
                            onClick = { sex = option },
                            label = { Text(option.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                HorizontalDivider(color = tokens.cardBorder)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Units", style = MaterialTheme.typography.labelLarge, color = tokens.textSecondary, modifier = Modifier.weight(1f))
                    FilterChip(selected = !imperial, onClick = {
                        if (imperial) {
                            parsedHeight?.let { heightCm = "${it.toInt()}" }
                            parsedWeight?.let { weight = "${BodyUnits.round1(it)}" }
                            onImperialChange(false)
                        }
                    }, label = { Text("cm · kg") })
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(selected = imperial, onClick = {
                        if (!imperial) {
                            parsedHeight?.let { cm -> BodyUnits.cmToFeetInches(cm).let { (ft, inch) -> heightFt = "$ft"; heightIn = "$inch" } }
                            parsedWeight?.let { weight = "${BodyUnits.round1(BodyUnits.kgToLb(it))}" }
                            onImperialChange(true)
                        }
                    }, label = { Text("ft · lb") })
                }

                val heightError = heightTyped && validHeight == null
                if (imperial) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(heightFt, { heightFt = it }, "Height (ft)", Modifier.weight(1f), decimal = false, isError = heightError)
                        NumberField(heightIn, { heightIn = it }, "(in)", Modifier.weight(1f), isError = heightError)
                    }
                } else {
                    NumberField(heightCm, { heightCm = it }, "Height (cm)", Modifier.fillMaxWidth(), isError = heightError,
                        supporting = if (heightError) "That doesn't look like a height in cm" else null)
                }
                val weightError = weight.isNotEmpty() && validWeight == null
                NumberField(
                    weight, { weight = it }, if (imperial) "Weight (lb)" else "Weight (kg)", Modifier.fillMaxWidth(),
                    isError = weightError,
                    supporting = if (weightError) "That doesn't look like a weight in ${if (imperial) "lb" else "kg"}" else
                        "Synced weigh-ins from Health Connect take over when they arrive",
                    last = true
                )
            }
        }
    }
}

private val COMMON_ALLERGIES = listOf("Peanuts", "Tree nuts", "Milk", "Eggs", "Gluten", "Fish", "Shellfish", "Soy", "Sesame")
private val COMMON_CONDITIONS = listOf("Type 1 diabetes", "Type 2 diabetes", "High blood pressure", "Asthma", "PCOS", "Thyroid condition")

/**
 * Step 2: all optional. Each answer switches something on - meal allergen alerts, your own
 * sleep and step targets, the tape-measure body-fat estimate, goal pacing.
 */
@Composable
fun ProfileExtrasScreen(
    profile: UserProfileEntity?,
    sex: BiologicalSex?,
    currentWeightKg: Double?,
    imperial: Boolean,
    onNavigateBack: () -> Unit,
    onDone: (ProfileExtras) -> Unit,
    onSkip: () -> Unit
) {
    val tokens = LocalHangryTokens.current
    fun toDisplay(kg: Double) = BodyUnits.round1(if (imperial) BodyUnits.kgToLb(kg) else kg)
    fun toKg(value: Double) = if (imperial) BodyUnits.lbToKg(value) else value
    val lengthUnit = if (imperial) "in" else "cm"
    fun lengthToCm(text: String) = text.toDoubleOrNull()?.let { if (imperial) it * 2.54 else it }

    var goalWeight by rememberSaveable { mutableStateOf(profile?.weightGoalKg?.let { "${toDisplay(it)}" } ?: "") }
    var targetDate by rememberSaveable { mutableStateOf(profile?.goalTargetDate) }
    var sleepHours by rememberSaveable { mutableStateOf<Double?>(null) }
    var steps by rememberSaveable { mutableStateOf<Long?>(null) }
    var neck by rememberSaveable { mutableStateOf("") }
    var waist by rememberSaveable { mutableStateOf("") }
    var hips by rememberSaveable { mutableStateOf("") }
    var allergies by rememberSaveable { mutableStateOf(listOf<String>()) }
    var conditions by rememberSaveable { mutableStateOf(listOf<String>()) }
    var otherAllergy by rememberSaveable { mutableStateOf("") }
    var otherCondition by rememberSaveable { mutableStateOf("") }
    var pickingDate by remember { mutableStateOf(false) }

    val goalKg = goalWeight.toDoubleOrNull()?.let(::toKg)?.let(BodyUnits::plausibleWeight)
    val direction = if (goalKg != null && currentWeightKg != null) when {
        goalKg < currentWeightKg - 0.5 -> "Lose ${toDisplay(currentWeightKg - goalKg)} ${if (imperial) "lb" else "kg"}"
        goalKg > currentWeightKg + 0.5 -> "Gain ${toDisplay(goalKg - currentWeightKg)} ${if (imperial) "lb" else "kg"}"
        else -> "Maintain your weight"
    } else null

    fun splitOthers(text: String) = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    fun collect() = ProfileExtras(
        weightGoalKg = goalKg?.let(BodyUnits::round1),
        goalTargetDate = targetDate?.takeIf { goalKg != null },
        sleepGoalMinutes = sleepHours?.let { (it * 60).toInt() },
        dailyStepGoal = steps,
        neckCm = lengthToCm(neck),
        waistCm = lengthToCm(waist),
        hipCm = lengthToCm(hips),
        allergies = (allergies + splitOthers(otherAllergy)).distinctBy { it.lowercase() },
        conditions = (conditions + splitOthers(otherCondition)).distinctBy { it.lowercase() }
    )

    if (pickingDate) {
        GoalDatePickerDialog(initial = targetDate, onDismiss = { pickingDate = false }, onPicked = {
            targetDate = it
            pickingDate = false
        })
    }

    OnboardingProfileScaffold(
        title = "Make It Yours",
        step = OnboardingSteps.EXTRAS,
        onNavigateBack = onNavigateBack,
        primaryLabel = "Save & Continue",
        primaryEnabled = true,
        onPrimary = { onDone(collect()) },
        secondaryLabel = "Skip for now",
        onSecondary = onSkip,
        dashMood = DashMood.THINKING,
        heading = "A few optional extras",
        subheading = "Answer any, all or none - each one makes Hangry a bit smarter. You can change them any time in Settings."
    ) {
        ExtrasSection("Your goal", "Dash paces your calories and checks progress against it.") {
            NumberField(
                goalWeight, { goalWeight = it }, if (imperial) "Goal weight (lb)" else "Goal weight (kg)", Modifier.fillMaxWidth(),
                isError = goalWeight.isNotEmpty() && goalKg == null,
                supporting = direction
            )
            if (goalKg != null) {
                OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(targetDate?.let { "By ${it.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}" } ?: "Pick a target date (optional)")
                }
            }
        }

        ExtrasSection("Sleep goal", "Sets your sleep need, sleep debt and bedtime reminder. Most adults need 7-9 hours.") {
            ChoiceChips(listOf(7.0, 7.5, 8.0, 8.5, 9.0), sleepHours, { sleepHours = it }) {
                if (it % 1.0 == 0.0) "${it.toInt()} h" else "$it h"
            }
        }

        ExtrasSection("Daily step goal", "Your step target, streak and the Today ring.") {
            ChoiceChips(listOf(6_000L, 8_000L, 10_000L, 12_000L), steps, { steps = it }) { "${it / 1000}k" }
        }

        ExtrasSection("Allergies", "Dash warns you when a logged meal or a meal idea may contain these.") {
            ToggleChips(COMMON_ALLERGIES, allergies) { allergies = it }
            OutlinedTextField(
                value = otherAllergy,
                onValueChange = { otherAllergy = it },
                label = { Text("Others (comma-separated)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
        }

        ExtrasSection("Health conditions", "Helps Dash keep advice about food, exercise and supplements safe for you.") {
            ToggleChips(COMMON_CONDITIONS, conditions) { conditions = it }
            OutlinedTextField(
                value = otherCondition,
                onValueChange = { otherCondition = it },
                label = { Text("Others (comma-separated)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
        }

        ExtrasSection(
            "Tape measurements",
            "With a tape measure, Hangry estimates body fat (U.S. Navy method) - no photo needed. Measure at the narrowest part of the neck and at the navel."
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(neck, { neck = it }, "Neck ($lengthUnit)", Modifier.weight(1f))
                NumberField(waist, { waist = it }, "Waist ($lengthUnit)", Modifier.weight(1f), last = sex != BiologicalSex.FEMALE)
            }
            // The Navy formula for women also uses hips.
            if (sex == BiologicalSex.FEMALE) {
                NumberField(hips, { hips = it }, "Hips ($lengthUnit)", Modifier.fillMaxWidth(), last = true)
            }
        }

        Text(
            "Stays on this device. Allergies and conditions also appear in Health Records, where you can edit them.",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted
        )
    }
}

/** The dark photo-backed frame shared by the onboarding screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingProfileScaffold(
    title: String,
    step: Int,
    onNavigateBack: () -> Unit,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    dashMood: DashMood,
    heading: String,
    subheading: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalHangryTokens.current
    Box(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Image(
            painter = painterResource(id = R.drawable.onboarding_ambient_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.45f),
                        0.6f to Color.Black.copy(alpha = 0.65f),
                        1.0f to BackgroundDark.copy(alpha = 0.95f)
                    )
                )
        )
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = {
                TopAppBar(
                    title = { Text(title, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, BackgroundDark.copy(alpha = 0.95f))))
                        .navigationBarsPadding()
                        .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onPrimary,
                        enabled = primaryEnabled,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BlueRibbon, contentColor = Color.White)
                    ) {
                        Text(primaryLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    TextButton(onClick = onSecondary) {
                        Text(secondaryLabel, color = tokens.textSecondary)
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HangryTokens.Spacing.m, vertical = HangryTokens.Spacing.s),
                verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.m)
            ) {
                OnboardingStepIndicator(currentStep = step, totalSteps = OnboardingSteps.TOTAL)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DashExpression(mood = dashMood, size = 88.dp)
                    Spacer(modifier = Modifier.width(HangryTokens.Spacing.m))
                    Column {
                        Text(heading, style = MaterialTheme.typography.headlineSmall, color = tokens.textPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(subheading, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                    }
                }
                content()
            }
        }
    }
}

@Composable
private fun ExtrasSection(title: String, why: String, content: @Composable ColumnScope.() -> Unit) {
    HangryCard {
        Column(verticalArrangement = Arrangement.spacedBy(HangryTokens.Spacing.s)) {
            FieldLabel(title, why)
            content()
        }
    }
}

@Composable
private fun FieldLabel(title: String, why: String? = null) {
    val tokens = LocalHangryTokens.current
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
        if (why != null) Text(why, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    decimal: Boolean = true,
    isError: Boolean = false,
    supporting: String? = null,
    last: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed -> onValueChange(typed.filter { it.isDigit() || (decimal && it == '.') }.take(6)) },
        label = { Text(label) },
        isError = isError,
        singleLine = true,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = if (last) ImeAction.Done else ImeAction.Next
        ),
        modifier = modifier
    )
}

/** Pick one (tap again to clear). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceChips(options: List<T>, selected: T?, onSelect: (T?) -> Unit, label: (T) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(if (selected == option) null else option) },
                label = { Text(label(option)) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToggleChips(options: List<String>, selected: List<String>, onChange: (List<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - option else selected + option) },
                label = { Text(option) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDatePickerDialog(initial: LocalDate?, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val today = LocalDate.now()
    val utc = ZoneOffset.UTC
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: today.plusMonths(3)).atStartOfDay(utc).toInstant().toEpochMilli(),
        yearRange = today.year..(today.year + 5),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Instant.ofEpochMilli(utcTimeMillis).atZone(utc).toLocalDate().isAfter(today)
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(utc).toLocalDate()) } }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = state, title = { Text("Reach your goal by", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

package com.kevan.hangry.domain.calculation

import com.kevan.hangry.domain.model.BiologicalSex
import com.kevan.hangry.domain.model.BodyMetric
import com.kevan.hangry.domain.model.BodyMetricGroup
import com.kevan.hangry.domain.model.BodyMetricsInput
import com.kevan.hangry.domain.model.MetricBand
import com.kevan.hangry.domain.model.MetricInfo
import com.kevan.hangry.domain.model.MetricTone.CAUTION
import com.kevan.hangry.domain.model.MetricTone.GOOD
import com.kevan.hangry.domain.model.MetricTone.NEUTRAL
import com.kevan.hangry.domain.model.MetricTone.RISK
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Derives every body metric we can from height, weight, age, sex, tape measurements and the
 * latest body-fat reading. Pure and synchronous: each metric either has a value or lists the
 * inputs it's missing, so the UI can show "add your waist to see this" instead of hiding it.
 *
 * Every metric carries its own explanation (what it is, how the number was worked out with
 * the user's own figures, why it matters, and where the ranges come from).
 */
class BodyMetricsCalculator(private val calorieCalculator: CalorieCalculator) {

    fun calculate(input: BodyMetricsInput): List<BodyMetricGroup> {
        val ctx = Derived(input)
        return listOf(
            BodyMetricGroup(
                id = "size",
                title = "Size & weight",
                description = "How your weight compares with your height.",
                metrics = listOf(bmi(ctx), healthyWeightRange(ctx), idealWeight(ctx))
            ),
            BodyMetricGroup(
                id = "composition",
                title = "Body composition",
                description = "What your weight is made of - muscle and other lean tissue versus fat.",
                metrics = listOf(bodyFat(ctx), leanMass(ctx), fatMass(ctx), ffmi(ctx), fatMassIndex(ctx))
            ),
            BodyMetricGroup(
                id = "shape",
                title = "Shape & fat distribution",
                description = "Where you carry weight. Fat around the middle matters more for health than total weight.",
                metrics = listOf(
                    waistToHeight(ctx), waistTarget(ctx), waistSize(ctx), waistToHip(ctx),
                    bodyRoundness(ctx), conicity(ctx), vTaper(ctx), neckSize(ctx)
                )
            ),
            BodyMetricGroup(
                id = "energy",
                title = "Energy & daily targets",
                description = "How much energy your body uses, and what to aim for each day.",
                metrics = listOf(
                    maintenanceCalories(ctx), goalCalories(ctx), bmrMifflin(ctx), bmrKatch(ctx),
                    averageBurn(ctx), proteinTarget(ctx), waterTarget(ctx)
                )
            ),
            BodyMetricGroup(
                id = "reference",
                title = "Reference numbers",
                description = "Handy figures for training and medical contexts.",
                metrics = listOf(maxHeartRate(ctx), bodySurfaceArea(ctx))
            )
        )
    }

    /** Pre-derived values shared by several metrics. */
    private inner class Derived(val input: BodyMetricsInput) {
        val heightCm = input.heightCm?.takeIf { it > 0 }
        val weightKg = input.weightKg?.takeIf { it > 0 }
        val heightM = heightCm?.div(100.0)
        val age = input.age?.takeIf { it > 0 }
        val sex = input.sex
        val waist = input.waistCm?.takeIf { it > 0 }
        val hip = input.hipCm?.takeIf { it > 0 }
        val chest = input.chestCm?.takeIf { it > 0 }
        val neck = input.neckCm?.takeIf { it > 0 }
        val bmi = if (weightKg != null && heightM != null) weightKg / heightM.pow(2) else null

        /** Measured body fat if we have a scan, otherwise the Deurenberg estimate from BMI. */
        val bodyFat: Double? = input.bodyFatPercent?.takeIf { it > 0 }
            ?: if (bmi != null && age != null && sex != null) {
                ((1.20 * bmi) + (0.23 * age) - (10.8 * sexFactor(sex)) - 5.4).coerceIn(3.0, 65.0)
            } else null
        val bodyFatIsEstimate = input.bodyFatPercent == null && bodyFat != null
        val leanKg = if (weightKg != null && bodyFat != null) weightKg * (1 - bodyFat / 100.0) else null
        val fatKg = if (weightKg != null && bodyFat != null) weightKg * bodyFat / 100.0 else null

        val bodyFatNote: String
            get() = when {
                bodyFatIsEstimate -> "Body fat here is estimated from your BMI, age and sex (Deurenberg). Save a tape or photo scan for a better reading."
                input.bodyFatSource != null -> "Body fat is from your latest ${input.bodyFatSource} scan."
                else -> ""
            }
    }

    // region Size & weight

    private fun bmi(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "Body Mass Index compares your weight with your height. It's the most widely used screening number for weight.",
                howCalculated = how,
                whyItMatters = "Large population studies link a BMI outside 18.5–25 with higher risk of heart disease, type 2 diabetes and early death. It can't tell muscle from fat, so muscular people often read \"overweight\" - read it together with waist-to-height ratio and body fat.",
                source = "World Health Organization adult BMI classification."
            )
        }
        val bmi = c.bmi ?: return unavailable("bmi", "BMI", "", info("Weight (kg) ÷ height (m)²."), missing("Height" to c.heightCm, "Weight" to c.weightKg))
        return BodyMetric(
            id = "bmi",
            name = "BMI",
            value = bmi,
            displayValue = f(bmi, 1),
            bands = listOf(
                MetricBand("Underweight", 18.5, CAUTION),
                MetricBand("Healthy", 25.0, GOOD),
                MetricBand("Overweight", 30.0, CAUTION),
                MetricBand("Obese", null, RISK)
            ),
            scaleMin = 15.0,
            scaleMax = 40.0,
            info = info("Weight ÷ height² = ${f(c.weightKg!!, 1)} kg ÷ (${f(c.heightM!!, 2)} m)² = ${f(bmi, 1)}.")
        )
    }

    private fun healthyWeightRange(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "The weight range that keeps your BMI between 18.5 and 24.9 at your height.",
                howCalculated = how,
                whyItMatters = "A concrete target is easier to act on than a BMI number. If you carry a lot of muscle, you can sit above this range and still be lean - your waist and body fat tell you which.",
                source = "Derived from the WHO healthy BMI range (18.5–24.9)."
            )
        }
        val h = c.heightM ?: return unavailable("weight_range", "Healthy weight range", "kg", info("18.5 × height² to 24.9 × height²."), missing("Height" to c.heightCm))
        val low = 18.5 * h * h
        val high = 24.9 * h * h
        val how = "18.5 × (${f(h, 2)} m)² = ${f(low, 1)} kg, up to 24.9 × (${f(h, 2)} m)² = ${f(high, 1)} kg."
        val w = c.weightKg
            ?: return unavailable("weight_range", "Healthy weight range", "kg", info(how), listOf("Weight")).copy(
                displayValue = "${f(low, 0)}–${f(high, 0)}"
            )
        val status = when {
            w < low -> "${f(low - w, 1)} kg below range"
            w > high -> "${f(w - high, 1)} kg above range"
            else -> "In range"
        }
        return BodyMetric(
            id = "weight_range",
            name = "Healthy weight range",
            value = w,
            displayValue = "${f(low, 0)}–${f(high, 0)}",
            unit = "kg",
            bands = listOf(
                MetricBand("Below range", low, CAUTION),
                MetricBand("In range", high, GOOD),
                MetricBand("Above range", null, CAUTION)
            ),
            scaleMin = low * 0.8,
            scaleMax = high * 1.25,
            statusOverride = status,
            info = info("$how You weigh ${f(w, 1)} kg.")
        )
    }

    private fun idealWeight(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "A single reference weight for your height and sex, from the Devine formula.",
                howCalculated = how,
                whyItMatters = "Doctors and pharmacists still use it to dose some medicines. It ignores build and muscle, so treat it as a reference point, not a goal.",
                source = "Devine BJ, 1974."
            )
        }
        val heightCm = c.heightCm
        val sex = c.sex
        if (heightCm == null || sex == null) {
            return unavailable("ideal_weight", "Ideal weight (Devine)", "kg", info("Base weight + 2.3 kg per inch of height above 5 ft."), missing("Height" to heightCm, "Sex" to sex))
        }
        val inchesOver5ft = heightCm / 2.54 - 60
        val base = bySex(sex, 50.0, 45.5)
        val ideal = max(0.0, base + 2.3 * inchesOver5ft)
        return BodyMetric(
            id = "ideal_weight",
            name = "Ideal weight (Devine)",
            value = ideal,
            displayValue = f(ideal, 1),
            unit = "kg",
            info = info("${f(base, 1)} kg + 2.3 kg × ${f(inchesOver5ft, 1)} inches above 5 ft = ${f(ideal, 1)} kg.")
        )
    }

    // endregion

    // region Composition

    private fun bodyFatBands(sex: BiologicalSex): List<MetricBand> {
        val cuts = sexCuts(sex, male = listOf(6.0, 14.0, 18.0, 25.0), female = listOf(14.0, 21.0, 25.0, 32.0))
        return listOf(
            MetricBand("Essential", cuts[0], CAUTION),
            MetricBand("Athletic", cuts[1], GOOD),
            MetricBand("Fit", cuts[2], GOOD),
            MetricBand("Average", cuts[3], NEUTRAL),
            MetricBand("High", null, CAUTION)
        )
    }

    private fun bodyFat(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "The share of your body weight that is fat.",
                howCalculated = how,
                whyItMatters = "It separates \"heavy because of muscle\" from \"heavy because of fat\", which BMI can't. Some fat is essential for hormones and health - very low levels aren't a goal either.",
                source = "Ranges from the American Council on Exercise body-fat chart."
            )
        }
        val bf = c.bodyFat
        val sex = c.sex
        if (bf == null || sex == null) {
            return unavailable(
                "body_fat", "Body fat", "%",
                info("Save a tape-measure or photo scan in the Body Fat calculator, or add height, weight, age and sex for an estimate."),
                missing("Body fat scan or height/weight/age" to bf, "Sex" to sex)
            )
        }
        val cuts = sexCuts(sex, listOf(6.0, 14.0, 18.0, 25.0), listOf(14.0, 21.0, 25.0, 32.0))
        return BodyMetric(
            id = "body_fat",
            name = if (c.bodyFatIsEstimate) "Body fat (estimated)" else "Body fat",
            value = bf,
            displayValue = f(bf, 1),
            unit = "%",
            bands = bodyFatBands(sex),
            scaleMin = cuts.first() - 4,
            scaleMax = cuts.last() + 12,
            info = info(
                if (c.bodyFatIsEstimate) {
                    "Deurenberg formula: 1.2 × BMI + 0.23 × age − 10.8 × sex − 5.4 = ${f(bf, 1)}%. This is a rough estimate; a tape or photo scan is more accurate."
                } else {
                    "From your latest ${c.input.bodyFatSource ?: "body fat"} scan: ${f(bf, 1)}%."
                }
            )
        )
    }

    private fun leanMass(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "Everything in your body that isn't fat: muscle, bone, organs and water.",
                howCalculated = how,
                whyItMatters = "Keeping or building lean mass while losing weight is the difference between getting smaller and getting healthier. Track it over time - it should hold steady or rise.",
                source = "Weight × (1 − body fat %)."
            )
        }
        val lean = c.leanKg ?: return unavailable("lean_mass", "Lean mass", "kg", info("Weight × (1 − body fat %)."), missing("Weight" to c.weightKg, "Body fat" to c.bodyFat))
        return BodyMetric(
            id = "lean_mass",
            name = "Lean mass",
            value = lean,
            displayValue = f(lean, 1),
            unit = "kg",
            info = info("${f(c.weightKg!!, 1)} kg × (1 − ${f(c.bodyFat!!, 1)}%) = ${f(lean, 1)} kg. ${c.bodyFatNote}".trim())
        )
    }

    private fun fatMass(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "How many kilograms of your body weight are fat.",
                howCalculated = how,
                whyItMatters = "When you're cutting, this is the number you actually want to go down - scale weight also moves with water and muscle.",
                source = "Weight × body fat %."
            )
        }
        val fat = c.fatKg ?: return unavailable("fat_mass", "Fat mass", "kg", info("Weight × body fat %."), missing("Weight" to c.weightKg, "Body fat" to c.bodyFat))
        return BodyMetric(
            id = "fat_mass",
            name = "Fat mass",
            value = fat,
            displayValue = f(fat, 1),
            unit = "kg",
            info = info("${f(c.weightKg!!, 1)} kg × ${f(c.bodyFat!!, 1)}% = ${f(fat, 1)} kg. ${c.bodyFatNote}".trim())
        )
    }

    private fun ffmi(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "Fat-Free Mass Index - like BMI, but using only your lean mass. It measures how muscular you are for your height.",
                howCalculated = how,
                whyItMatters = "It's the fairest way to compare muscle across people of different heights and to track muscle gain. Values around 25 (men) or 21 (women) are near the upper limit most people reach without performance-enhancing drugs.",
                source = "Kouri et al., 1995 (normalized to 1.8 m)."
            )
        }
        val lean = c.leanKg
        val h = c.heightM
        val sex = c.sex
        if (lean == null || h == null || sex == null) {
            return unavailable("ffmi", "FFMI", "", info("Lean mass ÷ height², adjusted to a 1.8 m reference height."), missing("Height" to h, "Lean mass" to lean, "Sex" to sex))
        }
        val raw = lean / (h * h)
        val normalized = raw + 6.1 * (1.8 - h)
        val cuts = sexCuts(sex, listOf(18.0, 20.0, 22.0, 25.0), listOf(14.0, 16.0, 18.0, 21.0))
        return BodyMetric(
            id = "ffmi",
            name = "FFMI",
            value = normalized,
            displayValue = f(normalized, 1),
            bands = listOf(
                MetricBand("Below average", cuts[0], NEUTRAL),
                MetricBand("Average", cuts[1], NEUTRAL),
                MetricBand("Above average", cuts[2], GOOD),
                MetricBand("Excellent", cuts[3], GOOD),
                MetricBand("Exceptional", null, GOOD)
            ),
            scaleMin = cuts.first() - 4,
            scaleMax = cuts.last() + 3,
            info = info(
                "${f(lean, 1)} kg ÷ (${f(h, 2)} m)² = ${f(raw, 1)}, adjusted for height: ${f(raw, 1)} + 6.1 × (1.8 − ${f(h, 2)}) = ${f(normalized, 1)}. ${c.bodyFatNote}".trim()
            )
        )
    }

    private fun fatMassIndex(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "Fat Mass Index - your fat mass relative to your height.",
                howCalculated = how,
                whyItMatters = "Paired with FFMI, it splits BMI into its two halves: how much of your size is fat and how much is lean tissue.",
                source = "Kelly et al., 2009 (DXA reference ranges)."
            )
        }
        val fat = c.fatKg
        val h = c.heightM
        val sex = c.sex
        if (fat == null || h == null || sex == null) {
            return unavailable("fmi", "Fat mass index", "", info("Fat mass ÷ height²."), missing("Height" to h, "Fat mass" to fat, "Sex" to sex))
        }
        val fmi = fat / (h * h)
        val cuts = sexCuts(sex, listOf(3.0, 6.0, 9.0), listOf(5.0, 9.0, 13.0))
        return BodyMetric(
            id = "fmi",
            name = "Fat mass index",
            value = fmi,
            displayValue = f(fmi, 1),
            bands = listOf(
                MetricBand("Low", cuts[0], CAUTION),
                MetricBand("Healthy", cuts[1], GOOD),
                MetricBand("Excess", cuts[2], CAUTION),
                MetricBand("High", null, RISK)
            ),
            scaleMin = 0.0,
            scaleMax = cuts.last() + 6,
            info = info("${f(fat, 1)} kg ÷ (${f(h, 2)} m)² = ${f(fmi, 1)}. ${c.bodyFatNote}".trim())
        )
    }

    // endregion

    // region Shape

    private fun waistToHeight(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "Your waist divided by your height.",
                howCalculated = how,
                whyItMatters = "One of the best simple predictors of heart and metabolic risk - better than BMI in many studies. The rule of thumb: keep your waist to less than half your height.",
                source = "Ashwell et al., 2012 meta-analysis; UK NICE guidance, 2022."
            )
        }
        val waist = c.waist
        val height = c.heightCm
        if (waist == null || height == null) return unavailable("whtr", "Waist-to-height ratio", "", info("Waist ÷ height."), missing("Waist" to waist, "Height" to height))
        val ratio = waist / height
        return BodyMetric(
            id = "whtr",
            name = "Waist-to-height ratio",
            value = ratio,
            displayValue = f(ratio, 2),
            bands = listOf(
                MetricBand("Very slim", 0.4, CAUTION),
                MetricBand("Healthy", 0.5, GOOD),
                MetricBand("Increased risk", 0.6, CAUTION),
                MetricBand("High risk", null, RISK)
            ),
            scaleMin = 0.3,
            scaleMax = 0.75,
            info = info("${f(waist, 1)} cm ÷ ${f(height, 0)} cm = ${f(ratio, 2)}.")
        )
    }

    private fun waistTarget(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "The largest waist that keeps your waist-to-height ratio under 0.5.",
                howCalculated = how,
                whyItMatters = "It turns the waist-to-height rule into a number you can check with a tape measure.",
                source = "Based on the 0.5 waist-to-height boundary (Ashwell; NICE)."
            )
        }
        val height = c.heightCm ?: return unavailable("waist_target", "Waist target", "cm", info("Half your height."), listOf("Height"))
        val target = height * 0.5
        val how = "${f(height, 0)} cm ÷ 2 = ${f(target, 0)} cm."
        val waist = c.waist
            ?: return unavailable("waist_target", "Waist target", "cm", info(how), listOf("Waist")).copy(displayValue = "< ${f(target, 0)}")
        val diff = waist - target
        return BodyMetric(
            id = "waist_target",
            name = "Waist target",
            value = waist,
            displayValue = "< ${f(target, 0)}",
            unit = "cm",
            bands = listOf(
                MetricBand("Under target", target, GOOD),
                MetricBand("Over target", null, CAUTION)
            ),
            scaleMin = target * 0.75,
            scaleMax = target * 1.3,
            statusOverride = if (diff < 0) "${f(-diff, 1)} cm under" else "${f(diff, 1)} cm over",
            info = info("$how Your waist is ${f(waist, 1)} cm.")
        )
    }

    private fun waistSize(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "Your waist measurement on its own, compared with sex-specific risk thresholds.",
                howCalculated = how,
                whyItMatters = "A large waist signals visceral fat - the fat around your organs that drives insulin resistance, high blood pressure and heart disease.",
                source = "World Health Organization waist circumference thresholds (2008)."
            )
        }
        val waist = c.waist
        val sex = c.sex
        if (waist == null || sex == null) return unavailable("waist_size", "Waist size", "cm", info("Measured at the midpoint between your lowest rib and hip bone."), missing("Waist" to waist, "Sex" to sex))
        val cuts = sexCuts(sex, listOf(94.0, 102.0), listOf(80.0, 88.0))
        return BodyMetric(
            id = "waist_size",
            name = "Waist size",
            value = waist,
            displayValue = f(waist, 1),
            unit = "cm",
            bands = listOf(
                MetricBand("Low risk", cuts[0], GOOD),
                MetricBand("Increased risk", cuts[1], CAUTION),
                MetricBand("High risk", null, RISK)
            ),
            scaleMin = cuts[0] - 30,
            scaleMax = cuts[1] + 18,
            info = info("Your waist: ${f(waist, 1)} cm. Risk rises from ${f(cuts[0], 0)} cm and is high from ${f(cuts[1], 0)} cm.")
        )
    }

    private fun waistToHip(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "Your waist divided by your hips - whether you carry weight more around the middle (\"apple\") or the hips (\"pear\").",
                howCalculated = how,
                whyItMatters = "Weight around the middle is linked with more heart attacks and diabetes than the same weight on the hips and thighs.",
                source = "World Health Organization, Waist Circumference and Waist–Hip Ratio (2008)."
            )
        }
        val waist = c.waist
        val hip = c.hip
        val sex = c.sex
        if (waist == null || hip == null || sex == null) return unavailable("whr", "Waist-to-hip ratio", "", info("Waist ÷ hips."), missing("Waist" to waist, "Hips" to hip, "Sex" to sex))
        val ratio = waist / hip
        val cuts = sexCuts(sex, listOf(0.90, 1.0), listOf(0.80, 0.85))
        return BodyMetric(
            id = "whr",
            name = "Waist-to-hip ratio",
            value = ratio,
            displayValue = f(ratio, 2),
            bands = listOf(
                MetricBand("Healthy", cuts[0], GOOD),
                MetricBand("Moderate risk", cuts[1], CAUTION),
                MetricBand("High risk", null, RISK)
            ),
            scaleMin = 0.65,
            scaleMax = 1.1,
            info = info("${f(waist, 1)} cm ÷ ${f(hip, 1)} cm = ${f(ratio, 2)}.")
        )
    }

    private fun bodyRoundness(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "Body Roundness Index - models your body as an ellipse from your waist and height to estimate how round versus lean your shape is.",
                howCalculated = how,
                whyItMatters = "In a 2024 study of 33,000 US adults, risk of death was lowest for BRI around 4.5–5.5, higher below 3.4 and clearly higher above 6.9. It tracks visceral fat better than BMI.",
                source = "Thomas et al., 2013; Zhang et al., JAMA Network Open, 2024."
            )
        }
        val waist = c.waist
        val h = c.heightM
        if (waist == null || h == null) return unavailable("bri", "Body roundness index", "", info("364.2 − 365.5 × √(1 − (waist ÷ 2π)² ÷ (height ÷ 2)²)."), missing("Waist" to waist, "Height" to h))
        val waistM = waist / 100.0
        val inner = 1 - (waistM / (2 * PI)).pow(2) / (0.5 * h).pow(2)
        if (inner <= 0) return unavailable("bri", "Body roundness index", "", info("Couldn't compute - check your waist and height."), emptyList())
        val bri = 364.2 - 365.5 * sqrt(inner)
        return BodyMetric(
            id = "bri",
            name = "Body roundness index",
            value = bri,
            displayValue = f(bri, 1),
            bands = listOf(
                MetricBand("Very low", 3.4, CAUTION),
                MetricBand("Healthy range", 6.9, GOOD),
                MetricBand("High", null, RISK)
            ),
            scaleMin = 1.0,
            scaleMax = 10.0,
            info = info("364.2 − 365.5 × √(1 − (${f(waistM, 2)} m ÷ 2π)² ÷ (${f(h, 2)} m ÷ 2)²) = ${f(bri, 1)}.")
        )
    }

    private fun conicity(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "Conicity Index - how much your body narrows from the middle like a cone versus a straight cylinder.",
                howCalculated = how,
                whyItMatters = "Higher values mean more fat concentrated at the waist, which is linked with higher cardiovascular risk. 1.0 would be a perfect cylinder.",
                source = "Valdez, 1991; cut-offs from Pitanga & Lessa, 2004."
            )
        }
        val waist = c.waist
        val w = c.weightKg
        val h = c.heightM
        val sex = c.sex
        if (waist == null || w == null || h == null || sex == null) {
            return unavailable("conicity", "Conicity index", "", info("Waist (m) ÷ (0.109 × √(weight ÷ height))."), missing("Waist" to waist, "Weight" to w, "Height" to h, "Sex" to sex))
        }
        val index = (waist / 100.0) / (0.109 * sqrt(w / h))
        val cut = sexCuts(sex, listOf(1.25), listOf(1.18))[0]
        return BodyMetric(
            id = "conicity",
            name = "Conicity index",
            value = index,
            displayValue = f(index, 2),
            bands = listOf(
                MetricBand("Healthy", cut, GOOD),
                MetricBand("Elevated", null, CAUTION)
            ),
            scaleMin = 1.0,
            scaleMax = 1.5,
            info = info("${f(waist / 100.0, 2)} m ÷ (0.109 × √(${f(w, 1)} kg ÷ ${f(h, 2)} m)) = ${f(index, 2)}.")
        )
    }

    private fun vTaper(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "Chest-to-waist ratio - the \"V-taper\" from a broad upper body to a narrower waist.",
                howCalculated = how,
                whyItMatters = "It's a physique measure rather than a health marker. It rises when you build your chest, back and shoulders or lose fat at the waist, so it's a good progress signal for training.",
                source = "Common physique-training benchmark."
            )
        }
        val chest = c.chest
        val waist = c.waist
        if (chest == null || waist == null) return unavailable("v_taper", "V-taper (chest ÷ waist)", "", info("Chest ÷ waist."), missing("Chest" to chest, "Waist" to waist))
        val ratio = chest / waist
        return BodyMetric(
            id = "v_taper",
            name = "V-taper (chest ÷ waist)",
            value = ratio,
            displayValue = f(ratio, 2),
            bands = listOf(
                MetricBand("Straight", 1.1, NEUTRAL),
                MetricBand("Moderate taper", 1.25, GOOD),
                MetricBand("Athletic taper", 1.4, GOOD),
                MetricBand("Pronounced taper", null, GOOD)
            ),
            scaleMin = 0.9,
            scaleMax = 1.6,
            info = info("${f(chest, 1)} cm ÷ ${f(waist, 1)} cm = ${f(ratio, 2)}.")
        )
    }

    private fun neckSize(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "Your neck measurement, used as a screening flag for obstructive sleep apnea.",
                howCalculated = how,
                whyItMatters = "A neck over 40 cm is one of the eight STOP-BANG warning signs for sleep apnea. It isn't a diagnosis - but if you also snore, wake tired or have high blood pressure, it's worth raising with a doctor.",
                source = "STOP-BANG questionnaire (Chung et al., 2008)."
            )
        }
        val neck = c.neck ?: return unavailable("neck", "Neck size", "cm", info("Measured just below the larynx (Adam's apple)."), listOf("Neck"))
        return BodyMetric(
            id = "neck",
            name = "Neck size",
            value = neck,
            displayValue = f(neck, 1),
            unit = "cm",
            bands = listOf(
                MetricBand("Typical", 40.0, GOOD),
                MetricBand("Sleep-apnea flag", null, CAUTION)
            ),
            scaleMin = 28.0,
            scaleMax = 50.0,
            info = info("Your neck: ${f(neck, 1)} cm. The STOP-BANG threshold is 40 cm.")
        )
    }

    // endregion

    // region Energy

    private fun maintenanceCalories(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "How many calories you can eat per day to stay at your current weight, based on how you actually lived the last 7 days.",
                howCalculated = how,
                whyItMatters = "It's the anchor for every nutrition goal: eat at it to maintain, below it to lose fat, above it to gain. Because it uses your own steps and workouts, it moves with you - a more active week raises it.",
                source = "BMR (Katch–McArdle with a recent body-fat scan, else Mifflin–St Jeor) for the time you weren't active, plus every step (~0.5 kcal per kg per km walked, plus resting burn while walking) and every workout in full, plus 10% for digesting food (thermic effect of food)."
            )
        }
        val result = c.input.energy
        val e = result?.estimate ?: return unavailable(
            "maintenance", "Maintenance calories", "kcal/day",
            info("Resting burn + every step + every workout, plus 10% for digesting food, over the last 7 full days."),
            result?.missing ?: listOf("Weight", "Height", "Age", "Sex", "Step data")
        )
        val notes = if (e.workoutsWithoutCalories > 0) {
            "${e.workoutsWithoutCalories} workout(s) had no calorie data and weren't counted."
        } else ""
        val how = "Over ${e.daysWithData} day(s) with data (${e.windowStart} to ${e.windowEnd}):\n" +
            "• Resting (${e.bmrMethod} BMR ${f(e.bmrKcal, 0)} kcal, for the ${f(1440 - e.avgActiveMinutes, 0)} inactive minutes): ${f(e.restingKcal, 0)} kcal\n" +
            "• Steps: ${f(e.avgCountedSteps, 0)} steps outside workouts × ${f(e.kcalPerStep, 3)} kcal = ${f(e.stepKcal, 0)} kcal\n" +
            "• Workouts: ${e.workoutsCounted} session(s), averaging ${f(e.avgWorkoutKcal, 0)} kcal per day, counted in full\n" +
            "• Digesting food (thermic effect, 10%): ${f(e.tefKcal, 0)} kcal\n" +
            "= ${f(e.maintenanceKcal, 0)} kcal/day." + if (notes.isNotEmpty()) "\n$notes" else ""
        return BodyMetric(
            id = "maintenance",
            name = "Maintenance calories",
            value = e.maintenanceKcal,
            displayValue = f(e.maintenanceKcal, 0),
            unit = "kcal/day",
            info = info(how)
        )
    }

    private fun goalCalories(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "How much to eat each day to reach your goal weight by your target date.",
                howCalculated = how,
                whyItMatters = "It turns a goal into a daily number. Aggressive timelines are capped at about 0.7 kg per week (and never below 1,200 kcal) so you lose fat, not muscle.",
                source = "~7,700 kcal per kg of body fat, applied evenly across the days until your target date."
            )
        }
        val result = c.input.energy
        val e = result?.estimate ?: return unavailable(
            "goal_calories", "Goal calorie target", "kcal/day",
            info("Maintenance calories ± the daily deficit or surplus needed to reach your goal weight by your target date."),
            result?.missing ?: listOf("Weight", "Height", "Age", "Sex", "Step data")
        )
        val goalWeight = e.goalWeightKg
        val goalDate = e.goalDate
        if (goalWeight == null || goalDate == null) {
            return unavailable(
                "goal_calories", "Goal calorie target", "kcal/day",
                info("Set a goal weight and target date in Settings and we'll work out your daily calories."),
                missing("Goal weight" to goalWeight, "Target date" to goalDate)
            )
        }
        val goal = e.goal
        val target = goal?.dailyCalorieTarget
        if (goal == null || target == null) {
            return unavailable("goal_calories", "Goal calorie target", "kcal/day", info(goal?.guidance ?: "Choose a target date in the future."), listOf("Future target date"))
        }
        val weight = c.weightKg ?: goalWeight
        val days = java.time.temporal.ChronoUnit.DAYS.between(e.windowEnd.plusDays(1), goalDate)
        val change = goalWeight - weight
        val requested = if (days > 0) change * 7700 / days else 0.0
        val adjustment = target - e.maintenanceKcal
        val how = "Going from ${f(weight, 1)} kg to ${f(goalWeight, 1)} kg by $goalDate ($days days): " +
            "${f(kotlin.math.abs(change), 1)} kg × 7,700 kcal ÷ $days days = ${f(kotlin.math.abs(requested), 0)} kcal/day ${if (change < 0) "deficit" else "surplus"}.\n" +
            "Maintenance ${f(e.maintenanceKcal, 0)} ${if (adjustment < 0) "−" else "+"} ${f(kotlin.math.abs(adjustment), 0)} = $target kcal/day.\n${goal.guidance}"
        val status = when {
            adjustment <= -25 -> "${f(-adjustment, 0)} kcal/day deficit"
            adjustment >= 25 -> "${f(adjustment, 0)} kcal/day surplus"
            else -> "Maintain"
        }
        return BodyMetric(
            id = "goal_calories",
            name = "Goal calorie target",
            value = target.toDouble(),
            displayValue = target.toString(),
            unit = "kcal/day",
            statusOverride = status,
            info = info(how)
        )
    }

    private fun bmrMifflin(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "Basal Metabolic Rate - the calories your body burns at complete rest just to stay alive.",
                howCalculated = how,
                whyItMatters = "It's usually 60–70% of what you burn in a day and the base for any calorie target. Eating well below it for long stretches makes it hard to keep muscle.",
                source = "Mifflin–St Jeor equation, 1990 - the most accurate general formula in validation studies."
            )
        }
        val w = c.weightKg
        val h = c.heightCm
        val age = c.age
        val sex = c.sex
        if (w == null || h == null || age == null || sex == null) {
            return unavailable("bmr", "BMR", "kcal/day", info("10 × weight + 6.25 × height − 5 × age + sex adjustment."), missing("Weight" to w, "Height" to h, "Age" to age, "Sex" to sex))
        }
        val bmr = calorieCalculator.calculateBmr(w, h, age, sex)
        val sexTerm = when (sex) {
            BiologicalSex.MALE -> "+ 5"
            BiologicalSex.FEMALE -> "− 161"
            BiologicalSex.OTHER -> "− 78"
        }
        return BodyMetric(
            id = "bmr",
            name = "BMR",
            value = bmr,
            displayValue = f(bmr, 0),
            unit = "kcal/day",
            info = info("10 × ${f(w, 1)} + 6.25 × ${f(h, 0)} − 5 × $age $sexTerm = ${f(bmr, 0)} kcal/day.")
        )
    }

    private fun bmrKatch(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "BMR worked out from your lean mass instead of your total weight.",
                howCalculated = how,
                whyItMatters = "Lean tissue burns most of your resting energy, so this is often more accurate than Mifflin–St Jeor for muscular or very lean people. If the two differ a lot, trust this one when your body-fat reading is good.",
                source = "Katch–McArdle equation."
            )
        }
        val lean = c.leanKg ?: return unavailable("bmr_lean", "BMR (lean-mass based)", "kcal/day", info("370 + 21.6 × lean mass."), missing("Weight" to c.weightKg, "Body fat" to c.bodyFat))
        val bmr = 370 + 21.6 * lean
        return BodyMetric(
            id = "bmr_lean",
            name = "BMR (lean-mass based)",
            value = bmr,
            displayValue = f(bmr, 0),
            unit = "kcal/day",
            info = info("370 + 21.6 × ${f(lean, 1)} kg = ${f(bmr, 0)} kcal/day. ${c.bodyFatNote}".trim())
        )
    }

    private fun averageBurn(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "The total calories your watch or phone reported burning per day recently, straight from Health Connect.",
                howCalculated = how,
                whyItMatters = "A cross-check for Maintenance calories above. Devices differ a lot in how they estimate burn, so if the two disagree, Maintenance calories (built from your steps and workouts) is usually the steadier guide.",
                source = "Average of daily total calories from Health Connect."
            )
        }
        val burn = c.input.averageDailyBurnKcal?.takeIf { it > 0 }
            ?: return unavailable("avg_burn", "Health Connect daily burn", "kcal/day", info("Needs synced total-calorie data from Health Connect."), listOf("Synced calorie data"))
        return BodyMetric(
            id = "avg_burn",
            name = "Health Connect daily burn",
            value = burn,
            displayValue = f(burn, 0),
            unit = "kcal/day",
            info = info("Average of your last ${c.input.averageDailyBurnDays} days with data = ${f(burn, 0)} kcal/day.")
        )
    }

    private fun proteinTarget(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "A daily protein range that supports keeping and building muscle.",
                howCalculated = how,
                whyItMatters = "Enough protein protects muscle while dieting and maximises gains from training. Spread it over 3–5 meals of roughly 25–40 g each.",
                source = "International Society of Sports Nutrition position stand (Jäger et al., 2017)."
            )
        }
        val w = c.weightKg ?: return unavailable("protein", "Protein target", "g/day", info("1.6–2.2 g per kg of body weight."), listOf("Weight"))
        // With a high BMI, dose from the weight at BMI 25 so the target isn't inflated by fat mass.
        val h = c.heightM
        val bmi = c.bmi
        val basis = if (bmi != null && bmi >= 30 && h != null) 25.0 * h * h else w
        val low = 1.6 * basis
        val high = 2.2 * basis
        val basisNote = if (basis != w) " Based on ${f(basis, 1)} kg (your weight at a BMI of 25), since dosing from total weight overshoots at higher body fat." else ""
        return BodyMetric(
            id = "protein",
            name = "Protein target",
            value = (low + high) / 2,
            displayValue = "${f(low, 0)}–${f(high, 0)}",
            unit = "g/day",
            info = info("1.6–2.2 g × ${f(basis, 1)} kg = ${f(low, 0)}–${f(high, 0)} g per day.$basisNote")
        )
    }

    private fun waterTarget(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "A baseline daily fluid intake for your body weight.",
                howCalculated = how,
                whyItMatters = "Even mild dehydration lowers performance, focus and recovery. Add roughly 0.5–1 L for every hour of exercise or on hot days; food covers about a fifth of your needs.",
                source = "Common clinical guideline of 30–35 ml per kg of body weight."
            )
        }
        val w = c.weightKg ?: return unavailable("water", "Water target", "L/day", info("30–35 ml per kg of body weight."), listOf("Weight"))
        val low = w * 0.030
        val high = w * 0.035
        return BodyMetric(
            id = "water",
            name = "Water target",
            value = (low + high) / 2,
            displayValue = "${f(low, 1)}–${f(high, 1)}",
            unit = "L/day",
            info = info("30–35 ml × ${f(w, 1)} kg = ${f(low, 1)}–${f(high, 1)} L per day, before exercise.")
        )
    }

    // endregion

    // region Reference

    private fun maxHeartRate(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "An estimate of the highest heart rate you can reach during all-out effort.",
                howCalculated = how,
                whyItMatters = "Heart-rate training zones are set as percentages of it (for example, easy \"zone 2\" is about 60–70%). Individuals can differ by 10+ bpm, so a real max-effort test beats any formula.",
                source = "Tanaka et al., 2001 - more accurate than the classic \"220 − age\"."
            )
        }
        val age = c.age ?: return unavailable("max_hr", "Max heart rate (estimated)", "bpm", info("208 − 0.7 × age."), listOf("Age"))
        val hr = 208 - 0.7 * age
        return BodyMetric(
            id = "max_hr",
            name = "Max heart rate (estimated)",
            value = hr,
            displayValue = f(hr, 0),
            unit = "bpm",
            info = info("208 − 0.7 × $age = ${f(hr, 0)} bpm. Easy zone 2 ≈ ${f(hr * 0.6, 0)}–${f(hr * 0.7, 0)} bpm.")
        )
    }

    private fun bodySurfaceArea(c: Derived): BodyMetric {
        val info = { how: String ->
            MetricInfo(
                whatItIs = "The total surface area of your skin.",
                howCalculated = how,
                whyItMatters = "Used in medicine to dose some drugs (like chemotherapy) and to scale kidney and heart measurements. Not something to train for - just a useful number to know.",
                source = "Mosteller formula, 1987."
            )
        }
        val w = c.weightKg
        val h = c.heightCm
        if (w == null || h == null) return unavailable("bsa", "Body surface area", "m²", info("√(height × weight ÷ 3600)."), missing("Height" to h, "Weight" to w))
        val bsa = sqrt(h * w / 3600.0)
        return BodyMetric(
            id = "bsa",
            name = "Body surface area",
            value = bsa,
            displayValue = f(bsa, 2),
            unit = "m²",
            info = info("√(${f(h, 0)} cm × ${f(w, 1)} kg ÷ 3600) = ${f(bsa, 2)} m².")
        )
    }

    // endregion

    private fun unavailable(id: String, name: String, unit: String, info: MetricInfo, missing: List<String>) =
        BodyMetric(id = id, name = name, value = null, displayValue = "—", unit = unit, info = info, missingInputs = missing)

    private fun missing(vararg inputs: Pair<String, Any?>): List<String> =
        inputs.filter { it.second == null }.map { it.first }

    private fun sexFactor(sex: BiologicalSex) = when (sex) {
        BiologicalSex.MALE -> 1.0
        BiologicalSex.FEMALE -> 0.0
        BiologicalSex.OTHER -> 0.5
    }

    private fun bySex(sex: BiologicalSex, male: Double, female: Double) = when (sex) {
        BiologicalSex.MALE -> male
        BiologicalSex.FEMALE -> female
        BiologicalSex.OTHER -> (male + female) / 2
    }

    /** Sex-specific cut-offs; "Other" uses the midpoint of each pair. */
    private fun sexCuts(sex: BiologicalSex, male: List<Double>, female: List<Double>): List<Double> =
        male.indices.map { bySex(sex, male[it], female[it]) }

    private fun f(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)
}

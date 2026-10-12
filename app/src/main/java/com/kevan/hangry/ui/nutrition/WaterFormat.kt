package com.kevan.hangry.ui.nutrition

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kevan.hangry.R

/**
 * The one place water volumes become text (screen, snackbar, widget). Liters and ml for now;
 * a fluid-ounce option only needs a unit parameter here.
 */
internal object WaterFormat {

    /** A single amount: "250 ml", "1.5 L". */
    fun amount(ml: Int): Text =
        if (ml < 1000) Text(R.string.nutrition_water_ml, ml) else Text(R.string.nutrition_water_liters, ml / 1000.0)

    /** Intake against target: "1.2 / 2.5 L". */
    fun progress(intakeMl: Int, targetMl: Int): Text =
        Text(R.string.nutrition_water_progress, intakeMl / 1000.0, targetMl / 1000.0)

    class Text(@StringRes val res: Int, vararg val args: Any)
}

internal fun Resources.waterText(text: WaterFormat.Text): String = getString(text.res, *text.args)

@Composable
internal fun waterText(text: WaterFormat.Text): String = stringResource(text.res, *text.args)

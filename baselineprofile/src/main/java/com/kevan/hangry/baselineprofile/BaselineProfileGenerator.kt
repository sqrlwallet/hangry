package com.kevan.hangry.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code Hangry runs at start-up and while switching tabs, so it ships precompiled.
 * Run with `./gradlew :app:generateBaselineProfile` - see baselineprofile/README.md.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        finishOnboardingIfShown()
        device.wait(Until.hasObject(By.desc(TABS.first())), TIMEOUT_MS)
    }

    @Test
    fun tabs() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()
        finishOnboardingIfShown()
        // Every tab, scrolled, then back to Today - the switch animation runs each time.
        (TABS.drop(1) + TABS.first()).forEach { tab ->
            device.wait(Until.findObject(By.desc(tab).clickable(true)), TIMEOUT_MS)?.click()
            device.waitForIdle()
            device.findObject(By.scrollable(true))?.let {
                it.fling(Direction.DOWN)
                it.fling(Direction.UP)
            }
        }
    }

    /**
     * A fresh install opens on onboarding: skip the profile, carry on with whatever Health
     * Connect access there is (no permission dialog), and finish the short import.
     */
    private fun MacrobenchmarkScope.finishOnboardingIfShown() {
        if (!device.wait(Until.hasObject(By.text("Get Started")), 5_000)) return
        tap("Get Started")
        tap("Skip for now")
        tap("Skip for now")
        tap("Continue with Available Permissions", "Continue Without Health Connect", "Continue")
        tap("Not now", "Continue")
        tap("Start Historical Import")
        // The finish button stays disabled until the import ends.
        val deadline = System.currentTimeMillis() + IMPORT_TIMEOUT_MS
        while (!device.hasObject(By.desc(TABS.first())) && System.currentTimeMillis() < deadline) {
            device.wait(Until.findObject(By.text("Enter Hangry")), 1_000)?.click()
                ?: device.findObject(By.text("Continue to Today"))?.click()
            device.waitForIdle()
        }
    }

    /** Taps the first of [labels] that appears. */
    private fun MacrobenchmarkScope.tap(vararg labels: String) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            labels.firstNotNullOfOrNull { device.findObject(By.text(it)) }?.let {
                it.click()
                device.waitForIdle()
                return
            }
            Thread.sleep(200)
        }
        error("None of ${labels.toList()} appeared")
    }

    private companion object {
        const val PACKAGE = "com.kevan.hangry"
        const val TIMEOUT_MS = 10_000L
        const val IMPORT_TIMEOUT_MS = 120_000L
        // The dock's tab labels (body_nav_* strings), in order.
        val TABS = listOf("Today", "Nutrition", "Ask Dash", "Workouts", "More")
    }
}

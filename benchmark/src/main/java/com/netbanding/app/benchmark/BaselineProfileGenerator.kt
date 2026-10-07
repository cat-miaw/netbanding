package com.netbanding.app.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Traces a real cold start of the release build and records which classes and
 * methods ART should AOT-compile. Shipping this file is the single biggest cold
 * start win available to an Android app: it typically removes 15-30% of launch
 * time and speeds up scroll/gesture handling in the first few minutes.
 *
 * Run it on a physical device with `gradle :app:generateBaselineProfile`, then
 * commit the generated app/src/main/baseline-prof.txt.
 */
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(packageName = PACKAGE_NAME) {
        pressHome()
        startActivityAndWait()

        device.wait(Until.hasObject(By.scrollable(true)), 5_000)

        // Exercise the scroll path too, so the profile covers jank-prone code
        // and not just app start.
        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(androidx.test.uiautomator.Direction.DOWN)
            device.waitForIdle()
        }

        pressHome()
    }

    private companion object {
        const val PACKAGE_NAME = "com.netbanding.app"
    }
}

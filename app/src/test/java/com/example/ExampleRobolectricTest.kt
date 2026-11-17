package com.example

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.apilens.ApiLens
import com.apilens.R as ApiLensR
import com.apilens.presentation.dashboard.ApiLensDashboardActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("API Lens", appName)
    }

    @Test
    fun `main activity attaches floating overlay and dashboard launches cleanly`() {
        assertTrue(ApiLens.isEnabled())

        val mainController = Robolectric.buildActivity(MainActivity::class.java).setup()
        val mainActivity = mainController.get()
        val overlayView = mainActivity.findViewById<View>(ApiLensR.id.api_lens_overlay_view)
        assertNotNull("Floating overlay should attach automatically on Activity resume", overlayView)

        val dashController = Robolectric.buildActivity(ApiLensDashboardActivity::class.java).setup()
        val dashActivity = dashController.get()
        assertNotNull(dashActivity)
    }
}

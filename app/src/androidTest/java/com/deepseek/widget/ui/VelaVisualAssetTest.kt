package com.deepseek.widget.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.deepseek.widget.R
import com.deepseek.widget.ui.components.VelaTitle
import com.deepseek.widget.ui.components.VelaTitleRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VelaVisualAssetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun titleAssetsUseLockedCanvases() {
        VelaTitle.entries.forEach { title ->
            val bitmap = BitmapFactory.decodeResource(context.resources, title.drawableRes)
            assertNotNull(title.name, bitmap)
            val expected = if (title.role == VelaTitleRole.PAGE) 1200 to 240 else 1200 to 160
            assertEquals("${title.name} width", expected.first, bitmap.width)
            assertEquals("${title.name} height", expected.second, bitmap.height)
        }
    }

    @Test
    fun entryAssetsKeepAuthoredPixelDimensions() {
        val expected = mapOf(
            R.drawable.vela_entry_top to (1080 to 1550),
            R.drawable.vela_entry_band to (1080 to 290),
            R.drawable.vela_entry_footer to (1080 to 560)
        )
        expected.forEach { (resource, dimensions) ->
            val bitmap = BitmapFactory.decodeResource(context.resources, resource)
            assertEquals(dimensions.first, bitmap.width)
            assertEquals(dimensions.second, bitmap.height)
        }
    }
}

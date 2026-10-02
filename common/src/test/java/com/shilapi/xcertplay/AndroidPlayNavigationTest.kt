package com.shilapi.xcertplay

import android.content.res.Configuration
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPlayNavigationTest {
    @Test fun backGlyphAndLabelStayCenteredAtDifferentFontSizes() {
        val application = RuntimeEnvironment.getApplication()
        for (scale in listOf(1f, 1.3f, 2f)) {
            val context = application.createConfigurationContext(Configuration(application.resources.configuration).apply { fontScale = scale })
            for (title in listOf("返回", "Back")) {
                var returned = false
                val back = AndroidPlayNavigation.back(context, title, title) { returned = true }
                back.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                back.layout(0, 0, back.measuredWidth, back.measuredHeight)
                val glyph = back.getChildAt(0)
                val label = back.getChildAt(1)
                assertEquals((glyph.top + glyph.bottom) / 2f, (label.top + label.bottom) / 2f, 1f)
                assertTrue(back.measuredHeight >= 48 * context.resources.displayMetrics.density)
                assertTrue(label.top >= 0 && label.bottom <= back.measuredHeight)
                back.performClick()
                assertTrue(returned)
            }
        }
    }
    @Test fun settingsPagesRenderAndVersionRowsAreReadOnly() {
        val application = RuntimeEnvironment.getApplication()
        application.getSharedPreferences("androidplay_updates", 0).edit().putBoolean("automatic", false).commit()
        application.getSharedPreferences("androidplay", 0).edit().putString("language", "zh-Hans").commit()
        val controller = org.robolectric.Robolectric.buildActivity(AndroidPlayActivity::class.java).create()
        val activity = controller.get()
        val show = AndroidPlayActivity::class.java.getDeclaredMethod("showPage", String::class.java).apply { isAccessible = true }
        fun texts(view: View): List<android.widget.TextView> = buildList {
            if (view is android.widget.TextView) add(view)
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount) addAll(texts(view.getChildAt(i)))
        }
        for (page in listOf("settings", "language", "orientation", "fps", "sound", "input", "output", "about")) {
            show.invoke(activity, page)
            val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            content.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY))
            content.layout(0, 0, content.measuredWidth, content.measuredHeight)
            assertTrue(texts(content).any { it.text.toString() == "返回" })
            if (page == "about") {
                val labels = texts(content)
                val version = labels.first { it.text.toString() == "当前版本" }
                assertFalse((version.parent.parent as View).isClickable)
                assertFalse(labels.any { it.text.contains("每天检查一次GitHub") })
            }
        }
        controller.destroy()
    }

}

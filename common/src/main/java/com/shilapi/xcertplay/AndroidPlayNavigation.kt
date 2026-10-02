// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.shilapi.xcertplay.host.R

/** Font-independent navigation glyphs share a vertically centered touch target. */
internal object AndroidPlayNavigation {
    fun back(context: Context, title: String, description: String, action: () -> Unit): LinearLayout {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isBaselineAligned = false
            minimumHeight = dp(48)
            setPadding(dp(4), dp(4), dp(20), dp(4))
            contentDescription = description
            isClickable = true; isFocusable = true
            val ripple = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
            background = ripple.getDrawable(0); ripple.recycle()
            setOnClickListener { action() }
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_androidplay_chevron_left)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
            addView(TextView(context).apply {
                text = title; textSize = 17f; includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL; maxLines = 1
                setTextColor(Color.rgb(110, 157, 230))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        }
    }
}

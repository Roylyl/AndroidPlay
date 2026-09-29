package com.shilapi.xcertplay.airplay

import kotlin.math.roundToInt

/** Like upstream CarPlaySize, change reported physical size, not video or touch pixels. */
object CarPlayUiScale {
    const val DEFAULT = 100
    val presets = listOf(75, DEFAULT, 125, 150)
    fun sanitize(percent: Int): Int = percent.takeIf { it in presets } ?: DEFAULT
    fun label(percent: Int): String = "${sanitize(percent)}%"

    fun apply(display: AirPlayDisplayConfig, percent: Int): AirPlayDisplayConfig {
        val scale = sanitize(percent)
        if (scale == DEFAULT) return display
        require(display.widthPixels > 0 && display.heightPixels > 0)
        val baseWidth = display.widthPhysicalMm ?: CarPlaySize.DEFAULT.widthMillimeters
        val baseHeight = display.heightPhysicalMm
            ?: (baseWidth * display.heightPixels.toDouble() / display.widthPixels).roundToInt()
        fun millimeters(value: Int): Int = AirPlayDisplaySettings.sanitizeReportedPhysicalMm(
            (value * 100.0 / scale).roundToInt(),
        )
        return display.copy(widthPhysicalMm = millimeters(baseWidth), heightPhysicalMm = millimeters(baseHeight))
    }
}

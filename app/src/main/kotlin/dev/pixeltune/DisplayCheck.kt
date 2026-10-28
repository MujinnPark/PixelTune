package dev.pixeltune

/** Reads the refresh rate the display reports, so a refresh-rate tweak can be compared with what the panel does. */
object DisplayCheck {
    val IDS = setOf("hz", "hz60", "hzmi")
    private val RATE = Regex("renderFrameRate ([0-9]+(?:\\.[0-9]+)?)")

    /** Hz from DisplayDeviceInfo (Android 14 format), or null if this ROM does not print it. Informational only. */
    fun activeHz(): Int? {
        Thread.sleep(1500) // let the display settle after the settings change
        val out = Shell.run("dumpsys display | grep -m1 -o 'renderFrameRate [0-9.]*'")
        return RATE.find(out)?.groupValues?.get(1)?.toFloatOrNull()?.let { Math.round(it) }
    }
}

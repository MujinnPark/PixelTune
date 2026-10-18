package dev.pixeltune

import android.os.Build

/** Coarse OEM family. Values match the "vendors" field in packs.json. */
object Vendor {
    /** google | samsung | xiaomi | oplus (realme, OPPO, OnePlus) | other */
    fun detect(): String {
        val s = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
        return when {
            "samsung" in s -> "samsung"
            "xiaomi" in s || "redmi" in s || "poco" in s -> "xiaomi"
            "realme" in s || "oppo" in s || "oneplus" in s -> "oplus"
            "google" in s -> "google"
            else -> "other"
        }
    }
}

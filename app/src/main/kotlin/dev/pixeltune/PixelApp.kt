package dev.pixeltune

import android.app.Application
import rikka.shizuku.Shizuku

class PixelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Shizuku wakes this process when it starts; the sticky listener also fires at once if it is already running.
        Shizuku.addBinderReceivedListenerSticky { AutoReset.runIfNeeded(this) }
    }
}

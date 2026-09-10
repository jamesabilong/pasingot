package app.personal.workouttracker.weardata

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/** Services retain data on disk; this signal lets an open WebView read it immediately. */
internal object WatchDataUpdates {
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }
    fun notifyChanged() {
        mainHandler.post { listeners.forEach { it() } }
    }
}

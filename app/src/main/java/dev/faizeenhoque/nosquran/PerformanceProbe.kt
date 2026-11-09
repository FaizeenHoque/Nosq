package dev.faizeenhoque.nosquran

import android.app.Activity
import android.os.*
import android.util.Log
import android.view.FrameMetrics
import android.view.Window

/** Benchmark-only counters. No callbacks, logging or allocation sampling in production. */
class PerformanceProbe(private val activity: Activity) {
    private val thread = HandlerThread("NOS-frame-metrics").apply { start() }
    private val histogram = LongArray(1001)
    private var frames = 0L
    private var missed = 0L
    private var droppedCallbacks = 0L
    private val startAllocated =
        Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0L
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
        val deadline =
            if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE)
            else (1e9 / activity.windowManager.defaultDisplay.refreshRate).toLong()
        histogram[(duration / 1_000_000).toInt().coerceIn(0, 1000)]++
        frames++
        if (duration > deadline) missed++
        droppedCallbacks += dropped
    }

    init {
        activity.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
    }

    fun close() {
        activity.window.removeOnFrameMetricsAvailableListener(listener)
        Handler(thread.looper).post {
            val allocated =
                (Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0L) -
                    startAllocated
            Log.i(
                "NOS.Performance",
                "frames=$frames overDeadline=$missed droppedCallbacks=$droppedCallbacks allocationDeltaBytes=$allocated totalPssKb=${Debug.getPss()}",
            )
            thread.quitSafely()
        }
    }
}

package com.jarvis.assistant.core.research

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

class ResearchRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val watches = ResearchWatchStore.list(applicationContext)
        if (watches.isEmpty()) return Result.success()
        var refreshed = 0
        for (watch in watches) {
            try {
                val sources = ResearchEngine.search(watch.query)
                if (ResearchWatchStore.saveResults(applicationContext, watch.query, sources)) refreshed++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Continue with the next saved query; WorkManager retries if every query fails.
            }
        }
        return if (refreshed > 0 || watches.isEmpty()) Result.success() else Result.retry()
    }
}

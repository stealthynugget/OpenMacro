package dev.openmacro.core.engine.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.openmacro.core.engine.engine
import java.util.concurrent.TimeUnit

/** Periodic safety net that re-arms the time-trigger alarm (e.g. after OEM alarm purges). */
class EngineMaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        applicationContext.engine.rescheduleAlarms()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "engine_maintenance"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<EngineMaintenanceWorker>(6, TimeUnit.HOURS).build(),
            )
        }
    }
}

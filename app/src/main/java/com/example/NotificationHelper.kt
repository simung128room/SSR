package com.example

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object NotificationHelper {
    fun scheduleTaskReminder(context: Context, task: TaskEntity) {
        val workManager = WorkManager.getInstance(context)

        // Cancel existing work for this task if any
        workManager.cancelAllWorkByTag("task_${task.id}")

        if (task.isCompleted) return

        // Assuming deadlineMs is endTimeMs if we want to alert before deadline. 
        // For now, let's say we alert 15 minutes before the start time or end time.
        // Let's use endTimeMs as deadline. If not present, use startTimeMs.
        var targetTimeMs = task.endTimeMs ?: task.startTimeMs
        var alertOffsetMs = 15 * 60 * 1000L // 15 minutes before by default

        if (targetTimeMs == null && task.deadlineMs != null) {
            // If only deadlineMs is present, let's set the alarm to 9:00 AM of that day
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = task.deadlineMs
                set(java.util.Calendar.HOUR_OF_DAY, 9)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            targetTimeMs = cal.timeInMillis
            alertOffsetMs = 0L // Alert precisely at 9:00 AM on that day!
        }

        if (targetTimeMs != null) {
            val delayMs = targetTimeMs - System.currentTimeMillis() - alertOffsetMs // 15 minutes before or exactly at 9am
            
            if (delayMs > 0) {
                val data = Data.Builder()
                    .putString("task_title", task.title)
                    .putInt("task_id", task.id)
                    .build()

                val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .addTag("task_${task.id}")
                    .setInputData(data)
                    .build()

                workManager.enqueue(request)
            }
        }
    }
    
    fun cancelTaskReminder(context: Context, taskId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag("task_$taskId")
    }
}

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
        val targetTimeMs = task.endTimeMs ?: task.startTimeMs
        if (targetTimeMs != null) {
            val delayMs = targetTimeMs - System.currentTimeMillis() - 15 * 60 * 1000 // 15 minutes before
            
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

package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PriorityWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_COMPLETE_TASK) {
            // Protect against spoofing: ensure the intent is targeted to our package specifically
            if (intent.getPackage() != context.packageName) {
                return
            }
            val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
            if (taskId != -1) {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = DatabaseProvider.getDatabase(context.applicationContext)
                    val taskDao = db.taskDao()
                    val tasks = taskDao.getPendingTasks().first()
                    val task = tasks.find { it.id == taskId }
                    if (task != null) {
                        taskDao.updateTask(task.copy(isCompleted = true))
                        NotificationHelper.cancelTaskReminder(context.applicationContext, taskId)
                        
                        // Force update all widgets
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val thisAppWidgetComponentName = ComponentName(context.packageName, PriorityWidgetProvider::class.java.name)
                        val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidgetComponentName)
                        for (appWidgetId in appWidgetIds) {
                            updateAppWidget(context, appWidgetManager, appWidgetId)
                        }
                    }
                }
            }
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_priority)
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_task_name, pendingIntent)

        CoroutineScope(Dispatchers.IO).launch {
            val db = DatabaseProvider.getDatabase(context.applicationContext)
            val tasks = db.taskDao().getPendingTasks().first()
            
            // Priority formula matching recommend logic exactly
            val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            val energyStr = prefs.getString("user_energy", null)
            val currentEnergy = if (energyStr != null) {
                try { EnergyLevel.valueOf(energyStr) } catch(e: Exception) { EnergyLevel.MEDIUM }
            } else {
                EnergyLevel.MEDIUM
            }

            val task = tasks.maxByOrNull { t ->
                var score = 0.0

                if (t.isImportant) score += 1000

                if (t.deadlineMs != null) {
                    val timeRemaining = t.deadlineMs - System.currentTimeMillis()
                    if (timeRemaining < 0) {
                        score += 2000
                    } else if (timeRemaining < 24 * 60 * 60 * 1000) {
                        score += 1500
                    } else {
                        score += 500
                    }
                }

                score += when {
                    currentEnergy == EnergyLevel.LOW && t.energyRequired == EnergyLevel.HIGH -> -1000
                    currentEnergy == EnergyLevel.LOW && t.energyRequired == EnergyLevel.LOW -> +500
                    currentEnergy == EnergyLevel.HIGH && t.energyRequired == EnergyLevel.HIGH -> +500
                    currentEnergy == t.energyRequired -> +300
                    else -> 0
                }

                score
            }

            if (task != null) {
                views.setTextViewText(R.id.widget_task_name, task.title)
                views.setViewVisibility(R.id.widget_complete_btn, View.VISIBLE)
                
                // Set up pending intent for the complete button
                val completeIntent = Intent(context, PriorityWidgetProvider::class.java).apply {
                    action = ACTION_COMPLETE_TASK
                    putExtra(EXTRA_TASK_ID, task.id)
                    setPackage(context.packageName)
                }
                val completePendingIntent = PendingIntent.getBroadcast(
                    context,
                    task.id,
                    completeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_complete_btn, completePendingIntent)
            } else {
                views.setTextViewText(R.id.widget_task_name, "ไม่มีงานเร่งด่วน พักผ่อนได้ 🎉")
                views.setViewVisibility(R.id.widget_complete_btn, View.GONE)
            }
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        const val ACTION_COMPLETE_TASK = "com.example.ACTION_COMPLETE_TASK"
        const val EXTRA_TASK_ID = "com.example.EXTRA_TASK_ID"

        fun triggerUpdate(context: Context) {
            val intent = Intent(context, PriorityWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, PriorityWidgetProvider::class.java))
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}

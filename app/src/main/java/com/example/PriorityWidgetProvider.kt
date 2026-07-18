package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.room.Room
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

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_priority)
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_task_name, pendingIntent)

        CoroutineScope(Dispatchers.IO).launch {
            val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "priority-db").fallbackToDestructiveMigration().build()
            val tasks = db.taskDao().getPendingTasks().first()
            
            val task = tasks.maxByOrNull { 
                var score = 0
                if (it.isImportant) score += 10
                score
            }

            val taskName = task?.title ?: "ไม่มีงานเร่งด่วน พักผ่อนได้"
            views.setTextViewText(R.id.widget_task_name, taskName)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

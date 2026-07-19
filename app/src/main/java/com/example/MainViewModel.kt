package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import java.util.Calendar

data class DayModel(
    val timeMs: Long,
    val dayOfWeek: String,
    val dayOfMonth: String,
    val isToday: Boolean
)

data class UserProfile(
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val isLoggedIn: Boolean = false
)

data class UiState(
    val pendingTasks: List<TaskEntity> = emptyList(),
    val completedTasks: List<TaskEntity> = emptyList(),
    val recommendedTask: TaskEntity? = null,
    val userEnergy: EnergyLevel = EnergyLevel.MEDIUM,
    val selectedDateMs: Long = 0L,
    val days: List<DayModel> = emptyList(),
    val dailyBriefing: String = "",
    val isBriefingLoading: Boolean = false,
    val userProfile: UserProfile = UserProfile(null, null, null, false)
)

class MainViewModel(private val application: Application, private val repository: TaskRepository) : AndroidViewModel(application) {

    private val userEnergy = MutableStateFlow(
        try {
            val prefs = application.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
            val saved = prefs.getString("user_energy", null)
            if (saved != null) EnergyLevel.valueOf(saved) else EnergyLevel.MEDIUM
        } catch (e: Exception) {
            EnergyLevel.MEDIUM
        }
    )
    private val dailyBriefingText = MutableStateFlow("")
    private val isBriefingLoading = MutableStateFlow(false)
    private val userProfile = MutableStateFlow(loadUserProfile())
    
    private fun loadUserProfile(): UserProfile {
        val prefs = getApplication<Application>().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        if (isLoggedIn) {
            return UserProfile(
                displayName = prefs.getString("display_name", null),
                email = prefs.getString("email", null),
                photoUrl = prefs.getString("photo_url", null),
                isLoggedIn = true
            )
        }
        return UserProfile(null, null, null, false)
    }

    fun signInUser(displayName: String?, email: String?, photoUrl: String?) {
        val prefs = getApplication<Application>().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("is_logged_in", true)
            putString("display_name", displayName)
            putString("email", email)
            putString("photo_url", photoUrl)
            apply()
        }
        userProfile.value = UserProfile(displayName, email, photoUrl, true)
    }

    fun signOutUser() {
        val prefs = getApplication<Application>().getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("is_logged_in", false)
            remove("display_name")
            remove("email")
            remove("photo_url")
            apply()
        }
        userProfile.value = UserProfile(null, null, null, false)
    }
    
    private val startOfToday: Long
    init {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startOfToday = calendar.timeInMillis
    }
    private val selectedDateMs = MutableStateFlow(startOfToday)

    private fun generateDays(): List<DayModel> {
        val days = mutableListOf<DayModel>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = startOfToday
        cal.add(Calendar.DAY_OF_YEAR, -3)

        val dayNames = arrayOf("อา.", "จ.", "อ.", "พ.", "พฤ.", "ศ.", "ส.")

        for (i in 0..14) {
            days.add(
                DayModel(
                    timeMs = cal.timeInMillis,
                    dayOfWeek = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1],
                    dayOfMonth = cal.get(Calendar.DAY_OF_MONTH).toString(),
                    isToday = cal.timeInMillis == startOfToday
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return days
    }

    val uiState: StateFlow<UiState> = combine(
        repository.pendingTasks,
        repository.completedTasks,
        userEnergy,
        selectedDateMs,
        combine(dailyBriefingText, isBriefingLoading, userProfile) { text, loading, profile -> Triple(text, loading, profile) }
    ) { pending, completed, energy, dateMs, triple ->
        // For simplicity, we just filter tasks whose deadline is before or on selected date, 
        // or have no deadline. But to make the date selector useful, let's say tasks are assigned 
        // to a date if their deadline is on that date, OR they have no deadline (backlog).
        val tasksForDate = pending.filter { task ->
            if (task.deadlineMs == null) true
            else {
                val cal = Calendar.getInstance()
                cal.timeInMillis = task.deadlineMs
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis <= dateMs
            }
        }
        
        UiState(
            pendingTasks = tasksForDate,
            completedTasks = completed,
            recommendedTask = calculateRecommendedTask(tasksForDate, energy),
            userEnergy = energy,
            selectedDateMs = dateMs,
            days = generateDays(),
            dailyBriefing = triple.first,
            isBriefingLoading = triple.second,
            userProfile = triple.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    fun setEnergy(level: EnergyLevel) {
        userEnergy.value = level
        val prefs = application.getSharedPreferences("user_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("user_energy", level.name).apply()
        PriorityWidgetProvider.triggerUpdate(application)
    }

    fun setSelectedDate(ms: Long) {
        selectedDateMs.value = ms
    }

    fun addTask(title: String, isImportant: Boolean, energyRequired: EnergyLevel, category: String, startTimeMs: Long?, endTimeMs: Long?, deadlineMs: Long?, imagePath: String?) {
        viewModelScope.launch {
            val task = TaskEntity(
                title = title,
                isImportant = isImportant,
                energyRequired = energyRequired,
                category = category,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                deadlineMs = deadlineMs,
                imagePath = imagePath
            )
            val id = repository.addTask(task)
            NotificationHelper.scheduleTaskReminder(getApplication(), task.copy(id = id.toInt()))
            PriorityWidgetProvider.triggerUpdate(getApplication())
        }
    }

    fun editTask(task: TaskEntity, title: String, isImportant: Boolean, energyRequired: EnergyLevel, category: String, startTimeMs: Long?, endTimeMs: Long?, deadlineMs: Long?, imagePath: String?) {
        viewModelScope.launch {
            val updatedTask = task.copy(
                title = title,
                isImportant = isImportant,
                energyRequired = energyRequired,
                category = category,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                deadlineMs = deadlineMs,
                imagePath = imagePath
            )
            repository.updateTask(updatedTask)
            NotificationHelper.scheduleTaskReminder(getApplication(), updatedTask)
            PriorityWidgetProvider.triggerUpdate(getApplication())
        }
    }

    fun markTaskCompleted(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = true))
            NotificationHelper.cancelTaskReminder(getApplication(), task.id)
            PriorityWidgetProvider.triggerUpdate(getApplication())
        }
    }
    
    fun unmarkTaskCompleted(task: TaskEntity) {
        viewModelScope.launch {
            val updatedTask = task.copy(isCompleted = false)
            repository.updateTask(updatedTask)
            NotificationHelper.scheduleTaskReminder(getApplication(), updatedTask)
            PriorityWidgetProvider.triggerUpdate(getApplication())
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task.id)
            NotificationHelper.cancelTaskReminder(getApplication(), task.id)
            PriorityWidgetProvider.triggerUpdate(getApplication())
        }
    }

    fun restoreTask(task: TaskEntity) {
        viewModelScope.launch {
            repository.addTask(task)
            NotificationHelper.scheduleTaskReminder(getApplication(), task)
            PriorityWidgetProvider.triggerUpdate(getApplication())
        }
    }

    private fun calculateRecommendedTask(tasks: List<TaskEntity>, currentEnergy: EnergyLevel): TaskEntity? {
        if (tasks.isEmpty()) return null

        return tasks.maxByOrNull { task ->
            var score = 0.0

            if (task.isImportant) score += 1000

            if (task.deadlineMs != null) {
                val timeRemaining = task.deadlineMs - System.currentTimeMillis()
                if (timeRemaining < 0) {
                    score += 2000
                } else if (timeRemaining < 24 * 60 * 60 * 1000) {
                    score += 1500
                } else {
                    score += 500
                }
            }

            score += when {
                currentEnergy == EnergyLevel.LOW && task.energyRequired == EnergyLevel.HIGH -> -1000
                currentEnergy == EnergyLevel.LOW && task.energyRequired == EnergyLevel.LOW -> +500
                currentEnergy == EnergyLevel.HIGH && task.energyRequired == EnergyLevel.HIGH -> +500
                currentEnergy == task.energyRequired -> +300
                else -> 0
            }

            score
        }
    }

    fun fetchDailyBriefing(tasks: List<TaskEntity>) {
        viewModelScope.launch {
            isBriefingLoading.value = true
            try {
                val briefing = GeminiService.getDailyBriefing(tasks, userEnergy.value)
                dailyBriefingText.value = briefing
            } catch (e: Exception) {
                dailyBriefingText.value = "ไม่สามารถเชื่อมต่อผู้ช่วย AI ได้: ${e.message}"
            } finally {
                isBriefingLoading.value = false
            }
        }
    }

    fun suggestTaskMetadata(title: String, onResult: (AutoSuggestResult?) -> Unit) {
        viewModelScope.launch {
            val result = GeminiService.autoSuggestTask(title)
            onResult(result)
        }
    }

    fun breakdownTask(title: String, deadlineMs: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val subtasks = GeminiService.breakdownTask(title)
            if (subtasks != null && subtasks.isNotEmpty()) {
                var currentStartMs = deadlineMs + 9 * 60 * 60 * 1000 // Start at 9:00 AM on selected date
                for (subtask in subtasks) {
                    val energy = try {
                        EnergyLevel.valueOf(subtask.energyRequired.uppercase())
                    } catch (e: Exception) {
                        EnergyLevel.MEDIUM
                    }
                    val endMs = currentStartMs + subtask.durationMinutes * 60 * 1000
                    
                    val task = TaskEntity(
                        title = subtask.title,
                        isImportant = false,
                        energyRequired = energy,
                        category = subtask.category,
                        startTimeMs = currentStartMs,
                        endTimeMs = endMs,
                        deadlineMs = deadlineMs
                    )
                    val id = repository.addTask(task)
                    NotificationHelper.scheduleTaskReminder(getApplication(), task.copy(id = id.toInt()))
                    
                    currentStartMs = endMs + 10 * 60 * 1000 // 10 minutes break
                }
                PriorityWidgetProvider.triggerUpdate(getApplication())
                onResult(true)
            } else {
                onResult(false)
            }
        }
    }
}

class MainViewModelFactory(private val application: Application, private val repository: TaskRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

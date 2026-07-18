package com.example

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

data class UiState(
    val pendingTasks: List<TaskEntity> = emptyList(),
    val completedTasks: List<TaskEntity> = emptyList(),
    val recommendedTask: TaskEntity? = null,
    val userEnergy: EnergyLevel = EnergyLevel.MEDIUM,
    val selectedDateMs: Long = 0L,
    val days: List<DayModel> = emptyList()
)

class MainViewModel(private val repository: TaskRepository) : ViewModel() {

    private val userEnergy = MutableStateFlow(EnergyLevel.MEDIUM)
    
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
        selectedDateMs
    ) { pending, completed, energy, dateMs ->
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
            days = generateDays()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    fun setEnergy(level: EnergyLevel) {
        userEnergy.value = level
    }

    fun setSelectedDate(ms: Long) {
        selectedDateMs.value = ms
    }

    fun addTask(title: String, isImportant: Boolean, energyRequired: EnergyLevel, category: String, startTimeMs: Long?, endTimeMs: Long?, deadlineMs: Long?) {
        viewModelScope.launch {
            repository.addTask(
                TaskEntity(
                    title = title,
                    isImportant = isImportant,
                    energyRequired = energyRequired,
                    category = category,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    deadlineMs = deadlineMs
                )
            )
        }
    }

    fun markTaskCompleted(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = true))
        }
    }
    
    fun unmarkTaskCompleted(task: TaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = false))
        }
    }

    fun deleteTask(id: Int) {
        viewModelScope.launch {
            repository.deleteTask(id)
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
}

class MainViewModelFactory(private val repository: TaskRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

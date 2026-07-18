package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.ui.theme.PriorityTheme
import kotlinx.coroutines.launch

fun EnergyLevel.toThaiString(): String = when(this) {
    EnergyLevel.LOW -> "น้อย"
    EnergyLevel.MEDIUM -> "ปานกลาง"
    EnergyLevel.HIGH -> "มาก"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "priority-db"
        ).fallbackToDestructiveMigration().build()
        
        val repository = TaskRepository(database.taskDao())
        val factory = MainViewModelFactory(repository)

        setContent {
            PriorityTheme {
                val viewModel: MainViewModel = viewModel(factory = factory)
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                
                var showAddTaskDialog by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showAddTaskDialog = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Task")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 24.dp)
                    ) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "ลำดับความสำคัญ",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        DaySelector(
                            days = uiState.days,
                            selectedDateMs = uiState.selectedDateMs,
                            onDaySelected = { viewModel.setSelectedDate(it) }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        EnergySelector(
                            currentEnergy = uiState.userEnergy,
                            onEnergySelected = { viewModel.setEnergy(it) }
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        if (uiState.recommendedTask != null) {
                            Text(
                                text = "ควรทำสิ่งนี้ตอนนี้",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            RecommendedTaskCard(
                                task = uiState.recommendedTask!!,
                                onComplete = { viewModel.markTaskCompleted(it) }
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                        }

                        Text(
                            text = "งานค้างทั้งหมด",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (uiState.pendingTasks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "เคลียร์หมดแล้ว พักผ่อนให้เต็มที่",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                val remainingTasks = uiState.pendingTasks.filter { it.id != uiState.recommendedTask?.id }
                                val scheduledTasks = remainingTasks.filter { it.startTimeMs != null }.sortedBy { it.startTimeMs }
                                val backlogTasks = remainingTasks.filter { it.startTimeMs == null }

                                if (scheduledTasks.isNotEmpty()) {
                                    item {
                                        Text(
                                            "ตารางเวลา",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                    items(
                                        items = scheduledTasks,
                                        key = { it.id }
                                    ) { task ->
                                        BacklogTaskItem(
                                            task = task,
                                            onComplete = { viewModel.markTaskCompleted(it) },
                                            onDelete = { viewModel.deleteTask(it.id) }
                                        )
                                    }
                                }

                                if (backlogTasks.isNotEmpty()) {
                                    item {
                                        Text(
                                            "งานค้าง",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(top = 16.dp)
                                        )
                                    }
                                    items(
                                        items = backlogTasks,
                                        key = { it.id }
                                    ) { task ->
                                        BacklogTaskItem(
                                            task = task,
                                            onComplete = { viewModel.markTaskCompleted(it) },
                                            onDelete = { viewModel.deleteTask(it.id) }
                                        )
                                    }
                                }

                                item { Spacer(modifier = Modifier.height(80.dp)) }
                            }
                        }
                    }
                }

                if (showAddTaskDialog) {
                    AddTaskDialog(
                        onDismiss = { showAddTaskDialog = false },
                        onAddTask = { title, isImportant, energy, category, startMs, endMs ->
                            viewModel.addTask(title, isImportant, energy, category, startMs, endMs, uiState.selectedDateMs)
                            showAddTaskDialog = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DaySelector(days: List<DayModel>, selectedDateMs: Long, onDaySelected: (Long) -> Unit) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    LaunchedEffect(days, selectedDateMs) {
        val index = days.indexOfFirst { it.timeMs == selectedDateMs }
        if (index != -1) {
            listState.animateScrollToItem(maxOf(0, index - 2))
        }
    }

    androidx.compose.foundation.lazy.LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days) { day ->
            val isSelected = day.timeMs == selectedDateMs
            val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            
            Column(
                modifier = Modifier
                    .width(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(containerColor)
                    .clickable { onDaySelected(day.timeMs) }
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = day.dayOfWeek,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = day.dayOfMonth,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = contentColor
                )
                if (day.isToday) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(contentColor)
                    )
                }
            }
        }
    }
}

@Composable
fun EnergySelector(currentEnergy: EnergyLevel, onEnergySelected: (EnergyLevel) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EnergyLevel.values().forEach { level ->
            val isSelected = currentEnergy == level
            FilterChip(
                selected = isSelected,
                onClick = { onEnergySelected(level) },
                label = { Text(level.toThaiString()) },
                leadingIcon = {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

@Composable
fun RecommendedTaskCard(task: TaskEntity, onComplete: (TaskEntity) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.isImportant) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Important",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "ใช้พลังงาน ${task.energyRequired.toThaiString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { onComplete(task) },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    contentColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text("ทำเสร็จแล้ว", modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
fun BacklogTaskItem(task: TaskEntity, onComplete: (TaskEntity) -> Unit, onDelete: (TaskEntity) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { /* Could open details */ }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onComplete(task) }) {
            Icon(
                imageVector = Icons.Outlined.Circle,
                contentDescription = "Complete",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.isImportant) {
                    Text(
                        text = "งานสำคัญ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "[${task.category}]",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = task.energyRequired.toThaiString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (task.startTimeMs != null && task.endTimeMs != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format("%02d:%02d - %02d:%02d", 
                            (task.startTimeMs / 3600000L), (task.startTimeMs % 3600000L) / 60000L,
                            (task.endTimeMs / 3600000L), (task.endTimeMs % 3600000L) / 60000L),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onAddTask: (String, Boolean, EnergyLevel, String, Long?, Long?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var isImportant by remember { mutableStateOf(false) }
    var energyRequired by remember { mutableStateOf(EnergyLevel.MEDIUM) }
    var category by remember { mutableStateOf("ทั่วไป") }
    val categories = listOf("ทั่วไป", "สอบ", "เรียน", "เนื้อหา", "อ่านหนังสือ")
    
    var hasTime by remember { mutableStateOf(false) }
    var startHour by remember { mutableStateOf(9) }
    var startMinute by remember { mutableStateOf(0) }
    var endHour by remember { mutableStateOf(10) }
    var endMinute by remember { mutableStateOf(0) }
    
    val context = androidx.compose.ui.platform.LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "เพิ่มงานใหม่",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("รายละเอียดงาน (หรือวิชาที่ค้าง)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isImportant, onCheckedChange = { isImportant = it })
                Text("เป็นงานสำคัญ")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("หมวดหมู่", style = MaterialTheme.typography.labelMedium)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories.size) { index ->
                    val cat = categories[index]
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("พลังงานที่ใช้", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnergyLevel.values().forEach { level ->
                    FilterChip(
                        selected = energyRequired == level,
                        onClick = { energyRequired = level },
                        label = { Text(level.toThaiString()) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hasTime, onCheckedChange = { hasTime = it })
                Text("จัดลงตารางเวลา (วันนี้)")
            }
            if (hasTime) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            android.app.TimePickerDialog(context, { _, h, m ->
                                startHour = h
                                startMinute = m
                            }, startHour, startMinute, true).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(String.format("เริ่ม: %02d:%02d", startHour, startMinute))
                    }
                    Text("-")
                    Button(
                        onClick = {
                            android.app.TimePickerDialog(context, { _, h, m ->
                                endHour = h
                                endMinute = m
                            }, endHour, endMinute, true).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(String.format("จบ: %02d:%02d", endHour, endMinute))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { 
                    if (title.isNotBlank()) {
                        val startMs = if (hasTime) startHour * 3600000L + startMinute * 60000L else null
                        val endMs = if (hasTime) endHour * 3600000L + endMinute * 60000L else null
                        onAddTask(title, isImportant, energyRequired, category, startMs, endMs) 
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank()
            ) {
                Text("เพิ่มงาน")
            }
        }
    }
}

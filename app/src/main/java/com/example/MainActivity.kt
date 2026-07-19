package com.example

import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import android.widget.Toast
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
import com.example.ui.theme.PriorityTheme

fun EnergyLevel.toThaiString(): String = when(this) {
    EnergyLevel.LOW -> "น้อย"
    EnergyLevel.MEDIUM -> "ปานกลาง"
    EnergyLevel.HIGH -> "มาก"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = DatabaseProvider.getDatabase(applicationContext)
        
        val repository = TaskRepository(database.taskDao())
        val factory = MainViewModelFactory(application, repository)
        setContent {
            PriorityTheme {
                val viewModel: MainViewModel = viewModel(factory = factory)
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        // Handle if needed
                    }
                )

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val snackbarHostState = remember { SnackbarHostState() }
                val coroutineScope = rememberCoroutineScope()
                var showAddTaskDialog by remember { mutableStateOf(false) }
                var taskToEdit by remember { mutableStateOf<TaskEntity?>(null) }
                var activeViewerImagePath by remember { mutableStateOf<String?>(null) }
                var taskToAnalyze by remember { mutableStateOf<TaskEntity?>(null) }
                var showAuthErrorDialog by remember { mutableStateOf(false) }
                var showProfileDialog by remember { mutableStateOf(false) }
                val context = androidx.compose.ui.platform.LocalContext.current

                val gso = remember {
                    val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestProfile()
                    
                    val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                    if (webClientId.isNotEmpty()) {
                        builder.requestIdToken(webClientId)
                    }
                    builder.build()
                }
                val googleSignInClient = remember {
                    try {
                        GoogleSignIn.getClient(context, gso)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }

                val googleSignInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)
                        if (account != null) {
                            val name = account.displayName ?: "ผู้ใช้นิรนาม"
                            val email = account.email ?: ""
                            val photoUrl = account.photoUrl?.toString()
                            viewModel.signInUser(name, email, photoUrl)
                            Toast.makeText(context, "เข้าสู่ระบบสำเร็จ: สวัสดีครับ $name", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showAuthErrorDialog = true
                    }
                }

                val handleComplete: (TaskEntity) -> Unit = { task ->
                    viewModel.markTaskCompleted(task)
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "ทำเสร็จแล้ว: ${task.title}",
                            actionLabel = "เลิกทำ"
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.unmarkTaskCompleted(task)
                        }
                    }
                }

                val handleDelete: (TaskEntity) -> Unit = { task ->
                    viewModel.deleteTask(task)
                    coroutineScope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = "ลบงาน: ${task.title}",
                            actionLabel = "เลิกทำ"
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.restoreTask(task)
                        }
                    }
                }

                var activeTab by remember { mutableStateOf(0) }
                LaunchedEffect(uiState.userEnergy, uiState.selectedDateMs, uiState.pendingTasks.size) {
                    if (uiState.pendingTasks.isNotEmpty()) {
                        viewModel.fetchDailyBriefing(uiState.pendingTasks)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { 
                                taskToEdit = null
                                showAddTaskDialog = true 
                            },
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    // Use a safe approach to load the image
                                    Image(
                                        painter = painterResource(id = R.drawable.app_icon_image),
                                        contentDescription = "SkLife Logo",
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "SkLife",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            
                            if (uiState.userProfile.isLoggedIn) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { showProfileDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (uiState.userProfile.photoUrl != null) {
                                        coil.compose.AsyncImage(
                                            model = uiState.userProfile.photoUrl,
                                            contentDescription = "Profile Photo",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        val initials = uiState.userProfile.displayName?.firstOrNull()?.toString() ?: "U"
                                        Text(
                                            text = initials.uppercase(),
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        try {
                                            val signInIntent = googleSignInClient?.signInIntent
                                            if (signInIntent != null) {
                                                googleSignInLauncher.launch(signInIntent)
                                            } else {
                                                showAuthErrorDialog = true
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            showAuthErrorDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Google Sign-In",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "ลงชื่อเข้าใช้", 
                                        style = MaterialTheme.typography.bodyMedium, 
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        TabRow(
                            selectedTabIndex = activeTab,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Tab(
                                selected = activeTab == 0,
                                onClick = { activeTab = 0 },
                                text = { Text("ตารางงาน", fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.DateRange, contentDescription = "ตารางงาน") }
                            )
                            Tab(
                                selected = activeTab == 1,
                                onClick = { activeTab = 1 },
                                text = { Text("สถิติวิเคราะห์", fontWeight = FontWeight.Bold) },
                                icon = { Icon(Icons.Default.List, contentDescription = "สถิติวิเคราะห์") }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (activeTab == 0) {
                            DaySelector(
                                days = uiState.days,
                                selectedDateMs = uiState.selectedDateMs,
                                onDaySelected = { viewModel.setSelectedDate(it) }
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            EnergySelector(
                                currentEnergy = uiState.userEnergy,
                                onEnergySelected = { viewModel.setEnergy(it) }
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            // Daily Briefing
                            DailyBriefingCard(
                                briefing = uiState.dailyBriefing,
                                isLoading = uiState.isBriefingLoading,
                                onRefresh = { viewModel.fetchDailyBriefing(uiState.pendingTasks) }
                            )
                            
                            Spacer(modifier = Modifier.height(20.dp))
                            
                            val recTask = uiState.recommendedTask
                            if (recTask != null) {
                                Text(
                                    text = "ควรทำสิ่งนี้ตอนนี้ ✨",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                RecommendedTaskCard(
                                    task = recTask,
                                    onComplete = handleComplete,
                                    onClick = {
                                        taskToEdit = recTask
                                        showAddTaskDialog = true
                                    },
                                    onImageClick = {
                                        activeViewerImagePath = recTask.imagePath
                                    },
                                    onAnalyzeClick = {
                                        taskToAnalyze = recTask
                                    }
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            Text(
                                text = "งานสะสมทั้งหมด 📝",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            if (uiState.pendingTasks.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "เคลียร์หมดแล้ว พักผ่อนให้เต็มที่ 🎉",
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
                                    
                                    val selectedCal = java.util.Calendar.getInstance().apply {
                                        timeInMillis = uiState.selectedDateMs
                                        set(java.util.Calendar.HOUR_OF_DAY, 0)
                                        set(java.util.Calendar.MINUTE, 0)
                                        set(java.util.Calendar.SECOND, 0)
                                        set(java.util.Calendar.MILLISECOND, 0)
                                    }
                                    val selectedDayMidnight = selectedCal.timeInMillis

                                    val isOverdue = { task: TaskEntity ->
                                        if (task.deadlineMs == null) false
                                        else {
                                            val tCal = java.util.Calendar.getInstance().apply {
                                                timeInMillis = task.deadlineMs
                                                set(java.util.Calendar.HOUR_OF_DAY, 0)
                                                set(java.util.Calendar.MINUTE, 0)
                                                set(java.util.Calendar.SECOND, 0)
                                                set(java.util.Calendar.MILLISECOND, 0)
                                            }
                                            tCal.timeInMillis < selectedDayMidnight
                                        }
                                    }

                                    val overdueTasks = remainingTasks.filter { isOverdue(it) }
                                    val currentDayTasks = remainingTasks.filter { !isOverdue(it) }

                                    val scheduledTasks = currentDayTasks.filter { it.startTimeMs != null }.sortedBy { it.startTimeMs }
                                    val backlogTasks = currentDayTasks.filter { it.startTimeMs == null }

                                    if (overdueTasks.isNotEmpty()) {
                                        item {
                                            Text(
                                                "งานค้างจากวันก่อน ⚠️",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.padding(top = 8.dp)
                                            )
                                        }
                                        items(
                                            items = overdueTasks,
                                            key = { it.id }
                                        ) { task ->
                                            BacklogTaskItem(
                                                task = task,
                                                modifier = Modifier.animateItem(),
                                                onClick = {
                                                    taskToEdit = task
                                                    showAddTaskDialog = true
                                                },
                                                onComplete = handleComplete,
                                                onDelete = handleDelete,
                                                onImageClick = {
                                                    activeViewerImagePath = task.imagePath
                                                },
                                                onAnalyzeClick = {
                                                    taskToAnalyze = task
                                                }
                                            )
                                        }
                                    }

                                    if (scheduledTasks.isNotEmpty()) {
                                        item {
                                            Text(
                                                "ตารางเวลาวันนี้ ⏰",
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
                                                modifier = Modifier.animateItem(),
                                                onClick = {
                                                    taskToEdit = task
                                                    showAddTaskDialog = true
                                                },
                                                onComplete = handleComplete,
                                                onDelete = handleDelete,
                                                onImageClick = {
                                                    activeViewerImagePath = task.imagePath
                                                },
                                                onAnalyzeClick = {
                                                    taskToAnalyze = task
                                                }
                                            )
                                        }
                                    }

                                    if (backlogTasks.isNotEmpty()) {
                                        item {
                                            Text(
                                                "งานประจำวัน 📌",
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
                                                modifier = Modifier.animateItem(),
                                                onClick = {
                                                    taskToEdit = task
                                                    showAddTaskDialog = true
                                                },
                                                onComplete = handleComplete,
                                                onDelete = handleDelete,
                                                onImageClick = {
                                                    activeViewerImagePath = task.imagePath
                                                },
                                                onAnalyzeClick = {
                                                    taskToAnalyze = task
                                                }
                                            )
                                        }
                                    }

                                    if (uiState.completedTasks.isNotEmpty()) {
                                        item {
                                            Text(
                                                "งานที่ทำเสร็จแล้ว ✅ (${uiState.completedTasks.size})",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(top = 24.dp)
                                            )
                                        }
                                        items(
                                            items = uiState.completedTasks,
                                            key = { it.id }
                                        ) { task ->
                                            BacklogTaskItem(
                                                task = task,
                                                modifier = Modifier.animateItem().alpha(0.6f),
                                                onClick = {
                                                    taskToEdit = task
                                                    showAddTaskDialog = true
                                                },
                                                onComplete = {
                                                    viewModel.unmarkTaskCompleted(task)
                                                },
                                                onDelete = handleDelete,
                                                onImageClick = {
                                                    activeViewerImagePath = task.imagePath
                                                },
                                                onAnalyzeClick = {
                                                    taskToAnalyze = task
                                                }
                                            )
                                        }
                                    }

                                    item { Spacer(modifier = Modifier.height(80.dp)) }
                                }
                            }
                        } else {
                            // Insights Tab
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    InsightsDashboard(
                                        completedTasks = uiState.completedTasks,
                                        pendingTasks = uiState.pendingTasks
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(80.dp)) }
                            }
                        }
                    }
                }

                if (showAddTaskDialog) {
                    TaskDialog(
                        selectedDateMs = uiState.selectedDateMs,
                        editingTask = taskToEdit,
                        onDismiss = { 
                            showAddTaskDialog = false 
                            taskToEdit = null
                        },
                        onSaveTask = { title, isImportant, energy, category, startMs, endMs, deadlineMs, imgPath ->
                            if (taskToEdit != null) {
                                viewModel.editTask(taskToEdit!!, title, isImportant, energy, category, startMs, endMs, deadlineMs, imgPath)
                            } else {
                                viewModel.addTask(title, isImportant, energy, category, startMs, endMs, deadlineMs, imgPath)
                            }
                            showAddTaskDialog = false
                            taskToEdit = null
                        },
                        onBreakdownTask = { mainTitle, onFinished ->
                            viewModel.breakdownTask(mainTitle, uiState.selectedDateMs) { success ->
                                onFinished(success)
                                if (success) {
                                    android.widget.Toast.makeText(context, "แตกงานย่อยด้วย AI สำเร็จแล้ว ✨", android.widget.Toast.LENGTH_LONG).show()
                                    showAddTaskDialog = false
                                    taskToEdit = null
                                } else {
                                    android.widget.Toast.makeText(context, "AI แตกงานย่อยไม่สำเร็จ กรุณาลองใหม่อีกครั้ง", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }

                activeViewerImagePath?.let { path ->
                    ImageViewerDialog(
                        imagePath = path,
                        onDismiss = { activeViewerImagePath = null }
                    )
                }

                taskToAnalyze?.let { task ->
                    TaskAnalysisDialog(
                        task = task,
                        onDismiss = { taskToAnalyze = null }
                    )
                }

                if (showProfileDialog) {
                    UserProfileDialog(
                        userProfile = uiState.userProfile,
                        onSignOut = {
                            viewModel.signOutUser()
                            try {
                                googleSignInClient?.signOut()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            showProfileDialog = false
                            Toast.makeText(context, "ออกจากระบบแล้ว", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { showProfileDialog = false }
                    )
                }

                if (showAuthErrorDialog) {
                    AuthErrorDialog(
                        onBypass = {
                            viewModel.signInUser("สมชาย ตั้งใจเรียน", "somchai.dev@gmail.com", null)
                            showAuthErrorDialog = false
                            Toast.makeText(context, "เข้าสู่ระบบจำลองสำเร็จ", Toast.LENGTH_SHORT).show()
                        },
                        onDismiss = { showAuthErrorDialog = false }
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
            val icon = when (level) {
                EnergyLevel.LOW -> Icons.Default.EnergySavingsLeaf
                EnergyLevel.MEDIUM -> Icons.Default.Info
                EnergyLevel.HIGH -> Icons.Default.Star
            }
            val tint = when (level) {
                EnergyLevel.LOW -> Color(0xFF4CAF50)
                EnergyLevel.MEDIUM -> Color(0xFFFF9800)
                EnergyLevel.HIGH -> Color(0xFFF44336)
            }
            FilterChip(
                selected = isSelected,
                onClick = { onEnergySelected(level) },
                label = { Text(level.toThaiString()) },
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else tint
                    )
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
fun RecommendedTaskCard(
    task: TaskEntity,
    onComplete: (TaskEntity) -> Unit,
    onClick: () -> Unit = {},
    onImageClick: (() -> Unit)? = null,
    onAnalyzeClick: (() -> Unit)? = null
) {
    val categoryIcon = when (task.category) {
        "สอบ" -> Icons.Default.Star
        "เรียน" -> Icons.Default.Info
        "เนื้อหา" -> Icons.Default.List
        "อ่านหนังสือ" -> Icons.Default.DateRange
        else -> Icons.Default.Home
    }

    val energyIcon = when (task.energyRequired) {
        EnergyLevel.LOW -> Icons.Default.EnergySavingsLeaf
        EnergyLevel.MEDIUM -> Icons.Default.Info
        EnergyLevel.HIGH -> Icons.Default.Star
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.isImportant) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Important",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Icon(
                        imageVector = energyIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ใช้พลังงาน ${task.energyRequired.toThaiString()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                // Category Badge using standard AssistChip-like design for consistency
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = task.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            
            if (task.imagePath != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.05f))
                        .clickable { onImageClick?.invoke() }
                ) {
                    coil.compose.AsyncImage(
                        model = task.imagePath,
                        contentDescription = "ดูรูปประกอบ",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "แตะเพื่อดูทบทวน",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            if (task.imagePath != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onComplete(task) },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("เสร็จแล้ว", fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { onAnalyzeClick?.invoke() },
                        modifier = Modifier.weight(1f),
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "ดูเฉลยด้วย AI",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ดูเฉลยด้วย AI", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = { onComplete(task) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        contentColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ทำเสร็จแล้ว", modifier = Modifier.padding(vertical = 4.dp), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun BacklogTaskItem(
    task: TaskEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onComplete: (TaskEntity) -> Unit,
    onDelete: (TaskEntity) -> Unit,
    onImageClick: (() -> Unit)? = null,
    onAnalyzeClick: (() -> Unit)? = null
) {
    val categoryIcon = when (task.category) {
        "สอบ" -> Icons.Default.Star
        "เรียน" -> Icons.Default.Info
        "เนื้อหา" -> Icons.Default.List
        "อ่านหนังสือ" -> Icons.Default.DateRange
        else -> Icons.Default.Home
    }

    val energyIcon = when (task.energyRequired) {
        EnergyLevel.LOW -> Icons.Default.EnergySavingsLeaf
        EnergyLevel.MEDIUM -> Icons.Default.Info
        EnergyLevel.HIGH -> Icons.Default.Star
    }

    val energyColor = when (task.energyRequired) {
        EnergyLevel.LOW -> Color(0xFF4CAF50)
        EnergyLevel.MEDIUM -> Color(0xFFFF9800)
        EnergyLevel.HIGH -> Color(0xFFF44336)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onComplete(task) }) {
            Icon(
                imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (task.isCompleted) "Uncomplete" else "Complete",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.isImportant) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "สำคัญ",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category with Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = task.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Energy with Icon
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = energyIcon,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = energyColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = task.energyRequired.toThaiString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Time with Icon
                if (task.startTimeMs != null && task.endTimeMs != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "เวลา",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val startCal = java.util.Calendar.getInstance().apply { timeInMillis = task.startTimeMs!! }
                        val endCal = java.util.Calendar.getInstance().apply { timeInMillis = task.endTimeMs!! }
                        val timeStr = String.format(
                            "%02d:%02d - %02d:%02d",
                            startCal.get(java.util.Calendar.HOUR_OF_DAY),
                            startCal.get(java.util.Calendar.MINUTE),
                            endCal.get(java.util.Calendar.HOUR_OF_DAY),
                            endCal.get(java.util.Calendar.MINUTE)
                        )
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
        
        if (task.imagePath != null) {
            IconButton(onClick = { onAnalyzeClick?.invoke() }) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "ดูเฉลยด้วย AI",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onImageClick?.invoke() }
            ) {
                coil.compose.AsyncImage(
                    model = task.imagePath,
                    contentDescription = "ดูรูปประกอบ",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        IconButton(onClick = { onDelete(task) }) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "ลบงาน",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDialog(
    selectedDateMs: Long,
    editingTask: TaskEntity? = null,
    onDismiss: () -> Unit,
    onSaveTask: (String, Boolean, EnergyLevel, String, Long?, Long?, Long?, String?) -> Unit,
    onBreakdownTask: (String, (Boolean) -> Unit) -> Unit
) {
    var title by remember { mutableStateOf(editingTask?.title ?: "") }
    var isImportant by remember { mutableStateOf(editingTask?.isImportant ?: false) }
    var energyRequired by remember { mutableStateOf(editingTask?.energyRequired ?: EnergyLevel.MEDIUM) }
    var category by remember { mutableStateOf(editingTask?.category ?: "ทั่วไป") }
    val categories = listOf("ทั่วไป", "สอบ", "เรียน", "เนื้อหา", "อ่านหนังสือ")
    
    val initialHasTime = editingTask?.startTimeMs != null
    var hasTime by remember { mutableStateOf(initialHasTime) }
    
    var startHour by remember { mutableStateOf(9) }
    var startMinute by remember { mutableStateOf(0) }
    var endHour by remember { mutableStateOf(10) }
    var endMinute by remember { mutableStateOf(0) }

    var imagePath by remember { mutableStateOf(editingTask?.imagePath) }

    var isAiLoading by remember { mutableStateOf(false) }
    var isBreakdownLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var deadlineMs by remember { mutableStateOf(editingTask?.deadlineMs ?: selectedDateMs) }

    LaunchedEffect(editingTask) {
        if (editingTask?.startTimeMs != null) {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = editingTask.startTimeMs }
            startHour = c.get(java.util.Calendar.HOUR_OF_DAY)
            startMinute = c.get(java.util.Calendar.MINUTE)
        }
        if (editingTask?.endTimeMs != null) {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = editingTask.endTimeMs }
            endHour = c.get(java.util.Calendar.HOUR_OF_DAY)
            endMinute = c.get(java.util.Calendar.MINUTE)
        }
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                val localPath = saveImageLocally(context, uri)
                if (localPath != null) {
                    imagePath = localPath
                }
            }
        }
    )
    
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = deadlineMs
    val dateString = "${cal.get(java.util.Calendar.DAY_OF_MONTH)}/${cal.get(java.util.Calendar.MONTH)+1}/${cal.get(java.util.Calendar.YEAR)}"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (editingTask == null) Icons.Default.Add else Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (editingTask == null) "เพิ่มงานใหม่" else "แก้ไขงาน",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("รายละเอียดงาน (หรือวิชาที่ค้าง)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            // AI Suggest & Breakdown Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            isAiLoading = true
                            coroutineScope.launch {
                                val result = GeminiService.autoSuggestTask(title)
                                if (result != null) {
                                    category = result.category
                                    isImportant = result.isImportant
                                    energyRequired = try {
                                        EnergyLevel.valueOf(result.energyRequired.uppercase())
                                    } catch (e: Exception) {
                                        EnergyLevel.MEDIUM
                                    }
                                }
                                isAiLoading = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = title.isNotBlank() && !isAiLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    if (isAiLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ให้ AI เติมข้อมูล", fontWeight = FontWeight.Bold)
                    }
                }
                
                if (editingTask == null) {
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                isBreakdownLoading = true
                                onBreakdownTask(title) { success ->
                                    isBreakdownLoading = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1.1f),
                        enabled = title.isNotBlank() && !isBreakdownLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        if (isBreakdownLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("แตกงานย่อยด้วย AI", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isImportant, onCheckedChange = { isImportant = it })
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (isImportant) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("เป็นงานสำคัญ", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("หมวดหมู่", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories.size) { index ->
                    val cat = categories[index]
                    val isCatSelected = category == cat
                    val catIcon = when (cat) {
                        "สอบ" -> Icons.Default.Star
                        "เรียน" -> Icons.Default.Info
                        "เนื้อหา" -> Icons.Default.List
                        "อ่านหนังสือ" -> Icons.Default.DateRange
                        else -> Icons.Default.Home
                    }
                    FilterChip(
                        selected = isCatSelected,
                        onClick = { category = cat },
                        label = { Text(cat) },
                        leadingIcon = {
                            Icon(
                                imageVector = catIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("พลังงานที่ใช้", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnergyLevel.values().forEach { level ->
                    val isLvlSelected = energyRequired == level
                    val lvlIcon = when (level) {
                        EnergyLevel.LOW -> Icons.Default.EnergySavingsLeaf
                        EnergyLevel.MEDIUM -> Icons.Default.Info
                        EnergyLevel.HIGH -> Icons.Default.Star
                    }
                    val lvlColor = when (level) {
                        EnergyLevel.LOW -> Color(0xFF4CAF50)
                        EnergyLevel.MEDIUM -> Color(0xFFFF9800)
                        EnergyLevel.HIGH -> Color(0xFFF44336)
                    }
                    FilterChip(
                        selected = isLvlSelected,
                        onClick = { energyRequired = level },
                        label = { Text(level.toThaiString()) },
                        leadingIcon = {
                            Icon(
                                imageVector = lvlIcon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isLvlSelected) MaterialTheme.colorScheme.onSecondaryContainer else lvlColor
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("รูปภาพประกอบ (สำหรับดูทบทวน)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            if (imagePath != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    coil.compose.AsyncImage(
                        model = imagePath,
                        contentDescription = "Task photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    
                    IconButton(
                        onClick = { imagePath = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f), CircleShape)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "ลบรูป",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        pickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("เพิ่มรูปภาพเพื่อช่วยทบทวน", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val c = java.util.Calendar.getInstance().apply { timeInMillis = deadlineMs }
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val newCal = java.util.Calendar.getInstance().apply {
                                    timeInMillis = deadlineMs
                                    set(java.util.Calendar.YEAR, year)
                                    set(java.util.Calendar.MONTH, month)
                                    set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                                }
                                deadlineMs = newCal.timeInMillis
                            },
                            c.get(java.util.Calendar.YEAR),
                            c.get(java.util.Calendar.MONTH),
                            c.get(java.util.Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                    .padding(vertical = 8.dp)
            ) {
                Checkbox(checked = hasTime, onCheckedChange = { hasTime = it })
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = if (hasTime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text("จัดลงตารางเวลา ($dateString)", fontWeight = FontWeight.Bold)
                    Text("แตะตรงนี้เพื่อเลือกวันที่อื่น 📅", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (hasTime) {
                Spacer(modifier = Modifier.height(8.dp))
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
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(String.format("เริ่ม: %02d:%02d", startHour, startMinute))
                    }
                    Text("-", fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            android.app.TimePickerDialog(context, { _, h, m ->
                                endHour = h
                                endMinute = m
                            }, endHour, endMinute, true).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(String.format("จบ: %02d:%02d", endHour, endMinute))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { 
                    if (title.isNotBlank()) {
                        val startMs = if (hasTime) {
                            val c = java.util.Calendar.getInstance().apply { timeInMillis = deadlineMs }
                            c.set(java.util.Calendar.HOUR_OF_DAY, startHour)
                            c.set(java.util.Calendar.MINUTE, startMinute)
                            c.timeInMillis
                        } else null
                        val endMs = if (hasTime) {
                            val c = java.util.Calendar.getInstance().apply { timeInMillis = deadlineMs }
                            c.set(java.util.Calendar.HOUR_OF_DAY, endHour)
                            c.set(java.util.Calendar.MINUTE, endMinute)
                            c.timeInMillis
                        } else null
                        onSaveTask(title, isImportant, energyRequired, category, startMs, endMs, deadlineMs, imagePath) 
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (editingTask == null) "เพิ่มงาน" else "บันทึก",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun DailyBriefingCard(
    briefing: String,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "AI",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "สรุปและแนะนำรายวันโดย AI",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "รีเฟรชสรุป AI",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                Text(
                    text = briefing.ifBlank { "กดปุ่มรีเฟรชด้านบน เพื่อสรุปแผนงานและจัดสรรพลังงานสำหรับวิชากิจกรรมของคุณในวันนี้ด้วย AI!" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
fun InsightsDashboard(
    completedTasks: List<TaskEntity>,
    pendingTasks: List<TaskEntity>
) {
    val totalCompleted = completedTasks.size
    val totalPending = pendingTasks.size
    val totalTasks = totalCompleted + totalPending
    
    val completionRate = if (totalTasks > 0) {
        (totalCompleted.toFloat() / totalTasks.toFloat())
    } else 0f

    // Group energy spent by category with explicit type parameter mapping
    val energyByCategory: Map<String, Int> = completedTasks.groupBy { it.category }.mapValues { entry: Map.Entry<String, List<TaskEntity>> ->
        entry.value.fold(0) { acc: Int, task: TaskEntity ->
            acc + when (task.energyRequired) {
                EnergyLevel.LOW -> 1
                EnergyLevel.MEDIUM -> 2
                EnergyLevel.HIGH -> 3
            }
        }
    }
    
    val maxEnergy = (energyByCategory.values.maxOrNull() ?: 1).toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "สรุปความสำเร็จและการใช้พลังงาน",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress Indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                CircularProgressIndicator(
                    progress = { completionRate },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 8.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(completionRate * 100).toInt()}%",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "ความสำเร็จ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "เคลียร์เสร็จสิ้นแล้ว $totalCompleted งาน (คงเหลือค้างอยู่ $totalPending งาน)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    Spacer(modifier = Modifier.height(24.dp))
    
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "แต้มพลังงานที่เคลียร์สำเร็จแยกตามหมวดหมู่",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    
    if (energyByCategory.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "ทำภารกิจให้เสร็จเพื่อเริ่มวิเคราะห์พลังงาน!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            energyByCategory.forEach { entry ->
                val cat = entry.key
                val energy = entry.value
                val ratio = energy.toFloat() / maxEnergy
                val catIcon = when (cat) {
                    "สอบ" -> Icons.Default.Star
                    "เรียน" -> Icons.Default.Info
                    "เนื้อหา" -> Icons.Default.List
                    "อ่านหนังสือ" -> Icons.Default.DateRange
                    else -> Icons.Default.Home
                }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = catIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "$energy แต้มพลังงาน",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

fun saveImageLocally(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val fileName = "task_img_${System.currentTimeMillis()}.jpg"
        val file = java.io.File(context.filesDir, fileName)
        val outputStream = java.io.FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun ImageViewerDialog(
    imagePath: String,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "รูปภาพประกอบสำหรับทบทวน",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "ปิด",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    coil.compose.AsyncImage(
                        model = imagePath,
                        contentDescription = "Task Review Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ตกลง", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TaskAnalysisDialog(
    task: TaskEntity,
    onDismiss: () -> Unit
) {
    var result by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(task) {
        if (task.imagePath != null) {
            isLoading = true
            result = GeminiService.analyzeTaskImage(task.title, task.imagePath)
            isLoading = false
        } else {
            result = "ไม่มีรูปภาพประกอบสำหรับงานนี้"
            isLoading = false
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "เฉลยและวิเคราะห์งานด้วย AI",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "ปิด",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "หัวข้องาน: ${task.title}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (task.imagePath != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            coil.compose.AsyncImage(
                                model = task.imagePath,
                                contentDescription = "Task image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "AI กำลังวิเคราะห์รูปภาพและเฉลยงานให้นะ...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "ขั้นตอนนี้อาจใช้เวลาประมาณ 5-10 วินาที",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "ผลลัพธ์คำเฉลย:",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = result ?: "เกิดข้อผิดพลาดในการรับข้อมูลเฉลย",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isLoading && result != null) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(result!!))
                                android.widget.Toast.makeText(context, "คัดลอกเฉลยแล้ว", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "คัดลอก",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("คัดลอกเฉลย", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ตกลง", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun UserProfileDialog(
    userProfile: UserProfile,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "โปรไฟล์ผู้ใช้",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "ปิด",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (userProfile.photoUrl != null) {
                        coil.compose.AsyncImage(
                            model = userProfile.photoUrl,
                            contentDescription = "Profile Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        val initials = userProfile.displayName?.firstOrNull()?.toString() ?: "U"
                        Text(
                            text = initials.uppercase(),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = userProfile.displayName ?: "ผู้ใช้นิรนาม",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = userProfile.email ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "สวัสดีครับคุณ ${userProfile.displayName ?: "ผู้ใช้"}, มาเคลียร์งานวันนี้ให้เรียบร้อยกันเถอะ! 💪",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ปิด")
                    }
                    Button(
                        onClick = onSignOut,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("ออกจากระบบ", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AuthErrorDialog(
    onBypass: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ตั้งค่าการเข้าสู่ระบบด้วย Google",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "ปิด",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "หากต้องการใช้ปุ่ม 'เข้าสู่ระบบด้วย Google' บนอุปกรณ์จริงหรือ Emulator คุณต้องกำหนดค่า OAuth Client ID ใน Google Cloud Console ดังนี้:\n\n" +
                            "1️⃣ ไปที่ Google Cloud Console (https://console.cloud.google.com)\n" +
                            "2️⃣ ไปที่ APIs & Services > Credentials\n" +
                            "3️⃣ กดปุ่ม 'Create Credentials' > 'OAuth client ID'\n" +
                            "4️⃣ เลือก Application Type เป็น 'Web application' (เพื่อขอ ID Token ในการยืนยันตัวตนแบบ Cross-Platform)\n" +
                            "5️⃣ คัดลอก Client ID ที่ได้ นำไปใส่ในไฟล์ `.env` ที่โฟลเดอร์หลักของโปรเจกต์:\n" +
                            "   `GOOGLE_WEB_CLIENT_ID=คีย์ของคุณ.apps.googleusercontent.com`\n" +
                            "6️⃣ สำหรับ Android ต้องเพิ่ม SHA-1 ของ Keystore ใน Google Console เพื่อเปิดใช้งานระบบล็อกอินจากแอปโดยตรงด้วย\n\n" +
                            "💡 สำหรับสภาพแวดล้อม Sandbox หรือขณะทดสอบระบบ คุณสามารถกดปุ่ม 'เข้าสู่ระบบจำลอง' ด้านล่างเพื่อจำลองผลลัพธ์จาก Google Account ได้ทันที!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("ยกเลิก")
                    }
                    Button(
                        onClick = onBypass,
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("เข้าสู่ระบบจำลอง", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

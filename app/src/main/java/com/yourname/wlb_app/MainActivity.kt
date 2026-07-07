package com.yourname.wlb_app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.yourname.wlb_app.ui.theme.WLB_appTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WLB_appTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("entry") { EntryScreen(navController, entryId = -1) }
        composable("entry/{entryId}") { backStackEntry ->
            val entryId = backStackEntry.arguments?.getString("entryId")?.toIntOrNull() ?: -1
            EntryScreen(navController, entryId = entryId)
        }
        composable("categories") { CategoriesScreen(navController) }
    }
}

data class DateTimePoint(
    var day: Int,
    var month: Int,
    var year: Int,
    var hour: Int,
    var minute: Int
) {
    fun label(): String =
        String.format("%02d.%02d.%d  %02d:%02d", day, month + 1, year, hour, minute)

    fun toCalendar(): Calendar {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, hour, minute, 0)
        return cal
    }

    fun toMillis(): Long = toCalendar().timeInMillis
}

fun now(): DateTimePoint {
    val c = Calendar.getInstance()
    return DateTimePoint(
        c.get(Calendar.DAY_OF_MONTH),
        c.get(Calendar.MONTH),
        c.get(Calendar.YEAR),
        c.get(Calendar.HOUR_OF_DAY),
        c.get(Calendar.MINUTE)
    )
}

fun durationLabel(startMillis: Long, endMillis: Long): String {
    val diff = endMillis - startMillis
    if (diff < 0) return "⚠️ Кінець раніше початку"
    val totalMinutes = diff / 60000
    return "${totalMinutes / 60} год ${totalMinutes % 60} хв"
}

fun durationHours(startMillis: Long, endMillis: Long): Double {
    val diff = endMillis - startMillis
    if (diff < 0) return 0.0
    return diff / 3_600_000.0
}

// ===== ViewModel =====

class EntryViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.timeEntryDao()
    private val categoryDao = db.categoryDao()

    var entries by mutableStateOf<List<TimeEntry>>(emptyList())
        private set

    var categories by mutableStateOf<List<Category>>(emptyList())
        private set

    var filterFrom by mutableStateOf<Long?>(
        System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L // дефолт — тиждень назад
    )
    var filterTo by mutableStateOf<Long?>(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            dao.getAll().collectLatest { list ->
                entries = list
            }
        }
        viewModelScope.launch {
            categoryDao.getAll().collectLatest { list ->
                categories = list
                // якщо база порожня — додаємо дефолтні категорії
                if (list.isEmpty()) {
                    categoryDao.insert(Category(name = "Сон", isPositive = true, targetHours = 8.0))
                    categoryDao.insert(Category(name = "Робота", isPositive = false, targetHours = 9.0))
                    categoryDao.insert(Category(name = "Відпочинок", isPositive = true, targetHours = 3.0))
                    categoryDao.insert(Category(name = "Прокрастинація", isPositive = false, targetHours = 2.0))
                    categoryDao.insert(Category(name = "Спорт", isPositive = true, targetHours = 1.0))
                    categoryDao.insert(Category(name = "Рутина", isPositive = true, targetHours = 1.0))
                }
            }
        }
    }

    fun saveEntry(entry: TimeEntry) {
        viewModelScope.launch { dao.insert(entry) }
    }

    fun updateEntry(entry: TimeEntry) {
        viewModelScope.launch { dao.update(entry) }
    }

    fun deleteEntry(entry: TimeEntry) {
        viewModelScope.launch { dao.delete(entry) }
    }

    fun addCategory(name: String, isPositive: Boolean = true, targetHours: Double = 1.0) {
        if (name.isBlank()) return
        viewModelScope.launch {
            categoryDao.insert(Category(
                name = name.trim(),
                isPositive = isPositive,
                targetHours = targetHours
            ))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            dao.deleteByCategory(category.name)
            categoryDao.delete(category)
        }
    }

    fun categoryNames(): List<String> = categories.map { it.name }

    fun filteredEntries(): List<TimeEntry> {
        val from = filterFrom ?: Long.MIN_VALUE
        val to = filterTo ?: Long.MAX_VALUE
        // filterTo до кінця дня
        val toEndOfDay = to + 24 * 60 * 60 * 1000L - 1
        return entries.filter { it.startMillis in from..toEndOfDay }
    }

    fun averageHours(category: String): Double {
        val relevant = filteredEntries().filter { it.category == category }
        if (relevant.isEmpty()) return 0.0
        val days = daysBetween(filterFrom, filterTo).coerceAtLeast(1)
        return relevant.sumOf { durationHours(it.startMillis, it.endMillis) } / days
    }

    fun daysBetween(from: Long?, to: Long?): Int {
        val f = from ?: return 1
        val t = to ?: return 1
        return ((t - f) / (24 * 60 * 60 * 1000L)).toInt() + 1
    }

    fun generateAdvice(): String {
        val sleep = averageHours("Сон")
        val work = averageHours("Робота")
        val rest = averageHours("Відпочинок")
        val procrastination = averageHours("Прокрастинація")
        return when {
            entries.isEmpty() -> "Додай перший запис щоб отримати персональні поради 👋"
            filteredEntries().isEmpty() -> "За цей період записів немає 📭"
            sleep < 6 -> "😴 Середній сон менше 6 год/день. Спробуй лягати раніше."
            work > 10 -> "💼 Робота більше 10 год/день. Це ризик вигорання."
            procrastination > 3 -> "⏳ Багато часу на прокрастинацію. Спробуй техніку Pomodoro."
            rest < 1 -> "🌿 Майже немає відпочинку. Додай хоча б 30 хв прогулянки."
            sleep >= 7 && work <= 8 -> "✅ Гарний баланс! Так тримати."
            else -> "⚖️ Непоганий баланс, продовжуй відстежувати."
        }
    }
    fun calculateWLBIndex(): Double {
        if (filteredEntries().isEmpty()) return 0.0
        if (categories.isEmpty()) return 0.0

        var positiveScore = 0.0
        var positiveCount = 0
        var negativePenalty = 0.0

        categories.forEach { cat ->
            val avg = averageHours(cat.name)
            if (cat.isPositive) {
                val score = (avg / cat.targetHours.coerceAtLeast(0.1)).coerceAtMost(1.0) * 100
                positiveScore += score
                positiveCount++
            } else {
                val excess = (avg - cat.targetHours.coerceAtLeast(0.1)).coerceAtLeast(0.0)
                negativePenalty += excess * 10
            }
        }

        if (positiveCount == 0) return 0.0
        val base = positiveScore / positiveCount
        return (base - negativePenalty).coerceIn(0.0, 100.0)
    }

    fun wlbIndexHistory(): List<Pair<Long, Double>> {
        if (entries.isEmpty()) return emptyList()

        val from = filterFrom ?: return emptyList()
        val to = filterTo ?: System.currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L
        val days = daysBetween(filterFrom, filterTo).coerceAtMost(30)

        return (0 until days).map { i ->
            val dayStart = from + i * dayMillis
            val dayEnd = dayStart + dayMillis - 1

            val dayEntries = entries.filter { it.startMillis in dayStart..dayEnd }

            var positiveScore = 0.0
            var positiveCount = 0
            var negativePenalty = 0.0

            categories.forEach { cat ->
                val hours = dayEntries
                    .filter { it.category == cat.name }
                    .sumOf { durationHours(it.startMillis, it.endMillis) }

                if (cat.isPositive) {
                    val score = (hours / cat.targetHours).coerceAtMost(1.0) * 100
                    positiveScore += score
                    positiveCount++
                } else {
                    val excess = (hours - cat.targetHours).coerceAtLeast(0.0)
                    negativePenalty += excess * 10
                }
            }

            val index = if (positiveCount > 0) {
                ((positiveScore / positiveCount) - negativePenalty).coerceIn(0.0, 100.0)
            } else 0.0

            dayStart to index
        }
    }
    fun toggleDashboard(category: Category) {
        viewModelScope.launch {
            categoryDao.update(category.copy(showOnDashboard = !category.showOnDashboard))
        }
    }

    fun dashboardCategories(): List<Category> = categories.filter { it.showOnDashboard }

}

// ===== HomeScreen =====

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController, viewModel: EntryViewModel = viewModel()) {
    var showFilterFrom by remember { mutableStateOf(false) }
    var showFilterTo by remember { mutableStateOf(false) }

    val filterFromState = rememberDatePickerState(
        initialSelectedDateMillis = viewModel.filterFrom
    )
    val filterToState = rememberDatePickerState(
        initialSelectedDateMillis = viewModel.filterTo
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Work-Life Balance") },
                actions = {
                    IconButton(onClick = { navController.navigate("categories") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Категорії")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("entry") }) {
                Icon(Icons.Default.Add, contentDescription = "Додати запис")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Фільтр
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showFilterFrom = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = viewModel.filterFrom?.let { formatDateShort(it) } ?: "Від",
                        fontSize = 13.sp
                    )
                }
                Text("—", modifier = Modifier.align(Alignment.CenterVertically))
                OutlinedButton(
                    onClick = { showFilterTo = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = viewModel.filterTo?.let { formatDateShort(it) } ?: "До",
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // WLB індекс — закріплений великий модуль
            val wlbIndex = viewModel.calculateWLBIndex()
            WLBIndexCard(wlbIndex = wlbIndex, history = viewModel.wlbIndexHistory())

            Spacer(modifier = Modifier.height(12.dp))

            // Сітка категорій 2 колонки
            val dashboardCats = viewModel.dashboardCategories()
            if (dashboardCats.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Увімкни категорії в налаштуваннях ⚙️",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp
                    )
                }
            } else {
                val rows = dashboardCats.chunked(2)
                rows.forEach { rowCats ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowCats.forEach { cat ->
                            CategoryTile(
                                category = cat,
                                avgHours = viewModel.averageHours(cat.name),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowCats.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Діалог "від"
    if (showFilterFrom) {
        DatePickerDialog(
            onDismissRequest = { showFilterFrom = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.filterFrom = filterFromState.selectedDateMillis
                    showFilterFrom = false
                }) { Text("Ок") }
            },
            dismissButton = {
                TextButton(onClick = { showFilterFrom = false }) { Text("Скасувати") }
            }
        ) { DatePicker(state = filterFromState) }
    }

    // Діалог "до"
    if (showFilterTo) {
        DatePickerDialog(
            onDismissRequest = { showFilterTo = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.filterTo = filterToState.selectedDateMillis
                    showFilterTo = false
                }) { Text("Ок") }
            },
            dismissButton = {
                TextButton(onClick = { showFilterTo = false }) { Text("Скасувати") }
            }
        ) { DatePicker(state = filterToState) }
    }
}

// WLB індекс картка
@Composable
fun WLBIndexCard(wlbIndex: Double, history: List<Pair<Long, Double>>) {
    val color = when {
        wlbIndex >= 70 -> Color(0xFF2E7D32)
        wlbIndex >= 40 -> Color(0xFFFF8F00)
        else -> Color(0xFFE53935)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("WLB Індекс", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = String.format("%.0f", wlbIndex),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
                Text("/ 100", fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Bottom).padding(bottom = 8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (wlbIndex / 100).toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = color
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Тренд", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            WLBLineChart(data = history)
        }
    }
}

// Плитка категорії
@Composable
fun CategoryTile(
    category: Category,
    avgHours: Double,
    modifier: Modifier = Modifier
) {
    val isGood = if (category.isPositive) {
        avgHours >= category.targetHours * 0.8
    } else {
        avgHours <= category.targetHours * 1.2
    }

    fun formatHours(hours: Double): String {
        return if (hours % 1.0 == 0.0) "${hours.toInt()} год" else "$hours год"
    }

    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = category.name,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format("%.1f год", avgHours),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGood) Color(0xFF2E7D32) else Color(0xFFE53935)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (category.isPositive) "ціль ${formatHours(category.targetHours)}"
                else "межа ${formatHours(category.targetHours)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ===== EntryScreen =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(navController: NavHostController, entryId: Int = -1, viewModel: EntryViewModel = viewModel()) {
    val isEditing = entryId != -1
    val existingEntry = remember(viewModel.entries, entryId) {
        viewModel.entries.find { it.id == entryId }
    }

    var selectedCategory by remember { mutableStateOf(existingEntry?.category ?: "") }

    LaunchedEffect(viewModel.categories) {
        if (selectedCategory.isEmpty() && viewModel.categories.isNotEmpty()) {
            selectedCategory = viewModel.categories.first().name
        }
    }
    var expanded by remember { mutableStateOf(false) }

    // Стани для дат
    val startDateState = rememberDatePickerState(
        initialSelectedDateMillis = existingEntry?.startMillis ?: System.currentTimeMillis()
    )
    val endDateState = rememberDatePickerState(
        initialSelectedDateMillis = existingEntry?.endMillis ?: System.currentTimeMillis()
    )

    // Стани для часу
    val startCal = Calendar.getInstance().apply {
        timeInMillis = existingEntry?.startMillis ?: System.currentTimeMillis()
    }
    val endCal = Calendar.getInstance().apply {
        timeInMillis = existingEntry?.endMillis ?: System.currentTimeMillis()
    }

    var startHour by remember { mutableStateOf(startCal.get(Calendar.HOUR_OF_DAY)) }
    var startMinute by remember { mutableStateOf(startCal.get(Calendar.MINUTE)) }
    var endHour by remember { mutableStateOf(endCal.get(Calendar.HOUR_OF_DAY)) }
    var endMinute by remember { mutableStateOf(endCal.get(Calendar.MINUTE)) }

    // Який пікер зараз відкритий
    var openPicker by remember { mutableStateOf<String?>(null) }
    // "startDate", "startTime", "endDate", "endTime", null

    fun buildMillis(dateState: DatePickerState, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dateState.selectedDateMillis ?: System.currentTimeMillis()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }



    val startMillis = buildMillis(startDateState, startHour, startMinute)
    val endMillis = buildMillis(endDateState, endHour, endMinute)


    fun formatDate(millis: Long?): String {
        if (millis == null) return "Оберіть дату"
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format("%02d.%02d.%d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.YEAR))
    }

    fun formatTime(hour: Int, minute: Int): String = String.format("%02d:%02d", hour, minute)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Редагувати запис" else "Новий запис") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("←", fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Категорія
            Text("Категорія", fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
            Spacer(modifier = Modifier.height(4.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                TextField(
                    value = selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    viewModel.categoryNames().forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = { selectedCategory = category; expanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // --- ПОЧАТОК ---
            Text("Початок", fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
            Spacer(modifier = Modifier.height(8.dp))

            // Дата початку
            PickerRow(
                label = "Дата",
                value = formatDate(startDateState.selectedDateMillis),
                isOpen = openPicker == "startDate",
                onClick = { openPicker = if (openPicker == "startDate") null else "startDate" }
            )
            if (openPicker == "startDate") {
                DatePicker(
                    state = startDateState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Час початку
            PickerRow(
                label = "Час",
                value = formatTime(startHour, startMinute),
                isOpen = openPicker == "startTime",
                onClick = { openPicker = if (openPicker == "startTime") null else "startTime" }
            )
            if (openPicker == "startTime") {
                TimeScrollPicker(
                    hour = startHour,
                    minute = startMinute,
                    onHourChange = { startHour = it },
                    onMinuteChange = { startMinute = it },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // --- КІНЕЦЬ ---
            Text("Кінець", fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp))
            Spacer(modifier = Modifier.height(8.dp))

            // Дата кінця
            PickerRow(
                label = "Дата",
                value = formatDate(endDateState.selectedDateMillis),
                isOpen = openPicker == "endDate",
                onClick = { openPicker = if (openPicker == "endDate") null else "endDate" }
            )
            if (openPicker == "endDate") {
                DatePicker(
                    state = endDateState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Час кінця
            PickerRow(
                label = "Час",
                value = formatTime(endHour, endMinute),
                isOpen = openPicker == "endTime",
                onClick = { openPicker = if (openPicker == "endTime") null else "endTime" }
            )
            if (openPicker == "endTime") {
                TimeScrollPicker(
                    hour = endHour,
                    minute = endMinute,
                    onHourChange = { endHour = it },
                    onMinuteChange = { endMinute = it },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Тривалість
            Text(
                "Тривалість: ${durationLabel(startMillis, endMillis)}",
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (endMillis <= startMillis) return@Button // ігноруємо якщо кінець раніше початку
                    if (selectedCategory.isBlank()) return@Button

                    if (isEditing && existingEntry != null) {
                        viewModel.updateEntry(existingEntry.copy(
                            category = selectedCategory,
                            startMillis = startMillis,
                            endMillis = endMillis
                        ))
                    } else {
                        viewModel.saveEntry(TimeEntry(
                            category = selectedCategory,
                            startMillis = startMillis,
                            endMillis = endMillis
                        ))
                    }
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isEditing) "Зберегти зміни" else "Зберегти")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Допоміжний компонент — рядок з міткою і значенням, клікабельний
@Composable
fun PickerRow(label: String, value: String, isOpen: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (isOpen) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 15.sp)
        }
    }
}

@Composable
fun WeeklyBarChart(data: List<Pair<String, Double>>) {
    val barColors = listOf(
        Color(0xFF6650A4), // Сон - фіолетовий
        Color(0xFF0288D1), // Робота - синій
        Color(0xFF2E7D32), // Відпочинок - зелений
        Color(0xFFE53935), // Прокрастинація - червоний
        Color(0xFFFF8F00), // Спорт - помаранчевий
        Color(0xFF00897B)  // Рутина - бірюзовий
    )

    val maxValue = maxOf(data.maxOfOrNull { it.second } ?: 1.0, 1.0)

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val barCount = data.size
            val totalWidth = size.width
            val totalHeight = size.height
            val barWidth = totalWidth / (barCount * 2f) // стовпчик + відступ
            val topPadding = 16f

            // горизонтальні лінії сітки (0, 25%, 50%, 75%, 100%)
            val gridColor = Color(0x33000000)
            for (i in 0..4) {
                val y = topPadding + (totalHeight - topPadding) * (1f - i / 4f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(totalWidth, y),
                    strokeWidth = 1f
                )
            }

            // стовпчики
            data.forEachIndexed { index, (_, value) ->
                val barHeight = ((value / maxValue) * (totalHeight - topPadding)).toFloat()
                val x = index * (barWidth * 2f) + barWidth / 2f
                val y = totalHeight - barHeight

                drawRoundRect(
                    color = barColors[index % barColors.size],
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
            }
        }

        // підписи під стовпчиками
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            data.forEachIndexed { index, (label, _) ->
                Text(
                    text = label.take(3), // скорочення: "Сон", "Роб", "Від"...
                    fontSize = 10.sp,
                    color = barColors[index % barColors.size]
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(navController: NavHostController, viewModel: EntryViewModel = viewModel()) {
    var newCategoryName by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var isPositive by remember { mutableStateOf(true) }
    var targetHours by remember { mutableStateOf("1") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Категорії") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("←", fontSize = 20.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Назва") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = targetHours,
                    onValueChange = { input ->
                        val filtered = input.filter { c -> c.isDigit() || c == '.' }
                        // не дозволяємо більше однієї крапки
                        if (filtered.count { it == '.' } <= 1) {
                            targetHours = filtered
                        }
                    },
                    label = { Text("Ціль (год)") },
                    modifier = Modifier.width(110.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = isPositive,
                    onClick = { isPositive = true },
                    label = { Text("✅ Позитивна") }
                )
                FilterChip(
                    selected = !isPositive,
                    onClick = { isPositive = false },
                    label = { Text("⚠️ Негативна") }
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        viewModel.addCategory(
                            name = newCategoryName,
                            isPositive = isPositive,
                            targetHours = targetHours.toDoubleOrNull() ?: 1.0
                        )
                        newCategoryName = ""
                        targetHours = "1"
                        isPositive = true
                    },
                    enabled = newCategoryName.isNotBlank()
                ) {
                    Text("Додати")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Поточні категорії", fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            viewModel.categories.forEach { category ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(category.name, fontSize = 15.sp)
                            Text(
                                text = if (category.isPositive)
                                    "✅ ціль: ${category.targetHours} год"
                                else
                                    "⚠️ межа: ${category.targetHours} год",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = category.showOnDashboard,
                            onCheckedChange = { viewModel.toggleDashboard(category) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        IconButton(onClick = { categoryToDelete = category }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Видалити",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Видалити категорію?") },
            text = { Text("\"${cat.name}\" буде видалена. Записи з цією категорією залишаться.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(cat)
                    categoryToDelete = null
                }) { Text("Видалити") }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("Скасувати") }
            }
        )
    }
}


@Composable
fun ScrollPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeightDp = 48.dp
    val visibleItems = 5
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = maxOf(0, selectedIndex - 1)
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex
            val clampedIndex = centerIndex.coerceIn(0, items.size - 1)
            if (clampedIndex != selectedIndex) {
                onSelectedIndexChange(clampedIndex)
            }
        }
    }

    Box(
        modifier = modifier
            .height(itemHeightDp * visibleItems)
            .width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeightDp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.shapes.small
                )
        )

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            items(2) { Spacer(modifier = Modifier.height(itemHeightDp)) }

            itemsIndexed(items) { index, item ->
                val isSelected = index == selectedIndex
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(itemHeightDp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = item,
                        fontSize = if (isSelected) 22.sp else 18.sp,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            items(2) { Spacer(modifier = Modifier.height(itemHeightDp)) }
        }
    }
}

@Composable
fun TimeScrollPicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hours = (0..23).map { String.format("%02d", it) }
    val minutes = (0..59).map { String.format("%02d", it) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ScrollPicker(
            items = hours,
            selectedIndex = hour,
            onSelectedIndexChange = onHourChange
        )
        Text(
            text = ":",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        ScrollPicker(
            items = minutes,
            selectedIndex = minute,
            onSelectedIndexChange = onMinuteChange
        )
    }
}

fun formatDateShort(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return String.format("%02d.%02d.%d",
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.YEAR)
    )
}

@Composable
fun WLBLineChart(data: List<Pair<Long, Double>>) {
    if (data.isEmpty()) {
        Text("Недостатньо даних для графіку", fontSize = 13.sp)
        return
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val width = size.width
        val height = size.height
        val padLeft = 8f
        val padBottom = 8f
        val padTop = 16f
        val chartWidth = width - padLeft
        val chartHeight = height - padBottom - padTop

        val gridColor = Color(0x33888888)

        // сітка
        for (i in 0..4) {
            val y = padTop + chartHeight * (1f - i / 4f)
            drawLine(gridColor, Offset(padLeft, y), Offset(width, y), strokeWidth = 1f)
        }

        if (data.size < 2) return@Canvas

        val points = data.mapIndexed { index, (_, value) ->
            val x = padLeft + (index.toFloat() / (data.size - 1)) * chartWidth
            val y = padTop + chartHeight * (1f - (value / 100f).toFloat())
            Offset(x, y)
        }

        // лінія
        for (i in 0 until points.size - 1) {
            drawLine(
                color = Color(0xFF6650A4),
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3f
            )
        }

        // точки
        points.forEach { point ->
            drawCircle(color = Color(0xFF6650A4), radius = 6f, center = point)
            drawCircle(color = Color.White, radius = 3f, center = point)
        }
    }
}


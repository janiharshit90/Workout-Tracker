# Write-Sources.ps1 — creates all missing Kotlin source files

$base = ".\app\src\main\java\com\example\mentzertracker"
New-Item -ItemType Directory -Force -Path "$base\data", "$base\ui\theme" | Out-Null

function W($path, $content) {
    [System.IO.File]::WriteAllText((Join-Path $PWD $path), $content, [System.Text.UTF8Encoding]::new($false))
    Write-Host "  wrote $path" -ForegroundColor Green
}

Write-Host "Writing Kotlin sources..." -ForegroundColor Cyan

# ---------- MainActivity.kt ----------
W "$base\MainActivity.kt" @'
package com.example.mentzertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.*
import com.example.mentzertracker.ui.*
import com.example.mentzertracker.ui.theme.MentzerTrackerTheme

class MainActivity : ComponentActivity() {
    private val vm: WorkoutViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MentzerTrackerTheme { AppRoot(vm) } }
    }
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun AppRoot(vm: WorkoutViewModel) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val tabs = listOf(
        Tab("today", "Today") { Icon(Icons.Filled.FitnessCenter, null) },
        Tab("history", "History") { Icon(Icons.Filled.History, null) },
        Tab("prs", "PRs") { Icon(Icons.Filled.EmojiEvents, null) }
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val back by nav.currentBackStackEntryAsState()
                val current = back?.destination?.route
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = t.icon,
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "today", modifier = Modifier.padding(pad)) {
            composable("today")   { TodayScreen(vm) }
            composable("history") { HistoryScreen(vm, ctx) }
            composable("prs")     { PRsScreen(vm) }
        }
    }
}
'@

# ---------- WorkoutViewModel.kt ----------
W "$base\WorkoutViewModel.kt" @'
package com.example.mentzertracker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mentzertracker.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

data class SetInput(val weight: String = "", val reps: String = "")

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val exerciseDao = db.exerciseDao()
    private val workoutDao = db.workoutDao()
    private val prDao = db.prDao()
    private val templateDao = db.templateDao()

    val exercises: StateFlow<List<Exercise>> = exerciseDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<SetWithExercise>> = workoutDao.getAllWithExercise()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val prs: StateFlow<List<PRWithExercise>> = prDao.getAllWithExercise()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<WorkoutTemplate>> = templateDao.getAllTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inputs = MutableStateFlow<Map<Long, List<SetInput>>>(emptyMap())
    val inputs: StateFlow<Map<Long, List<SetInput>>> = _inputs

    private val _heavyDutyMode = MutableStateFlow(false)
    val heavyDutyMode: StateFlow<Boolean> = _heavyDutyMode

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events

    init {
        viewModelScope.launch {
            if (exerciseDao.count() == 0) {
                exerciseDao.insertAll(SeedData.exercises)
                val all = exerciseDao.getAllOnce()
                SeedData.templates.forEachIndexed { idx, pair ->
                    val (name, exIdx) = pair
                    val tId = templateDao.insertTemplate(WorkoutTemplate(name = name, sortOrder = idx))
                    templateDao.insertTemplateExercises(
                        exIdx.mapIndexed { order, ei -> TemplateExercise(tId, all[ei].id, order) }
                    )
                }
            }
        }
    }

    fun loadTemplate(templateId: Long) {
        viewModelScope.launch {
            val exIds = templateDao.getExerciseIdsForTemplate(templateId)
            _inputs.value = exIds.associateWith { List(3) { SetInput() } }
            _events.emit("Template loaded")
        }
    }

    fun toggleHeavyDuty() { _heavyDutyMode.value = !_heavyDutyMode.value }

    fun updateSet(exerciseId: Long, setIndex: Int, weight: String? = null, reps: String? = null) {
        val current = _inputs.value
        val list = (current[exerciseId] ?: List(3) { SetInput() }).toMutableList()
        list[setIndex] = list[setIndex].copy(
            weight = weight ?: list[setIndex].weight,
            reps = reps ?: list[setIndex].reps
        )
        if (weight != null && setIndex == 0) {
            for (i in 1 until list.size) if (list[i].weight.isBlank())
                list[i] = list[i].copy(weight = weight)
        }
        _inputs.value = current + (exerciseId to list.toList())
    }

    fun logWorkout() {
        viewModelScope.launch {
            val sessionDate = System.currentTimeMillis()
            val toInsert = mutableListOf<WorkoutSet>()

            _inputs.value.forEach { (exId, sets) ->
                val effectiveSets = if (_heavyDutyMode.value) sets.take(1) else sets
                effectiveSets.forEachIndexed { idx, s ->
                    val w = s.weight.toFloatOrNull(); val r = s.reps.toIntOrNull()
                    if (w != null && w > 0f && r != null && r > 0)
                        toInsert.add(WorkoutSet(exerciseId = exId, sessionDate = sessionDate,
                            setNumber = idx + 1, weight = w, reps = r))
                }
            }

            if (toInsert.isEmpty()) { _events.emit("Enter at least one set"); return@launch }
            workoutDao.insertAll(toInsert)

            toInsert.groupBy { it.exerciseId }.forEach { (exId, sets) ->
                val best = sets.maxByOrNull { it.weight } ?: return@forEach
                val existing = prDao.getForExercise(exId)
                val isPR = existing == null || best.weight > existing.maxWeight ||
                        (best.weight == existing.maxWeight && best.reps > existing.repsAtMaxWeight)
                if (isPR) prDao.upsert(PersonalRecord(
                    exerciseId = exId, maxWeight = best.weight,
                    repsAtMaxWeight = best.reps, dateAchieved = sessionDate))
            }
            _inputs.value = emptyMap()
            _events.emit(if (_heavyDutyMode.value) "Heavy Duty set logged" else "Workout logged")
        }
    }

    fun deleteSession(sessionDate: Long) {
        viewModelScope.launch {
            workoutDao.deleteSession(sessionDate)
            _events.emit("Session deleted")
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            workoutDao.getSetById(setId)?.let { workoutDao.deleteSet(it) }
            _events.emit("Set deleted")
        }
    }

    fun exportCsv(context: Context): String? {
        val rows = history.value
        if (rows.isEmpty()) {
            viewModelScope.launch { _events.emit("No data to export") }
            return null
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val file = File(context.getExternalFilesDir(null), "mentzer_history.csv")
        FileWriter(file).use { w ->
            w.append("Date,Exercise,Set,Weight (lbs),Reps,Notes\n")
            rows.sortedBy { it.sessionDate }.forEach { r ->
                w.append("${fmt.format(Date(r.sessionDate))},${r.exerciseName},${r.setNumber},${r.weight},${r.reps},\"${r.notes}\"\n")
            }
        }
        return file.absolutePath
    }
}
'@

# ---------- data/Entities.kt ----------
W "$base\data\Entities.kt" @'
package com.example.mentzertracker.data

import androidx.room.*

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cue: String,
    val sortOrder: Int
)

@Entity(tableName = "workout_templates")
data class WorkoutTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int
)

@Entity(
    tableName = "template_exercises",
    primaryKeys = ["templateId", "exerciseId"],
    foreignKeys = [
        ForeignKey(entity = WorkoutTemplate::class, parentColumns = ["id"],
            childColumns = ["templateId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"],
            childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("templateId"), Index("exerciseId")]
)
data class TemplateExercise(
    val templateId: Long,
    val exerciseId: Long,
    val sortOrder: Int
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [ForeignKey(entity = Exercise::class, parentColumns = ["id"],
        childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseId"), Index("sessionDate")]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val sessionDate: Long,
    val setNumber: Int,
    val weight: Float,
    val reps: Int,
    val notes: String = ""
)

@Entity(tableName = "personal_records",
    indices = [Index(value = ["exerciseId"], unique = true)])
data class PersonalRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val maxWeight: Float,
    val repsAtMaxWeight: Int,
    val dateAchieved: Long,
    val notes: String = ""
)

data class SetWithExercise(
    val id: Long, val exerciseId: Long, val exerciseName: String,
    val sessionDate: Long, val setNumber: Int, val weight: Float,
    val reps: Int, val notes: String
)

data class PRWithExercise(
    val id: Long, val exerciseId: Long, val exerciseName: String,
    val maxWeight: Float, val repsAtMaxWeight: Int,
    val dateAchieved: Long, val notes: String
)
'@

# ---------- data/Daos.kt ----------
W "$base\data\Daos.kt" @'
package com.example.mentzertracker.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<Exercise>>
    @Insert suspend fun insertAll(items: List<Exercise>)
    @Query("SELECT COUNT(*) FROM exercises") suspend fun count(): Int
    @Query("SELECT * FROM exercises ORDER BY sortOrder ASC")
    suspend fun getAllOnce(): List<Exercise>
}

@Dao
interface WorkoutDao {
    @Query("""
        SELECT s.id, s.exerciseId, e.name AS exerciseName, s.sessionDate,
               s.setNumber, s.weight, s.reps, s.notes
        FROM workout_sets s JOIN exercises e ON s.exerciseId = e.id
        ORDER BY s.sessionDate DESC, e.sortOrder ASC, s.setNumber ASC
    """)
    fun getAllWithExercise(): Flow<List<SetWithExercise>>

    @Insert suspend fun insertAll(sets: List<WorkoutSet>)
    @Query("DELETE FROM workout_sets WHERE sessionDate = :sessionDate")
    suspend fun deleteSession(sessionDate: Long)
    @Delete suspend fun deleteSet(set: WorkoutSet)
    @Update suspend fun updateSet(set: WorkoutSet)
    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getSetById(id: Long): WorkoutSet?
}

@Dao
interface PRDao {
    @Query("""
        SELECT p.id, p.exerciseId, e.name AS exerciseName, p.maxWeight,
               p.repsAtMaxWeight, p.dateAchieved, p.notes
        FROM personal_records p JOIN exercises e ON p.exerciseId = e.id
        ORDER BY e.sortOrder ASC
    """)
    fun getAllWithExercise(): Flow<List<PRWithExercise>>
    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId")
    suspend fun getForExercise(exerciseId: Long): PersonalRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pr: PersonalRecord)
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM workout_templates ORDER BY sortOrder ASC")
    fun getAllTemplates(): Flow<List<WorkoutTemplate>>
    @Query("SELECT exerciseId FROM template_exercises WHERE templateId = :templateId ORDER BY sortOrder ASC")
    suspend fun getExerciseIdsForTemplate(templateId: Long): List<Long>
    @Insert suspend fun insertTemplate(t: WorkoutTemplate): Long
    @Insert suspend fun insertTemplateExercises(items: List<TemplateExercise>)
}
'@

# ---------- data/AppDatabase.kt ----------
W "$base\data\AppDatabase.kt" @'
package com.example.mentzertracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Exercise::class, WorkoutTemplate::class, TemplateExercise::class,
                WorkoutSet::class, PersonalRecord::class],
    version = 2, exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun prDao(): PRDao
    abstract fun templateDao(): TemplateDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "mentzer.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
'@

# ---------- data/SeedData.kt ----------
W "$base\data\SeedData.kt" @'
package com.example.mentzertracker.data

object SeedData {
    val exercises = listOf(
        Exercise(name = "Dumbbell Incline Chest Press",
            cue = "Arms 45 deg, shoulders down and retracted back at all times", sortOrder = 0),
        Exercise(name = "1 arm dumbbell rows",
            cue = "Elbows reaching for the ceiling", sortOrder = 1),
        Exercise(name = "Dumbbell overhead press",
            cue = "Elbows straight, no extreme bending", sortOrder = 2),
        Exercise(name = "Lateral raises",
            cue = "Slight bend in elbow, thumb side slightly down", sortOrder = 3),
        Exercise(name = "Bicep curls",
            cue = "Elbows locked in place, no elbow pushing up", sortOrder = 4),
        Exercise(name = "Skull crushers",
            cue = "Arm angled towards hairline", sortOrder = 5),
        Exercise(name = "Hammer curls",
            cue = "Elbows locked in place, no elbow pushing up", sortOrder = 6)
    )

    val templates = listOf(
        "Day A - Full Body"    to listOf(0,1,2,3,4,5,6),
        "Day B - Chest / Back" to listOf(0,1,3,4)
    )
}
'@

# ---------- ui/theme/Theme.kt ----------
W "$base\ui\theme\Theme.kt" @'
package com.example.mentzertracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Dark = darkColorScheme(
    primary = Color(0xFFFF7A45),
    onPrimary = Color.Black,
    secondary = Color(0xFF9CCC65),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurface = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFFB0B0B0)
)

@Composable
fun MentzerTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, content = content)
}
'@

Write-Host ""
Write-Host "Done! Files created under $base" -ForegroundColor Cyan
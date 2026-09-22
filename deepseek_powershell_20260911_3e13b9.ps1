# Make-MentzerTracker.ps1
# Creates the complete MentzerTracker Android project on Windows.

$ErrorActionPreference = "Stop"
$root = "MentzerTracker"

if (Test-Path $root)        { Remove-Item -Recurse -Force $root }
if (Test-Path "$root.zip")  { Remove-Item -Force "$root.zip" }

function New-File {
    param([string]$Path, [string]$Content)
    $full = Join-Path $PWD $Path
    $dir  = Split-Path $full -Parent
    if ($dir -and !(Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    [System.IO.File]::WriteAllText($full, $Content, [System.Text.UTF8Encoding]::new($false))
}

# ============================================================
#  ROOT GRADLE FILES
# ============================================================

New-File "$root/settings.gradle.kts" @'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "MentzerTracker"
include(":app")
'@

New-File "$root/build.gradle.kts" @'
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
}
'@

New-File "$root/gradle.properties" @'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
'@

New-File "$root/.gitignore" @'
*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
*.jks
*.keystore
keystore.properties
'@

# ============================================================
#  APP BUILD + MANIFEST
# ============================================================

New-File "$root/app/build.gradle.kts" @'
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}

android {
    namespace = "com.example.mentzertracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mentzertracker"
        minSdk = 24; targetSdk = 34
        versionCode = 1; versionName = "1.0"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
'@

New-File "$root/app/proguard-rules.pro" "# empty – add rules here if you enable minify" 

New-File "$root/app/src/main/AndroidManifest.xml" @'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="Mentzer Tracker"
        android:theme="@android:style/Theme.Material.NoActionBar"
        android:supportsRtl="true">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
'@

# ============================================================
#  KOTLIN — DATA LAYER
# ============================================================

New-File "$root/app/src/main/java/com/example/mentzertracker/data/Entities.kt" @'
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

New-File "$root/app/src/main/java/com/example/mentzertracker/data/Daos.kt" @'
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

New-File "$root/app/src/main/java/com/example/mentzertracker/data/AppDatabase.kt" @'
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

New-File "$root/app/src/main/java/com/example/mentzertracker/data/SeedData.kt" @'
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
        "Day A - Full Body"   to listOf(0,1,2,3,4,5,6),
        "Day B - Chest / Back" to listOf(0,1,3,4)
    )
}
'@

# ============================================================
#  KOTLIN — VIEWMODEL
# ============================================================

New-File "$root/app/src/main/java/com/example/mentzertracker/WorkoutViewModel.kt" @'
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

# ============================================================
#  KOTLIN — UI
# ============================================================

New-File "$root/app/src/main/java/com/example/mentzertracker/ui/theme/Theme.kt" @'
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

New-File "$root/app/src/main/java/com/example/mentzertracker/ui/TodayScreen.kt" @'
package com.example.mentzertracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.SetInput
import com.example.mentzertracker.WorkoutViewModel
import com.example.mentzertracker.data.Exercise
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(vm: WorkoutViewModel) {
    val exercises by vm.exercises.collectAsState()
    val inputs by vm.inputs.collectAsState()
    val templates by vm.templates.collectAsState()
    val heavy by vm.heavyDutyMode.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            item { RestReminder() }
            item { RestTimer() }

            item {
                Column {
                    Text("Templates", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        templates.forEach { t ->
                            AssistChip(onClick = { vm.loadTemplate(t.id) },
                                label = { Text(t.name) })
                        }
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Heavy Duty Mode (1 set to failure)",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = heavy, onCheckedChange = { vm.toggleHeavyDuty() })
                }
            }

            items(exercises, key = { it.id }) { ex ->
                ExerciseCard(
                    exercise = ex,
                    sets = inputs[ex.id] ?: List(if (heavy) 1 else 3) { SetInput() },
                    onSetChange = { idx, w, r -> vm.updateSet(ex.id, idx, w, r) }
                )
            }

            item {
                Button(onClick = { vm.logWorkout() },
                    modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp))
                    Text("LOG WORKOUT", fontWeight = FontWeight.Bold)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun RestReminder() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("REST 3-5 MIN BETWEEN SETS",
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Optimized for hypertrophy. Train to failure - one all-out set is the Mentzer ideal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RestTimer() {
    var selectedMin by remember { mutableIntStateOf(3) }
    var remaining by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000L)
            remaining -= 1
        }
        if (remaining == 0) running = false
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Rest Timer", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("%d:%02d".format(remaining / 60, remaining % 60),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 4, 5).forEach { m ->
                    FilterChip(
                        selected = selectedMin == m,
                        onClick = { selectedMin = m; if (!running) remaining = m * 60 },
                        label = { Text("${m}m") }
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (running) running = false
                    else { if (remaining <= 0) remaining = selectedMin * 60; running = true }
                }) { Text(if (running) "Stop" else "Start") }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise,
    sets: List<SetInput>,
    onSetChange: (Int, String?, String?) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(exercise.cue, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                Text("Set", Modifier.width(36.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Weight (lbs)", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("Reps", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))

            sets.forEachIndexed { idx, s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${idx + 1}", Modifier.width(36.dp), fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = s.weight,
                        onValueChange = { onSetChange(idx, it, null) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("lbs") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = s.reps,
                        onValueChange = { onSetChange(idx, null, it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }
    }
}
'@

New-File "$root/app/src/main/java/com/example/mentzertracker/ui/HistoryScreen.kt" @'
package com.example.mentzertracker.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(vm: WorkoutViewModel, context: Context) {
    val history by vm.history.collectAsState()
    val fmt = remember { SimpleDateFormat("EEE, MMM d yyyy - h:mm a", Locale.getDefault()) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No workouts logged yet.\nLog one from the Today tab.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val grouped = history.groupBy { it.sessionDate }
            LazyColumn(Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {

                item {
                    Button(onClick = {
                        val path = vm.exportCsv(context)
                        if (path != null) Toast.makeText(context,
                            "CSV saved: $path", Toast.LENGTH_LONG).show()
                    }) { Text("Export CSV") }
                    Spacer(Modifier.height(8.dp))
                }

                grouped.forEach { (date, sets) ->
                    item(key = "h_$date") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(fmt.format(Date(date)),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.deleteSession(date) }) {
                                Icon(Icons.Filled.Delete, "Delete session")
                            }
                        }
                    }
                    val byExercise = sets.groupBy { it.exerciseId }
                    items(byExercise.values.toList(),
                        key = { "e_${date}_${it.first().exerciseId}" }) { exSets ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(exSets.first().exerciseName, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                exSets.sortedBy { it.setNumber }.forEach { s ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Set ${s.setNumber}:  ${s.reps} reps @ ${formatWeight(s.weight)} lbs",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f))
                                        IconButton(onClick = { vm.deleteSet(s.id) }) {
                                            Icon(Icons.Filled.Close, "Delete set",
                                                modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

private fun formatWeight(w: Float): String =
    if (w % 1f == 0f) w.toInt().toString() else w.toString()
'@

New-File "$root/app/src/main/java/com/example/mentzertracker/ui/PRsScreen.kt" @'
package com.example.mentzertracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mentzertracker.WorkoutViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PRsScreen(vm: WorkoutViewModel) {
    val prs by vm.prs.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val fmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        item {
            Text("Personal Records", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Auto-tracked when you log a heavier set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }

        val prMap = prs.associateBy { it.exerciseId }
        items(exercises, key = { it.id }) { ex ->
            val pr = prMap[ex.id]
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (pr != null) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(ex.name, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        if (pr == null) {
                            Text("No PR yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("${formatW(pr.maxWeight)} lbs x ${pr.repsAtMaxWeight} reps",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                            Text("Achieved ${fmt.format(Date(pr.dateAchieved))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun formatW(w: Float): String =
    if (w % 1f == 0f) w.toInt().toString() else w.toString()
'@

# ============================================================
#  KOTLIN — MainActivity
# ============================================================

New-File "$root/app/src/main/java/com/example/mentzertracker/MainActivity.kt" @'
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

# ============================================================
#  GITHUB ACTIONS WORKFLOW
# ============================================================

New-File "$root/.github/workflows/build.yml" @'
name: Build APK

on:
  push:
    branches: [ main, master ]
  pull_request:
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Grant execute permission for gradlew
        run: chmod +x ./gradlew || true

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3

      - name: Build Debug APK
        run: ./gradlew assembleDebug --no-daemon

      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: MentzerTracker-debug
          path: app/build/outputs/apk/debug/*.apk

      - name: Decode keystore
        if: ${{ secrets.KEYSTORE_BASE64 != '' }}
        run: |
          echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > release.keystore
          cat > keystore.properties <<EOF
          storeFile=release.keystore
          storePassword=${{ secrets.STORE_PASSWORD }}
          keyAlias=${{ secrets.KEY_ALIAS }}
          keyPassword=${{ secrets.KEY_PASSWORD }}
          EOF

      - name: Build Release APK (signed)
        if: ${{ secrets.KEYSTORE_BASE64 != '' }}
        run: ./gradlew assembleRelease --no-daemon

      - name: Upload Release APK
        if: ${{ secrets.KEYSTORE_BASE64 != '' }}
        uses: actions/upload-artifact@v4
        with:
          name: MentzerTracker-release
          path: app/build/outputs/apk/release/*.apk
'@

# ============================================================
#  ZIP IT
# ============================================================

if (Get-Command Compress-Archive -ErrorAction SilentlyContinue) {
    Compress-Archive -Path $root -DestinationPath "$root.zip" -Force
    Write-Host ""
    Write-Host "==================================================" -ForegroundColor Green
    Write-Host " DONE!  Created $PWD\$root.zip" -ForegroundColor Green
    Write-Host "==================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:"
    Write-Host "  1. Install JDK 17 (winget install EclipseAdoptium.Temurin.17.JDK)"
    Write-Host "  2. Install Gradle  (scoop install gradle   OR   choco install gradle)"
    Write-Host "  3. cd $root"
    Write-Host "  4. gradle wrapper --gradle-version 8.5"
    Write-Host "  5. .\gradlew assembleDebug"
} else {
    Write-Host "Zip skipped (Compress-Archive unavailable). Project is in: $PWD\$root" -ForegroundColor Yellow
}
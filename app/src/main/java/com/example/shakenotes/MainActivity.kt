package com.example.shakenotes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val overlayGranted = mutableStateOf(false)
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)

        if (store.shakeEnabled) ShakeService.start(this)
        setContent { MaterialTheme { Surface { AppScreen(store, overlayGranted.value) } } }
    }

    override fun onResume() {
        super.onResume()
        overlayGranted.value = Settings.canDrawOverlays(this)
    }
}

@Composable
fun AppScreen(store: Store, overlayGranted: Boolean) {
    val expenses = remember { mutableStateListOf<Expense>().apply { addAll(store.loadExpenses()) } }
    val notes = remember { mutableStateListOf<Note>().apply { addAll(store.loadNotes()) } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var addingExpense by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }

    Scaffold(floatingActionButton = {
        FloatingActionButton(onClick = {
            if (tab == 0) addingExpense = true else editingNote = Note(0, "", "", 0)
        }) { Icon(Icons.Default.Add, "Add") }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ShakeCard(store, overlayGranted)
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Expenses") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Notes") })
            }
            if (tab == 0) {
                ExpenseList(expenses) { expenses.remove(it); store.saveExpenses(expenses) }
            } else {
                NoteList(notes, onOpen = { editingNote = it }) {
                    notes.remove(it); store.saveNotes(notes)
                }
            }
        }
    }

    if (addingExpense) ExpenseDialog(onDismiss = { addingExpense = false }) {
        expenses.add(0, it); store.saveExpenses(expenses); addingExpense = false
    }
    editingNote?.let { note ->
        NoteDialog(note, onDismiss = { editingNote = null }) { saved ->
            val i = notes.indexOfFirst { it.id == saved.id }
            if (i >= 0) notes[i] = saved else notes.add(0, saved)
            store.saveNotes(notes); editingNote = null
        }
    }
}

@Composable
fun ShakeCard(store: Store, overlayGranted: Boolean) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(store.shakeEnabled) }
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Shake to open", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (overlayGranted) "Shake your phone twice to open this app"
                    else "Needs the \"Display over other apps\" permission",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(checked = enabled, onCheckedChange = { on ->
                enabled = on; store.shakeEnabled = on
                if (on) ShakeService.start(ctx) else ShakeService.stop(ctx)
            })
        }
        if (enabled && !overlayGranted) {
            TextButton(
                onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")))
                },
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            ) { Text("Grant permission") }
        }
    }
}

@Composable
fun ExpenseList(expenses: List<Expense>, onDelete: (Expense) -> Unit) {
    val money = remember { NumberFormat.getCurrencyInstance() }
    val thisMonth = expenses.filter { isThisMonth(it.time) }
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Spent this month", style = MaterialTheme.typography.labelLarge)
                    Text(money.format(thisMonth.sumOf { it.amount }),
                        style = MaterialTheme.typography.headlineMedium)
                    thisMonth.groupBy { it.category }.forEach { (cat, list) ->
                        Text("$cat · ${money.format(list.sumOf { it.amount })}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (expenses.isEmpty()) item {
            Text("No expenses yet — tap + to add one.", Modifier.padding(16.dp))
        }
        items(expenses, key = { it.id }) { e ->
            ListItem(
                headlineContent = { Text(e.title) },
                supportingContent = { Text("${e.category} · ${formatDate(e.time)}") },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(money.format(e.amount))
                        IconButton(onClick = { onDelete(e) }) { Icon(Icons.Default.Delete, "Delete") }
                    }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun NoteList(notes: List<Note>, onOpen: (Note) -> Unit, onDelete: (Note) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        if (notes.isEmpty()) item {
            Text("No notes yet — tap + to write one.", Modifier.padding(16.dp))
        }
        items(notes, key = { it.id }) { n ->
            ListItem(
                modifier = Modifier.clickable { onOpen(n) },
                headlineContent = { Text(n.title.ifBlank { "Untitled" }) },
                supportingContent = { Text(n.body, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    IconButton(onClick = { onDelete(n) }) { Icon(Icons.Default.Delete, "Delete") }
                }
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun ExpenseDialog(onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(CATEGORIES[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(title, { title = it }, label = { Text("What for? (optional)") },
                    singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CATEGORIES.forEach { c ->
                        FilterChip(selected = c == category, onClick = { category = c },
                            label = { Text(c) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.replace(',', '.').toDoubleOrNull()
                if (a != null && a > 0) {
                    val now = System.currentTimeMillis()
                    onSave(Expense(now, title.ifBlank { category }, a, category, now))
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun NoteDialog(note: Note, onDismiss: () -> Unit, onSave: (Note) -> Unit) {
    var title by remember(note) { mutableStateOf(note.title) }
    var body by remember(note) { mutableStateOf(note.body) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (note.id == 0L) "New note" else "Edit note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(body, { body = it }, label = { Text("Note") }, minLines = 4)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val now = System.currentTimeMillis()
                if (title.isBlank() && body.isBlank()) onDismiss()
                else onSave(note.copy(id = if (note.id == 0L) now else note.id,
                    title = title, body = body, time = now))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

fun isThisMonth(t: Long): Boolean {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = t }
    return now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.MONTH) == c.get(Calendar.MONTH)
}

fun formatDate(t: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(t))

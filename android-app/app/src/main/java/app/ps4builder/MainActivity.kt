package app.ps4builder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainVm = viewModel()
            App(vm)
        }
    }
}

private fun mb(n: Long) = "%.2f MB".format(n / 1048576.0)

@Composable
fun App(vm: MainVm) {
    val ar = vm.settings.lang == "ar"
    val s = S(ar)
    var tab by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(Modifier.fillMaxSize()) {
                if (vm.showLog) {
                    LogViewer(vm, s)
                } else {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        TabRow(selectedTabIndex = tab) {
                            listOf(s.tabBuild, s.tabHistory, s.tabSettings).forEachIndexed { i, label ->
                                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                            }
                        }
                        when (tab) {
                            0 -> BuildScreen(vm, s)
                            1 -> HistoryScreen(vm, s)
                            else -> SettingsScreen(vm, s)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BuildScreen(vm: MainVm, s: S) {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.startBuild(uri)
    }
    var copied by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
            enabled = !vm.working, modifier = Modifier.fillMaxWidth(),
        ) { Text(s.pickZip) }

        if (vm.working) {
            Text(vm.stage)
            LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { vm.cancelRun() }, enabled = vm.runId != null) { Text(s.cancel) }
        }
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        vm.success?.let { e ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.success, style = MaterialTheme.typography.titleMedium)
                    Text("${s.version}: ${e.version}")
                    Text("${s.size}: ${mb(e.size)}")
                    Button(onClick = { vm.send(e) }) { Text(s.sendPs4) }
                }
            }
        }
        vm.sendStatus?.let { Text(it) }

        vm.failed?.let { f ->
            Text("${s.failed} (build-${f.runNumber})", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            Text(s.errorsTitle, style = MaterialTheme.typography.labelLarge)
            Text(f.errors, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("build-log", vm.copyText()))
                    copied = true
                }) { Text(s.copyLog) }
                OutlinedButton(onClick = { vm.showLog = true }) { Text(s.viewLog) }
            }
            if (copied) Text(s.logCopied)
        }
    }
}

@Composable
fun HistoryScreen(vm: MainVm, s: S) {
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        vm.sendStatus?.let { Text(it); Spacer(Modifier.height(8.dp)) }
        if (vm.history.isEmpty()) Text(s.noHistory)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.history) { e ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("build-${e.runNumber} • ${fmt.format(Date(e.time))}")
                        Text(
                            (if (e.ok) "✔ " else "✘ ") + (if (e.ok) s.success else s.failed) +
                                (if (e.version.isNotBlank()) " • ${e.version}" else ""),
                            color = if (e.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        if (e.ok) {
                            Text("${s.size}: ${mb(e.size)}")
                            OutlinedButton(onClick = { vm.send(e) }) { Text(s.resend) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: MainVm, s: S) {
    var token by remember(vm.settings) { mutableStateOf(vm.settings.token) }
    var owner by remember(vm.settings) { mutableStateOf(vm.settings.owner) }
    var repo by remember(vm.settings) { mutableStateOf(vm.settings.repo) }
    var ip by remember(vm.settings) { mutableStateOf(vm.settings.ip) }
    var lang by remember(vm.settings) { mutableStateOf(vm.settings.lang) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(token, { token = it }, label = { Text(s.token) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(owner, { owner = it }, label = { Text(s.owner) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(repo, { repo = it }, label = { Text(s.repo) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ip, { ip = it }, label = { Text(s.ip) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(s.language)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = lang == "ar", onClick = { lang = "ar" }, label = { Text("العربية") })
            FilterChip(selected = lang == "en", onClick = { lang = "en" }, label = { Text("English") })
        }
        Button(onClick = { vm.saveSettings(Settings(token, owner, repo, ip, lang)) }) { Text(s.save) }
        vm.settingsMsg?.let { Text(it) }
    }
}

@Composable
fun LogViewer(vm: MainVm, s: S) {
    BackHandler { vm.showLog = false }
    val lines = remember(vm.fullLog) { vm.fullLog.lines() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(8.dp)) {
        OutlinedButton(onClick = { vm.showLog = false }) { Text(s.close) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(lines) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
        }
    }
}

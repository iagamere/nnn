package app.ps4builder

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class FailInfo(val errors: String, val logTail: String, val runNumber: Int)

class MainVm(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private var store: SecureStore? = null
    private val historyFile = File(ctx.filesDir, "history.json")
    private val elfDir = File(ctx.filesDir, "elfs").apply { mkdirs() }

    var settings by mutableStateOf(Settings())
    var settingsMsg by mutableStateOf<String?>(null)

    var working by mutableStateOf(false)
    var stage by mutableStateOf("")
    var progress by mutableStateOf(0f)
    var runId by mutableStateOf<Long?>(null)
    var error by mutableStateOf<String?>(null)
    var success by mutableStateOf<HistoryEntry?>(null)
    var failed by mutableStateOf<FailInfo?>(null)
    var fullLog by mutableStateOf("")
    var showLog by mutableStateOf(false)
    var sendStatus by mutableStateOf<String?>(null)
    val history = mutableStateListOf<HistoryEntry>()

    private val s get() = S(settings.lang == "ar")

    init {
        try {
            store = SecureStore(ctx).also { settings = it.load() }
        } catch (e: Exception) {
            error = "Secure storage unavailable: ${e.message}"
        }
        history.addAll(History.load(historyFile))
    }

    fun saveSettings(n: Settings) {
        val st = store
        if (st == null) { settingsMsg = "Secure storage unavailable"; return }
        try { st.save(n); settings = n.copy(token = n.token.trim()); settingsMsg = S(n.lang == "ar").saved }
        catch (e: Exception) { settingsMsg = "Save failed: ${e.message}" }
    }

    private fun displayName(uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "project.zip"

    private fun reset() {
        error = null; success = null; failed = null; sendStatus = null; fullLog = ""; progress = 0f
    }

    fun startBuild(uri: Uri) {
        if (working) return
        val st = settings
        if (st.token.isBlank() || st.owner.isBlank() || st.repo.isBlank()) { error = s.fillSettings; return }
        reset(); working = true
        viewModelScope.launch(Dispatchers.IO) {
            try { runBuild(uri, st) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: e.toString() }
            finally { working = false; runId = null }
        }
    }

    private suspend fun runBuild(uri: Uri, st: Settings) {
        val api = GitHubApi(st.token.trim(), st.owner.trim(), st.repo.trim())
        stage = s.reading
        val name = displayName(uri)
        val data = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw ApiException("Cannot read the selected file")
        stage = s.uploading
        val sha = api.uploadZip(name, data)
        stage = s.waitingRun
        var found: RunInfo? = null
        for (i in 0 until 30) {
            found = api.findRun(sha)
            if (found != null) break
            delay(3000)
        }
        val first = found ?: throw ApiException(s.noRunFound)
        runId = first.id
        var cur = first
        while (true) {
            cur = api.getRun(first.id)
            val sp = api.steps(first.id)
            stage = sp.current?.let { s.running(it) } ?: s.waitingRun
            progress = if (sp.total > 0) sp.done / sp.total.toFloat() else 0f
            if (cur.status == "completed") break
            delay(4000)
        }
        if (cur.conclusion == "cancelled") { error = s.cancelled; return }
        finish(api, cur)
    }

    private fun finish(api: GitHubApi, run: RunInfo) {
        val tag = "build-${run.number}"
        val assets = api.releaseAssets(tag) ?: throw ApiException(s.noRelease(run.conclusion))
        fun asset(n: String) = assets.firstOrNull { it.name == n }
            ?: throw ApiException("Release $tag has no asset '$n'")
        val status = JSONObject(String(api.downloadAsset(asset("status.json").id)))
        val ok = status.optBoolean("ok", false)
        val version = status.optString("version")
        val now = System.currentTimeMillis()
        if (ok) {
            val elfName = status.getString("elf")
            val bytes = api.downloadAsset(asset(elfName).id)
            val local = "$tag-$elfName"
            File(elfDir, local).writeBytes(bytes)
            val e = HistoryEntry(run.number, now, true, version, local, bytes.size.toLong())
            addHistory(e); success = e
        } else {
            val errors = String(api.downloadAsset(asset("errors.txt").id))
            val log = String(api.downloadAsset(asset("build.log").id))
            failed = FailInfo(errors, log.lines().takeLast(200).joinToString("\n"), run.number)
            fullLog = log
            addHistory(HistoryEntry(run.number, now, false, version, null, 0))
        }
    }

    private fun addHistory(e: HistoryEntry) {
        history.add(0, e)
        History.save(historyFile, history.toList())
    }

    fun cancelRun() {
        val id = runId ?: return
        val st = settings
        viewModelScope.launch(Dispatchers.IO) {
            try { GitHubApi(st.token.trim(), st.owner.trim(), st.repo.trim()).cancel(id) }
            catch (e: Exception) { error = e.message }
        }
    }

    fun copyText(): String {
        val f = failed ?: return ""
        return f.errors + "\n\n--- last 200 lines of build.log ---\n" + f.logTail
    }

    fun send(entry: HistoryEntry) {
        val ip = settings.ip.trim()
        if (ip.isEmpty()) { sendStatus = s.fillIp; return }
        val f = entry.elf?.let { File(elfDir, it) }
        if (f == null || !f.exists()) { sendStatus = s.elfMissing; return }
        sendStatus = s.sending(ip)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Ps4.send(ip, f)
                sendStatus = s.checking
                delay(3000)
                sendStatus = when (val r = Ps4.info(ip)) {
                    is Ps4.Info.Ok -> s.serverOk(r.version)
                    is Ps4.Info.NoResponse -> s.serverNo(r.reason)
                }
            } catch (e: Exception) {
                sendStatus = s.sendFailed(e.message ?: e.toString())
            }
        }
    }
}

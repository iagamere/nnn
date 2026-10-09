package com.abdo.ps4builder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { PayloadApp() } }
}

data class BuildRecord(val date: String, val ok: Boolean, val version: String, val elfPath: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayloadApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { GithubApi() }
    val prefs = remember { securePrefs(ctx) }
    var owner by remember { mutableStateOf(prefs.getString("owner", "") ?: "") }
    var repo by remember { mutableStateOf(prefs.getString("repo", "") ?: "") }
    var token by remember { mutableStateOf(prefs.getString("token", "") ?: "") }
    var ps4Ip by remember { mutableStateOf(prefs.getString("ip", "") ?: "") }
    var english by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var runId by remember { mutableStateOf<Long?>(null) }
    var stage by remember { mutableStateOf(if (english) "Ready" else "جاهز") }
    var logText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf("") }
    var selectedElf by remember { mutableStateOf<File?>(null) }
    var elfVersion by remember { mutableStateOf("") }
    var elfSize by remember { mutableStateOf(0L) }
    var history by remember { mutableStateOf(listOf<BuildRecord>()) }
    var infoResult by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            persist(prefs, owner, repo, token, ps4Ip)
            if (owner.isBlank() || repo.isBlank() || token.isBlank()) { stage = tr(english, "أدخل owner/repo وtoken أولاً", "Enter owner/repo and token first"); return@launch }
            busy = true; logText = ""; errorText = ""; stage = tr(english, "جارٍ قراءة ZIP...", "Reading ZIP...")
            try {
                val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("تعذر قراءة الملف") }
                require(bytes.isNotEmpty()) { "ZIP فارغ" }
                val remotePath = "incoming/${System.currentTimeMillis()}-${uri.lastPathSegment?.substringAfterLast('/')?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "source.zip"}.zip"
                stage = tr(english, "رفع المصدر إلى GitHub...", "Uploading source to GitHub...")
                api.uploadZip(owner, repo, token, remotePath, bytes)
                stage = tr(english, "انتظار workflow...", "Waiting for workflow...")
                delay(2500)
                val run = api.findLatestRun(owner, repo, token)
                runId = run
                var done = false
                while (!done) {
                    delay(4000)
                    val state = api.getRun(owner, repo, token, run)
                    stage = "${state.first} — ${state.second}"
                    done = state.first == "completed"
                }
                val conclusion = api.getConclusion(owner, repo, token, run)
                if (conclusion == "success") {
                    stage = tr(english, "اكتمل البناء، جارٍ تنزيل Release...", "Build succeeded; downloading release...")
                    val assets = api.getReleaseAssets(owner, repo, token, "build-${api.getRunNumber(owner, repo, token, run)}")
                    val elfAsset = assets.firstOrNull { it.first.endsWith(".elf") } ?: error("Release لا يحتوي ملف ELF")
                    val elfBytes = api.downloadAsset(owner, repo, token, elfAsset.second)
                    val file = File(ctx.filesDir, elfAsset.first); file.writeBytes(elfBytes)
                    selectedElf = file; elfSize = file.length(); elfVersion = elfAsset.first.removePrefix("ezremote-server-").removeSuffix(".elf")
                    history = listOf(BuildRecord(Instant.now().toString(), true, elfVersion, file.absolutePath)) + history
                    logText = api.downloadReleaseText(owner, repo, token, "build-${api.getRunNumber(owner, repo, token, run)}", "build.log")
                    stage = tr(english, "نجح البناء", "Build succeeded")
                } else {
                    stage = tr(english, "فشل البناء؛ جارٍ جلب السجلات...", "Build failed; fetching logs...")
                    val artifact = api.getLatestArtifact(owner, repo, token, run)
                    val zip = api.downloadArtifact(owner, repo, token, artifact)
                    val diag = File(ctx.cacheDir, "diagnostics.zip"); diag.writeBytes(zip)
                    val files = unzipTextFiles(ctx, diag)
                    errorText = files["errors.txt"] ?: "errors.txt غير موجود في artifact. راجع سجل Actions."
                    logText = files["build.log"] ?: "build.log غير موجود في artifact."
                    history = listOf(BuildRecord(Instant.now().toString(), false, "unknown")) + history
                    stage = tr(english, "فشل البناء", "Build failed")
                }
            } catch (e: Exception) { stage = "${tr(english, "خطأ", "Error")}: ${e.message ?: e.javaClass.simpleName}" }
            finally { busy = false }
        }
    }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("PS4 Payload Builder", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { english = !english }) { Text(if (english) "العربية" else "English") }
            }
            OutlinedTextField(owner, { owner = it }, label = { Text("GitHub owner") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(repo, { repo = it }, label = { Text("Repository") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(token, { token = it }, label = { Text("Fine-grained GitHub token") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
            OutlinedTextField(ps4Ip, { ps4Ip = it }, label = { Text("PS4 IP address") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = { persist(prefs, owner, repo, token, ps4Ip); stage = tr(english, "حُفظت الإعدادات", "Settings saved") }, modifier = Modifier.fillMaxWidth()) { Text(tr(english, "حفظ الإعدادات", "Save settings")) }
            Button(enabled = !busy, onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text(tr(english, "اختر ZIP وابدأ البناء", "Choose ZIP and build")) }
            if (busy && runId != null) OutlinedButton(onClick = { scope.launch { try { api.cancelRun(owner, repo, token, runId!!); stage = tr(english, "أُرسل طلب الإلغاء إلى GitHub", "Cancellation requested from GitHub") } catch (e: Exception) { stage = "Cancel error: ${e.message}" } } }, modifier = Modifier.fillMaxWidth()) { Text(tr(english, "إلغاء البناء", "Cancel build")) }
            Text(tr(english, "الحالة", "Status") + ": $stage")
            if (selectedElf != null) {
                Text("ELF: ${selectedElf!!.name}\nVersion: $elfVersion\nSize: ${formatSize(elfSize)}")
                Button(onClick = { scope.launch {
                    try { stage = tr(english, "جارٍ الإرسال إلى BinLoader...", "Sending to BinLoader..."); withContext(Dispatchers.IO) { sendElf(ps4Ip, selectedElf!!) }; delay(3000); infoResult = withContext(Dispatchers.IO) { queryVersion(ps4Ip) }; stage = infoResult }
                    catch (e: Exception) { infoResult = "${tr(english, "لم يستجب السيرفر أو فشل الاتصال", "Server did not respond or connection failed")}: ${e.message}"; stage = infoResult }
                } }, modifier = Modifier.fillMaxWidth()) { Text(tr(english, "أرسل إلى PS4", "Send to PS4")) }
                Text(infoResult)
            }
            if (errorText.isNotBlank()) {
                Text(tr(english, "أخطاء البناء", "Build errors"), style = MaterialTheme.typography.titleMedium)
                Text(errorText)
                Button(onClick = { val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clip.setPrimaryClip(ClipData.newPlainText("build diagnostics", errorText + "\n\n--- Last 200 build log lines ---\n" + logText.lines().takeLast(200).joinToString("\n"))) }) { Text(tr(english, "انسخ السجل", "Copy diagnostics")) }
            }
            if (logText.isNotBlank()) {
                var showLog by remember { mutableStateOf(false) }
                TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) tr(english, "إخفاء build.log", "Hide build.log") else tr(english, "عرض build.log كاملاً", "Show full build.log")) }
                if (showLog) Text(logText)
            }
            if (history.isNotEmpty()) {
                Text(tr(english, "سجل البناءات", "Build history"), style = MaterialTheme.typography.titleMedium)
                history.forEach { item ->
                    Text("${item.date} | ${if(item.ok) "OK" else "FAILED"} | ${item.version}")
                    if (item.ok && item.elfPath.isNotBlank()) TextButton(onClick = { val f = File(item.elfPath); if (f.exists()) { selectedElf = f; elfVersion = item.version; elfSize = f.length() } else stage = tr(english, "ملف ELF السابق غير موجود محلياً", "Previous ELF is not available locally") }) { Text(tr(english, "إعادة اختيار ELF", "Select previous ELF")) }
                }
            }
            Text("Security: token is encrypted at rest; only use a token limited to this repository.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun tr(en: Boolean, ar: String, eng: String) = if (en) eng else ar
private fun formatSize(n: Long): String = if (n < 1024) "$n B" else if (n < 1024*1024) "%.2f KiB".format(n/1024.0) else "%.2f MiB".format(n/(1024.0*1024.0))
private fun securePrefs(ctx: Context) = EncryptedSharedPreferences.create(ctx, "secure_settings", MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
private fun persist(p: android.content.SharedPreferences, owner: String, repo: String, token: String, ip: String) { p.edit().putString("owner", owner).putString("repo", repo).putString("token", token).putString("ip", ip).apply() }

private fun sendElf(ip: String, file: File) {
    require(ip.isNotBlank()) { "PS4 IP is empty" }
    Socket().use { socket -> socket.connect(InetSocketAddress(ip, 9090), 8000); socket.soTimeout = 8000; file.inputStream().use { input -> socket.getOutputStream().use { out -> input.copyTo(out, 64*1024); out.flush() } } }
}
private fun queryVersion(ip: String): String {
    val req = Request.Builder().url("http://$ip:6701/v2/info").get().build()
    OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
        if (!r.isSuccessful) return "HTTP ${r.code}; server did not confirm version"
        val body = r.body?.string() ?: return "Empty /v2/info response"
        val version = JSONObject(body).optString("version", "")
        return if (version.isNotBlank()) "Server responded; version=$version" else "Server responded, but JSON has no version field: $body"
    }
}
private fun unzipTextFiles(ctx: Context, zip: File): Map<String,String> {
    val out=mutableMapOf<String,String>(); java.util.zip.ZipInputStream(zip.inputStream()).use { z -> var e=z.nextEntry; while(e!=null) { if (!e.isDirectory && (e.name.endsWith(".txt") || e.name.endsWith(".log") || e.name.endsWith(".json"))) out[e.name.substringAfterLast('/')] = z.readBytes().toString(Charsets.UTF_8); e=z.nextEntry } }; return out
}

private class GithubApi {
    private val client = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()
    private fun req(url:String, token:String) = Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
    private fun response(r: Request): String = client.newCall(r).execute().use { if (!it.isSuccessful) error("GitHub HTTP ${it.code}: ${it.body?.string()?.take(500)}") else it.body?.string() ?: "" }
    fun uploadZip(owner:String, repo:String, token:String, path:String, bytes:ByteArray) {
        val json=JSONObject().put("message", "Upload PS4 build source ${path.substringAfterLast('/')}").put("content", Base64.getEncoder().encodeToString(bytes))
        val r=req("https://api.github.com/repos/$owner/$repo/contents/$path",token).put(json.toString().toRequestBody("application/json".toMediaType())).build(); response(r)
    }
    fun findLatestRun(owner:String, repo:String, token:String):Long {
        val j=JSONObject(response(req("https://api.github.com/repos/$owner/$repo/actions/runs?per_page=10",token).get().build())).getJSONArray("workflow_runs")
        if(j.length()==0) error("No workflow runs found")
        // Select most recent run whose name is Build PS4 ELF; polling starts after upload.
        for(i in 0 until j.length()) { val x=j.getJSONObject(i); if(x.optString("name").contains("Build PS4 ELF",true)) return x.getLong("id") }
        return j.getJSONObject(0).getLong("id")
    }
    fun getRun(owner:String,repo:String,token:String,id:Long):Pair<String,String> { val j=JSONObject(response(req("https://api.github.com/repos/$owner/$repo/actions/runs/$id",token).get().build())); return j.optString("status") to j.optString("conclusion", "in_progress") }
    fun getConclusion(owner:String,repo:String,token:String,id:Long)=JSONObject(response(req("https://api.github.com/repos/$owner/$repo/actions/runs/$id",token).get().build())).optString("conclusion")
    fun getRunNumber(owner:String,repo:String,token:String,id:Long)=JSONObject(response(req("https://api.github.com/repos/$owner/$repo/actions/runs/$id",token).get().build())).getLong("run_number")
    fun cancelRun(owner:String,repo:String,token:String,id:Long) { response(req("https://api.github.com/repos/$owner/$repo/actions/runs/$id/cancel",token).post("{}".toRequestBody("application/json".toMediaType())).build()) }
    fun getReleaseAssets(owner:String,repo:String,token:String,tag:String):List<Pair<String,String>> {
        val j=JSONObject(response(req("https://api.github.com/repos/$owner/$repo/releases/tags/$tag",token).get().build())).getJSONArray("assets")
        return (0 until j.length()).map { val a=j.getJSONObject(it); a.getString("name") to a.getString("url") }
    }
    fun downloadAsset(owner:String,repo:String,token:String,url:String):ByteArray { val r=req(url,token).header("Accept","application/octet-stream").get().build(); client.newCall(r).execute().use { if(!it.isSuccessful) error("Asset download HTTP ${it.code}"); return it.body?.bytes() ?: byteArrayOf() } }
    fun downloadReleaseText(owner:String,repo:String,token:String,tag:String,name:String):String { val a=getReleaseAssets(owner,repo,token,tag).firstOrNull{it.first==name} ?: return ""; return downloadAsset(owner,repo,token,a.second).toString(Charsets.UTF_8) }
    fun getLatestArtifact(owner:String,repo:String,token:String,run:Long):String { val j=JSONObject(response(req("https://api.github.com/repos/$owner/$repo/actions/runs/$run/artifacts",token).get().build())).getJSONArray("artifacts"); if(j.length()==0) error("No diagnostics artifact found"); return j.getJSONObject(0).getString("archive_download_url") }
    fun downloadArtifact(owner:String,repo:String,token:String,url:String):ByteArray { val r=req(url,token).header("Accept","application/vnd.github+json").get().build(); client.newCall(r).execute().use { if(!it.isSuccessful) error("Artifact download HTTP ${it.code}"); return it.body?.bytes() ?: byteArrayOf() } }
}

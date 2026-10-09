package app.ps4builder

import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = 0) : Exception(message)

data class RunInfo(val id: Long, val number: Int, val status: String, val conclusion: String?)
data class StepProgress(val current: String?, val done: Int, val total: Int)
data class Asset(val id: Long, val name: String)

class GitHubApi(private val token: String, private val owner: String, private val repo: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    private fun builder(path: String) = Request.Builder()
        .url("https://api.github.com/repos/$owner/$repo$path")
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")

    private fun errMessage(body: String): String =
        runCatching { JSONObject(body).optString("message") }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: body.take(200)

    private fun bytes(req: Request): ByteArray {
        val result: ByteArray
        try {
            result = client.newCall(req).execute().use { resp ->
                val data = resp.body?.bytes() ?: ByteArray(0)
                if (!resp.isSuccessful) {
                    throw ApiException(
                        "GitHub ${resp.code} (${req.method} ${req.url.encodedPath}): ${errMessage(String(data))}",
                        resp.code,
                    )
                }
                data
            }
        } catch (e: IOException) {
            throw ApiException("Network error: ${e.message}")
        }
        return result
    }

    private fun text(req: Request) = String(bytes(req))

    /** Creates ONE commit containing the zip (Contents API). Returns the commit sha. */
    fun uploadZip(displayName: String, data: ByteArray): String {
        var safe = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (!safe.endsWith(".zip", ignoreCase = true)) safe += ".zip"
        val path = "incoming/${System.currentTimeMillis()}-$safe"
        val body = JSONObject()
            .put("message", "upload $safe")
            .put("content", Base64.encodeToString(data, Base64.NO_WRAP))
            .toString().toRequestBody("application/json".toMediaType())
        val resp = text(builder("/contents/$path").put(body).build())
        return JSONObject(resp).getJSONObject("commit").getString("sha")
    }

    private fun parseRun(o: JSONObject) = RunInfo(
        id = o.getLong("id"),
        number = o.getInt("run_number"),
        status = o.getString("status"),
        conclusion = if (o.isNull("conclusion")) null else o.getString("conclusion"),
    )

    fun findRun(sha: String): RunInfo? {
        val arr = JSONObject(text(builder("/actions/runs?head_sha=$sha&per_page=20").get().build()))
            .getJSONArray("workflow_runs")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("path").endsWith("build.yml")) return parseRun(o)
        }
        return null
    }

    fun getRun(id: Long): RunInfo =
        parseRun(JSONObject(text(builder("/actions/runs/$id").get().build())))

    fun steps(runId: Long): StepProgress {
        val jobs = JSONObject(text(builder("/actions/runs/$runId/jobs").get().build())).getJSONArray("jobs")
        if (jobs.length() == 0) return StepProgress(null, 0, 0)
        val st = jobs.getJSONObject(0).optJSONArray("steps") ?: return StepProgress(null, 0, 0)
        var done = 0
        var current: String? = null
        for (i in 0 until st.length()) {
            val s = st.getJSONObject(i)
            when (s.optString("status")) {
                "completed" -> done++
                "in_progress" -> if (current == null) current = s.optString("name")
            }
        }
        return StepProgress(current, done, st.length())
    }

    fun cancel(runId: Long) {
        text(builder("/actions/runs/$runId/cancel").post("".toRequestBody(null)).build())
    }

    /** Returns assets of release [tag], or null if the release does not exist. */
    fun releaseAssets(tag: String): List<Asset>? {
        val body = try {
            text(builder("/releases/tags/$tag").get().build())
        } catch (e: ApiException) {
            if (e.code == 404) return null else throw e
        }
        val arr = JSONObject(body).getJSONArray("assets")
        return (0 until arr.length()).map {
            val a = arr.getJSONObject(it)
            Asset(a.getLong("id"), a.getString("name"))
        }
    }

    fun downloadAsset(id: Long): ByteArray =
        bytes(builder("/releases/assets/$id").header("Accept", "application/octet-stream").get().build())
}

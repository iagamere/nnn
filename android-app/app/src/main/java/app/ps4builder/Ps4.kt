package app.ps4builder

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

object Ps4 {
    const val LOADER_PORT = 9090   // GoldHEN BinLoader (as specified by the user)
    const val INFO_PORT = 6701     // http://<ip>:6701/v2/info

    /** Opens TCP to ip:9090, writes the ELF bytes, closes. Throws on failure. */
    fun send(ip: String, elf: File) {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, LOADER_PORT), 5000)
            s.getOutputStream().use { out ->
                elf.inputStream().use { it.copyTo(out, 64 * 1024) }
                out.flush()
            }
        }
    }

    sealed interface Info {
        data class Ok(val version: String) : Info
        data class NoResponse(val reason: String) : Info
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun info(ip: String): Info = try {
        http.newCall(Request.Builder().url("http://$ip:$INFO_PORT/v2/info").build()).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) Info.NoResponse("HTTP ${r.code}")
            else {
                val v = runCatching { JSONObject(body).optString("version") }.getOrDefault("")
                if (v.isNotBlank()) Info.Ok(v) else Info.NoResponse("no 'version' field in response")
            }
        }
    } catch (e: Exception) {
        Info.NoResponse(e.message ?: e.toString())
    }
}

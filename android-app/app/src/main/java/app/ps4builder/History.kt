package app.ps4builder

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryEntry(
    val runNumber: Int,
    val time: Long,
    val ok: Boolean,
    val version: String,
    val elf: String?,   // file name inside filesDir/elfs, null for failed builds
    val size: Long,
)

object History {
    fun load(f: File): List<HistoryEntry> {
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                HistoryEntry(
                    o.getInt("run"), o.getLong("time"), o.getBoolean("ok"), o.optString("version"),
                    if (o.isNull("elf")) null else o.getString("elf"), o.optLong("size"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun save(f: File, list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("run", it.runNumber).put("time", it.time).put("ok", it.ok)
                    .put("version", it.version).put("elf", it.elf ?: JSONObject.NULL).put("size", it.size),
            )
        }
        f.writeText(arr.toString())
    }
}

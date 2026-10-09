package app.ps4builder

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class Settings(
    val token: String = "",
    val owner: String = "",
    val repo: String = "",
    val ip: String = "",
    val lang: String = "ar",
)

/** All settings (including the GitHub token) live in EncryptedSharedPreferences. Nothing is logged. */
class SecureStore(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx,
        "secure_settings",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun load() = Settings(
        token = prefs.getString("token", "") ?: "",
        owner = prefs.getString("owner", "") ?: "",
        repo = prefs.getString("repo", "") ?: "",
        ip = prefs.getString("ip", "") ?: "",
        lang = prefs.getString("lang", "ar") ?: "ar",
    )

    fun save(s: Settings) {
        prefs.edit()
            .putString("token", s.token.trim())
            .putString("owner", s.owner.trim())
            .putString("repo", s.repo.trim())
            .putString("ip", s.ip.trim())
            .putString("lang", s.lang)
            .apply()
    }
}

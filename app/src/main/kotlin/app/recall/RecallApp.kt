package app.recall

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class RecallApp : Application() {
    val models by lazy { ModelManager(this) }
    val db by lazy { IndexDb(this) }
    val media by lazy { MediaRepo(contentResolver) }
    val settings by lazy { Settings(this) }

    /** Full or partial (Android 14 "selected photos") access both count. */
    fun hasPhotoPermission(): Boolean = photoPermissions().any {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    fun hasFullPhotoAccess(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        fun photoPermissions(): Array<String> = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Vision token budget per photo: 70 fast, 140 balanced, 280 detailed. */
    var photoDetail: Int
        get() = prefs.getInt("photo_detail", 70)
        set(value) = prefs.edit().putInt("photo_detail", value).apply()
}

package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import android.util.Log
import com.example.data.model.GameApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class AppRepository(private val context: Context) {

    companion object {
        private const val TAG = "AppRepository"

        private val EMULATOR_KEYWORDS = listOf(
            "ppsspp", "retroarch", "dolphin", "aether", "nether",
            "duckstation", "citra", "skyline", "yuzu", "drastic",
            "mupen", "snes", "gba", "myboy", "myoldboy", "flycast",
            "redream", "scummvm", "epsxe", "fpinball", "fba", "mame"
        )
    }

    suspend fun getInstalledGamesAndApps(): List<GameApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                pm.queryIntentActivities(mainIntent, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying launcher intents: ${e.message}")
            emptyList()
        }

        val gameApps = mutableListOf<GameApp>()

        for (info in resolveInfos) {
            val pkgName = info.activityInfo.packageName
            // Skip Sunshine Cast itself from the remote launcher grid
            if (pkgName == context.packageName) continue

            val appName = try {
                info.loadLabel(pm).toString()
            } catch (e: Exception) {
                pkgName
            }

            val isGameCategory = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    info.activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_GAME
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }

            val pkgLower = pkgName.lowercase()
            val nameLower = appName.lowercase()
            val isEmulator = EMULATOR_KEYWORDS.any { pkgLower.contains(it) || nameLower.contains(it) }

            val iconBase64 = try {
                val iconDrawable = info.loadIcon(pm)
                drawableToBase64(iconDrawable)
            } catch (e: Exception) {
                ""
            }

            gameApps.add(
                GameApp(
                    packageName = pkgName,
                    appName = appName,
                    iconBase64 = iconBase64,
                    isPinned = isEmulator,
                    isEmulator = isEmulator,
                    category = if (isEmulator) "Emulator" else if (isGameCategory) "Game" else "App"
                )
            )
        }

        // Sort: Emulators first, then Games, then other Apps alphabetically
        gameApps.sortedWith(
            compareByDescending<GameApp> { it.isEmulator }
                .thenByDescending { it.category == "Game" }
                .thenBy { it.appName.lowercase() }
        )
    }

    fun launchPackage(packageName: String): Boolean {
        return try {
            if (packageName.equals("launcher", ignoreCase = true) || packageName.isEmpty()) {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(homeIntent)
                return true
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
                Log.i(TAG, "Successfully launched app package: $packageName")
                true
            } else {
                Log.w(TAG, "No launch intent found for package: $packageName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package $packageName", e)
            false
        }
    }

    private fun drawableToBase64(drawable: Drawable): String {
        val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(96) else 96
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(96) else 96
            val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(b)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            b
        }

        // Scale to 72x72 max for super fast JSON transmission
        val scaled = if (bitmap.width > 72 || bitmap.height > 72) {
            Bitmap.createScaledBitmap(bitmap, 72, 72, true)
        } else {
            bitmap
        }

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 90, baos)
        val bytes = baos.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

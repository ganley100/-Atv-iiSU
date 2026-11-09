package com.ganley.tiledeck

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/** Starting apps and games. */
object Launch {

    private val RETROARCH_64 = listOf("com.retroarch.aarch64", "com.retroarch", "com.retroarch.ra32")
    private val RETROARCH_32 = listOf("com.retroarch.ra32", "com.retroarch", "com.retroarch.aarch64")

    fun app(ctx: Context, app: Tile.App): Boolean {
        val direct = Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (start(ctx, direct)) return true
        val pm = ctx.packageManager
        val fallback = pm.getLeanbackLaunchIntentForPackage(app.component.packageName)
            ?: pm.getLaunchIntentForPackage(app.component.packageName)
            ?: return false
        return start(ctx, fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** The installed RetroArch package best suited to this device, if any. */
    fun retroArch(ctx: Context): String? {
        val order = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) RETROARCH_64 else RETROARCH_32
        return order.firstOrNull { isInstalled(ctx.packageManager, it) }
    }

    /** Default action: RetroArch if it's installed and the console has a core, otherwise ask. */
    fun play(ctx: Context, game: Tile.Game): Boolean {
        val ra = retroArch(ctx)
        if (ra != null && game.system.core != null && withRetroArch(ctx, game, ra)) return true
        return withOtherApp(ctx, game)
    }

    fun withRetroArch(ctx: Context, game: Tile.Game, pkg: String): Boolean {
        val core = game.system.core ?: return false
        val storage = Environment.getExternalStorageDirectory().absolutePath
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(pkg, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            putExtra("ROM", game.path)
            putExtra("LIBRETRO", "/data/data/$pkg/cores/${core}_libretro_android.so")
            putExtra("CONFIGFILE", "$storage/Android/data/$pkg/files/retroarch.cfg")
            putExtra("IME", Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
            putExtra("DATADIR", "/data/data/$pkg")
            putExtra("SDCARD", storage)
            putExtra("EXTERNAL", "$storage/Android/data/$pkg/files")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        return start(ctx, intent)
    }

    /** Hands the ROM to whatever emulator app wants it (shows Android's app picker). */
    fun withOtherApp(ctx: Context, game: Tile.Game): Boolean {
        val file = File(game.path)
        val uri = try {
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        } catch (e: IllegalArgumentException) {
            return false
        }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val chooser = Intent.createChooser(view, "Open ${game.title} with")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(ctx, chooser)
    }

    fun appInfo(ctx: Context, pkg: String): Boolean = start(
        ctx,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

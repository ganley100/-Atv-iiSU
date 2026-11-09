package com.ganley.tiledeck

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Loads app icons and game box art off the main thread, with small caches. */
class ArtLoader(ctx: Context) {

    private val pm = ctx.packageManager
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2)

    private val icons = LruCache<String, Drawable>(200)
    private val art = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val noArt: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    fun appIcon(app: Tile.App, done: (Drawable?) -> Unit) {
        icons.get(app.key)?.let { done(copy(it)); return }
        pool.execute {
            val d = try {
                val ai = pm.getActivityInfo(app.component, 0)
                val hasIcon = ai.iconResource != 0 || ai.applicationInfo.icon != 0
                if (hasIcon) ai.loadIcon(pm) else ai.loadBanner(pm) ?: ai.loadIcon(pm)
            } catch (e: Exception) {
                null
            }
            if (d != null) icons.put(app.key, d)
            main.post { done(d?.let(::copy)) }
        }
    }

    fun gameArt(game: Tile.Game, done: (Bitmap?) -> Unit) {
        art.get(game.key)?.let { done(it); return }
        if (game.key in noArt) { done(null); return }
        pool.execute {
            val bmp = findArt(game)?.let { decode(it, 320) }
            if (bmp != null) art.put(game.key, bmp) else noArt.add(game.key)
            main.post { done(bmp) }
        }
    }

    /** Forget "no art found" results so newly added images show up after a refresh. */
    fun forgetMissing() = noArt.clear()

    private fun copy(d: Drawable): Drawable = d.constantState?.newDrawable()?.mutate() ?: d

    /**
     * Looks for box art next to the ROM:
     *   ROMs/snes/Game.png, ROMs/snes/media/Game.png, ROMs/snes/media/covers/Game.png,
     *   ROMs/snes/images/Game-image.png, and ES-DE's downloaded_media folder.
     */
    private fun findArt(game: Tile.Game): File? {
        val rom = File(game.path)
        val dir = rom.parentFile ?: return null
        val base = rom.nameWithoutExtension
        val dirs = mutableListOf(
            dir,
            File(dir, "media"),
            File(dir, "media/covers"),
            File(dir, "media/images"),
            File(dir, "media/boxart"),
            File(dir, "covers"),
            File(dir, "boxart"),
            File(dir, "images"),
        )
        File(game.romRoot).parentFile?.let { storage ->
            val esde = File(storage, "ES-DE/downloaded_media/${game.folder}")
            dirs += File(esde, "covers")
            dirs += File(esde, "miximages")
            dirs += File(esde, "screenshots")
        }
        val names = listOf(base, "$base-image", "$base-thumb")
        for (d in dirs) for (n in names) for (ext in EXTS) {
            val f = File(d, "$n.$ext")
            if (f.isFile) return f
        }
        return null
    }

    private fun decode(file: File, target: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
            sample *= 2
        }
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Throwable) {
        null
    }

    private companion object {
        val EXTS = listOf("png", "jpg", "jpeg", "webp")
    }
}

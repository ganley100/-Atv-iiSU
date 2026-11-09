package com.ganley.tiledeck

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Environment
import java.io.File

/** Finds installed apps and ROM files, and remembers favorites. */
class Library(private val ctx: Context) {

    private val prefs = ctx.getSharedPreferences("tiledeck", Context.MODE_PRIVATE)

    // ---- Favorites ----

    fun favorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet()

    /** Returns true if the key is now a favorite. */
    fun toggleFavorite(key: String): Boolean {
        val set = favorites().toMutableSet()
        val nowFavorite = if (key in set) {
            set.remove(key); false
        } else {
            set.add(key); true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, set).apply()
        return nowFavorite
    }

    // ---- File access ----

    fun hasFileAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    // ---- Apps ----

    fun loadApps(): List<Tile.App> {
        val pm = ctx.packageManager
        val byPackage = LinkedHashMap<String, Tile.App>()
        // TV apps first so their TV entry point wins over a phone one.
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in queryActivities(pm, query)) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == ctx.packageName || byPackage.containsKey(ai.packageName)) continue
                byPackage[ai.packageName] = Tile.App(
                    label = ri.loadLabel(pm).toString(),
                    component = ComponentName(ai.packageName, ai.name),
                    leanback = category == Intent.CATEGORY_LEANBACK_LAUNCHER,
                )
            }
        }
        return byPackage.values.sortedBy { it.label.lowercase() }
    }

    private fun queryActivities(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

    // ---- ROMs ----

    /** Every `ROMs` folder (any capitalisation) on internal storage and attached USB/SD storage. */
    fun romRoots(): List<File> {
        val bases = mutableListOf(Environment.getExternalStorageDirectory())
        File("/storage").listFiles()
            ?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }
            ?.let { bases += it }
        return bases.flatMap { base ->
            base.listFiles()?.filter { it.isDirectory && it.name.equals("roms", ignoreCase = true) }
                ?: emptyList()
        }
    }

    fun scanGames(): Map<GameSystem, List<Tile.Game>> {
        val found = HashMap<GameSystem, MutableList<Tile.Game>>()
        for (root in romRoots()) {
            for (dir in root.listFiles().orEmpty()) {
                if (!dir.isDirectory) continue
                val system = Systems.forFolder(dir.name) ?: continue
                collect(dir, system, dir.name, root, found.getOrPut(system) { mutableListOf() }, 0)
            }
        }
        val ordered = LinkedHashMap<GameSystem, List<Tile.Game>>()
        for (system in Systems.all) {
            val games = found[system]
            if (!games.isNullOrEmpty()) {
                ordered[system] = games.distinctBy { it.path }.sortedBy { it.title.lowercase() }
            }
        }
        return ordered
    }

    private fun collect(
        dir: File,
        system: GameSystem,
        folder: String,
        root: File,
        out: MutableList<Tile.Game>,
        depth: Int,
    ) {
        val entries = dir.listFiles().orEmpty().filter { !it.name.startsWith(".") }

        // Discs referenced by a .m3u playlist are hidden; the playlist is shown instead.
        val inPlaylists = entries
            .filter { it.isFile && it.extension.equals("m3u", ignoreCase = true) }
            .flatMap { m3u ->
                runCatching { m3u.readLines() }.getOrDefault(emptyList())
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { File(it).name.lowercase() }
            }
            .toSet()

        for (f in entries) {
            if (f.isDirectory) {
                if (depth < 2 && f.name.lowercase() !in SKIP_DIRS) {
                    collect(f, system, folder, root, out, depth + 1)
                }
                continue
            }
            if (f.extension.lowercase() !in system.extensions) continue
            if (f.name.lowercase() in inPlaylists) continue
            out += Tile.Game(
                title = cleanTitle(f.nameWithoutExtension),
                path = f.absolutePath,
                system = system,
                folder = folder,
                romRoot = root.absolutePath,
            )
        }
    }

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private val SKIP_DIRS = setOf(
            "media", "images", "videos", "manuals", "boxart", "covers",
            "screenshots", "snap", "snaps", "marquees", "titles",
        )
        private val TAGS = Regex("""\s*[(\[][^)\]]*[)\]]""")

        /** "Super Game (USA) (Rev 1) [!]" -> "Super Game" */
        fun cleanTitle(name: String): String =
            name.replace(TAGS, "").replace('_', ' ').trim().ifEmpty { name }
    }
}

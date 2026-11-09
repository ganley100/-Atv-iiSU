package com.ganley.tiledeck

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var grid: RecyclerView
    private lateinit var summary: TextView
    private lateinit var adapter: TileAdapter
    private lateinit var library: Library
    private lateinit var art: ArtLoader

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var focusedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        library = Library(this)
        art = ArtLoader(this)
        summary = findViewById(R.id.summary)
        grid = findViewById(R.id.grid)

        adapter = TileAdapter(art, ::open, ::showOptions)
        val spans = (resources.configuration.screenWidthDp / 150).coerceIn(3, 8)
        grid.layoutManager = GridLayoutManager(this, spans).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (adapter.getItemViewType(position) == TileAdapter.TYPE_HEADER) spans else 1
            }
        }
        grid.itemAnimator = null
        grid.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        summary.text = "Scanning…"
        worker.execute {
            val (tiles, text) = buildTiles()
            main.post {
                summary.text = text
                adapter.submitList(tiles) { focusFirstTileOnce() }
            }
        }
    }

    /** Builds the one big menu: favorites, games by console, apps, then system tiles. */
    private fun buildTiles(): Pair<List<Tile>, String> {
        val access = library.hasFileAccess()
        val apps = library.loadApps()
        val games = if (access) library.scanGames() else emptyMap()
        val favorites = library.favorites()
        val allGames = games.values.flatten()
        val tiles = mutableListOf<Tile>()

        if (!access) {
            tiles += Tile.Header("start", "Get started")
            tiles += Tile.Action(Tile.Kind.FILE_ACCESS, "Allow file access", "📁")
            tiles += Tile.Action(Tile.Kind.HELP, "Where to put ROMs", "?")
        }

        val favGames = allGames.filter { it.key in favorites }.map { it.copy(section = "fav") }
        val favApps = apps.filter { it.key in favorites }.map { it.copy(section = "fav") }
        if (favGames.isNotEmpty() || favApps.isNotEmpty()) {
            tiles += Tile.Header("fav", "★ Favorites")
            tiles += favGames
            tiles += favApps
        }

        for ((system, list) in games) {
            tiles += Tile.Header("sys:${system.short}", "${system.name}  ·  ${list.size}")
            tiles += list
        }

        if (access && games.isEmpty()) {
            tiles += Tile.Header("nogames", "Games")
            tiles += Tile.Action(Tile.Kind.HELP, "No ROMs yet — how to add", "?")
        }

        tiles += Tile.Header("apps", "Apps  ·  ${apps.size}")
        tiles += apps

        tiles += Tile.Header("system", "System")
        tiles += Tile.Action(Tile.Kind.REFRESH, "Refresh", "↻")
        tiles += Tile.Action(Tile.Kind.SETTINGS, "TV settings", "⚙")
        if (access) tiles += Tile.Action(Tile.Kind.HELP, "ROM folders", "?")

        return tiles to "${allGames.size} games  ·  ${apps.size} apps"
    }

    private fun focusFirstTileOnce() {
        if (focusedOnce) return
        val index = adapter.currentList.indexOfFirst { it !is Tile.Header }
        if (index < 0) return
        focusedOnce = true
        grid.scrollToPosition(index)
        grid.post { grid.layoutManager?.findViewByPosition(index)?.requestFocus() }
    }

    // ---- Actions ----

    private fun open(tile: Tile) {
        when (tile) {
            is Tile.App -> if (!Launch.app(this, tile)) toast("Couldn't open ${tile.label}")
            is Tile.Game -> if (!Launch.play(this, tile)) {
                toast("No app can open this game. Install RetroArch or an emulator.")
            }
            is Tile.Action -> when (tile.kind) {
                Tile.Kind.FILE_ACCESS -> requestFileAccess()
                Tile.Kind.HELP -> showHelp()
                Tile.Kind.REFRESH -> { art.forgetMissing(); reload() }
                Tile.Kind.SETTINGS -> Launch.start(this, Intent(Settings.ACTION_SETTINGS))
            }
            is Tile.Header -> Unit
        }
    }

    private fun showOptions(tile: Tile) {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        fun add(label: String, action: () -> Unit) { labels += label; actions += action }

        val isFav = tile.key in library.favorites()
        val favLabel = if (isFav) "Remove from favorites" else "Add to favorites"
        val title: String

        when (tile) {
            is Tile.Game -> {
                title = tile.title
                add("Play") { open(tile) }
                Launch.retroArch(this)?.takeIf { tile.system.core != null }?.let { ra ->
                    add("Play with RetroArch") {
                        if (!Launch.withRetroArch(this, tile, ra)) toast("RetroArch didn't start")
                    }
                }
                add("Open with another app…") {
                    if (!Launch.withOtherApp(this, tile)) toast("No app can open this file")
                }
                add(favLabel) { library.toggleFavorite(tile.key); reload() }
            }
            is Tile.App -> {
                title = tile.label
                add("Open") { open(tile) }
                add(favLabel) { library.toggleFavorite(tile.key); reload() }
                add("App info") { Launch.appInfo(this, tile.component.packageName) }
            }
            else -> { open(tile); return }
        }

        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, i -> actions[i]() }
            .show()
    }

    private fun requestFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pkgUri = Uri.parse("package:$packageName")
            val opened = Launch.start(this, Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri)) ||
                Launch.start(this, Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            if (!opened) showNoAccessScreen()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_STORAGE)
        }
    }

    private fun showNoAccessScreen() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("This TV hides the file access setting")
            .setMessage(
                "Try: Settings → Apps → Special app access → All files access → TileDeck.\n\n" +
                    "If that screen doesn't exist, run this once from any computer with adb:\n\n" +
                    "adb shell appops set $packageName MANAGE_EXTERNAL_STORAGE allow\n\n" +
                    "Then press Refresh."
            )
            .setPositiveButton("App info") { _, _ -> Launch.appInfo(this, packageName) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showHelp() {
        val folders = Systems.all.joinToString("\n") { "  ROMs/${it.folders.first()}   →  ${it.name}" }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Adding games")
            .setMessage(
                "Put games in a ROMs folder on the TV's storage or a USB drive, one folder per console:\n\n" +
                    folders +
                    "\n\nBox art: put Game Name.png next to the ROM (or in a media folder) and press Refresh.\n\n" +
                    "Games open in RetroArch when it's installed — download cores inside RetroArch " +
                    "(Main Menu → Online Updater → Core Downloader). Hold OK on a game to pick a different emulator."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ---- Input ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (event.action == KeyEvent.ACTION_UP) currentFocus?.performClick()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_MENU -> {
                if (event.action == KeyEvent.ACTION_UP) focusedTile()?.let(::showOptions)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (event.action == KeyEvent.ACTION_UP) handleBack()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun focusedTile(): Tile? {
        val focus = currentFocus ?: return null
        val vh = grid.findContainingViewHolder(focus) ?: return null
        return adapter.currentList.getOrNull(vh.bindingAdapterPosition)
    }

    @Deprecated("Still the simplest way to catch Back on Android TV")
    override fun onBackPressed() = handleBack()

    private fun handleBack() {
        val launchedAsHome = intent?.hasCategory(Intent.CATEGORY_HOME) == true
        if (launchedAsHome) {
            // Home screens don't close; jump back to the top instead.
            focusedOnce = false
            focusFirstTileOnce()
        } else {
            finish()
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == REQ_STORAGE) reload()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val REQ_STORAGE = 1
    }
}

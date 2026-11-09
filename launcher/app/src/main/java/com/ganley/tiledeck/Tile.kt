package com.ganley.tiledeck

import android.content.ComponentName

/** Everything that appears in the big menu. */
sealed class Tile {
    /** Identity used for favorites (same game/app in two sections shares it). */
    abstract val key: String
    /** Section this copy of the tile lives in, so duplicates stay distinct in the list. */
    abstract val section: String
    val listId: String get() = "$section|$key"

    data class Header(val id: String, val title: String) : Tile() {
        override val key get() = "hdr:$id"
        override val section get() = id
    }

    data class App(
        val label: String,
        val component: ComponentName,
        val leanback: Boolean,
        override val section: String = "apps",
    ) : Tile() {
        override val key get() = "app:${component.packageName}"
    }

    data class Game(
        val title: String,
        val path: String,
        val system: GameSystem,
        /** The actual folder name under ROMs/ (used to find ES-DE media). */
        val folder: String,
        val romRoot: String,
        override val section: String = "games",
    ) : Tile() {
        override val key get() = "game:$path"
    }

    data class Action(val kind: Kind, val label: String, val glyph: String) : Tile() {
        override val key get() = "act:$kind"
        override val section get() = "system"
    }

    enum class Kind { FILE_ACCESS, HELP, REFRESH, SETTINGS, SET_HOME, STOCK_HOME }
}

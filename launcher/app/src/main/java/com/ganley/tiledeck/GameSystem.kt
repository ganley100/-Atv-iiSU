package com.ganley.tiledeck

/**
 * A console/platform. ROMs are found in `ROMs/<folder>` where folder matches one of [folders]
 * (case-insensitive). [core] is the RetroArch libretro core used when RetroArch is installed.
 */
data class GameSystem(
    val name: String,
    val short: String,
    val folders: List<String>,
    val extensions: Set<String>,
    val core: String?,
    val color: Int,
)

object Systems {
    private fun c(hex: Long) = hex.toInt()

    val all = listOf(
        GameSystem("NES", "NES", listOf("nes", "famicom", "fc"),
            setOf("nes", "fds", "unf", "unif", "zip", "7z"), "fceumm", c(0xFFD84343)),
        GameSystem("Super Nintendo", "SNES", listOf("snes", "sfc", "superfamicom"),
            setOf("sfc", "smc", "fig", "swc", "zip", "7z"), "snes9x", c(0xFF6A5ACD)),
        GameSystem("Nintendo 64", "N64", listOf("n64"),
            setOf("n64", "z64", "v64", "zip", "7z"), "mupen64plus_next_gles3", c(0xFF2E9E5B)),
        GameSystem("Game Boy", "GB", listOf("gb"),
            setOf("gb", "zip", "7z"), "gambatte", c(0xFF7A8C3A)),
        GameSystem("Game Boy Color", "GBC", listOf("gbc"),
            setOf("gbc", "gb", "zip", "7z"), "gambatte", c(0xFF8E44AD)),
        GameSystem("Game Boy Advance", "GBA", listOf("gba"),
            setOf("gba", "zip", "7z"), "mgba", c(0xFF3F51B5)),
        GameSystem("Nintendo DS", "NDS", listOf("nds"),
            setOf("nds", "zip", "7z"), "melonds", c(0xFF607D8B)),
        GameSystem("Genesis / Mega Drive", "MD", listOf("genesis", "megadrive", "md"),
            setOf("md", "gen", "smd", "bin", "zip", "7z"), "genesis_plus_gx", c(0xFF263238)),
        GameSystem("Master System", "SMS", listOf("mastersystem", "sms"),
            setOf("sms", "zip", "7z"), "genesis_plus_gx", c(0xFFC62828)),
        GameSystem("Game Gear", "GG", listOf("gamegear", "gg"),
            setOf("gg", "zip", "7z"), "genesis_plus_gx", c(0xFF00838F)),
        GameSystem("PlayStation", "PS1", listOf("psx", "ps1", "playstation"),
            setOf("cue", "chd", "pbp", "m3u", "iso", "img"), "pcsx_rearmed", c(0xFF546E7A)),
        GameSystem("PSP", "PSP", listOf("psp"),
            setOf("iso", "cso", "pbp", "chd"), "ppsspp", c(0xFF37474F)),
        GameSystem("Dreamcast", "DC", listOf("dreamcast", "dc"),
            setOf("chd", "cdi", "gdi", "m3u"), "flycast", c(0xFFEF6C00)),
        GameSystem("Arcade", "ARC", listOf("arcade", "fbneo", "mame", "neogeo"),
            setOf("zip", "7z"), "fbneo", c(0xFFAD1457)),
        GameSystem("PC Engine", "PCE", listOf("pcengine", "tg16", "turbografx16"),
            setOf("pce", "zip", "7z"), "mednafen_pce_fast", c(0xFFF57C00)),
        GameSystem("Atari 2600", "2600", listOf("atari2600", "a2600"),
            setOf("a26", "bin", "zip", "7z"), "stella", c(0xFF795548)),
    )

    fun forFolder(name: String): GameSystem? {
        val n = name.lowercase()
        return all.firstOrNull { n in it.folders }
    }
}

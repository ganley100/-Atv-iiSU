# TileDeck

A game launcher for Google TV / Android TV (built for the onn stick). Your **emulator games and
Android apps live in one big menu** of rounded square tiles, Wii U-menu style.

- Games are grouped by console, with box art if you have it (initials tiles if you don't)
- All your installed apps are in the same menu
- Favorites row at the top
- Works with the TV remote or a Bluetooth gamepad
- Games open in **RetroArch** automatically; hold OK to pick another emulator instead

## Install it (no computer needed)

GitHub builds the APK automatically whenever this folder changes. The download link never changes:

```
https://github.com/ganley100/-Atv-iiSU/releases/download/tiledeck-latest/TileDeck.apk
```

1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
2. Allow it to install apps: Settings → Apps → Security & restrictions → Unknown sources → Downloader.
3. Open Downloader and type the link above. Tip: make a short link on your iPhone first
   (for example with tinyurl.com) so there's less to type with the remote.
4. Install, then open **TileDeck** from your apps row.

To update, repeat steps 3–4. Your favorites are kept.

## Let it see your ROMs

On first launch, select **Allow file access**. If your TV doesn't show that screen, try
Settings → Apps → Special app access → All files access → TileDeck. If that's missing too,
it needs one command from a computer with adb:

```
adb shell appops set com.ganley.tiledeck MANAGE_EXTERNAL_STORAGE allow
```

## Where games go

Make a `ROMs` folder on the TV's storage (or a USB drive) with one folder per console:

| Folder | Console | | Folder | Console |
|---|---|---|---|---|
| `ROMs/nes` | NES | | `ROMs/genesis` | Genesis / Mega Drive |
| `ROMs/snes` | Super Nintendo | | `ROMs/mastersystem` | Master System |
| `ROMs/n64` | Nintendo 64 | | `ROMs/gamegear` | Game Gear |
| `ROMs/gb` | Game Boy | | `ROMs/psx` | PlayStation |
| `ROMs/gbc` | Game Boy Color | | `ROMs/psp` | PSP |
| `ROMs/gba` | Game Boy Advance | | `ROMs/dreamcast` | Dreamcast |
| `ROMs/nds` | Nintendo DS | | `ROMs/arcade` | Arcade (FBNeo) |
| `ROMs/pcengine` | PC Engine | | `ROMs/atari2600` | Atari 2600 |

From an iPhone, File Manager Plus's **Access from network** + an FTP app (e.g. FE File Explorer)
is the easiest way to copy files over.

**Box art:** put `Game Name.png` (same name as the ROM) next to the ROM or in a `media` folder.
ES-DE's `downloaded_media` covers are picked up automatically.

## Playing

- Install **RetroArch** and download cores inside it (Main Menu → Online Updater → Core Downloader).
  RetroArch also needs file access to your ROMs folder.
- Without RetroArch, TileDeck asks which emulator app to open a game with.

## Controls

| Button | Action |
|---|---|
| OK / A | Open |
| Hold OK / X / Y / Menu | Options (favorite, other emulator, app info) |
| Back / B | Back |

## Build it yourself

```
cd launcher
./gradlew assembleRelease
```

The APK is signed with `app/tiledeck-sideload.keystore` (password `android`) so each build
installs over the last one. It's a public sideload key, not a secret.

package com.ganley.tiledeck

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Makes TileDeck the home screen on Google TV, which has no "default home app" setting.
 *
 * Whenever the stock Google TV / Android TV home screen comes to the front (Home button,
 * boot, leaving an app), this immediately brings TileDeck up instead. The user turns it on
 * once in Settings → System → Accessibility.
 */
class HomeOverrideService : AccessibilityService() {

    private var lastRedirect = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg !in STOCK_HOMES) {
            // Once the user leaves the stock home, the one-time bypass is used up.
            if (pkg != packageName && pkg != "com.android.systemui") bypass = false
            return
        }
        if (bypass) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirect < 700) return
        lastRedirect = now

        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_FROM_HOME, true)
        )
    }

    override fun onInterrupt() = Unit

    companion object {
        /** Home screens that TileDeck replaces. */
        val STOCK_HOMES = setOf(
            "com.google.android.apps.tv.launcherx",   // Google TV
            "com.google.android.tvlauncher",          // Android TV
            "com.google.android.leanbacklauncher",    // older Android TV
        )

        /** Set when the user asks for the Google TV home on purpose; cleared when they leave it. */
        @Volatile
        var bypass = false

        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, HomeOverrideService::class.java)
            val enabled = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == me
            }
        }

        /** Opens the stock Google TV home once without being redirected back. */
        fun openStockHome(ctx: Context): Boolean {
            val pm = ctx.packageManager
            for (pkg in STOCK_HOMES) {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (pm.resolveActivity(intent, 0) != null) {
                    bypass = true
                    if (Launch.start(ctx, intent)) return true
                    bypass = false
                }
            }
            return false
        }
    }
}

package io.embodit.bandagewrap.platform

import android.content.Context
import io.embodit.bandagewrap.domain.ArmSide
import io.embodit.bandagewrap.domain.Girth
import io.embodit.bandagewrap.ui.Lang
import io.embodit.bandagewrap.ui.Prefs

/** Local persistence (design persistence): settings and which wraps were watched, in SharedPreferences. Offline only. */
class Store(context: Context) {
    private val p = context.getSharedPreferences("bandage_wrap", Context.MODE_PRIVATE)

    fun prefs(): Prefs {
        val d = Prefs()
        return Prefs(
            lang = runCatching { Lang.valueOf(p.getString("lang", d.lang.name)!!) }.getOrDefault(d.lang),
            side = runCatching { ArmSide.valueOf(p.getString("side", d.side.name)!!) }.getOrDefault(d.side),
            forearmCm = p.getInt("forearmCm", 0).takeIf { it > 0 },
            girth = p.getString("girth", null)?.let { g -> runCatching { Girth.valueOf(g) }.getOrNull() },
            sound = p.getBoolean("sound", d.sound),
            volume = p.getInt("volume", d.volume),
        )
    }

    fun savePrefs(v: Prefs) {
        p.edit().putString("lang", v.lang.name).putString("side", v.side.name).putInt("forearmCm", v.forearmCm ?: 0)
            .putString("girth", v.girth?.name).putBoolean("sound", v.sound).putInt("volume", v.volume).apply()
    }

    fun seen(): Set<String> = p.getStringSet("seen", emptySet())?.toSet() ?: emptySet()

    fun saveSeen(s: Set<String>) {
        p.edit().putStringSet("seen", HashSet(s)).apply()
    }
}

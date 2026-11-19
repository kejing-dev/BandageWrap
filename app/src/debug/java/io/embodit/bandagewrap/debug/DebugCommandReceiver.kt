package io.embodit.bandagewrap.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.embodit.bandagewrap.platform.BwLog

/**
 * Debug builds only. Forwards adb broadcasts to the running app (action `io.embodit.bandagewrap.DEBUG`); see
 * [DebugCommands] and AGENTS.md for the commands.
 *
 *   adb -s emulator-5554 shell "am broadcast -a io.embodit.bandagewrap.DEBUG -p io.embodit.bandagewrap --es cmd 'scramble=15 seed=7'"
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val raw = intent.getStringExtra("cmd") ?: return
        BwLog.i("CMD recv '$raw'")
        // broadcasts arrive on the main thread, where the controller and the scene live; a bad command logs, never crashes
        runCatching { BwLog.i("CMD '$raw' -> ${DebugCommands.run(context.applicationContext, raw)}") }.onFailure { BwLog.w("CMD '$raw' failed: $it") }
    }
}

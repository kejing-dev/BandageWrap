package io.embodit.bandagewrap.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent

/**
 * Controller keys (profile D60). In Full Space the system turns controller B into KEYCODE_BACK (sometimes after
 * KEYCODE_BUTTON_B) and A into DPAD_CENTER, sent to whichever window holds key focus: the Stage activity (window
 * closed), the main window, or the system TabBar's own window (after a tab was clicked — it keeps focus and swallows
 * the key before the activity's OnBackPressedCallback). So the keys are caught at every layer, earliest first:
 *  1. each TabBar item and the window root: [Modifier.controllerKeys] (onPreviewKeyEvent, not focusable);
 *  2. each activity: a Window.Callback wrapper (dispatchKeyEvent) + an OnBackPressedCallback;
 *  3. every root view of the process (WindowInspector, re-scanned each second): an unhandled-key listener. The Stage's
 *     key focus is a system-added root ("Sys…:LaunchActivity"), not the activity's own window, so with the main window
 *     closed B only reaches us here (emulator v0.1.1: B / BACK on that root, handled=false, before this hook).
 * BACK / B all go to one handler ([onBack]); A / X / Y / ENTER / DPAD_CENTER are eaten (no button is "clicked" by a key).
 * One press = one back step: the press is acted on at key-up, and repeats within [DEBOUNCE_MS] are dropped (B arrives as
 * BUTTON_B + BACK).
 */
object BackKeys {
    @Volatile var onBack: (String) -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    private var lastBackMs = 0L
    private const val DEBOUNCE_MS = 250L

    private val backCodes = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE)
    private val eatenCodes =
        setOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START,
        )

    /** True when the key is ours (consumed). Called from every layer; only BACK / B key-ups do anything. */
    fun handle(e: KeyEvent, layer: String): Boolean {
        val code = e.keyCode
        if (code in backCodes) {
            if (e.action == KeyEvent.ACTION_UP && !e.isCanceled) back("$layer ${KeyEvent.keyCodeToString(code)}")
            return true
        }
        if (code in eatenCodes) {
            if (e.action == KeyEvent.ACTION_UP) BwLog.i("KEY eaten $layer ${KeyEvent.keyCodeToString(code)}")
            return true
        }
        return false
    }

    /** The single back entry (keys, OnBackPressedCallback, debug command). */
    fun back(src: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBackMs < DEBOUNCE_MS) {
            BwLog.i("BACK $src: repeat dropped")
            return
        }
        lastBackMs = now
        if (Looper.myLooper() == Looper.getMainLooper()) onBack(src) else main.post { onBack(src) }
    }

    /** Hook every activity of the process (the Stage's launch activity and the windows' stub activities). */
    fun install(app: Application) {
        main.post(scan)
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(a: Activity, b: Bundle?) = hook(a)
                override fun onActivityStarted(a: Activity) = Unit
                override fun onActivityResumed(a: Activity) = Unit
                override fun onActivityPaused(a: Activity) = Unit
                override fun onActivityStopped(a: Activity) = Unit
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
                override fun onActivityDestroyed(a: Activity) = Unit
            },
        )
    }

    private fun hook(a: Activity) {
        val name = a.javaClass.simpleName
        val w = a.window
        val cb = w.callback
        if (cb != null && cb !is KeyCallback) w.callback = KeyCallback(cb, "activity:$name")
        (a as? ComponentActivity)?.onBackPressedDispatcher?.addCallback(
            a,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = back("dispatcher:$name")
            },
        )
        BwLog.i("BACK hooked $name")
        main.post(scan)
    }

    private val hookedRoots = java.util.WeakHashMap<View, Boolean>()

    /** Adds the unhandled-key listener to every new root view of the process; runs every second. */
    private val scan: Runnable =
        object : Runnable {
            override fun run() {
                runCatching {
                    for (v in android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                        if (hookedRoots.containsKey(v)) continue
                        hookedRoots[v] = true
                        val title = (v.layoutParams as? android.view.WindowManager.LayoutParams)?.title?.toString()?.substringAfterLast('/') ?: v.javaClass.simpleName
                        v.addOnUnhandledKeyEventListener { _, e -> handle(e, "root:$title") }
                        BwLog.i("BACK hooked root $title (${v.javaClass.simpleName})")
                    }
                }.onFailure { BwLog.w("BACK root scan failed: $it") }
                main.removeCallbacks(this)
                main.postDelayed(this, 1000)
            }
        }

    /** Wraps an activity's Window.Callback so its keys are seen before the view tree and the system. */
    private class KeyCallback(private val d: Window.Callback, private val layer: String) : Window.Callback by d {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean = handle(event, layer) || d.dispatchKeyEvent(event)
    }
}

/** For the window root and each TabBar item: not focusable, and controller keys caught before the content sees them. */
fun Modifier.controllerKeys(layer: String): Modifier =
    this.focusProperties { canFocus = false }.onPreviewKeyEvent { BackKeys.handle(it.nativeKeyEvent, "compose:$layer") }

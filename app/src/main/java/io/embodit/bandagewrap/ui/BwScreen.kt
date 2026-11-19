package io.embodit.bandagewrap.ui

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.platform.LocalStageUpperLimbRenderModelManager
import com.pico.spatial.ui.platform.ability.UpperLimbRenderMode
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.UpperLimbRenderModeChangeListener
import io.embodit.bandagewrap.platform.BwLog
import io.embodit.bandagewrap.ui.components.BwStage
import io.embodit.bandagewrap.ui.components.LocalBwResources
import io.embodit.bandagewrap.ui.components.localizedResources
import io.embodit.bandagewrap.ui.debug.Probe
import io.embodit.bandagewrap.ui.scene.BwFrameSystem
import io.embodit.bandagewrap.ui.scene.BwScene
import io.embodit.bandagewrap.ui.window.WindowCtl

/**
 * The Stage screen: wires [BwController] (state), [BwScene] (entities, per-frame work) and [BwStage], and opens the main
 * window through the level gate (design lifecycle: Stage(Mixed) → head pose → level → window + arm).
 */
@Composable
fun BwScreen() {
    val context = LocalContext.current
    remember(context) {
        Probe.enabled = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        BwController.start(context)
        true
    }
    val state by BwController.state.collectAsStateWithLifecycle()
    val scene = remember { BwScene() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(scene, lifecycle) {
        registerSystem<BwFrameSystem>()
        Probe.host = object : Probe.Host { override fun scene(): BwScene = scene }
        // background: stop trackers and the playback (design lifecycle); back: resume
        val observer =
            LifecycleEventObserver { _, event ->
                BwLog.i("LIFECYCLE event=$event")
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        scene.pause()
                        BwController.onBackground()
                    }
                    Lifecycle.Event.ON_START -> scene.resume()
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        BwLog.i("STAGE enter")
        onDispose {
            lifecycle.removeObserver(observer)
            Probe.host = null
            unregisterSystem<BwFrameSystem>()
            scene.detach()
            BwLog.i("STAGE disposed")
        }
    }

    // the real arms only in the passthrough, the bandage drawn over them (design container.upperLimbRenderMode: Hidden)
    val limbs = LocalStageUpperLimbRenderModelManager.current
    DisposableEffect(limbs) {
        val listener =
            object : UpperLimbRenderModeChangeListener {
                override fun onUpperLimbRenderModeChanged(upperLimbRenderMode: UpperLimbRenderMode) {
                    BwLog.i("LIMBS mode=$upperLimbRenderMode")
                }
            }
        runCatching { limbs.addUpperLimbRenderModeChangeListener(listener) }
        val result = runCatching { limbs.setUpperLimbRenderMode(UpperLimbRenderMode.Hidden) }
        BwLog.i("LIMBS set Hidden result=$result")
        onDispose { runCatching { limbs.removeUpperLimbRenderModeChangeListener(listener) } }
    }

    val navigator = LocalSpatialNavigator.current
    DisposableEffect(navigator) {
        WindowCtl.navigator = navigator
        onDispose { if (WindowCtl.navigator === navigator) WindowCtl.navigator = null }
    }
    LaunchedEffect(Unit) { WindowCtl.openWindow("stage") }

    val resources = remember(state.prefs.lang, context) { localizedResources(context, state.prefs.lang) }
    CompositionLocalProvider(LocalBwResources provides resources) {
        BwStage(state = state, scene = scene)
    }
}

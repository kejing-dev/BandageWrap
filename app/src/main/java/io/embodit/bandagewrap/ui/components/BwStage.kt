package io.embodit.bandagewrap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.geometry.Offset3D
import io.embodit.bandagewrap.R
import io.embodit.bandagewrap.domain.Vec3
import io.embodit.bandagewrap.platform.BwLog
import io.embodit.bandagewrap.ui.BwUiState
import io.embodit.bandagewrap.ui.debug.LocalProbePanel
import io.embodit.bandagewrap.ui.input.BoardInputListener
import io.embodit.bandagewrap.ui.input.InputSource
import io.embodit.bandagewrap.ui.input.Offset3M
import io.embodit.bandagewrap.ui.input.boardInput
import io.embodit.bandagewrap.ui.scene.BwScene
import io.embodit.bandagewrap.ui.scene.PanelIds
import io.embodit.bandagewrap.ui.scene.Target
import io.embodit.bandagewrap.ui.theme.BwColors
import io.embodit.bandagewrap.ui.theme.BwType
import io.embodit.bandagewrap.ui.window.WindowCtl

/**
 * The Stage content: one full-size SpatialView with the arm (built by [BwScene]) and three small attachment panels (the
 * turn label over the arm, the "打开窗口" chip, the one-line hint). 3D drags come in through the factory board-input
 * template (hand ray, eye-hand, controller pointer); near-field pinches / pokes are dropped (design input.tapFilter).
 * Everything else is in the system window.
 */
@Composable
internal fun BwStage(state: BwUiState, scene: BwScene) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bindTick = scene.bindTick.intValue
    val density = LocalDensity.current
    val listener =
        object : BoardInputListener<Target> {
            override fun onTap(target: Target?, source: InputSource, position: Offset3D?) {
                // taps on the arm do nothing (design: it plays by itself); logged for the tap filter check
                if (source == InputSource.DIRECT || source == InputSource.POKE) BwLog.i("TAP ignored kind=${source.label} target=$target")
                else if (target != null) BwLog.i("TAP target=$target src=${source.label} (no action)")
            }

            override fun onDragStart(target: Target, source: InputSource): Boolean {
                if (source == InputSource.DIRECT || source == InputSource.POKE) {
                    BwLog.i("DRAG ignored kind=${source.label} target=$target")
                    return false
                }
                return scene.startDrag(target, source.label)
            }

            override fun onDragMove(totalM: Offset3M) = scene.moveDrag(Vec3(totalM.x, totalM.y, totalM.z))

            override fun onDragEnd() = scene.endDrag("gesture")

            override fun onDragCancel() = scene.endDrag("gesture-cancel")

            // the system HoverEffect draws on the arm / handles; nothing extra (hover events do not reach apps on PICO OS 6.2)
            override fun onHover(target: Target?, source: InputSource) = Unit
        }
    SpatialView(
        modifier = Modifier.fillMaxSize().boardInput(BwScene.RESOLVER, listener),
        attachments = {
            AttachmentPanel(PanelIds.CHIP, size = density.px(BwScene.CHIP_W, BwScene.CHIP_H)) { CompositionLocalProvider(LocalProbePanel provides PanelIds.CHIP) { OpenChip(scene.panelShown[PanelIds.CHIP] == true) } }
            AttachmentPanel(PanelIds.HINT, size = density.px(BwScene.HINT_W, BwScene.HINT_H)) { CompositionLocalProvider(LocalProbePanel provides PanelIds.HINT) { StageHint(scene.panelShown[PanelIds.HINT] == true) } }
            AttachmentPanel(PanelIds.LABEL, size = density.px(BwScene.LABEL_W, BwScene.LABEL_H)) { CompositionLocalProvider(LocalProbePanel provides PanelIds.LABEL) { TurnLabel(scene, scene.panelShown[PanelIds.LABEL] == true) } }
        },
        update = { _, attachments ->
            @Suppress("UNUSED_EXPRESSION") bindTick
            scene.bindPanels(attachments)
        },
        initial = { content, attachments -> scene.attach(content, attachments, context) },
    )
}

/** Attachment sizes are pixels: the dp size of the panel at the composition's density. */
private fun Density.px(wDp: Float, hDp: Float): IntSize = IntSize(wDp.dp.roundToPx(), hDp.dp.roundToPx())

/** "打开窗口": re-opens the window (a minimised one is restored where it was). */
@Composable
private fun OpenChip(inView: Boolean) {
    if (!inView) return
    Box(Modifier.size(BwScene.CHIP_W.dp, BwScene.CHIP_H.dp), contentAlignment = Alignment.Center) {
        Key(rs(R.string.open_panel), "open_panel", R.drawable.ic_open_with, KeyKind.PRIMARY, 64.dp, Modifier.width((BwScene.CHIP_W - 8f).dp)) { WindowCtl.reopen("chip") }
    }
}

/** 准备中 (S00) / 平视前方，窗口就出来 / 看正前方，别动 (窗口回到面前). */
@Composable
private fun StageHint(inView: Boolean) {
    if (!inView) return
    val text = when {
        WindowCtl.recentering -> rs(R.string.look_ahead)
        WindowCtl.levelHint -> rs(R.string.level_hint)
        else -> rs(R.string.loading)
    }
    Box(Modifier.size(BwScene.HINT_W.dp, BwScene.HINT_H.dp).clip(RoundedCornerShape(40.dp)).background(BwColors.CardBg).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = BwType.title, color = BwColors.CardInk, maxLines = 1, textAlign = TextAlign.Center, onTextLayout = fits("stage/hint"))
    }
}

/** The turn label (design panels[1]): two lines, 「3 / 9」 and the short cue, dark slate (≈ 13:1), no interaction. */
@Composable
private fun TurnLabel(scene: BwScene, inView: Boolean) {
    if (!inView) return
    Column(
        Modifier.size(BwScene.LABEL_W.dp, BwScene.LABEL_H.dp).clip(RoundedCornerShape(14.dp)).background(BwColors.CardBg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = scene.labelTop.value, style = BwType.tagTop, color = BwColors.Lead, maxLines = 1, onTextLayout = fits("label/top"))
        Text(text = scene.labelSub.value, style = BwType.tagSub, color = BwColors.CardInk, maxLines = 1, onTextLayout = fits("label/sub"))
    }
}

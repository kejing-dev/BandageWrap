package io.embodit.bandagewrap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import io.embodit.bandagewrap.BuildConfig
import io.embodit.bandagewrap.R
import io.embodit.bandagewrap.domain.ArmSide
import io.embodit.bandagewrap.domain.Girth
import io.embodit.bandagewrap.domain.Segment
import io.embodit.bandagewrap.domain.T
import io.embodit.bandagewrap.domain.Wrap
import io.embodit.bandagewrap.domain.WrapBuilder
import io.embodit.bandagewrap.domain.ArmModel
import io.embodit.bandagewrap.domain.ArmParams
import io.embodit.bandagewrap.domain.Wraps
import io.embodit.bandagewrap.ui.BwController
import io.embodit.bandagewrap.ui.BwUiState
import io.embodit.bandagewrap.ui.Lang
import io.embodit.bandagewrap.ui.Screen
import io.embodit.bandagewrap.ui.SettingsAsk
import io.embodit.bandagewrap.ui.Ui
import io.embodit.bandagewrap.ui.debug.probe
import io.embodit.bandagewrap.ui.theme.BwColors
import io.embodit.bandagewrap.ui.theme.BwType

// =================================================================================================== shared pieces

/** PRIMARY orange (one per page, B31), DANGER light red, NORMAL system fill on the glass, LIGHT pale key on a card. */
enum class KeyKind { PRIMARY, DANGER, NORMAL, LIGHT, ON }

@Composable
fun zh(): Boolean = LocalBwResources.current.configuration.locales[0].language == "zh"

@Composable
fun T.txt(): String = of(zh())

/** A text key, ≥ 64 dp, icon + words (B19: no bare icons). */
@Composable
fun Key(text: String, name: String, icon: Int? = null, kind: KeyKind = KeyKind.NORMAL, height: Dp = 64.dp, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val container = when (kind) {
        KeyKind.PRIMARY -> BwColors.Lead
        KeyKind.DANGER -> BwColors.DangerFill
        KeyKind.NORMAL -> PicoTheme.colorScheme.fillTertiary
        KeyKind.LIGHT -> BwColors.CardInk
        KeyKind.ON -> BwColors.CardInk
    }
    val ink = when (kind) {
        KeyKind.NORMAL -> PicoTheme.colorScheme.labelPrimary
        KeyKind.DANGER -> BwColors.OnError
        KeyKind.PRIMARY -> BwColors.InkOnOrange
        else -> BwColors.CardBg
    }
    Button(
        onClick = onClick,
        modifier = modifier.probe(name).height(height),
        enabled = enabled,
        size = ButtonDefaults.Regular.copy(height = height),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = ink),
    ) {
        if (icon != null) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = ink)
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, maxLines = 1, color = ink, style = BwType.button, onTextLayout = fits("key/$name"))
    }
}

@Composable
fun Line(text: String, name: String, style: androidx.compose.ui.text.TextStyle = BwType.body, color: Color = PicoTheme.colorScheme.labelPrimary, lines: Int = 2, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(text = text, style = style, color = color, maxLines = lines, modifier = modifier.probe(name), textAlign = align ?: TextAlign.Unspecified, onTextLayout = fits("line/$name"))
}

/** A dark slate card (CardInk on CardBg ≈ 13:1). */
@Composable
fun Card(modifier: Modifier = Modifier, name: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(BwColors.CardBg).padding(horizontal = 20.dp, vertical = 14.dp).probe(name), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), content = content)
}

/** An amber inline bar (Stage has no dialogs / snackbars: confirmations and notices are inline, design spatialUiMap). */
@Composable
fun InlineBar(text: String, name: String, keys: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(BwColors.WarnBg).padding(horizontal = 16.dp, vertical = 12.dp).probe(name), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = text, style = BwType.body, color = BwColors.WarnInk, maxLines = 3, onTextLayout = fits("bar/$name"))
        keys()
    }
}

@Composable
private fun segColors() =
    SegmentControlDefaults.colors(
        itemBackgroundColor = Color.Transparent,
        itemContentColor = PicoTheme.colorScheme.labelPrimary,
        selectedItemBackgroundColor = BwColors.Lead,
        selectedItemContentColor = BwColors.InkOnOrange,
    )

/** Options inside one settings row (not page switching: pages use the system TabBar, D8), 48 dp items. */
@Composable
fun <V> Options(label: String, options: List<Pair<V, String>>, selected: V, name: String, itemWidth: Dp, onPick: (V) -> Unit) {
    // gate:allow button-gap a label over one segment control: the 6 dp is between text and the control, not between buttons
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) Text(text = label, style = BwType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("opt/$name"))
        SegmentControl(modifier = Modifier.width(itemWidth * options.size + 8.dp).height(56.dp), backgroundColor = PicoTheme.colorScheme.fillTertiary) {
            options.forEach { (v, text) ->
                val sel = v == selected
                SegmentItem(
                    selected = sel,
                    onClick = { onPick(v) },
                    modifier = Modifier.width(itemWidth).height(48.dp).probe("${name}_$text"),
                    textStyle = BwType.label,
                    colors = segColors(),
                    title = { Text(text = text, maxLines = 1, color = if (sel) BwColors.InkOnOrange else PicoTheme.colorScheme.labelPrimary, style = BwType.label, onTextLayout = fits("seg/${name}_$text")) },
                )
            }
        }
    }
}

/** Short pages: content spread evenly over the page (D44 空白 ≤ 25 %). */
@Composable
fun Centered(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, content = content)
}

@Composable
fun partName(s: Segment): String =
    rs(
        when (s) {
            Segment.FOREARM -> R.string.part_forearm
            Segment.WRIST -> R.string.part_wrist
            Segment.HAND -> R.string.part_hand
            Segment.THUMB -> R.string.part_thumb
            Segment.ELBOW -> R.string.part_elbow
        }
    )

/** Turn counts with the default table arm (design armModel.scopeNote: L1 4 / L2 9 / L3 9 / L4 10 / L5 18 / L6 12 / L7 10). */
object TurnCounts {
    private val cache = HashMap<String, Int>()

    fun of(w: Wrap): Int = cache.getOrPut(w.id) { WrapBuilder.turnCount(w, ArmModel(ArmParams(upright = w.id == "L7"))) }
}

/** The mute key, always on the watching page (D15 / B31 exception: 静音常显). */
@Composable
fun MuteKey(state: BwUiState, onEvent: (Ui) -> Unit) {
    val on = state.prefs.sound
    Key(rs(R.string.m_sound), "mute", if (on) R.drawable.ic_volume_up else R.drawable.ic_volume_off, if (on) KeyKind.NORMAL else KeyKind.ON, 64.dp) { onEvent(Ui.Mute) }
}

// =================================================================================================== S00 loading

@Composable
fun LoadingPage() {
    Centered {
        Line(rs(R.string.app_name), "load_title", BwType.big, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        ArmPicture(PicKind.TABLE, 200.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = painterResource(R.drawable.ic_sync), contentDescription = null, modifier = Modifier.size(30.dp), tint = BwColors.Lead)
            Spacer(Modifier.width(10.dp))
            Line(rs(R.string.loading), "loading", BwType.titleLarge, lines = 1)
        }
        Line(rs(R.string.load_tip), "load_tip", BwType.small, PicoTheme.colorScheme.labelSecondary, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// =================================================================================================== 包扎 tab

@Composable
fun WrapsPage(state: BwUiState, onEvent: (Ui) -> Unit) {
    if (state.loading) return LoadingPage()
    when (state.screen) {
        Screen.LIST -> ListPage(state, onEvent)
        Screen.WATCH -> if (state.moreOpen) MorePage(state, onEvent) else WatchPage(state, onEvent)
        Screen.DONE -> DonePage(state, onEvent)
        Screen.ALIGN -> AlignPage(state, onEvent)
        Screen.MANUAL -> ManualPage(state, onEvent)
        Screen.UNSUPPORTED -> UnsupportedPage(state, onEvent)
    }
}

// ---- S02 list

@Composable
private fun ListPage(state: BwUiState, onEvent: (Ui) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Line(rs(R.string.pick), "pick", BwType.titleLarge, lines = 1)
        Line(rs(R.string.pick_sub), "pick_sub", BwType.small, PicoTheme.colorScheme.labelSecondary, lines = 2)
        Spacer(Modifier.height(12.dp))
        // gate:allow button-gap list rows are 16 dp apart below (spacer), this column has no spacing of its own
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            Wraps.all.forEachIndexed { i, w ->
                if (i > 0) Spacer(Modifier.height(16.dp))
                WrapRow(w, w.id in state.seen) { onEvent(Ui.Pick(w.id)) }
            }
        }
    }
}

@Composable
private fun WrapRow(w: Wrap, seen: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(78.dp).probe("row_${w.id}"),
        size = ButtonDefaults.Regular.copy(height = 78.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
    ) {
        Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = w.scene.txt(), style = BwType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("row/${w.id}/name"))
            Text(text = rs(R.string.turns_about, TurnCounts.of(w), partName(w.segment)), style = BwType.label, color = PicoTheme.colorScheme.labelSecondary, maxLines = 1, onTextLayout = fits("row/${w.id}/sub"))
        }
        Icon(
            painter = painterResource(if (seen) R.drawable.ic_check_circle else R.drawable.ic_play_circle),
            contentDescription = if (seen) rs(R.string.seen_mark) else null,
            modifier = Modifier.size(30.dp),
            tint = if (seen) BwColors.PassInk else PicoTheme.colorScheme.labelSecondary,
        )
    }
}

// ---- S05 / S08 watching

@Composable
private fun WatchHeader(state: BwUiState, onEvent: (Ui) -> Unit, more: Boolean = true) {
    val w = Wraps.byId(state.wrapId ?: "") ?: return
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Line(w.scene.txt(), "title", BwType.title, lines = 2, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        MuteKey(state, onEvent)
        if (more) {
            Spacer(Modifier.width(12.dp))
            Key(rs(R.string.more), "more", R.drawable.ic_more_horiz, if (state.moreOpen) KeyKind.ON else KeyKind.NORMAL, 64.dp) { onEvent(Ui.More) }
        }
    }
}

@Composable
private fun TurnProgress(state: BwUiState) {
    val n = state.turns
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = if (n > 0) rs(R.string.turn_of, state.turn + 1, n) else rs(R.string.loading), style = BwType.label, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("turn_of"), modifier = Modifier.probe("turn_of"))
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f).height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 0 until n) {
                val c = when {
                    state.screen == Screen.DONE || i < state.turn -> BwColors.Gauze
                    i == state.turn -> BwColors.Lead
                    else -> BwColors.SegOff
                }
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c))
            }
        }
    }
}

@Composable
private fun WatchPage(state: BwUiState, onEvent: (Ui) -> Unit) {
    val w = Wraps.byId(state.wrapId ?: "") ?: return
    val pb = BwController.playback
    val kind = pb?.let { BwController.kindOf(it.turn) }
    val copy = kind?.let { Wraps.copy(w, it, state.turn, state.turns) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        WatchHeader(state, onEvent)
        TurnProgress(state)
        Text(
            text = if (state.building || copy == null) rs(R.string.building) else copy.cue.txt(),
            // zh: two lines of 27 sp; en sentences run longer: three lines of 22 sp in the same box
            style = if (zh()) BwType.big else BwType.bigEn,
            color = PicoTheme.colorScheme.labelPrimary,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth().height(86.dp).probe("cue"),
            onTextLayout = fits("watch/cue"),
        )
        Text(text = rs(R.string.why_lab) + (if (zh()) "" else " ") + w.why.txt(), style = BwType.small, color = PicoTheme.colorScheme.labelSecondary, maxLines = 3, modifier = Modifier.fillMaxWidth().probe("why"), onTextLayout = fits("watch/why"))
        val playing = state.playing
        Key(rs(if (playing) R.string.pause else R.string.play), "play", if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow, KeyKind.PRIMARY, 84.dp, Modifier.fillMaxWidth(), enabled = !state.building) { onEvent(Ui.PlayPause) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Key(rs(R.string.prev), "prev", R.drawable.ic_skip_previous, KeyKind.NORMAL, 72.dp, Modifier.weight(1f), enabled = !state.building) { onEvent(Ui.Prev) }
            Key(rs(if (state.slow) R.string.slow_on else R.string.slow), "slow", R.drawable.ic_slow_motion_video, if (state.slow) KeyKind.ON else KeyKind.NORMAL, 72.dp, Modifier.weight(1.25f), enabled = !state.building) { onEvent(Ui.Slow) }
            Key(rs(R.string.next), "next", R.drawable.ic_skip_next, KeyKind.NORMAL, 72.dp, Modifier.weight(1f), enabled = !state.building) { onEvent(Ui.Next) }
        }
        Line(rs(if (state.onMyArm) R.string.hint_own else R.string.hint_table), "hint", BwType.label, PicoTheme.colorScheme.labelSecondary, lines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** 「更多」 opened in place (design N12 fallback: a page-internal list instead of a floating Menu). */
@Composable
private fun MorePage(state: BwUiState, onEvent: (Ui) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        WatchHeader(state, onEvent)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.onMyArm) {
                MenuItem(rs(R.string.m_table), null, "m_table", R.drawable.ic_table_restaurant) { onEvent(Ui.BackToTable) }
                MenuItem(rs(R.string.m_realign), null, "m_realign", R.drawable.ic_center_focus_strong) { onEvent(Ui.Realign) }
            } else MenuItem(rs(R.string.m_on_me), null, "m_on_me", R.drawable.ic_back_hand) { onEvent(Ui.OnMyArm) }
            MenuItem(rs(R.string.m_xray), rs(if (state.xray) R.string.on else R.string.off), "m_xray", R.drawable.ic_layers) { onEvent(Ui.XRay) }
            if (!state.onMyArm) MenuItem(rs(R.string.m_spin), null, "m_spin", R.drawable.ic_sync) { onEvent(Ui.Spin) }
        }
        Key(rs(R.string.close_menu), "m_close", R.drawable.ic_close, KeyKind.NORMAL, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.More) }
    }
}

@Composable
private fun MenuItem(text: String, value: String?, name: String, icon: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(68.dp).probe(name),
        size = ButtonDefaults.Regular.copy(height = 68.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillTertiary, contentColor = PicoTheme.colorScheme.labelPrimary),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(26.dp), tint = PicoTheme.colorScheme.labelPrimary)
        Spacer(Modifier.width(12.dp))
        Text(text = text, style = BwType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, modifier = Modifier.weight(1f), onTextLayout = fits("menu/$name"))
        if (value != null) Text(text = value, style = BwType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, onTextLayout = fits("menu/$name/v"))
    }
}

// ---- S07 done

@Composable
private fun DonePage(state: BwUiState, onEvent: (Ui) -> Unit) {
    val w = Wraps.byId(state.wrapId ?: "") ?: return
    val nxt = Wraps.all.getOrNull(Wraps.index(w.id) + 1)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Line(rs(R.string.done_title), "done_title", BwType.big, lines = 2, modifier = Modifier.weight(1f))
            MuteKey(state, onEvent)
        }
        Card(Modifier.fillMaxWidth(), "card_how") {
            Text(text = rs(R.string.how), style = BwType.label, color = BwColors.CardDim, maxLines = 1, onTextLayout = fits("done/how_lab"))
            Text(text = w.tagline.txt(), style = BwType.body, color = BwColors.CardInk, maxLines = 3, onTextLayout = fits("done/how"))
        }
        Card(Modifier.fillMaxWidth(), "card_why") {
            Text(text = rs(R.string.why), style = BwType.label, color = BwColors.CardDim, maxLines = 1, onTextLayout = fits("done/why_lab"))
            Text(text = w.why.txt(), style = BwType.body, color = BwColors.CardInk, maxLines = 3, onTextLayout = fits("done/why"))
        }
        Line(rs(R.string.seen, state.seen.size), "seen", BwType.label, PicoTheme.colorScheme.labelSecondary, lines = 1)
        Key(if (nxt != null) rs(R.string.next_wrap, nxt.scene.txt()) else rs(R.string.to_list), "next_wrap", R.drawable.ic_skip_next, KeyKind.PRIMARY, 72.dp, Modifier.fillMaxWidth()) { onEvent(Ui.NextWrap) }
        Key(rs(R.string.again), "again", R.drawable.ic_replay, KeyKind.NORMAL, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.Again) }
    }
}

// ---- S04 / S04+ / S04b / S15

@Composable
private fun AlignPage(state: BwUiState, onEvent: (Ui) -> Unit) {
    val w = Wraps.byId(state.wrapId ?: "")
    val left = state.prefs.side == ArmSide.LEFT
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Line(w?.scene?.txt() ?: "", "title", BwType.title, lines = 1)
        Line(rs(R.string.align_big), "align_big", BwType.big, lines = 3)
        if (w != null && (w.segment == Segment.HAND || w.segment == Segment.WRIST || w.segment == Segment.THUMB)) Line(rs(R.string.align_hint_fingers), "align_fingers", BwType.small, PicoTheme.colorScheme.labelSecondary)
        if (state.alignWaitedLong && !state.handSeen) {
            ArmPicture(PicKind.ALIGN, 96.dp)
            InlineBar(rs(R.string.slow_find), "slow_find") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Key(rs(R.string.m_table_short), "bar_table", R.drawable.ic_table_restaurant, KeyKind.LIGHT, 64.dp, Modifier.weight(1.3f)) { onEvent(Ui.BackToTable) }
                    Key(rs(R.string.wait), "bar_wait", null, KeyKind.LIGHT, 64.dp, Modifier.weight(1f)) { onEvent(Ui.AlignWait) }
                }
            }
        } else {
            ArmPicture(PicKind.ALIGN, 120.dp)
            val seenText = if (state.handSeen) rs(if (left) R.string.see_left else R.string.see_right) else rs(if (left) R.string.no_left else R.string.no_right)
            Line(seenText, "align_status", BwType.label, PicoTheme.colorScheme.labelPrimary, lines = 2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Key(rs(R.string.manual_short), "manual_short", R.drawable.ic_open_with, KeyKind.NORMAL, 64.dp, Modifier.weight(1f)) { onEvent(Ui.ManualAlign) }
                Key(rs(R.string.m_table_short), "align_table", R.drawable.ic_table_restaurant, KeyKind.NORMAL, 64.dp, Modifier.weight(1f)) { onEvent(Ui.BackToTable) }
            }
        }
    }
}

@Composable
private fun ManualPage(state: BwUiState, onEvent: (Ui) -> Unit) {
    val w = Wraps.byId(state.wrapId ?: "")
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Line(w?.scene?.txt() ?: "", "title", BwType.title, lines = 1)
        Line(rs(R.string.manual_big), "manual_big", BwType.big, lines = 3)
        ArmPicture(PicKind.HANDLES, 130.dp)
        Key(rs(R.string.aligned), "aligned", R.drawable.ic_check, KeyKind.PRIMARY, 72.dp, Modifier.fillMaxWidth()) { onEvent(Ui.Aligned) }
        Key(rs(R.string.m_table), "manual_table", R.drawable.ic_table_restaurant, KeyKind.NORMAL, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.BackToTable) }
    }
}

@Composable
private fun UnsupportedPage(state: BwUiState, onEvent: (Ui) -> Unit) {
    val w = Wraps.byId(state.wrapId ?: "")
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Line(w?.scene?.txt() ?: "", "title", BwType.title, lines = 1)
        ArmPicture(PicKind.TABLE, 120.dp)
        Line(rs(R.string.no_track_sub), "no_track_sub", BwType.body)
        InlineBar(rs(R.string.no_track), "no_track") {
            Key(rs(R.string.m_table), "u_table", R.drawable.ic_table_restaurant, KeyKind.LIGHT, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.BackToTable) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Key(rs(R.string.manual), "u_manual", R.drawable.ic_open_with, KeyKind.LIGHT, 64.dp, Modifier.weight(1.4f)) { onEvent(Ui.ManualAlign) }
                Key(rs(R.string.retry), "u_retry", R.drawable.ic_replay, KeyKind.LIGHT, 64.dp, Modifier.weight(1f)) { onEvent(Ui.AlignRetry) }
            }
        }
    }
}

/** The arm pictogram of the sample (picto): a table arm with the bandage, or the arm on the desk to line up. */
enum class PicKind { TABLE, ALIGN, HANDLES }

@Composable
fun ArmPicture(kind: PicKind, height: Dp) {
    val skin = BwColors.Skin
    val cream = BwColors.Gauze
    val lead = BwColors.Lead
    val green = BwColors.Passable
    Box(
        Modifier.fillMaxWidth().height(height).drawBehind {
            val w = size.width
            val h = size.height
            val y0 = h - 14.dp.toPx()
            drawLine(Color(0x8CA07850), Offset(12.dp.toPx(), y0 + 6.dp.toPx()), Offset(w - 12.dp.toPx(), y0 + 6.dp.toPx()), strokeWidth = 12.dp.toPx(), cap = StrokeCap.Round)
            val ex = w * 0.24f
            val wx = w * 0.68f
            val ay = y0 - h * 0.30f
            if (kind != PicKind.TABLE) drawLine(if (kind == PicKind.ALIGN) green.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.4f), Offset(ex, ay + 6.dp.toPx()), Offset(w * 0.84f, ay - 8.dp.toPx()), strokeWidth = 46.dp.toPx(), cap = StrokeCap.Round)
            drawLine(skin, Offset(ex, ay + 6.dp.toPx()), Offset(wx, ay - 4.dp.toPx()), strokeWidth = 32.dp.toPx(), cap = StrokeCap.Round)
            drawLine(skin, Offset(wx, ay - 4.dp.toPx()), Offset(w * 0.84f, ay - 8.dp.toPx()), strokeWidth = 22.dp.toPx(), cap = StrokeCap.Round)
            if (kind == PicKind.TABLE) {
                for (i in 0 until 4) {
                    val x = wx - 30.dp.toPx() - i * 24.dp.toPx()
                    drawLine(cream, Offset(x, ay - 18.dp.toPx()), Offset(x + 9.dp.toPx(), ay + 22.dp.toPx()), strokeWidth = 14.dp.toPx(), cap = StrokeCap.Round)
                }
                drawCircle(cream, 14.dp.toPx(), Offset(wx - 6.dp.toPx(), ay - 26.dp.toPx()))
                drawLine(lead, Offset(wx + 10.dp.toPx(), ay - 34.dp.toPx()), Offset(wx + 26.dp.toPx(), ay - 38.dp.toPx()), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            }
            if (kind == PicKind.HANDLES) {
                drawCircle(lead, 10.dp.toPx(), Offset(wx, ay - 4.dp.toPx()))
                drawCircle(lead, 10.dp.toPx(), Offset(ex, ay + 6.dp.toPx()))
            }
        }
    )
}

// =================================================================================================== 设置 tab (S14)

@Composable
fun ColumnScope.SettingsPage(state: BwUiState, onEvent: (Ui) -> Unit) {
    val p = state.prefs
    fun pr(why: String, f: (io.embodit.bandagewrap.ui.Prefs) -> io.embodit.bandagewrap.ui.Prefs) = onEvent(Ui.SetPrefs(why, f))
    Line(rs(R.string.tab_settings), "settings_title", BwType.titleLarge, lines = 1)
    Line(rs(R.string.safety), "safety", BwType.label, PicoTheme.colorScheme.labelSecondary, lines = 1)
    Spacer(Modifier.height(8.dp))
    when (state.ask) {
        SettingsAsk.HOWTO -> Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Line(rs(R.string.how_to_title), "how_to_title", BwType.title, lines = 1)
            for ((icon, id) in listOf(R.drawable.ic_play_circle to R.string.how_to_pick, R.drawable.ic_pause to R.string.how_to_play, R.drawable.ic_open_with to R.string.how_to_drag, R.drawable.ic_sync to R.string.how_to_turn, R.drawable.ic_layers to R.string.how_to_xray, R.drawable.ic_back_hand to R.string.how_to_mine)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = PicoTheme.colorScheme.labelPrimary)
                    Spacer(Modifier.width(10.dp))
                    Line(rs(id), "how_$id", BwType.small, lines = 4)
                }
            }
            Key(rs(R.string.back), "how_back", R.drawable.ic_chevron_left, KeyKind.NORMAL, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.Ask(SettingsAsk.NONE)) }
        }
        SettingsAsk.ABOUT -> Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Line(rs(R.string.about_body), "about_body", BwType.small, lines = 9)
            Line(rs(R.string.s_about_v, BuildConfig.VERSION_NAME), "about_v", BwType.label, PicoTheme.colorScheme.labelSecondary, lines = 1)
            Key(rs(R.string.back), "about_back", R.drawable.ic_chevron_left, KeyKind.NORMAL, 64.dp, Modifier.fillMaxWidth()) { onEvent(Ui.Ask(SettingsAsk.NONE)) }
        }
        else -> Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val dim = state.ask == SettingsAsk.EXIT
            Options(rs(R.string.s_lang), listOf(Lang.SYSTEM to rs(R.string.lang_auto), Lang.ZH to rs(R.string.lang_zh), Lang.EN to rs(R.string.lang_en)), p.lang, "lang", 132.dp) { l -> if (!dim) pr("lang") { it.copy(lang = l) } }
            Options(rs(R.string.s_side), listOf(ArmSide.LEFT to rs(R.string.side_left), ArmSide.RIGHT to rs(R.string.side_right)), p.side, "side", 150.dp) { s -> if (!dim) pr("side") { it.copy(side = s) } }
            // sound: on / off + three steps (D15, B20)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = rs(R.string.s_sound), style = BwType.title, color = PicoTheme.colorScheme.labelPrimary, maxLines = 1, modifier = Modifier.weight(1f), onTextLayout = fits("settings/sound"))
                    Box(Modifier.height(56.dp).width(80.dp), contentAlignment = Alignment.Center) {
                        Switch(checked = p.sound, onCheckedChange = { on -> if (!dim) pr("sound") { it.copy(sound = on) } }, modifier = Modifier.probe("sound_switch"))
                    }
                }
                Options("", listOf(40 to rs(R.string.vol_low), 70 to rs(R.string.vol_mid), 100 to rs(R.string.vol_high)), listOf(40, 70, 100).minByOrNull { kotlin.math.abs(it - p.volume) } ?: 70, "vol", 120.dp) { v -> if (!dim) pr("volume") { it.copy(volume = v, sound = true) } }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Line(rs(R.string.s_arm), "s_arm", BwType.title, lines = 1)
                Line(rs(R.string.s_arm_v), "s_arm_v", BwType.label, PicoTheme.colorScheme.labelSecondary, lines = 1)
            }
            Options(rs(R.string.forearm), listOf<Pair<Int?, String>>(null to rs(R.string.forearm_auto), 22 to "22 cm", 25 to "25 cm", 28 to "28 cm"), p.forearmCm, "forearm", 100.dp) { v -> if (!dim) pr("forearm") { it.copy(forearmCm = v) } }
            Options(rs(R.string.wrist), listOf<Pair<Girth?, String>>(null to rs(R.string.forearm_auto), Girth.THIN to rs(R.string.girth_thin), Girth.MID to rs(R.string.girth_mid), Girth.THICK to rs(R.string.girth_thick)), p.girth, "girth", 100.dp) { v -> if (!dim) pr("girth") { it.copy(girth = v) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Key(rs(R.string.s_howto), "howto", R.drawable.ic_info, KeyKind.NORMAL, 64.dp, Modifier.weight(1f)) { if (!dim) onEvent(Ui.Ask(SettingsAsk.HOWTO)) }
                Key(rs(R.string.s_about), "about", R.drawable.ic_info, KeyKind.NORMAL, 64.dp, Modifier.weight(1f)) { if (!dim) onEvent(Ui.Ask(SettingsAsk.ABOUT)) }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    // D44 / D26: [窗口回到面前] and [退出应用] fixed at the bottom of every settings view, the quit confirmation in place
    if (state.ask == SettingsAsk.EXIT) {
        Card(Modifier.fillMaxWidth(), "exit_confirm") {
            Text(text = rs(R.string.exit_q), style = BwType.title, color = BwColors.CardInk, maxLines = 1, onTextLayout = fits("exit_q"))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Key(rs(R.string.cancel), "quit_cancel", null, KeyKind.LIGHT, 64.dp, Modifier.weight(1f)) { onEvent(Ui.Ask(SettingsAsk.NONE)) }
                Key(rs(R.string.exit), "quit_yes", R.drawable.ic_logout, KeyKind.DANGER, 64.dp, Modifier.weight(1f)) { onEvent(Ui.QuitYes) }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Key(rs(R.string.s_front), "recenter", R.drawable.ic_my_location, KeyKind.NORMAL, 64.dp, Modifier.weight(1.7f)) { onEvent(Ui.Recenter) }
            Key(rs(R.string.s_exit), "quit", R.drawable.ic_logout, KeyKind.DANGER, 64.dp, Modifier.weight(1f)) { onEvent(Ui.Ask(SettingsAsk.EXIT)) }
        }
    }
}

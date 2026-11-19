package io.embodit.bandagewrap.domain

/** A bilingual string (content is data, not resources: the 3D label and the window read the same table). */
data class T(val zh: String, val en: String) {
    fun of(zh: Boolean) = if (zh) this.zh else this.en
}

enum class Segment(val zh: String, val en: String) {
    WRIST("手腕", "wrist"),
    FOREARM("前臂", "forearm"),
    HAND("手", "hand"),
    THUMB("拇指", "thumb"),
    ELBOW("肘", "elbow"),
}

/** What one turn does (design dataModel.Turn.kind). */
enum class TurnKind { ANCHOR, SPIRAL, REVERSE, EIGHT_OUT, EIGHT_BACK, SPICA_OUT, SPICA_BACK, ELBOW_POINT, ELBOW_UP, ELBOW_DOWN, FINISH }

/**
 * The seven wraps (design content.lessons; steps rewritten from St John Ambulance public first-aid pages and common
 * nursing technique, technique only — no injury care claims). [width] = bandage width (armModel.bandageWidth).
 */
data class Wrap(
    val id: String,
    val scene: T,
    val tagline: T,
    val why: T,
    val segment: Segment,
    val width: Float,
    val sjaChecked: Boolean,
)

/** One turn's copy: the big line in the window, the short line on the 3D label, and the small "why". */
data class TurnCopy(val cue: T, val label: T, val why: T)

object Wraps {
    val all: List<Wrap> =
        listOf(
            Wrap("L1", T("先转两圈", "Two turns to start"), T("卷在上面，往外放，别往里拉。", "Roll on top. Unroll it away from you, don't drag it."),
                T("起手两圈压住绷带头，后面拉的时候不会散。", "Two flat turns pin the loose end, so it won't unravel when you pull."), Segment.WRIST, 0.05f, true),
            Wrap("L2", T("前臂擦破了", "Scraped forearm"), T("从手腕往肘走，每圈压住上一圈三分之二。", "Wrist to elbow, each turn covering two thirds of the last."),
                T("每圈压住三分之二，松紧才均匀，不会露缝。", "Covering two thirds keeps the tension even and leaves no gaps."), Segment.FOREARM, 0.075f, true),
            Wrap("L3", T("越往上越粗", "Thicker as you go"), T("每圈拇指一压、往回一折，就不会松。", "Press with your thumb, fold it back each turn, and it won't slip."),
                T("前臂越往上越粗，平着绕会翘边，反折一下就贴住了。", "The forearm widens. A flat turn gapes at the edge, a fold makes it lie flat."), Segment.FOREARM, 0.075f, false),
            Wrap("L4", T("手腕扭了", "Sprained wrist"), T("手腕一圈、手掌一圈，画个 8 字。", "Round the wrist, round the palm. Draw an 8."),
                T("手腕会动，八字交叉让绷带在关节两边都有着落，不往下滑。", "The wrist moves. Crossing in an 8 anchors it on both sides, so it doesn't slide."), Segment.WRIST, 0.05f, true),
            Wrap("L5", T("手背划了一道", "Cut on the back of the hand"), T("斜过手背到小指根，绕过去，再斜回来。只留指尖。", "Across the back to the little finger, round, and back. Leave the fingertips."),
                T("手背是斜的，斜着走才服帖。指尖露着，方便看颜色。", "The back of the hand slopes, so diagonal turns sit flat. Fingertips stay out so you can see their colour."), Segment.HAND, 0.05f, true),
            Wrap("L6", T("拇指别乱动", "Keep that thumb still"), T("手腕一圈、拇指一圈，在手背交叉。", "Round the wrist, round the thumb, cross on the back."),
                T("拇指根是个岔口，绕过拇指再回手腕，交叉压住才不松。", "The base of the thumb is a fork. Go round the thumb and back to the wrist, so the cross holds it."), Segment.THUMB, 0.035f, false),
            Wrap("L7", T("肘弯里", "Round the elbow"), T("肘半弯着，先包住肘尖，再一上一下画 8 字，越画越宽。", "Elbow half bent. Cover the point first, then 8s above and below, wider each time."),
                T("肘要弯，先盖肘尖再上下交替，弯的时候不会绷开。", "The elbow bends. Cover the point first, then alternate above and below, so it doesn't pull open."), Segment.ELBOW, 0.075f, true),
        )

    fun byId(id: String): Wrap? = all.firstOrNull { it.id == id }

    fun index(id: String): Int = all.indexOfFirst { it.id == id }

    // ---------------------------------------------------------------------------------------------- per-turn copy

    private val anchorWhy = T("起手两圈压住绷带头，后面不会散。", "Two flat turns pin the end so it won't come loose.")
    private val finishWhy = T("收尾平着绕，收口不翘。", "Flat turns at the end keep the edge down.")

    /** The window's big line and the 3D label line (copy as in the sample's cue(): screens.js), plus the small why. */
    fun copy(w: Wrap, kind: TurnKind, k: Int, n: Int): TurnCopy =
        when (kind) {
            TurnKind.ANCHOR -> TurnCopy(T("从手腕内侧起，卷在上面，绕两圈。", "Start inside the wrist, roll on top. Two turns."), T("从内侧起", "Start inside"), anchorWhy)
            TurnKind.ELBOW_POINT -> TurnCopy(T("从肘弯内侧起，先包住肘尖。", "Start inside the elbow. Cover the point first."), T("包住肘尖", "Cover the point"), w.why)
            TurnKind.FINISH -> TurnCopy(T("最后两圈平着绕，收口塞进去或贴胶带。", "Two flat turns to finish. Tuck the end in or tape it."), T("收尾两圈", "Two to finish"), finishWhy)
            TurnKind.SPIRAL -> TurnCopy(T("斜着往上走，压住上一圈三分之二。", "Angle up the arm. Cover two thirds of the last turn."), T("压住三分之二", "Cover two thirds"), w.why)
            TurnKind.REVERSE -> TurnCopy(T("这一圈在外侧反折一下，拇指按住。", "Fold it back on the outside. Thumb on the fold."), T("外侧反折", "Fold back"), w.why)
            TurnKind.EIGHT_OUT ->
                if (w.id == "L5") TurnCopy(T("斜过手背，奔小指根去。", "Across the back, toward the little finger."), T("奔小指根", "To little finger"), w.why)
                else TurnCopy(T("斜过手背，绕手掌一圈。", "Across the back of the hand, round the palm."), T("斜过手背", "Across the back"), w.why)
            TurnKind.EIGHT_BACK ->
                if (w.id == "L5") TurnCopy(T("绕过指前，斜回手腕。只留指尖。", "Round the fingers, back to the wrist. Leave the tips."), T("只留指尖", "Leave the tips"), w.why)
                else TurnCopy(T("斜回来，绕手腕一圈。", "Back across, round the wrist."), T("交叉在手背", "Cross on the back"), w.why)
            TurnKind.SPICA_OUT -> TurnCopy(T("斜过手背，绕拇指一圈。", "Across the back, round the thumb."), T("绕拇指", "Round the thumb"), w.why)
            TurnKind.SPICA_BACK -> TurnCopy(T("斜回来，绕手腕一圈。", "Back across, round the wrist."), T("交叉在手背", "Cross on the back"), w.why)
            TurnKind.ELBOW_UP -> TurnCopy(T("斜上去一圈，盖住肘上半。", "Up and round. Cover the upper half."), T("盖住上半", "Upper half"), w.why)
            TurnKind.ELBOW_DOWN -> TurnCopy(T("斜下来一圈，盖住肘下半。", "Down and round. Cover the lower half."), T("盖住下半", "Lower half"), w.why)
        }

    /** Forbidden words (design rules: technique only). Unit-tested over every string. */
    val banned = listOf("止血", "固定", "治疗", "止痛", "消毒")
}

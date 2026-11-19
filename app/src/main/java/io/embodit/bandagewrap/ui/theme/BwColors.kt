package io.embodit.bandagewrap.ui.theme

import androidx.compose.ui.graphics.Color

/** The design's colours (design-package palette + brandTokens). The only file with colour literals. */
object BwColors {
    // window keys (PicoTheme roles stay the system's own, design palette.colorScheme inherit)
    val Lead = Color(0xFFF26B1D) // design-style: fixed-figma-color leadOrange: main key, roll arrow, next-turn line
    val InkOnOrange = Color(0xFF1F1408) // design-style: fixed-figma-color inkOnOrange (≈ 6:1 on the orange)
    val Passable = Color(0xFF5FD3A4) // design-style: fixed-figma-color passable: aligned, watched ✓
    val PassInk = Color(0xFF1F7A57) // design-style: fixed-figma-color passable, darkened for icons on the light glass (≥ 3:1)
    val Alert = Color(0xFFFFB648) // design-style: fixed-figma-color alert: inline bars
    val DangerFill = Color(0xFFFF8A85) // design-style: fixed-figma-color quit key
    val OnError = Color(0xFF0A0D10) // design-style: fixed-figma-color ink on the danger fill

    // cards: opaque dark slate with light text (≥ 4.5:1 regardless of the glass behind)
    val CardBg = Color(0xFF1E2A33) // design-style: fixed-figma-color card background
    val CardInk = Color(0xFFE6F1F7) // design-style: fixed-figma-color card text
    val CardDim = Color(0xFFB8C7D1) // design-style: fixed-figma-color secondary text on the card
    val WarnBg = Color(0xFF4A2E0A) // design-style: fixed-figma-color amber inline bar
    val WarnInk = Color(0xFFFFD9A8) // design-style: fixed-figma-color amber text
    val SegOff = Color(0xFF3A4752) // design-style: fixed-figma-color turn progress, not yet

    // 3D (brandTokens)
    val Gauze = Color(0xFFF2EBDD) // design-style: fixed-figma-color gauzeCream
    val CurrentTurn = Color(0xFFFFE9A8) // design-style: fixed-figma-color currentTurnWarm
    val Skin = Color(0xFFD8AE92) // design-style: fixed-figma-color neutral skin (tableArm 中性肤色)
    val ClinicGreen = Color(0xFF2E7D6B) // design-style: fixed-figma-color app icon background
}

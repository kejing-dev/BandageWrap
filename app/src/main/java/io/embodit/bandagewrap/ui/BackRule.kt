package io.embodit.bandagewrap.ui

/**
 * What BACK / controller B does (profile D60): never quit, never leave the scene. The window closed → open it again;
 * otherwise go back one level inside the window; at the top of a tab → nothing (the key is eaten).
 */
enum class BackAction { REOPEN_WINDOW, WAIT, CLOSE_ASK, CLOSE_MORE, TO_WATCH, TO_TABLE, TO_LIST, NONE }

object BackRule {
    fun decide(s: BwUiState, windowOpen: Boolean, recentering: Boolean): BackAction =
        when {
            recentering -> BackAction.WAIT // "看正前方": the window comes back on its own once the head is steady
            !windowOpen -> BackAction.REOPEN_WINDOW
            s.loading -> BackAction.NONE
            s.tab == Tab.SETTINGS -> if (s.ask != SettingsAsk.NONE) BackAction.CLOSE_ASK else BackAction.NONE
            s.moreOpen -> BackAction.CLOSE_MORE
            s.screen == Screen.ALIGN || s.screen == Screen.MANUAL || s.screen == Screen.UNSUPPORTED -> BackAction.TO_WATCH
            s.screen == Screen.WATCH && s.onMyArm -> BackAction.TO_TABLE
            s.screen == Screen.WATCH || s.screen == Screen.DONE -> BackAction.TO_LIST
            else -> BackAction.NONE
        }
}

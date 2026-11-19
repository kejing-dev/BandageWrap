package io.embodit.bandagewrap.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** D60 (QA #1 q1-1): BACK / B reopens a closed window, otherwise goes back one level, and never quits. */
class BackRuleTest {
    private val ready = BwUiState(loading = false)

    private fun d(s: BwUiState, open: Boolean = true, recenter: Boolean = false) = BackRule.decide(s, open, recenter)

    @Test fun closedWindowReopens() {
        assertEquals(BackAction.REOPEN_WINDOW, d(ready, open = false))
        assertEquals(BackAction.REOPEN_WINDOW, d(ready.copy(screen = Screen.WATCH, wrapId = "L1"), open = false))
        assertEquals(BackAction.REOPEN_WINDOW, d(ready.copy(tab = Tab.SETTINGS, ask = SettingsAsk.EXIT), open = false))
    }

    @Test fun recenteringWaits() = assertEquals(BackAction.WAIT, d(ready, open = false, recenter = true))

    @Test fun oneLevelBack() {
        val watch = ready.copy(screen = Screen.WATCH, wrapId = "L1")
        assertEquals(BackAction.CLOSE_MORE, d(watch.copy(moreOpen = true)))
        assertEquals(BackAction.TO_LIST, d(watch))
        assertEquals(BackAction.TO_LIST, d(watch.copy(screen = Screen.DONE)))
        assertEquals(BackAction.TO_TABLE, d(watch.copy(onMyArm = true)))
        assertEquals(BackAction.TO_WATCH, d(watch.copy(screen = Screen.ALIGN)))
        assertEquals(BackAction.TO_WATCH, d(watch.copy(screen = Screen.MANUAL)))
        assertEquals(BackAction.TO_WATCH, d(watch.copy(screen = Screen.UNSUPPORTED)))
        assertEquals(BackAction.CLOSE_ASK, d(ready.copy(tab = Tab.SETTINGS, ask = SettingsAsk.EXIT)))
        assertEquals(BackAction.CLOSE_ASK, d(ready.copy(tab = Tab.SETTINGS, ask = SettingsAsk.ABOUT)))
    }

    @Test fun topLevelEatsTheKey() {
        assertEquals(BackAction.NONE, d(ready))
        assertEquals(BackAction.NONE, d(ready.copy(tab = Tab.SETTINGS)))
        assertEquals(BackAction.NONE, d(BwUiState()))
    }

    /** Every state × window open / closed: there is no action that leaves the scene. */
    @Test fun neverQuits() {
        for (tab in Tab.values()) for (sc in Screen.values()) for (ask in SettingsAsk.values()) for (open in listOf(true, false)) for (more in listOf(true, false)) {
            val a = d(ready.copy(tab = tab, screen = sc, ask = ask, moreOpen = more, wrapId = "L1"), open)
            assertNotEquals(null, a)
            if (!open) assertEquals(BackAction.REOPEN_WINDOW, a)
        }
    }
}

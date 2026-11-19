package io.embodit.bandagewrap

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import io.embodit.bandagewrap.ui.BwScreen
import io.embodit.bandagewrap.ui.components.PanelSizes
import io.embodit.bandagewrap.ui.theme.BwTheme
import io.embodit.bandagewrap.ui.window.BwWindow
import io.embodit.bandagewrap.ui.window.PANEL_ID

/**
 * App entry (design container STAGE_MIXED): the default Stage is Mixed (manifest pico.spatial.stage.style=1) so you see the
 * real room and your own arm; all 2D content is one system Planar window opened from the Stage (D8 / D21, native-window-move);
 * the system moves / closes / minimises it.
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage { BwTheme { BwScreen() } }
        WindowContainer(
            PANEL_ID,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = PanelSizes.WINDOW_W.dp, height = PanelSizes.WINDOW_H.dp),
        ) { BwWindow() }
    }

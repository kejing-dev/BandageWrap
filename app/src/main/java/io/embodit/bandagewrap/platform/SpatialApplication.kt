package io.embodit.bandagewrap.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import io.embodit.bandagewrap.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // D60: controller B = BACK never quits; caught on every activity before the system sees it
        BackKeys.onBack = { src -> io.embodit.bandagewrap.ui.BwController.onBack(src) }
        BackKeys.install(this)
        launch(::mainApp)
    }
}

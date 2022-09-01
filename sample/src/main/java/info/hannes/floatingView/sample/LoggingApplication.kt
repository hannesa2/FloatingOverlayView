package info.hannes.floatingView.sample

import android.app.Application
import info.hannes.timber.DebugFormatTree
import timber.log.Timber


class LoggingApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(DebugFormatTree())
    }
}

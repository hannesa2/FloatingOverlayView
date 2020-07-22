package info.hannes.floatingView.sample.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.IBinder
import android.os.Parcelable
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import info.hannes.floatingView.sample.R
import info.hannes.floatingview.FloatingViewListener
import info.hannes.floatingview.FloatingViewManager
import timber.log.Timber

class SimpleFloatingViewService : Service(), FloatingViewListener {

    private var floatingViewManager: FloatingViewManager? = null

    /**
     * {@inheritDoc}
     */
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (floatingViewManager != null) {
            return START_STICKY
        }
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)
        val inflater = LayoutInflater.from(this)

        @SuppressLint("InflateParams")
        val iconView = inflater.inflate(R.layout.widget_simple, null, false) as ImageView
        iconView.setOnClickListener { Timber.d(getString(R.string.chathead_click_message)) }

        val options = FloatingViewManager.Options()
        options.overMargin = (16 * metrics.density).toInt()

        floatingViewManager = FloatingViewManager(this, this).apply {
            setFixedTrashIconImage(R.drawable.ic_trash_fixed)
            setActionTrashIconImage(R.drawable.ic_trash_action)
            setSafeInsetRect(intent.getParcelableExtra<Parcelable>(EXTRA_CUTOUT_SAFE_AREA) as Rect)
            addViewToWindow(iconView, options)
        }

        startForeground(NOTIFICATION_ID, createNotification(this))
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        destroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onFinishFloatingView() {
        stopSelf()
        Timber.d(getString(R.string.finish_deleted))
    }

    override fun onTouchFinished(isFinishing: Boolean, x: Int, y: Int) {
        if (isFinishing) {
            Timber.d(getString(R.string.deleted_soon))
        } else {
            Timber.d("Current position $x $y")
        }
    }

    private fun destroy() {
        floatingViewManager?.apply { removeAllViewToWindow() }
        floatingViewManager = null
    }

    companion object {
        const val EXTRA_CUTOUT_SAFE_AREA = "cutout_safe_area"
        private const val NOTIFICATION_ID = 9083199
        private fun createNotification(context: Context): Notification {
            val builder = NotificationCompat.Builder(context, context.getString(R.string.default_floatingview_channel_id))
            builder.setWhen(System.currentTimeMillis())
            builder.setSmallIcon(R.mipmap.ic_launcher)
            builder.setContentTitle(context.getString(R.string.chathead_content_title))
            builder.setContentText(context.getString(R.string.content_text))
            builder.setOngoing(true)
            builder.priority = NotificationCompat.PRIORITY_MIN
            builder.setCategory(NotificationCompat.CATEGORY_SERVICE)
            return builder.build()
        }
    }
}
package info.hannes.floatingView.sample.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.IBinder
import android.os.Parcelable
import android.preference.PreferenceManager
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import info.hannes.floatingView.sample.DeleteActionActivity
import info.hannes.floatingView.sample.R
import info.hannes.floatingview.FloatingViewListener
import info.hannes.floatingview.FloatingViewManager
import timber.log.Timber

class CustomFloatingViewService : Service(), FloatingViewListener {

    private var floatingViewManager: FloatingViewManager? = null

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (floatingViewManager != null) {
            return START_STICKY
        }
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)
        val inflater = LayoutInflater.from(this)
        val iconView = inflater.inflate(R.layout.widget_mail, null, false) as ImageView
        iconView.setOnClickListener {
            val intentSend = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getString(R.string.mail_address), null))
            intentSend.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.mail_title))
            intentSend.putExtra(Intent.EXTRA_TEXT, getString(R.string.mail_content))
            intentSend.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intentSend)
            Timber.d("intentSend")
        }
        floatingViewManager = FloatingViewManager(this, this).apply {
            setFixedTrashIconImage(R.drawable.ic_trash_fixed)
            setActionTrashIconImage(R.drawable.ic_trash_action)
            setSafeInsetRect(intent.getParcelableExtra<Parcelable>(EXTRA_CUTOUT_SAFE_AREA) as Rect)
        }
        // Setting Options(you can change options at any time)
        loadDynamicOptions()
        // Initial Setting Options (you can't change options after created.)
        val options = loadOptions(metrics)
        floatingViewManager!!.addViewToWindow(iconView, options)

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
    }

    override fun onTouchFinished(isFinishing: Boolean, x: Int, y: Int) {
        if (!isFinishing) {
            // Save the last position
            val editor = PreferenceManager.getDefaultSharedPreferences(this).edit()
            editor.putInt(PREF_KEY_LAST_POSITION_X, x)
            editor.putInt(PREF_KEY_LAST_POSITION_Y, y)
            editor.apply()
            Timber.d("isFinishing save x=$x y=$y")
        } else
            Timber.d("isFinishing")
    }

    private fun destroy() {
        floatingViewManager?.apply {
            removeAllViewToWindow()
        }
        floatingViewManager = null
    }

    private fun loadDynamicOptions() {
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)
        val displayModeSettings = sharedPref.getString("settings_display_mode", "")
        if ("Always" == displayModeSettings) {
            floatingViewManager!!.setDisplayMode(FloatingViewManager.DISPLAY_MODE_SHOW_ALWAYS)
        } else if ("FullScreen" == displayModeSettings) {
            floatingViewManager!!.setDisplayMode(FloatingViewManager.DISPLAY_MODE_HIDE_FULLSCREEN)
        } else if ("Hide" == displayModeSettings) {
            floatingViewManager!!.setDisplayMode(FloatingViewManager.DISPLAY_MODE_HIDE_ALWAYS)
        }
    }

    private fun loadOptions(metrics: DisplayMetrics): FloatingViewManager.Options {
        val options = FloatingViewManager.Options()
        val sharedPref = PreferenceManager.getDefaultSharedPreferences(this)

        // Shape
        val shapeSettings = sharedPref.getString("settings_shape", "")
        if ("Circle" == shapeSettings) {
            options.shape = FloatingViewManager.SHAPE_CIRCLE
        } else if ("Rectangle" == shapeSettings) {
            options.shape = FloatingViewManager.SHAPE_RECTANGLE
        }

        options.docking = sharedPref.getBoolean("settings_docking", options.docking)
        // Margin
//        options.overMargin = sharedPref.getInt("settings_margin", options.overMargin)
//        options.overMarginX = sharedPref.getInt("settings_marginX", options.overMarginX)
//        options.overMarginY = sharedPref.getInt("settings_marginY", options.overMarginY)

        // MoveDirection
        when (sharedPref.getString("settings_move_direction", "")) {
            "Default" -> options.moveDirection = FloatingViewManager.MOVE_DIRECTION_DEFAULT
            "Left" -> options.moveDirection = FloatingViewManager.MOVE_DIRECTION_LEFT
            "Right" -> options.moveDirection = FloatingViewManager.MOVE_DIRECTION_RIGHT
            "Nearest" -> options.moveDirection = FloatingViewManager.MOVE_DIRECTION_NEAREST
            "Fix" -> options.moveDirection = FloatingViewManager.MOVE_DIRECTION_NONE
            "Thrown" -> options.moveDirection = FloatingViewManager.MOVE_DIRECTION_THROWN
        }
        options.usePhysics = sharedPref.getBoolean("settings_use_physics", true)

        // Last position
        val isUseLastPosition = sharedPref.getBoolean("settings_save_last_position", false)
        if (isUseLastPosition) {
            val defaultX = metrics.widthPixels / 2
            val defaultY = metrics.heightPixels / 2
            options.floatingViewX = sharedPref.getInt(PREF_KEY_LAST_POSITION_X, defaultX)
            options.floatingViewY = sharedPref.getInt(PREF_KEY_LAST_POSITION_Y, defaultY)
            Timber.d("Restore position x=${options.floatingViewX} y=${options.floatingViewY}")
        } else {
            // Init X/Y
            val initXSettings = sharedPref.getString("settings_init_x", POSITION_DEFAULT)
            val initYSettings = sharedPref.getString("settings_init_y", POSITION_DEFAULT)
            if (!(initXSettings == POSITION_DEFAULT) && !(initYSettings == POSITION_DEFAULT)) {
                val offset = (48 + 8 * metrics.density).toInt()
                options.floatingViewX = (metrics.widthPixels * initXSettings!!.toFloat() - offset).toInt()
                options.floatingViewY = (metrics.heightPixels * initYSettings!!.toFloat() - offset).toInt()
                Timber.d("Initial position preserve x=${options.floatingViewX} y=${options.floatingViewY}")
            } else {
                // center on default
                val offset = (48 + 8 * metrics.density).toInt()
                options.floatingViewX = (metrics.widthPixels / 2 - offset)
                options.floatingViewY = (metrics.heightPixels / 2 - offset)
                Timber.d("Initial position x=${options.floatingViewX} y=${options.floatingViewY}")
            }
        }

        // Initial Animation
        val animationSettings = sharedPref.getBoolean("settings_animation", options.animateInitialMove)
        options.animateInitialMove = animationSettings
        return options
    }

    companion object {
        const val POSITION_DEFAULT = "0"
        const val EXTRA_CUTOUT_SAFE_AREA = "cutout_safe_area"
        private const val NOTIFICATION_ID = 908114
        private const val PREF_KEY_LAST_POSITION_X = "last_position_x"
        private const val PREF_KEY_LAST_POSITION_Y = "last_position_y"
        private fun createNotification(context: Context): Notification {
            val builder = NotificationCompat.Builder(context, context.getString(R.string.default_floatingview_channel_id))
            builder.setWhen(System.currentTimeMillis())
            builder.setSmallIcon(R.mipmap.ic_launcher)
            builder.setContentTitle(context.getString(R.string.mail_content_title))
            builder.setContentText(context.getString(R.string.content_text))
            builder.setOngoing(true)
            builder.priority = NotificationCompat.PRIORITY_MIN
            builder.setCategory(NotificationCompat.CATEGORY_SERVICE)

            val notifyIntent = Intent(context, DeleteActionActivity::class.java)

            val requestID = System.currentTimeMillis().toInt() + Random.nextInt(0, 1000)

            val notifyPendingIntent: PendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.getActivity(
                    /* context = */ context,
                    /* requestCode = */ requestID,
                    /* intent = */ notifyIntent,
                    /* flags = */PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getActivity(
                    /* context = */ context,
                    /* requestCode = */ requestID,
                    /* intent = */ notifyIntent,
                    /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            builder.setContentIntent(notifyPendingIntent)
            return builder.build()
        }
    }
}

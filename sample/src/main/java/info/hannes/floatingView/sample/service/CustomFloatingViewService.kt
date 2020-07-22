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
import android.text.TextUtils
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import info.hannes.floatingview.FloatingViewListener
import info.hannes.floatingview.FloatingViewManager
import info.hannes.floatingView.sample.DeleteActionActivity
import info.hannes.floatingView.sample.R
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
            val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getString(R.string.mail_address), null))
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.mail_title))
            intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.mail_content))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Timber.d("iconView")
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
        }
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

        // Margin
        val marginSettings = sharedPref.getString("settings_margin", options.overMargin.toString())
        options.overMargin = marginSettings!!.toInt()

        // MoveDirection
        val moveDirectionSettings = sharedPref.getString("settings_move_direction", "")
        if ("Default" == moveDirectionSettings) {
            options.moveDirection = FloatingViewManager.MOVE_DIRECTION_DEFAULT
        } else if ("Left" == moveDirectionSettings) {
            options.moveDirection = FloatingViewManager.MOVE_DIRECTION_LEFT
        } else if ("Right" == moveDirectionSettings) {
            options.moveDirection = FloatingViewManager.MOVE_DIRECTION_RIGHT
        } else if ("Nearest" == moveDirectionSettings) {
            options.moveDirection = FloatingViewManager.MOVE_DIRECTION_NEAREST
        } else if ("Fix" == moveDirectionSettings) {
            options.moveDirection = FloatingViewManager.MOVE_DIRECTION_NONE
        } else if ("Thrown" == moveDirectionSettings) {
            options.moveDirection = FloatingViewManager.MOVE_DIRECTION_THROWN
        }
        options.usePhysics = sharedPref.getBoolean("settings_use_physics", true)

        // Last position
        val isUseLastPosition = sharedPref.getBoolean("settings_save_last_position", false)
        if (isUseLastPosition) {
            val defaultX = options.floatingViewX
            val defaultY = options.floatingViewY
            options.floatingViewX = sharedPref.getInt(PREF_KEY_LAST_POSITION_X, defaultX)
            options.floatingViewY = sharedPref.getInt(PREF_KEY_LAST_POSITION_Y, defaultY)
        } else {
            // Init X/Y
            val initXSettings = sharedPref.getString("settings_init_x", "")
            val initYSettings = sharedPref.getString("settings_init_y", "")
            if (!TextUtils.isEmpty(initXSettings) && !TextUtils.isEmpty(initYSettings)) {
                val offset = (48 + 8 * metrics.density).toInt()
                options.floatingViewX = (metrics.widthPixels * initXSettings!!.toFloat() - offset).toInt()
                options.floatingViewY = (metrics.heightPixels * initYSettings!!.toFloat() - offset).toInt()
            }
        }

        // Initial Animation
        val animationSettings = sharedPref.getBoolean("settings_animation", options.animateInitialMove)
        options.animateInitialMove = animationSettings
        return options
    }

    companion object {
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
            val notifyPendingIntent = PendingIntent.getActivity(context, 0, notifyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            builder.setContentIntent(notifyPendingIntent)
            return builder.build()
        }
    }
}
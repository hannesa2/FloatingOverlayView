package info.hannes.floatingView.sample.fragment

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import info.hannes.floatingView.sample.R
import info.hannes.floatingView.sample.service.SimpleFloatingViewService
import info.hannes.floatingView.sample.service.CustomFloatingViewService
import info.hannes.floatingview.FloatingViewManager
import kotlinx.android.synthetic.main.fragment_floating_view_control.*

class FloatingViewControlFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_floating_view_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        show_demo.setOnClickListener {
            showFloatingView(activity, true, false)
        }
        show_customized_demo.setOnClickListener {
            showFloatingView(activity, true, true)
        }
        show_settings.setOnClickListener {
            val ft = fragmentManager!!.beginTransaction()
            ft.replace(R.id.container, FloatingViewSettingsFragment.newInstance())
            ft.addToBackStack(null)
            ft.commit()
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CHATHEAD_OVERLAY_PERMISSION_REQUEST_CODE) {
            showFloatingView(activity, false, false)
        } else if (requestCode == CUSTOM_OVERLAY_PERMISSION_REQUEST_CODE) {
            showFloatingView(activity, false, true)
        }
    }

    @SuppressLint("NewApi")
    private fun showFloatingView(context: Context?, isShowOverlayPermission: Boolean, isCustomFloatingView: Boolean) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            startFloatingViewService(activity, isCustomFloatingView)
            return
        }
        if (Settings.canDrawOverlays(context)) {
            startFloatingViewService(activity, isCustomFloatingView)
            return
        }
        if (isShowOverlayPermission) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context!!.packageName))
            startActivityForResult(intent, if (isCustomFloatingView) CUSTOM_OVERLAY_PERMISSION_REQUEST_CODE else CHATHEAD_OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    companion object {
        private const val CHATHEAD_OVERLAY_PERMISSION_REQUEST_CODE = 100
        private const val CUSTOM_OVERLAY_PERMISSION_REQUEST_CODE = 101
        fun newInstance(): FloatingViewControlFragment {
            return FloatingViewControlFragment()
        }

        private fun startFloatingViewService(activity: Activity?, isCustomFloatingView: Boolean) {
            // *** You must follow these rules when obtain the cutout(FloatingViewManager.findCutoutSafeArea) ***
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // 1. 'windowLayoutInDisplayCutoutMode' do not be set to 'never'
                if (activity!!.window.attributes.layoutInDisplayCutoutMode == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER) {
                    throw RuntimeException("'windowLayoutInDisplayCutoutMode' do not be set to 'never'")
                }
            }

            // launch service
            val service: Class<out Service?>
            val key: String
            if (isCustomFloatingView) {
                service = CustomFloatingViewService::class.java
                key = CustomFloatingViewService.EXTRA_CUTOUT_SAFE_AREA
            } else {
                service = SimpleFloatingViewService::class.java
                key = SimpleFloatingViewService.EXTRA_CUTOUT_SAFE_AREA
            }
            val intent = Intent(activity, service)
            intent.putExtra(key, FloatingViewManager.findCutoutSafeArea(activity!!))
            ContextCompat.startForegroundService(activity, intent)
        }
    }
}
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
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import info.hannes.floatingView.sample.R
import info.hannes.floatingView.sample.databinding.FragmentFloatingViewControlBinding
import info.hannes.floatingView.sample.service.CustomFloatingViewService
import info.hannes.floatingView.sample.service.SimpleFloatingViewService
import info.hannes.floatingview.FloatingViewManager
import timber.log.Timber

class FloatingViewControlFragment : Fragment() {

    private var _binding: FragmentFloatingViewControlBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFloatingViewControlBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onResume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.canDraw.isChecked = Settings.canDrawOverlays(requireContext())
        } else
            binding.canDraw.visibility = View.GONE
        super.onResume()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.showDemo.setOnClickListener {
            showFloatingView(activity, isShowOverlayPermission = true, isCustomFloatingView = false)
        }
        binding.showCustomizedDemo.setOnClickListener {
            showFloatingView(activity, isShowOverlayPermission = true, isCustomFloatingView = true)
        }
        binding.showSettings.setOnClickListener {
            val ft = requireFragmentManager().beginTransaction()
            ft.replace(R.id.container, FloatingViewSettingsFragment.newInstance())
            ft.addToBackStack(null)
            ft.commit()
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CHATHEAD_OVERLAY_PERMISSION_REQUEST_CODE) {
            showFloatingView(activity, isShowOverlayPermission = false, isCustomFloatingView = false)
        } else if (requestCode == CUSTOM_OVERLAY_PERMISSION_REQUEST_CODE) {
            showFloatingView(activity, isShowOverlayPermission = false, isCustomFloatingView = true)
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
        } else {
            Snackbar.make(
                requireActivity().findViewById(android.R.id.content),
                "You need Settings.canDrawOverlays()",
                Snackbar.LENGTH_LONG
            )
                .setAction("Ok") {
                }
                .show()
            Timber.w("You need Settings.canDrawOverlays()")
        }
        if (isShowOverlayPermission) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + requireContext().packageName))
                requireContext().packageManager?.let {
                    if (it.resolveActivity(intent, 0) != null)
                        startActivityForResult(
                            intent,
                            if (isCustomFloatingView)
                                CUSTOM_OVERLAY_PERMISSION_REQUEST_CODE
                            else
                                CHATHEAD_OVERLAY_PERMISSION_REQUEST_CODE
                        )
                    else {
                        Handler(Looper.getMainLooper()).postDelayed({
                            Snackbar.make(
                                requireActivity().findViewById(android.R.id.content),
                                "Activity handling ACTION_MANAGE_OVERLAY_PERMISSION not exists",
                                Snackbar.LENGTH_LONG
                            )
                                .setAction("Ok") {
                                }
                                .show()
                        }, 1500)

                        Timber.e("Activity handling ACTION_MANAGE_OVERLAY_PERMISSION not exists")
                    }
                }
            } catch (e: Exception) {
                Snackbar.make(
                    requireActivity().findViewById(android.R.id.content),
                    e.message.toString(),
                    Snackbar.LENGTH_LONG
                )
                    .setAction("Ok") {
                    }
                    .show()
                Timber.e(e)
            }
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
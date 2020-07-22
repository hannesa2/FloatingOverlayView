package info.hannes.floatingView.sample.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.hannes.floatingView.sample.R
import info.hannes.floatingView.sample.service.CustomFloatingViewService
import kotlinx.android.synthetic.main.fragment_delete_action.*

class DeleteActionFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_delete_action, container, false)
        clearDemo.setOnClickListener { // Easy way to delete a service
            val activity: Activity? = activity
            activity!!.stopService(Intent(activity, CustomFloatingViewService::class.java))
        }
        return rootView
    }

    companion object {
        fun newInstance(): DeleteActionFragment {
            return DeleteActionFragment()
        }
    }
}
package info.hannes.floatingView.sample.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import info.hannes.floatingView.sample.databinding.FragmentDeleteActionBinding
import info.hannes.floatingView.sample.service.CustomFloatingViewService

class DeleteActionFragment : Fragment() {

    private var _binding: FragmentDeleteActionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeleteActionBinding.inflate(inflater, container, false)
        val view = binding.root

        binding.clearDemo.setOnClickListener { // Easy way to delete a service
            val activity: Activity? = activity
            activity!!.stopService(Intent(activity, CustomFloatingViewService::class.java))
        }
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): DeleteActionFragment {
            return DeleteActionFragment()
        }
    }
}
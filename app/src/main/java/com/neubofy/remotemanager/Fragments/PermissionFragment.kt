package com.neubofy.remotemanager.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.neubofy.remotemanager.Activities.MainActivity
import com.neubofy.remotemanager.databinding.FragmentPermissionsBinding
import com.neubofy.remotemanager.util.PermissionManager


class PermissionFragment : Fragment() {

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!
    private lateinit var mPermissionManager: PermissionManager
    private var isSettingsMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            isSettingsMode = it.getBoolean(ARG_IS_SETTINGS_MODE, false)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        mPermissionManager = PermissionManager(requireContext())
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        updateVisibilities()
        binding.buttonAlarms.setOnClickListener {
            mPermissionManager.requestAlarms()
        }
        binding.buttonStorage.setOnClickListener {
            mPermissionManager.requestStorage(this.requireActivity())
        }
        binding.buttonNotifications.setOnClickListener {
            startActivity(PermissionManager.getNotificationSettingsIntent(requireContext()))
        }
        binding.buttonBatteryOptimizations.setOnClickListener {
            mPermissionManager.requestBatteryOptimizationException()
        }
    }

    override fun onResume() {
        super.onResume()
        updateVisibilities()
    }

    fun updateVisibilities() {
        if (isSettingsMode) {
            if (mPermissionManager.grantedStorage()) {
                binding.buttonStorage.text = "Granted"
                binding.buttonStorage.isEnabled = false
            }
            if (mPermissionManager.grantedAlarms()) {
                binding.buttonAlarms.text = "Granted"
                binding.buttonAlarms.isEnabled = false
            }
            if (mPermissionManager.grantedNotifications()) {
                binding.buttonNotifications.text = "Granted"
                binding.buttonNotifications.isEnabled = false
            }
            if (mPermissionManager.grantedBatteryOptimizationExemption()) {
                binding.buttonBatteryOptimizations.text = "Granted"
                binding.buttonBatteryOptimizations.isEnabled = false
            }
        } else {
            if(mPermissionManager.grantedStorage()) {
                binding.cardStorage.visibility = View.GONE
            }
            if(mPermissionManager.grantedAlarms()) {
                binding.cardAlarms.visibility = View.GONE
            }
            if(mPermissionManager.grantedNotifications()) {
                binding.cardNotifications.visibility = View.GONE
            }
            if(mPermissionManager.grantedBatteryOptimizationExemption()) {
                binding.cardBatteryOptimizations.visibility = View.GONE
            }

            if(mPermissionManager.grantedStorage() &&
                mPermissionManager.grantedAlarms() &&
                mPermissionManager.grantedNotifications() &&
                mPermissionManager.grantedBatteryOptimizationExemption()) {
                (requireActivity() as MainActivity).startRemotesFragment()
            }
        }
    }

    companion object {
        private const val ARG_IS_SETTINGS_MODE = "is_settings_mode"

        fun newInstance(isSettingsMode: Boolean = false): PermissionFragment {
            val fragment = PermissionFragment()
            val args = Bundle()
            args.putBoolean(ARG_IS_SETTINGS_MODE, isSettingsMode)
            fragment.arguments = args
            return fragment
        }
    }
}
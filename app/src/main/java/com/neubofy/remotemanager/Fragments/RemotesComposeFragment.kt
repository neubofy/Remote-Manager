package com.neubofy.remotemanager.Fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.neubofy.remotemanager.Activities.MainActivity
import com.neubofy.remotemanager.Items.RemoteItem
import com.neubofy.remotemanager.RemoteConfig.RemoteConfig
import com.neubofy.remotemanager.ui.RemotesComposeScreen
import com.neubofy.remotemanager.ui.viewmodel.RemotesViewModel

class RemotesComposeFragment : Fragment() {

    companion object {
        const val CONFIG_REQ_CODE = 171
        const val CONFIG_EDIT_CODE = 156
        const val CONFIG_EDIT_TARGET = RemoteConfig.CONFIG_EDIT_TARGET

        @JvmStatic
        fun newInstance(): RemotesComposeFragment {
            return RemotesComposeFragment()
        }
    }

    interface OnRemoteClickListener {
        fun onRemoteClick(remote: RemoteItem)
    }

    interface AddRemoteToNavDrawer {
        fun addRemoteToNavDrawer()
        fun removeRemoteFromNavDrawer()
    }

    private val viewModel: RemotesViewModel by viewModels()
    private var remoteClickListener: OnRemoteClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnRemoteClickListener) {
            remoteClickListener = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        remoteClickListener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.neubofy.remotemanager.ui.theme.RemoteManagerTheme {
                    RemotesComposeScreen(
                        viewModel = viewModel,
                        onRemoteClick = { remote ->
                            remoteClickListener?.onRemoteClick(remote)
                        },
                        onAddNewRemote = {
                            val intent = Intent(requireContext(), RemoteConfig::class.java)
                            startActivityForResult(intent, CONFIG_REQ_CODE)
                        },
                        onEditRemoteConfig = { remote ->
                            val intent = Intent(requireContext(), RemoteConfig::class.java).apply {
                                putExtra(CONFIG_EDIT_TARGET, remote.name)
                            }
                            startActivityForResult(intent, CONFIG_EDIT_CODE)
                        }
                    )
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CONFIG_REQ_CODE || requestCode == CONFIG_EDIT_CODE) {
            viewModel.loadRemotes()
        }
    }
}

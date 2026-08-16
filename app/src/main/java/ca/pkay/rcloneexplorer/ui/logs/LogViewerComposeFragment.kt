package ca.pkay.rcloneexplorer.ui.logs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import ca.pkay.rcloneexplorer.ui.theme.RemoteManagerTheme

class LogViewerComposeFragment : Fragment() {

    private val viewModel: LogViewerViewModel by viewModels()

    companion object {
        @JvmStatic
        fun newInstance(): LogViewerComposeFragment {
            return LogViewerComposeFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                RemoteManagerTheme {
                    LogViewerComposeScreen(
                        viewModel = viewModel,
                        onNavigateBack = {
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                        }
                    )
                }
            }
        }
    }
}

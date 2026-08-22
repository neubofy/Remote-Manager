package com.neubofy.remotemanager.Fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neubofy.remotemanager.Activities.MainActivity
import com.neubofy.remotemanager.Activities.TaskActivity
import com.neubofy.remotemanager.Activities.TriggerActivity
import com.neubofy.remotemanager.R
import com.neubofy.remotemanager.ui.TasksComposeScreen
import com.neubofy.remotemanager.ui.viewmodel.TasksViewModel

class TasksComposeFragment : Fragment() {

    private val tasksViewModel: TasksViewModel by activityViewModels()

    companion object {
        @JvmStatic
        fun newInstance(): TasksComposeFragment {
            return TasksComposeFragment()
        }
    }

    override fun onResume() {
        super.onResume()
        tasksViewModel.refresh()
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
                    TasksComposeScreen(
                        viewModel = tasksViewModel,
                        onNewTaskClick = {
                            val intent = Intent(requireContext(), TaskActivity::class.java)
                            startActivity(intent)
                        },
                        onEditTaskClick = { task ->
                            val intent = Intent(requireContext(), TaskActivity::class.java).apply {
                                putExtra(TaskActivity.ID_EXTRA, task.id)
                            }
                            startActivity(intent)
                        },
                        onManageTriggersClick = { task ->
                            val intent = Intent(requireContext(), TriggerActivity::class.java).apply {
                                putExtra(TriggerActivity.TARGET_TASK_ID_EXTRA, task.id)
                            }
                            startActivity(intent)
                        },
                        onEditTriggerClick = { trigger ->
                            val intent = Intent(requireContext(), TriggerActivity::class.java).apply {
                                putExtra(TriggerActivity.ID_EXTRA, trigger.id)
                                putExtra(TriggerActivity.TARGET_TASK_ID_EXTRA, trigger.triggerTarget)
                            }
                            startActivity(intent)
                        },
                        onOpenLogsClick = {
                            val act = activity
                            if (act is MainActivity) {
                                act.startLogFragment()
                            } else {
                                parentFragmentManager.beginTransaction()
                                    .replace(R.id.flFragment, LogsComposeFragment.newInstance())
                                    .addToBackStack(null)
                                    .commit()
                            }
                        }
                    )
                }
            }
        }
    }
}

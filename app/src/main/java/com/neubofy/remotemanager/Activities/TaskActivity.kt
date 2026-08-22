package com.neubofy.remotemanager.Activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.neubofy.remotemanager.Database.DatabaseHandler
import com.neubofy.remotemanager.FilePicker
import com.neubofy.remotemanager.Fragments.FolderSelectorCallback
import com.neubofy.remotemanager.Fragments.RemoteFolderPickerFragment
import com.neubofy.remotemanager.Items.Filter
import com.neubofy.remotemanager.Items.RemoteItem
import com.neubofy.remotemanager.Items.Task
import com.neubofy.remotemanager.R
import com.neubofy.remotemanager.Rclone
import com.neubofy.remotemanager.Services.TriggerService
import com.neubofy.remotemanager.ui.TaskEditComposeScreen
import com.neubofy.remotemanager.util.ActivityHelper
import es.dmoral.toasty.Toasty

class TaskActivity : AppCompatActivity() {

    private lateinit var rcloneInstance: Rclone
    private lateinit var dbHandler: DatabaseHandler

    private var existingTask: Task? = null
    private var localPathOverride by mutableStateOf<String?>(null)
    private var remotePathOverride by mutableStateOf<String?>(null)

    companion object {
        const val ID_EXTRA = "TASK_EDIT_ID"
        const val REQUEST_CODE_FP_LOCAL = 500
        const val REQUEST_CODE_FILTER = 333
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_FP_LOCAL -> {
                if (data != null) {
                    val path = data.getStringExtra(FilePicker.FILE_PICKER_RESULT)
                    if (!path.isNullOrBlank()) {
                        localPathOverride = path
                    }
                }
            }
            REQUEST_CODE_FILTER -> {
                // Filter created/saved, recomposition will query dbHandler.allFilters
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)

        rcloneInstance = Rclone(this)
        dbHandler = DatabaseHandler(this)

        val extras = intent.extras
        if (extras != null && extras.containsKey(ID_EXTRA)) {
            val taskId = extras.getLong(ID_EXTRA)
            if (taskId != 0L) {
                existingTask = dbHandler.getTask(taskId)
                if (existingTask == null) {
                    Toasty.error(this, getString(R.string.taskactivity_task_not_found)).show()
                    finish()
                    return
                }
            }
        }

        setContent {
            com.neubofy.remotemanager.ui.theme.RemoteManagerTheme {
                TaskEditComposeScreen(
                    existingTask = existingTask,
                    remotes = rcloneInstance.remotes,
                    filters = dbHandler.allFilters,
                    allTasks = dbHandler.allTasks,
                    localPathOverride = localPathOverride,
                    remotePathOverride = remotePathOverride,
                    onSaveTask = { taskToSave ->
                        if (existingTask == null) {
                            dbHandler.createTask(taskToSave)
                        } else {
                            dbHandler.updateTask(taskToSave)
                        }
                        finish()
                    },
                    onDeleteTask = if (existingTask != null) {
                        { taskToDelete ->
                            // Cancel triggers in TriggerService
                            val triggers = dbHandler.getTriggersForTask(taskToDelete.id)
                            val triggerService = TriggerService(this)
                            for (t in triggers) {
                                triggerService.cancelTrigger(t.id)
                            }
                            dbHandler.deleteTask(taskToDelete.id)
                            finish()
                        }
                    } else null,
                    onPickLocalPath = {
                        val intent = Intent(applicationContext, FilePicker::class.java).apply {
                            putExtra(FilePicker.FILE_PICKER_PICK_DESTINATION_TYPE, true)
                        }
                        startActivityForResult(intent, REQUEST_CODE_FP_LOCAL)
                    },
                    onCreateFilter = {
                        val intent = Intent(this, FilterActivity::class.java)
                        startActivityForResult(intent, REQUEST_CODE_FILTER)
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}
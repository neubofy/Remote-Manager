package com.neubofy.remotemanager.Activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.neubofy.remotemanager.Database.DatabaseHandler
import com.neubofy.remotemanager.Items.Trigger
import com.neubofy.remotemanager.R
import com.neubofy.remotemanager.Services.TriggerService
import com.neubofy.remotemanager.ui.TriggerEditComposeScreen
import com.neubofy.remotemanager.util.ActivityHelper
import es.dmoral.toasty.Toasty

class TriggerActivity : AppCompatActivity() {

    companion object {
        const val ID_EXTRA = "TRIGGER_EDIT_ID"
        const val TARGET_TASK_ID_EXTRA = "TARGET_TASK_ID"
    }

    private lateinit var dbHandler: DatabaseHandler
    private var existingTrigger: Trigger? = null
    private var targetTaskId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)

        dbHandler = DatabaseHandler(this)

        val extras = intent.extras
        if (extras != null && extras.containsKey(ID_EXTRA)) {
            val triggerId = extras.getLong(ID_EXTRA)
            if (triggerId != 0L) {
                existingTrigger = dbHandler.getTrigger(triggerId)
                if (existingTrigger == null) {
                    Toasty.error(this, getString(R.string.triggeractivity_trigger_not_found)).show()
                    finish()
                    return
                }
            }
        }

        targetTaskId = intent.getLongExtra(TARGET_TASK_ID_EXTRA, -1L)

        val allTasks = dbHandler.allTasks
        if (allTasks.isEmpty()) {
            Toasty.error(this, getString(R.string.trigger_save_notasks)).show()
            finish()
            return
        }

        setContent {
            com.neubofy.remotemanager.ui.theme.RemoteManagerTheme {
                TriggerEditComposeScreen(
                    existingTrigger = existingTrigger,
                    initialTargetTaskId = targetTaskId,
                    allTasks = allTasks,
                    onSaveTrigger = { triggerToSave ->
                        val savedTrigger = if (existingTrigger == null || existingTrigger?.id == Trigger.TRIGGER_ID_DOESNTEXIST) {
                            dbHandler.createTrigger(triggerToSave)
                        } else {
                            dbHandler.updateTrigger(triggerToSave)
                            triggerToSave
                        }
                        TriggerService(this).queueSingleTrigger(savedTrigger)
                        finish()
                    },
                    onDeleteTrigger = if (existingTrigger != null) {
                        { triggerToDelete ->
                            TriggerService(this).cancelTrigger(triggerToDelete.id)
                            dbHandler.deleteTrigger(triggerToDelete.id)
                            finish()
                        }
                    } else null,
                    onBack = { finish() }
                )
            }
        }
    }
}
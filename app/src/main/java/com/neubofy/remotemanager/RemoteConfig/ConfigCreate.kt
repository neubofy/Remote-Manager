package com.neubofy.remotemanager.RemoteConfig

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast
import com.neubofy.remotemanager.Activities.MainActivity
import com.neubofy.remotemanager.R
import com.neubofy.remotemanager.Rclone
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.*
import java.util.ArrayList

class ConfigCreate internal constructor(
    options: ArrayList<String>?,
    formView: View,
    authView: View,
    context: Context,
    rclone: Rclone
) {
    private val options: ArrayList<String> = ArrayList(options ?: arrayListOf())
    private val mFormView: View = formView
    private val mAuthView: View = authView
    private val mContext: Context = context
    private val mRclone: Rclone = rclone
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    fun execute(): ConfigCreate {
        mAuthView.visibility = View.VISIBLE
        mFormView.visibility = View.GONE

        job = scope.launch {
            val success = withContext(Dispatchers.IO) {
                OauthHelper.createOptionsWithOauth(options, mRclone, mContext)
            }

            if (!isActive) return@launch

            if (!success) {
                Toasty.error(
                    mContext,
                    mContext.getString(R.string.error_creating_remote),
                    Toast.LENGTH_SHORT,
                    true
                ).show()
            } else {
                Toasty.success(
                    mContext,
                    mContext.getString(R.string.remote_creation_success),
                    Toast.LENGTH_SHORT,
                    true
                ).show()
            }
            val intent = Intent(mContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            mContext.startActivity(intent)
        }
        return this
    }

    fun cancel(@Suppress("UNUSED_PARAMETER") mayInterruptIfRunning: Boolean = true) {
        job?.cancel()
        scope.cancel()
    }
}
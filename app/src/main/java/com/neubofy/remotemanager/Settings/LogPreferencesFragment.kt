package com.neubofy.remotemanager.Settings

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.neubofy.remotemanager.R
import com.neubofy.remotemanager.util.FLog
import de.felixnuesse.extract.extensions.tag
import de.felixnuesse.extract.settings.preferences.ButtonPreference
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.regex.Pattern


class LogPreferencesFragment : PreferenceFragmentCompat() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_logging_preferences, rootKey)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        requireActivity().title = getString(R.string.logging_settings_header)

        updateLogInfo()

        findPreference<Preference>("pref_key_share_log")?.setOnPreferenceClickListener {
            shareDiagnosticLog()
            true
        }

        findPreference<Preference>("pref_key_clear_log")?.setOnPreferenceClickListener {
            clearDiagnosticLog()
            true
        }

        findPreference<Preference>("pref_key_log_file_info")?.setOnPreferenceClickListener {
            openLogDirectory()
            true
        }

        val sigkill = findPreference<Preference>("TempKeySigquit") as? ButtonPreference
        sigkill?.setButtonText(getString(R.string.pref_send_sigquit_button))
        sigkill?.setButtonOnClick {
            sigquitAll()
        }
    }

    override fun onResume() {
        super.onResume()
        updateLogInfo()
    }

    private fun updateLogInfo() {
        val ctx = context ?: return
        val logFile = com.neubofy.remotemanager.Log2File.getDiagnosticLogFile(ctx)
        val sizeFormatted = android.text.format.Formatter.formatFileSize(ctx, logFile.length())
        val infoPref = findPreference<Preference>("pref_key_log_file_info")
        infoPref?.summary = "${logFile.absolutePath}\nSize: $sizeFormatted"
    }

    private fun shareDiagnosticLog() {
        val ctx = context ?: return
        val logFile = com.neubofy.remotemanager.Log2File.getDiagnosticLogFile(ctx)
        if (!logFile.exists() || logFile.length() == 0L) {
            Toast.makeText(ctx, "Diagnostic log is currently empty", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                ctx,
                com.neubofy.remotemanager.BuildConfig.APPLICATION_ID + ".fileprovider",
                logFile
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "Share Diagnostic Log"))
        } catch (e: Exception) {
            FLog.e(tag(), "Error sharing log", e)
            Toast.makeText(ctx, "Could not share log: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearDiagnosticLog() {
        val ctx = context ?: return
        if (com.neubofy.remotemanager.Log2File.clearLog(ctx)) {
            Toast.makeText(ctx, "Diagnostic log cleared", Toast.LENGTH_SHORT).show()
            updateLogInfo()
        }
    }

    private fun openLogDirectory() {
        val ctx = context ?: return
        val dir = com.neubofy.remotemanager.Log2File.getLogDirectory(ctx)
        try {
            val uri = android.net.Uri.parse(dir.absolutePath)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Log folder: ${dir.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }


    private fun sigquitAll() {
        Toast.makeText(context, "Round Sync: Stopping everything", Toast.LENGTH_LONG).show()
        try {
            val runtime = Runtime.getRuntime()
            val process = runtime.exec("ps")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val output = StringBuilder()
            while ((reader.readLine().also { line = it }) != null) {
                output.append('\n')
                output.append(line)
            }

            process.waitFor()

            val regex = "\\s+(\\d+)\\s+\\d+\\s+\\d+\\s+.+librclone.+$"
            val pattern = Pattern.compile(regex, Pattern.MULTILINE)
            val matcher = pattern.matcher(output.toString())

            while (matcher.find()) {
                for (i in 1..matcher.groupCount()) {
                    val pidMatch = matcher.group(i) ?: continue
                    val pid = pidMatch.toInt()
                    FLog.i(tag(), "SIGQUIT to process pid=%s", pid)
                    Process.sendSignal(pid, Process.SIGNAL_QUIT)
                }
            }
            Process.killProcess(Process.myPid())
        } catch (e: IOException) {
            FLog.e(tag(), "Error executing shell commands", e)
        } catch (e: InterruptedException) {
            FLog.e(tag(), "Error executing shell commands", e)
        }
    }
}
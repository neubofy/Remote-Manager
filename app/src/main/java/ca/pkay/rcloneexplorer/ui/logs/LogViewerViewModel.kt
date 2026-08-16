package ca.pkay.rcloneexplorer.ui.logs

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.BuildConfig
import ca.pkay.rcloneexplorer.Log2File
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.util.FLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LogViewerUiState(
    val logs: List<LogEntry> = emptyList(),
    val filteredLogs: List<LogEntry> = emptyList(),
    val filterLevel: LogLevel = LogLevel.ALL,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isLoggingEnabled: Boolean = false,
    val logFileSizeFormatted: String = "0 B",
    val autoClearLimitMb: Int = Log2File.DEFAULT_LOG_SIZE_LIMIT_MB,
    val autoScroll: Boolean = true,
    val infoMessage: String? = null,
    val isLoading: Boolean = false,
    val levelCounts: Map<LogLevel, Int> = emptyMap()
)

class LogViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "LogViewerViewModel"
    private val prefs = PreferenceManager.getDefaultSharedPreferences(application)
    private val _uiState = MutableStateFlow(LogViewerUiState())
    val uiState: StateFlow<LogViewerUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadLogs()
        startPeriodicLogSync()
    }

    private fun loadSettings() {
        val loggingEnabled = prefs.getBoolean(getApplication<Application>().getString(R.string.pref_key_logs), false)
        val limitMb = prefs.getInt(Log2File.PREF_KEY_LOG_SIZE_LIMIT_MB, Log2File.DEFAULT_LOG_SIZE_LIMIT_MB)
        _uiState.update {
            it.copy(
                isLoggingEnabled = loggingEnabled,
                autoClearLimitMb = limitMb
            )
        }
    }

    fun loadLogs() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val lines = withContext(Dispatchers.IO) {
                Log2File.readLogs(app, 3000)
            }
            val parsed = lines.map { LogEntry.parse(it) }
            val fileSize = withContext(Dispatchers.IO) {
                Log2File.getLogFileSize(app)
            }
            val formattedSize = Formatter.formatFileSize(app, fileSize)

            val counts = mutableMapOf<LogLevel, Int>()
            counts[LogLevel.ALL] = parsed.size
            for (entry in parsed) {
                counts[entry.level] = (counts[entry.level] ?: 0) + 1
            }

            _uiState.update { current ->
                val filtered = applyFilter(parsed, current.filterLevel, current.searchQuery)
                current.copy(
                    logs = parsed,
                    filteredLogs = filtered,
                    logFileSizeFormatted = formattedSize,
                    levelCounts = counts,
                    isLoading = false
                )
            }
        }
    }

    private fun startPeriodicLogSync() {
        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                if (_uiState.value.isLoggingEnabled) {
                    val app = getApplication<Application>()
                    val fileSize = withContext(Dispatchers.IO) {
                        Log2File.getLogFileSize(app)
                    }
                    val formattedSize = Formatter.formatFileSize(app, fileSize)
                    val lines = withContext(Dispatchers.IO) {
                        Log2File.readLogs(app, 3000)
                    }
                    if (lines.size != _uiState.value.logs.size) {
                        val parsed = lines.map { LogEntry.parse(it) }
                        val counts = mutableMapOf<LogLevel, Int>()
                        counts[LogLevel.ALL] = parsed.size
                        for (entry in parsed) {
                            counts[entry.level] = (counts[entry.level] ?: 0) + 1
                        }
                        _uiState.update { current ->
                            val filtered = applyFilter(parsed, current.filterLevel, current.searchQuery)
                            current.copy(
                                logs = parsed,
                                filteredLogs = filtered,
                                logFileSizeFormatted = formattedSize,
                                levelCounts = counts
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyFilter(logs: List<LogEntry>, level: LogLevel, query: String): List<LogEntry> {
        var result = logs
        if (level != LogLevel.ALL) {
            result = result.filter { it.level == level }
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter {
                it.message.lowercase().contains(q) ||
                        it.timestamp.lowercase().contains(q) ||
                        it.tag.lowercase().contains(q) ||
                        it.raw.lowercase().contains(q)
            }
        }
        return result
    }

    fun toggleLogging(enabled: Boolean) {
        prefs.edit().putBoolean(getApplication<Application>().getString(R.string.pref_key_logs), enabled).apply()
        _uiState.update { it.copy(isLoggingEnabled = enabled) }
        if (enabled) {
            Log2File(getApplication()).log("[SYSTEM] Diagnostic logging enabled by user")
            loadLogs()
        }
    }

    fun setFilterLevel(level: LogLevel) {
        _uiState.update { current ->
            val filtered = applyFilter(current.logs, level, current.searchQuery)
            current.copy(filterLevel = level, filteredLogs = filtered)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val filtered = applyFilter(current.logs, current.filterLevel, query)
            current.copy(searchQuery = query, filteredLogs = filtered)
        }
    }

    fun toggleSearch() {
        _uiState.update { current ->
            val nextState = !current.isSearching
            val newQuery = if (nextState) current.searchQuery else ""
            val filtered = applyFilter(current.logs, current.filterLevel, newQuery)
            current.copy(isSearching = nextState, searchQuery = newQuery, filteredLogs = filtered)
        }
    }

    fun toggleAutoScroll() {
        _uiState.update { it.copy(autoScroll = !it.autoScroll) }
    }

    fun setAutoClearLimitMb(mb: Int) {
        prefs.edit().putInt(Log2File.PREF_KEY_LOG_SIZE_LIMIT_MB, mb).apply()
        _uiState.update { it.copy(autoClearLimitMb = mb) }
        showInfoMessage("Auto-cleanup threshold set to $mb MB")
    }

    fun clearLogs() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val success = withContext(Dispatchers.IO) {
                Log2File.clearLog(app)
            }
            if (success) {
                _uiState.update {
                    it.copy(
                        logs = emptyList(),
                        filteredLogs = emptyList(),
                        logFileSizeFormatted = "0 B",
                        levelCounts = emptyMap(),
                        infoMessage = "Logs cleared successfully"
                    )
                }
            } else {
                showInfoMessage("Could not clear logs")
            }
        }
    }

    fun exportToDownloads(context: Context) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                Log2File.exportLogToDownloads(context)
            }
            if (file != null && file.exists()) {
                showInfoMessage("Exported to Downloads/Remote-Manager/logs/${file.name}")
            } else {
                showInfoMessage("No logs to export or export failed")
            }
        }
    }

    fun shareLogs(context: Context) {
        val logFile = Log2File.getDiagnosticLogFile(context)
        if (!logFile.exists() || logFile.length() == 0L) {
            showInfoMessage("Log is empty, nothing to share")
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context,
                BuildConfig.APPLICATION_ID + ".fileprovider",
                logFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Diagnostic Log").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            FLog.e(TAG, "Error sharing log file", e)
            showInfoMessage("Could not share log: ${e.message}")
        }
    }

    fun copyLogsToClipboard(context: Context, filteredOnly: Boolean = true) {
        val targetLogs = if (filteredOnly) _uiState.value.filteredLogs else _uiState.value.logs
        if (targetLogs.isEmpty()) {
            showInfoMessage("No logs to copy")
            return
        }
        val text = targetLogs.joinToString("\n") { it.raw }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Rclone Logs", text))
        showInfoMessage("Copied ${targetLogs.size} log lines to clipboard")
    }

    fun copyLogEntry(context: Context, entry: LogEntry) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Log Entry", entry.raw))
        showInfoMessage("Log line copied")
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    private fun showInfoMessage(msg: String) {
        _uiState.update { it.copy(infoMessage = msg) }
    }
}

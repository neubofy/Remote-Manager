package ca.pkay.rcloneexplorer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import ca.pkay.rcloneexplorer.util.FLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Log2File {

    private static final String TAG = "Log2File";
    public static final String PREF_KEY_LOG_SIZE_LIMIT_MB = "pref_key_log_size_limit_mb";
    public static final int DEFAULT_LOG_SIZE_LIMIT_MB = 10;
    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor();

    private final Context context;

    public Log2File(Context context) {
        this.context = context.getApplicationContext();
    }

    public static File getLogDirectory(Context context) {
        File dir = new File(context.getFilesDir(), "logs");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static File getDiagnosticLogFile(Context context) {
        File dir = getLogDirectory(context);
        return new File(dir, "rclone_diagnostic.log");
    }

    public static long getMaxLogSizeBytes(Context context) {
        int mb = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(PREF_KEY_LOG_SIZE_LIMIT_MB, DEFAULT_LOG_SIZE_LIMIT_MB);
        return ((long) Math.max(1, mb)) * 1024L * 1024L;
    }

    public static long getLogFileSize(Context context) {
        File file = getDiagnosticLogFile(context);
        return file.exists() ? file.length() : 0L;
    }

    public static boolean clearLog(Context context) {
        try {
            File file = getDiagnosticLogFile(context);
            if (file.exists()) {
                return file.delete();
            }
            return true;
        } catch (Exception e) {
            FLog.e(TAG, "Error clearing log file", e);
            return false;
        }
    }

    public static File exportLogToDownloads(Context context) {
        File source = getDiagnosticLogFile(context);
        if (!source.exists() || source.length() == 0L) {
            return null;
        }
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File exportDir = new File(downloadDir, "Remote-Manager/logs");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = sdf.format(new Date());
        File dest = new File(exportDir, "rclone_diagnostic_" + timestamp + ".log");

        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();
            return dest;
        } catch (IOException e) {
            FLog.e(TAG, "Failed exporting log file to Downloads", e);
            return null;
        }
    }

    public static List<String> readLogs(Context context, int maxLines) {
        List<String> lines = new ArrayList<>();
        File logFile = getDiagnosticLogFile(context);
        if (!logFile.exists() || logFile.length() == 0L) {
            return lines;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            FLog.e(TAG, "Error reading diagnostic log", e);
        }
        if (maxLines > 0 && lines.size() > maxLines) {
            return lines.subList(lines.size() - maxLines, lines.size());
        }
        return lines;
    }

    public void log(String message) {
        boolean loggingEnabled = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.pref_key_logs), false);
        if (!loggingEnabled) {
            return;
        }
        logExecutor.execute(() -> {
            try {
                File logFile = getDiagnosticLogFile(context);
                autoClearIfSizeLimitExceeded(logFile);

                @SuppressLint("SimpleDateFormat")
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                String currentDateTime = dateFormat.format(new Date());
                String logMessage = currentDateTime + " - " + message + "\n";

                try (FileOutputStream stream = new FileOutputStream(logFile, true)) {
                    stream.write(logMessage.getBytes());
                    stream.flush();
                }
            } catch (IOException e) {
                FLog.e(TAG, "Could not write log file", e);
            }
        });
    }

    private void autoClearIfSizeLimitExceeded(File logFile) {
        long limit = getMaxLogSizeBytes(context);
        if (logFile.exists() && logFile.length() > limit) {
            try {
                logFile.delete();
                logFile.createNewFile();
                @SuppressLint("SimpleDateFormat")
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                String resetHeader = dateFormat.format(new Date()) + " - [SYSTEM] Log automatically cleared: size limit reached (" + (limit / (1024 * 1024)) + " MB)\n";
                try (FileOutputStream stream = new FileOutputStream(logFile, false)) {
                    stream.write(resetHeader.getBytes());
                    stream.flush();
                }
            } catch (Exception e) {
                FLog.e(TAG, "Error auto-clearing log file", e);
            }
        }
    }
}

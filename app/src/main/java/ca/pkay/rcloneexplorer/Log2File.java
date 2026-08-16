package ca.pkay.rcloneexplorer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import ca.pkay.rcloneexplorer.util.FLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Log2File {

    private static final String TAG = "Log2File";
    private static final long MAX_LOG_SIZE_BYTES = 10 * 1024 * 1024L; // 10 MB
    private static final ExecutorService logExecutor = Executors.newSingleThreadExecutor();

    private final Context context;

    public Log2File(Context context) {
        this.context = context.getApplicationContext();
    }

    public static File getLogDirectory(Context context) {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File dir = new File(downloadDir, "Remote-Manager/logs");
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                File fallback = context.getExternalFilesDir("logs");
                return fallback != null ? fallback : new File(context.getFilesDir(), "logs");
            }
        }
        return dir;
    }

    public static File getDiagnosticLogFile(Context context) {
        File dir = getLogDirectory(context);
        return new File(dir, "rclone_diagnostic.log");
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

    public void log(String message) {
        logExecutor.execute(() -> {
            try {
                File logFile = getDiagnosticLogFile(context);
                rotateLogsIfTooLarge(logFile);

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

    private void rotateLogsIfTooLarge(File logFile) {
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
            File oldLog = new File(logFile.getParentFile(), "rclone_diagnostic_old.log");
            if (oldLog.exists()) {
                oldLog.delete();
            }
            logFile.renameTo(oldLog);
        }
    }
}

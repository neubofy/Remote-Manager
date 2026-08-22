package com.neubofy.remotemanager.RemoteConfig;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import com.neubofy.remotemanager.Items.RemoteItem;
import com.neubofy.remotemanager.R;
import com.neubofy.remotemanager.Rclone;
import es.dmoral.toasty.Toasty;
import io.github.x0b.safdav.SafAccessProvider;
import io.github.x0b.safdav.file.SafConstants;

import java.util.ArrayList;

public class RemoteConfigHelper {

    public static String getRemotePath(String path, RemoteItem selectedRemote) {
        String remotePath;
        if (selectedRemote.isRemoteType(RemoteItem.LOCAL)) {
            if (path.equals("//" + selectedRemote.getName())) {
                remotePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
            } else {
                remotePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + path;
            }
        } else {
            if (path.equals("//" + selectedRemote.getName())) {
                remotePath = selectedRemote.getName() + ":";
            } else {
                remotePath = selectedRemote.getName() + ":" + path;
            }
        }
        return remotePath;
    }

    public static boolean updateAndWait(Context context, ArrayList<String> options) {
        Rclone rclone = new Rclone(context);
        Process process = rclone.configUpdate(options);
        return rcloneRun(process, context, options);
    }

    public static boolean setupAndWait(Context context, ArrayList<String> options) {
        Rclone rclone = new Rclone(context);
        Process process = rclone.configCreate(options);
        return rcloneRun(process, context, options);
    }

    private static boolean rcloneRun(Process process, Context context, ArrayList<String> options) {
        if (null == process) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                Toasty.error(context, context.getString(R.string.error_creating_remote), Toast.LENGTH_SHORT, true).show()
            );
            return false;
        }

        StringBuilder errSb = new StringBuilder();
        Thread errThread = new Thread(() -> {
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    errSb.append(line).append("\n");
                }
            } catch (Exception ignored) {}
        });
        errThread.setDaemon(true);
        errThread.start();

        int exitCode;
        while (true) {
            try {
                exitCode = process.waitFor();
                break;
            } catch (InterruptedException e) {
                try {
                    exitCode = process.exitValue();
                    break;
                } catch (IllegalStateException ignored) {}
            }
        }
        try {
            errThread.join(1000);
        } catch (InterruptedException ignored) {}

        if (0 != exitCode) {
            String errDetail = errSb.toString().trim();
            if (!errDetail.isEmpty()) {
                com.neubofy.remotemanager.util.FLog.e("RemoteConfigHelper", "Rclone config error: " + errDetail);
            }
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                Toasty.error(context, context.getString(R.string.error_creating_remote), Toast.LENGTH_SHORT, true).show()
            );
            return false;
        } else {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                Toasty.success(context, context.getString(R.string.remote_creation_success), Toast.LENGTH_SHORT, true).show()
            );
            return true;
        }
    }

    public static void enableSaf(Context context) {
        String user = SafAccessProvider.getUser(context);
        String pass = SafAccessProvider.getPassword(context);
        ArrayList<String> options = new ArrayList<>();
        options.add(SafConstants.SAF_REMOTE_NAME);
        options.add("webdav");
        options.add("url");
        options.add(SafConstants.SAF_REMOTE_URL);
        options.add("user");
        options.add(user);
        options.add("pass");
        options.add(pass);
        setupAndWait(context, options);
    }
}

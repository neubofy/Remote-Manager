package ca.pkay.rcloneexplorer.Dialogs;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import ca.pkay.rcloneexplorer.InteractiveRunner;
import ca.pkay.rcloneexplorer.InteractiveRunner.ErrorHandler;
import ca.pkay.rcloneexplorer.InteractiveRunner.Step;
import ca.pkay.rcloneexplorer.InteractiveRunner.StringAction;
import ca.pkay.rcloneexplorer.Items.RemoteItem;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Rclone;
import ca.pkay.rcloneexplorer.RemoteConfig.OauthHelper;
import ca.pkay.rcloneexplorer.RemoteConfig.OauthHelper.InitOauthStep;
import ca.pkay.rcloneexplorer.RemoteConfig.OauthHelper.OauthFinishStep;
import ca.pkay.rcloneexplorer.util.FLog;
import es.dmoral.toasty.Toasty;

public class RemotePropertiesDialog extends DialogFragment {

    private static final String TAG = "RemotePropertiesDialog";
    private static final String ARG_REMOTE = "remote";
    private static final String ARG_IS_DARK_THEME = "dark_theme";

    private final String SAVED_REMOTE = "ca.pkay.rcexplorer.RemotePropertiesDialog.REMOTE";
    private final String SAVED_STORAGE_BYTES_USED = "ca.pkay.rcexplorer.RemotePropertiesDialog.STORAGE_BYTES_USED";
    private final String SAVED_STORAGE_BYTES_TOTAL = "ca.pkay.rcexplorer.RemotePropertiesDialog.STORAGE_BYTES_TOTAL";
    private final String SAVED_STORAGE_BYTES_FREE = "ca.pkay.rcexplorer.RemotePropertiesDialog.STORAGE_BYTES_FREE";
    private final String SAVED_STORAGE_BYTES_TRASHED = "ca.pkay.rcexplorer.RemotePropertiesDialog.STORAGE_BYTES_TRASHED";

    private RemoteItem remote;
    private long storageUsed;
    private long storageTotal;
    private long storageFree;
    private long storageTrashed;

    private View view;
    private Rclone rclone;
    private TextView remoteStorageStats;
    private Context context;

    public RemotePropertiesDialog() {}

    public static RemotePropertiesDialog newInstance(RemoteItem remoteItem, boolean isDarkTheme) {
        RemotePropertiesDialog dialog = new RemotePropertiesDialog();
        Bundle args = new Bundle();
        args.putParcelable(ARG_REMOTE, remoteItem);
        args.putBoolean(ARG_IS_DARK_THEME, isDarkTheme);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle arguments;
        if (null != (arguments = getArguments())) {
            remote = arguments.getParcelable(ARG_REMOTE);
        }

        if (savedInstanceState != null) {
            remote = savedInstanceState.getParcelable(SAVED_REMOTE);
            storageUsed = savedInstanceState.getLong(SAVED_STORAGE_BYTES_USED);
            storageTotal = savedInstanceState.getLong(SAVED_STORAGE_BYTES_TOTAL);
            storageFree = savedInstanceState.getLong(SAVED_STORAGE_BYTES_FREE);
            storageTrashed = savedInstanceState.getLong(SAVED_STORAGE_BYTES_TRASHED);
        }

        rclone = new Rclone(context);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context, R.style.RoundedCornersDialog);
        LayoutInflater inflater = ((FragmentActivity) context).getLayoutInflater();
        view = inflater.inflate(R.layout.dialog_remote_properties, null);

        ((TextView) view.findViewById(R.id.remote_name)).setText(remote.getDisplayName());

        View storageContainer = view.findViewById(R.id.remote_storage_container);
        remoteStorageStats = view.findViewById(R.id.remote_storage_stats);
        
        // Instant display from persistent cache
        ca.pkay.rcloneexplorer.Rclone.AboutResult cachedResult = ca.pkay.rcloneexplorer.data.RemoteTelemetryCacheRepository.INSTANCE.get(context, remote.getName());
        if (cachedResult != null && !cachedResult.hasFailed()) {
            storageUsed = cachedResult.getUsed();
            storageTotal = cachedResult.getTotal();
            storageFree = cachedResult.getFree();
            storageTrashed = cachedResult.getTrashed();
            showStorageMetrics();
        }

        updateStorageUsage();
        storageContainer.setOnClickListener(v -> updateStorageUsage());

        View authorizeContainer = view.findViewById(R.id.remote_authorization_container);
        if (authorizeContainer != null) {
            authorizeContainer.setVisibility(View.GONE);
        }

        builder.setView(view).setPositiveButton(R.string.ok, null);
        return builder.create();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SAVED_REMOTE, remote);
        outState.putLong(SAVED_STORAGE_BYTES_TOTAL, storageTotal);
        outState.putLong(SAVED_STORAGE_BYTES_USED, storageUsed);
        outState.putLong(SAVED_STORAGE_BYTES_FREE, storageFree);
        outState.putLong(SAVED_STORAGE_BYTES_TRASHED, storageTrashed);
    }

    public RemotePropertiesDialog setRemote(RemoteItem remote) {
        this.remote = remote;
        return this;
    }

    private void updateStorageUsage() {
        if (storageUsed <= 0 && storageTotal <= 0) {
            remoteStorageStats.setText(R.string.calculating);
        }
        new AboutRemoteTask(rclone, remote, result -> {
            if (result.hasFailed()) {
                if (storageUsed <= 0 && storageTotal <= 0) {
                    remoteStorageStats.setText(R.string.remote_properties_about_failed);
                }
                return;
            }
            storageUsed = result.getUsed();
            storageTotal = result.getTotal();
            storageFree = result.getFree();
            storageTrashed = result.getTrashed();
            if (context != null) {
                ca.pkay.rcloneexplorer.data.RemoteTelemetryCacheRepository.INSTANCE.put(context, remote.getName(), result);
            }
            showStorageMetrics();
        }).execute();
    }

    private void showStorageMetrics() {
        String used = Formatter.formatFileSize(context, storageUsed);
        String total = Formatter.formatFileSize(context, storageTotal);
        String free = Formatter.formatFileSize(context, storageFree);

        if (null != remoteStorageStats && isAdded()) {
            if (storageUsed < 0) {
                remoteStorageStats.setText(R.string.remote_properties_about_failed);
            } else if (storageTotal < 0 || storageFree < 0) {
                remoteStorageStats.setText(getString(R.string.remote_properties_storage_used, used));
            } else {
                remoteStorageStats.setText(getString(R.string.remote_properties_storage_stats, used, total, free));
            }
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
    }

    private interface AboutResultHandler {
        void onResult(Rclone.AboutResult result);
    }

    private static class AboutRemoteTask extends AsyncTask<Void, Void, Rclone.AboutResult> {

        private Rclone rclone;
        private RemoteItem remoteItem;
        private AboutResultHandler handler;

        public AboutRemoteTask(Rclone rclone, RemoteItem remoteItem, AboutResultHandler handler) {
            this.rclone = rclone;
            this.remoteItem = remoteItem;
            this.handler = handler;
        }

        @Override
        protected Rclone.AboutResult doInBackground(Void... params) {
            return rclone.aboutRemote(remoteItem);
        }

        @Override
        protected void onPostExecute(Rclone.AboutResult result) {
            super.onPostExecute(result);
            handler.onResult(result);
        }
    }
}

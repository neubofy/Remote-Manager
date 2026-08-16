package ca.pkay.rcloneexplorer.RemoteConfig;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsIntent;
import ca.pkay.rcloneexplorer.InteractiveRunner;
import ca.pkay.rcloneexplorer.Rclone;
import ca.pkay.rcloneexplorer.util.FLog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides utility methods for authorization of OAuth remotes
 */
public class OauthHelper {

    private static final String TAG = "OAuthHelper";
    private static final String regex = "(?:(?:go to the following link:|Please go to:?)\\s+)(https?://[^\\s'\"]+)|(http://127\\.0\\.0\\.1:53682/[^\\s'\"]+)|(http://localhost:53682/[^\\s'\"]+)";
    private static final OauthProcessToken oauthProcessToken = new OauthProcessToken();

    // Since OAuth always blocks port 53682, only a single authentication
    // attempt is allowed at a time.
    static class OauthProcessToken {

        private volatile WeakReference<UrlAuthThread> threadAuthThread;
        private volatile WeakReference<InteractiveRunner> runner;

        public synchronized boolean acquire(UrlAuthThread controlThread) {
            boolean oldAttemptStopped = forceRelease();
            this.threadAuthThread = new WeakReference<>(controlThread);
            return oldAttemptStopped;
        }

        public synchronized boolean acquire(InteractiveRunner runner) {
            boolean oldAttemptStopped = forceRelease();
            this.runner = new WeakReference<>(runner);
            return oldAttemptStopped;
        }

        public synchronized boolean forceRelease() {
            UrlAuthThread oldThread = threadAuthThread != null ? threadAuthThread.get() : null;
            InteractiveRunner oldRunner = runner != null ? runner.get() : null;
            boolean killed = false;

            if (oldThread != null) {
                if (!oldThread.isStopped()) {
                    FLog.d(TAG, "Removing old auth attempt");
                    oldThread.forceStop();
                    killed = true;
                }
            }

            if (oldRunner != null) {
                FLog.d(TAG, "Removing old re-auth attempt");
                oldRunner.forceStop();
                killed = true;
            }

            return killed;
        }
    }

    /**
     * Ensure that an OAuth attempt can be made.
     */
    public static void registerRunner(InteractiveRunner runner) {
        oauthProcessToken.forceRelease();
        oauthProcessToken.acquire(runner);
    }

    /**
     * Save the options in the rclone config file and start the OAuth authentication process
     * @param options a list of rclone options, starting with remote name and type
     * @param rclone the rclone to use
     * @param context a context to start
     * @return true if successful
     **/
    public static boolean createOptionsWithOauth(ArrayList<String> options, Rclone rclone, Context context) {
        // Since authorization uses a fixed port, shut down previous attempt.
        oauthProcessToken.forceRelease();

        Process process = rclone.configCreate(options);
        if (null == process) {
            return false;
        }
        UrlAuthThread currentAuth = new OauthHelper.UrlAuthThread(process, context);
        oauthProcessToken.acquire(currentAuth);
        currentAuth.start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            FLog.d(TAG, "Auth stopped by process interrupt");
            currentAuth.forceStop();
        }
        return 0 == process.exitValue();
    }

    /**
     * Monitor a rclone process for an authentication url and launch a browser
     * tab for the user. Note: this consumes the processes InputStream (stdout).
     */
    public static class UrlAuthThread extends Thread {
        private static final Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);

        private static final String TAG = "UrlAuthThread";
        private final Process process;
        private final Context context;
        private volatile boolean stopped = false;
        private volatile boolean urlOpened = false;

        public UrlAuthThread(Process process, Context context) {
            this.process = process;
            this.context = context;
        }

        public void run() {
            Thread stdoutThread = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        checkLineForUrl(line);
                    }
                } catch (Exception ignored) {}
            });
            stdoutThread.setDaemon(true);
            stdoutThread.start();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    checkLineForUrl(line);
                }
            } catch (IOException e) {
                if (stopped) {
                    FLog.v(TAG, "Authentication attempt stopped");
                    return;
                }
                stopped = true;
                FLog.e(TAG, "doInBackground: could not read auth url", e);
                process.destroy();
            }
        }

        private synchronized void checkLineForUrl(String line) {
            if (line == null || urlOpened) return;
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                String url = matcher.group(1);
                if (url == null || url.isEmpty()) {
                    url = matcher.group(2);
                }
                if (url == null || url.isEmpty()) {
                    url = matcher.group(3);
                }
                if (url != null && !url.isEmpty()) {
                    urlOpened = true;
                    FLog.i(TAG, "Launching OAuth authentication URL in browser: " + url);
                    launchBrowser(context, url);
                }
            }
        }

        public void forceStop() {
            stopped = true;
            process.destroy();
        }

        public boolean isStopped() {
            return stopped;
        }
    }

    static void launchBrowser(@NonNull Context context, @NonNull String url) {
        try {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            customTabsIntent.launchUrl(context, Uri.parse(url));
        } catch (Exception e) {
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e2) {
                FLog.e(TAG, "Could not launch browser", e2);
            }
        }
    }

    private static class OauthAction implements InteractiveRunner.Action {

        private static final Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        private Context context;

        public OauthAction(Context context) {
            this.context = context;
        }

        @Override
        public void onTrigger(String cliBuffer) {
            Matcher matcher = pattern.matcher(cliBuffer);
            if (matcher.find()) {
                String url = matcher.group(1);
                if (url == null || url.isEmpty()) {
                    url = matcher.group(2);
                }
                if (url == null || url.isEmpty()) {
                    url = matcher.group(3);
                }
                if (url != null && !url.isEmpty()) {
                    FLog.i(TAG, "onTrigger: launching browser for " + url);
                    launchBrowser(context, url);
                }
            } else {
                FLog.w(TAG, "onTrigger: could not extract auth URL from buffer: %s", cliBuffer);
            }
        }

        @Override
        public String getInput() {
            return "";
        }
    }

    public static class InitOauthStep extends InteractiveRunner.Step {
        private static final String TRIGGER = "127.0.0.1:53682";

        /**
         * An OAuth step that launches a browser. ATTENTION: must be registered
         * with {@link OauthHelper} to allow removal in case the port is needed.
         * @param context
         */
        public InitOauthStep(Context context) {
            super(TRIGGER, InteractiveRunner.Step.CONTAINS, InteractiveRunner.Step.INTERLEAVED, new OauthHelper.OauthAction(context));
        }
    }

    public static class OauthFinishStep extends InteractiveRunner.Step {

        private static final String TRIGGER = "Got code";

        public OauthFinishStep() {
            super(TRIGGER, InteractiveRunner.Step.CONTAINS, InteractiveRunner.Step.INTERLEAVED,
                    new InteractiveRunner.StringAction(""));
        }

        @Override
        public long getTimeout() {
            return 5 * 60 * 1000L;
        }
    }
}

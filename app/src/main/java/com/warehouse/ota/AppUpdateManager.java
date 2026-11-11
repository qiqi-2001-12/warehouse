package com.warehouse.ota;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Downloads and installs an APK supplied by the platform. */
public final class AppUpdateManager {
    private static final String TAG = "AppUpdateManager";
    private static final String PREFERENCES_NAME = "ota_update";
    private static final String KEY_STATUS = "status";
    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_TARGET_VERSION = "target_version";
    private static final String KEY_TARGET_VERSION_CODE = "target_version_code";
    private static final String KEY_ERROR = "error";
    private static final String KEY_UPDATED_AT = "updated_at";
    private static final String KEY_INSTALL_STARTED_AT = "install_started_at";
    private static final long STARTUP_CONFIRM_TIMEOUT_MS = 60_000L;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int BUFFER_SIZE = 32 * 1024;

    public static final String STATUS_IDLE = "IDLE";
    public static final String STATUS_DOWNLOADING = "DOWNLOADING";
    public static final String STATUS_VERIFYING = "VERIFYING";
    public static final String STATUS_INSTALLING = "INSTALLING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private static volatile AppUpdateManager instance;

    private final Context appContext;
    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object taskLock = new Object();
    private boolean taskRunning;

    private AppUpdateManager(Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        recoverInterruptedTask();
    }

    public static AppUpdateManager get(Context context) {
        AppUpdateManager result = instance;
        if (result == null) {
            synchronized (AppUpdateManager.class) {
                result = instance;
                if (result == null) {
                    result = new AppUpdateManager(context);
                    instance = result;
                }
            }
        }
        return result;
    }

    /**
     * Starts an update asynchronously. A successful callback means the update was accepted;
     * download and installation progress is persisted and exposed through telemetry().
     */
    public void startUpdate(JSONObject payload, String deviceKey, Callback callback) {
        UpdateRequest request;
        try {
            request = UpdateRequest.from(payload);
            validateRequest(request);
        } catch (Exception error) {
            finishOnMain(callback, false, error.getMessage());
            return;
        }

        synchronized (taskLock) {
            if (taskRunning) {
                finishOnMain(callback, false, "another app update is already running");
                return;
            }
            taskRunning = true;
        }
        setState(STATUS_DOWNLOADING, 0, request.versionName, request.versionCode, "");
        finishOnMain(callback, true, null);
        executor.execute(() -> performUpdate(request, deviceKey));
    }

    /** Returns the update state that should be included in the next platform heartbeat. */
    public synchronized JSONObject telemetry() {
        JSONObject result = new JSONObject();
        try {
            PackageInfo packageInfo = packageInfo();
            result.put("versionName", packageInfo.versionName == null ? "" : packageInfo.versionName);
            result.put("versionCode", versionCode(packageInfo));
            result.put("updateStatus", preferences.getString(KEY_STATUS, STATUS_IDLE));
            result.put("updateProgress", preferences.getInt(KEY_PROGRESS, 0));
            String targetVersion = preferences.getString(KEY_TARGET_VERSION, "");
            if (!targetVersion.isEmpty()) {
                result.put("updateTargetVersion", targetVersion);
            }
            long targetVersionCode = preferences.getLong(KEY_TARGET_VERSION_CODE, -1L);
            if (targetVersionCode >= 0) {
                result.put("updateTargetVersionCode", targetVersionCode);
            }
            String error = preferences.getString(KEY_ERROR, "");
            if (!error.isEmpty()) {
                result.put("updateError", error);
            }
            result.put("updateStateChangedAt", preferences.getLong(KEY_UPDATED_AT, 0L));
        } catch (Exception error) {
            Log.w(TAG, "build update telemetry failed", error);
        }
        return result;
    }

    public interface Callback {
        void onFinished(boolean success, String errorMessage);
    }

    /** Marks the newly installed APK healthy after the app has started normally. */
    public void markStartupHealthy() {
        String status = preferences.getString(KEY_STATUS, STATUS_IDLE);
        if (!STATUS_INSTALLING.equals(status)) {
            return;
        }
        try {
            long currentVersion = versionCode(packageInfo());
            long targetVersion = preferences.getLong(KEY_TARGET_VERSION_CODE, -1L);
            if (targetVersion >= 0 && currentVersion >= targetVersion) {
                deleteQuietly(new File(appContext.getFilesDir(), "ota/previous.apk"));
                deleteQuietly(new File(appContext.getFilesDir(), "ota/update.apk"));
                setState(STATUS_SUCCESS, 100, preferences.getString(KEY_TARGET_VERSION, ""),
                        targetVersion, "");
            }
        } catch (Exception error) {
            Log.w(TAG, "cannot mark app update healthy", error);
        }
    }

    private void performUpdate(UpdateRequest request, String deviceKey) {
        File otaDirectory = new File(appContext.getFilesDir(), "ota");
        File partialFile = new File(otaDirectory, "update.apk.part");
        File updateFile = new File(otaDirectory, "update.apk");
        File backupFile = new File(otaDirectory, "previous.apk");
        try {
            if (!otaDirectory.exists() && !otaDirectory.mkdirs()) {
                throw new IOException("cannot create OTA directory");
            }
            deleteQuietly(partialFile);
            download(request, deviceKey, partialFile);
            verifyPackage(request, partialFile);
            setState(STATUS_VERIFYING, 100, request.versionName, request.versionCode, "");
            if (!partialFile.renameTo(updateFile)) {
                copyFile(partialFile, updateFile);
                deleteQuietly(partialFile);
            }
            backupCurrentApk(backupFile);
            setState(STATUS_INSTALLING, 100, request.versionName, request.versionCode, "");
            installWithRoot(updateFile, request.force);
            // Keep both files until the newly installed process confirms a healthy startup.
            // MainActivity calls markStartupHealthy() after the next launch; if it never
            // launches, recoverInterruptedTask() restores the previous APK on a later start.
        } catch (Exception error) {
            String message = messageOf(error);
            boolean rolledBack = restoreBackup(backupFile);
            if (rolledBack) {
                message += "; previous version restored";
            }
            setState(STATUS_FAILED, 0, request.versionName, request.versionCode, message);
            deleteQuietly(partialFile);
            deleteQuietly(updateFile);
            deleteQuietly(backupFile);
            Log.e(TAG, "app update failed", error);
        } finally {
            synchronized (taskLock) {
                taskRunning = false;
            }
        }
    }

    private void download(UpdateRequest request, String deviceKey, File destination) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(request.downloadUrl).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream");
        if (deviceKey != null && !deviceKey.trim().isEmpty()) {
            connection.setRequestProperty("X-Device-Key", deviceKey);
        }
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("update download returned HTTP " + status);
            }
            long contentLength = connection.getContentLengthLong();
            if (request.packageSize > 0 && contentLength >= 0 && contentLength != request.packageSize) {
                throw new IOException("update package size does not match metadata");
            }
            long total = contentLength > 0 ? contentLength : request.packageSize;
            long received = 0L;
            try (InputStream input = connection.getInputStream();
                 OutputStream output = new FileOutputStream(destination, false)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    received += count;
                    int progress = total > 0 ? (int) Math.min(99L, received * 100L / total) : 0;
                    setState(STATUS_DOWNLOADING, progress, request.versionName, request.versionCode, "");
                }
            }
            if (received <= 0L) {
                throw new IOException("downloaded update package is empty");
            }
            if (request.packageSize > 0 && received != request.packageSize) {
                throw new IOException("downloaded update size does not match metadata");
            }
        } finally {
            connection.disconnect();
        }
    }

    private void verifyPackage(UpdateRequest request, File file) throws IOException {
        if (!file.isFile() || file.length() <= 0L) {
            throw new IOException("downloaded APK is missing");
        }
        try {
            PackageInfo archiveInfo = appContext.getPackageManager()
                    .getPackageArchiveInfo(file.getAbsolutePath(), 0);
            if (archiveInfo == null || !appContext.getPackageName().equals(archiveInfo.packageName)) {
                throw new IOException("downloaded file is not a warehouse APK");
            }
            long archiveVersionCode = versionCode(archiveInfo);
            if (request.versionCode >= 0 && archiveVersionCode != request.versionCode) {
                throw new IOException("APK version does not match update metadata");
            }
            String archiveVersionName = archiveInfo.versionName == null ? "" : archiveInfo.versionName;
            if (!request.versionName.isEmpty() && !request.versionName.equals(archiveVersionName)) {
                throw new IOException("APK version name does not match update metadata");
            }
            request.versionCode = archiveVersionCode;
            request.versionName = archiveVersionName;
            long currentVersionCode = versionCode(packageInfo());
            if (!request.force && archiveVersionCode <= currentVersionCode) {
                throw new IOException("target version is not newer than current version");
            }
        } catch (PackageManager.NameNotFoundException error) {
            throw new IOException("current app package information is unavailable", error);
        } catch (SecurityException error) {
            throw new IOException("cannot inspect downloaded APK", error);
        }
    }

    private void backupCurrentApk(File destination) throws IOException {
        String sourcePath = appContext.getApplicationInfo().sourceDir;
        if (sourcePath == null || sourcePath.isEmpty()) {
            throw new IOException("current APK path is unavailable");
        }
        copyFile(new File(sourcePath), destination);
    }

    private void installWithRoot(File apkFile, boolean allowDowngrade) throws IOException {
        StringBuilder command = new StringBuilder("pm install -r");
        if (allowDowngrade) {
            command.append(" -d");
        }
        command.append(" ").append(shellQuote(apkFile.getAbsolutePath()));
        // The device firmware does not reliably deliver MY_PACKAGE_REPLACED to
        // this app. Keep the root shell alive through the install and start the
        // new activity after PackageManager has finished replacing the package.
        // The shell runs as root, so it survives the force-stop of this app.
        command.append(" && sleep 2 && am start -a android.intent.action.MAIN")
                .append(" -c android.intent.category.LAUNCHER -n ")
                .append(shellQuote(appContext.getPackageName() + "/.app.MainActivity"));
        RootResult result = runRootCommand(command.toString(), 120L);
        if (result.exitCode != 0 || !result.output.toLowerCase(Locale.US).contains("success")) {
            throw new IOException("APK installation failed: " + result.output.trim());
        }
    }

    private boolean restoreBackup(File backupFile) {
        if (!backupFile.isFile()) {
            return false;
        }
        try {
            RootResult result = runRootCommand(
                    "pm install -r " + shellQuote(backupFile.getAbsolutePath()), 120L);
            return result.exitCode == 0 && result.output.toLowerCase(Locale.US).contains("success");
        } catch (Exception error) {
            Log.e(TAG, "rollback failed", error);
            return false;
        }
    }

    private RootResult runRootCommand(String command, long timeoutSeconds) throws IOException {
        Process process;
        try {
            // This device's su follows Android's UID-first syntax; `su -c` is
            // parsed as an invalid UID and makes every installation fail.
            process = new ProcessBuilder("su", "0", "sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException error) {
            throw new IOException("root installer is unavailable", error);
        }
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("APK installation timed out");
            }
            return new RootResult(process.exitValue(), readFully(process.getInputStream()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("APK installation interrupted", error);
        } finally {
            process.destroy();
        }
    }

    private void recoverInterruptedTask() {
        String status = preferences.getString(KEY_STATUS, STATUS_IDLE);
        if (!STATUS_DOWNLOADING.equals(status) && !STATUS_VERIFYING.equals(status)
                && !STATUS_INSTALLING.equals(status)) {
            return;
        }
        long targetVersionCode = preferences.getLong(KEY_TARGET_VERSION_CODE, -1L);
        long currentVersionCode;
        try {
            currentVersionCode = versionCode(packageInfo());
        } catch (Exception error) {
            currentVersionCode = -1L;
        }
        if (STATUS_INSTALLING.equals(status) && targetVersionCode >= 0
                && currentVersionCode >= targetVersionCode) {
            long startedAt = preferences.getLong(KEY_INSTALL_STARTED_AT, 0L);
            if (startedAt > 0L && System.currentTimeMillis() - startedAt <= STARTUP_CONFIRM_TIMEOUT_MS) {
                return;
            }
            boolean rolledBack = restoreBackup(new File(appContext.getFilesDir(), "ota/previous.apk"));
            setState(STATUS_FAILED, 0, preferences.getString(KEY_TARGET_VERSION, ""),
                    targetVersionCode, rolledBack ? "new version did not start; previous version restored"
                            : "new version did not start; rollback unavailable");
        } else {
            setState(STATUS_FAILED, 0, preferences.getString(KEY_TARGET_VERSION, ""),
                    targetVersionCode, "update process interrupted; current version retained");
        }
    }

    private void validateRequest(UpdateRequest request) throws IOException {
        if (!request.downloadUrl.startsWith("https://") && !request.downloadUrl.startsWith("http://")) {
            throw new IOException("update URL must use HTTP or HTTPS");
        }
        if (request.packageSize == 0L || request.packageSize < -1L) {
            throw new IOException("update package size is invalid");
        }
    }

    private PackageInfo packageInfo() throws PackageManager.NameNotFoundException {
        return appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
    }

    @SuppressWarnings("deprecation")
    private static long versionCode(PackageInfo info) {
        return android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private synchronized void setState(String status, int progress, String targetVersion,
                                        long targetVersionCode, String error) {
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_STATUS, status)
                .putInt(KEY_PROGRESS, Math.max(0, Math.min(100, progress)))
                .putString(KEY_TARGET_VERSION, targetVersion == null ? "" : targetVersion)
                .putLong(KEY_TARGET_VERSION_CODE, targetVersionCode)
                .putString(KEY_ERROR, error == null ? "" : error)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis());
        if (STATUS_INSTALLING.equals(status)) {
            editor.putLong(KEY_INSTALL_STARTED_AT, System.currentTimeMillis());
        } else if (STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status)) {
            editor.remove(KEY_INSTALL_STARTED_AT);
        }
        editor.apply();
    }

    private void finishOnMain(Callback callback, boolean success, String error) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onFinished(success, error));
    }

    private static void copyFile(File source, File destination) throws IOException {
        if (!source.isFile()) {
            throw new IOException("file does not exist: " + source.getName());
        }
        File parent = destination.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create file directory");
        }
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
    }

    private static String readFully(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toString("UTF-8");
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String messageOf(Exception error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    private static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "cannot delete " + file.getAbsolutePath());
        }
    }

    private static final class RootResult {
        final int exitCode;
        final String output;

        RootResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }
    }

    private static final class UpdateRequest {
        final String downloadUrl;
        String versionName;
        long versionCode;
        final long packageSize;
        final boolean force;

        private UpdateRequest(String downloadUrl, String versionName, long versionCode,
                              long packageSize, boolean force) {
            this.downloadUrl = downloadUrl;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.packageSize = packageSize;
            this.force = force;
        }

        static UpdateRequest from(JSONObject payload) throws IOException {
            if (payload == null) {
                throw new IOException("update payload is missing");
            }
            String url = payload.optString("downloadUrl", payload.optString("url", "")).trim();
            String name = payload.optString("versionName", "").trim();
            long code = payload.optLong("versionCode", -1L);
            long size = payload.optLong("packageSize", payload.optLong("size", -1L));
            boolean force = payload.optBoolean("force", false);
            return new UpdateRequest(url, name, code, size, force);
        }
    }
}

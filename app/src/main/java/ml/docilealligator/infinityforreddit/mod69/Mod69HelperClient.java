package ml.docilealligator.infinityforreddit.mod69;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sends a downloaded file to the "69 Media Helper" app, which converts it (quality, GIF to MP4,
 * cover pictures) with its own shared settings. Must be called from a background thread.
 */
public final class Mod69HelperClient {
    private static final String TAG = "Mod69Helper";
    private static final long CONNECT_TIMEOUT_MS = 10_000;
    private static final long PROCESS_TIMEOUT_MS = 20 * 60_000;

    /** Settings read from the helper, refreshed at most every 30 seconds. */
    public static final class HelperSettings {
        public boolean lowQuality;
        public int lowVideoHeight = 480;
        public boolean gifToMp4;
    }

    @Nullable private static HelperSettings cachedSettings;
    private static long cachedAt;

    private Mod69HelperClient() {}

    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(HelperContract.PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Opens the helper's settings screen (the shared media settings). */
    public static boolean openSettings(Context context) {
        try {
            Intent i = new Intent(HelperContract.ACTION_OPEN_SETTINGS).setPackage(HelperContract.PACKAGE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Uri shareUri(Context context, File f) {
        return FileProvider.getUriForFile(context, context.getPackageName() + ".provider", f);
    }

    /** Connects, sends one message, waits for the answer. Returns the answer's data, or null. */
    @Nullable
    private static Bundle call(Context context, Message request, int expectedReply, long timeoutMs) {
        if (!isInstalled(context)) return null;
        Context app = context.getApplicationContext();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<Messenger> service = new AtomicReference<>();
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                service.set(new Messenger(binder));
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                service.set(null);
            }
        };
        HandlerThread replyThread = new HandlerThread("mod69-helper-reply");
        replyThread.start();
        try {
            Intent intent = new Intent(HelperContract.ACTION_PROCESS).setPackage(HelperContract.PACKAGE);
            boolean bound = app.bindService(intent, conn, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT);
            if (!bound || !connected.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS) || service.get() == null) {
                Log.w(TAG, "helper not reachable (is it signed with the same key?)");
                return null;
            }
            CountDownLatch answered = new CountDownLatch(1);
            AtomicReference<Bundle> answer = new AtomicReference<>();
            request.replyTo = new Messenger(new Handler(replyThread.getLooper(), m -> {
                if (m.what == expectedReply) {
                    answer.set(m.getData());
                    answered.countDown();
                }
                return true;
            }));
            service.get().send(request);
            long deadline = SystemClock.elapsedRealtime() + timeoutMs;
            while (!answered.await(1, TimeUnit.SECONDS)) {
                if (service.get() == null || SystemClock.elapsedRealtime() > deadline) {
                    Log.w(TAG, "helper did not answer in time");
                    return null;
                }
            }
            return answer.get();
        } catch (Exception e) {
            Log.w(TAG, "helper call failed", e);
            return null;
        } finally {
            try {
                app.unbindService(conn);
            } catch (Exception ignored) {
            }
            replyThread.quitSafely();
        }
    }

    /** The helper's current quality settings (cached), or null if the helper is not available. */
    @Nullable
    public static synchronized HelperSettings settings(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (cachedSettings != null && now - cachedAt < 30_000) return cachedSettings;
        Bundle b = call(context, Message.obtain(null, HelperContract.MSG_PING), HelperContract.MSG_PONG, CONNECT_TIMEOUT_MS);
        if (b == null) return null;
        HelperSettings s = new HelperSettings();
        s.lowQuality = b.getBoolean(HelperContract.KEY_LOW_QUALITY, false);
        s.lowVideoHeight = b.getInt(HelperContract.KEY_LOW_VIDEO_HEIGHT, 480);
        s.gifToMp4 = b.getBoolean(HelperContract.KEY_GIF_TO_MP4, false);
        cachedSettings = s;
        cachedAt = now;
        return s;
    }

    /**
     * Lets the helper convert [input]. On success the returned result points at the new file
     * (or still at [input] when nothing needed changing); null means "keep the download as it is".
     */
    @Nullable
    public static MediaProcessor.Result process(Context context, File input, int declaredType, @Nullable String declaredExt) {
        if (!isInstalled(context)) return null;
        File output = MediaProcessor.newTempFile(context, "out");
        Uri inUri;
        Uri outUri;
        try {
            //noinspection ResultOfMethodCallIgnored
            output.createNewFile();
            inUri = shareUri(context, input);
            outUri = shareUri(context, output);
        } catch (Exception e) {
            Log.w(TAG, "cannot share files with the helper", e);
            //noinspection ResultOfMethodCallIgnored
            output.delete();
            return null;
        }
        context.grantUriPermission(HelperContract.PACKAGE, inUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.grantUriPermission(HelperContract.PACKAGE, outUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            Message m = Message.obtain(null, HelperContract.MSG_PROCESS);
            Bundle data = new Bundle();
            data.putParcelable(HelperContract.KEY_IN, inUri);
            data.putParcelable(HelperContract.KEY_OUT, outUri);
            data.putInt(HelperContract.KEY_TYPE, declaredType);
            data.putString(HelperContract.KEY_EXT, declaredExt);
            m.setData(data);
            Bundle answer = call(context, m, HelperContract.MSG_RESULT, PROCESS_TIMEOUT_MS);
            if (answer == null || !answer.getBoolean(HelperContract.KEY_OK, false)) {
                //noinspection ResultOfMethodCallIgnored
                output.delete();
                return null;
            }
            MediaProcessor.Result r = new MediaProcessor.Result();
            boolean changed = answer.getBoolean(HelperContract.KEY_CHANGED, false) && output.length() > 0;
            if (changed) {
                //noinspection ResultOfMethodCallIgnored
                input.delete();
                r.file = output;
            } else {
                //noinspection ResultOfMethodCallIgnored
                output.delete();
                r.file = input;
            }
            r.mediaType = answer.getInt(HelperContract.KEY_TYPE, declaredType);
            r.extension = answer.getString(HelperContract.KEY_EXT, declaredExt != null ? declaredExt : "jpg");
            r.mimeType = answer.getString(HelperContract.KEY_MIME, MediaProcessor.mimeFor(r.extension));
            return r;
        } finally {
            context.revokeUriPermission(inUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.revokeUriPermission(outUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
    }
}

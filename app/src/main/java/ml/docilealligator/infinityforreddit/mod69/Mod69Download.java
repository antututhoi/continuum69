package ml.docilealligator.infinityforreddit.mod69;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import ml.docilealligator.infinityforreddit.apis.DownloadFile;
import okhttp3.ResponseBody;
import retrofit2.Response;

/** Download helpers: original-file URLs and retries on shaky connections. */
public final class Mod69Download {
    private static final String TAG = "Mod69Download";

    private Mod69Download() {}

    /** preview.redd.it/abc.jpg?width=640&... -> i.redd.it/abc.jpg (the full-size original). */
    public static String toOriginal(String url) {
        if (url == null) return null;
        String u = url.replace("&amp;", "&");
        try {
            Uri uri = Uri.parse(u);
            String host = uri.getHost();
            if ("preview.redd.it".equals(host)) {
                String path = uri.getPath();
                if (path != null && !path.isEmpty()) return "https://i.redd.it" + path;
            }
        } catch (Exception ignored) {
        }
        return u;
    }

    /** True for Reddit's resized copies (not the original). */
    public static boolean isPreview(@Nullable String url) {
        if (url == null) return false;
        try {
            String host = Uri.parse(url).getHost();
            return "preview.redd.it".equals(host) || "external-preview.redd.it".equals(host);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Downloads [url] into [out], retrying up to [attempts] times with a growing pause.
     * Client errors (404 and similar) are not retried.
     */
    public static boolean downloadTo(DownloadFile api, String url, File out, int attempts) {
        for (int i = 0; i < attempts; i++) {
            if (i > 0) {
                try {
                    Thread.sleep(1500L * i);
                } catch (InterruptedException e) {
                    return false;
                }
            }
            try {
                Response<ResponseBody> response = api.downloadFile(url).execute();
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful() || body == null) {
                        int code = response.code();
                        if (code >= 400 && code < 500 && code != 408 && code != 429) return false;
                        continue;
                    }
                    long expected = body.contentLength();
                    long written = 0;
                    try (InputStream in = body.byteStream(); OutputStream os = new FileOutputStream(out)) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            os.write(buf, 0, n);
                            written += n;
                        }
                    }
                    if (written > 0 && (expected <= 0 || written == expected)) return true;
                    Log.w(TAG, "incomplete download " + written + "/" + expected + " " + url);
                }
            } catch (IOException e) {
                Log.w(TAG, "attempt " + (i + 1) + " failed for " + url, e);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        out.delete();
        return false;
    }

    /** "name.gif" + "mp4" -> "name.mp4" */
    public static String withExtension(String fileName, String extension) {
        if (fileName == null) return "file." + extension;
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base + "." + extension;
    }
}

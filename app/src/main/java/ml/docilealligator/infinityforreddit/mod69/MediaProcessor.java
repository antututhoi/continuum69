package ml.docilealligator.infinityforreddit.mod69;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Prepares a downloaded file before it is saved. The conversions (quality, GIF to MP4, cover pictures)
 * are done by the separate "69 Media Helper" app; this class does the format check and the
 * notification thumbnail.
 */
public final class MediaProcessor {
    private static final String TAG = "Mod69Media";

    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_GIF = 1;
    public static final int TYPE_VIDEO = 2;

    public static final class Result {
        public File file;
        public String extension;
        public String mimeType;
        public int mediaType;
        @Nullable public Bitmap thumbnail;
    }

    private MediaProcessor() {}

    public static File workDir(Context context) {
        File dir = new File(context.getCacheDir(), "mod69");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    public static File newTempFile(Context context, String extension) {
        return new File(workDir(context), UUID.randomUUID().toString() + "." + extension);
    }

    /** Reads the first bytes of the file to find what it really is. */
    @Nullable
    public static String sniff(File file) {
        byte[] h = new byte[16];
        int n;
        try (InputStream in = new FileInputStream(file)) {
            n = in.read(h);
        } catch (IOException e) {
            return null;
        }
        if (n < 12) return null;
        if (h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') return "gif";
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpg";
        if ((h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
        if (h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            String brand = new String(h, 8, 4);
            if (brand.startsWith("avif") || brand.startsWith("avis")) return "avif";
            if (brand.startsWith("heic") || brand.startsWith("mif1") || brand.startsWith("heix")) return "heic";
            return "mp4";
        }
        if ((h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45 && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3) return "webm";
        return null;
    }

    public static String mimeFor(String ext) {
        switch (ext) {
            case "gif": return "image/gif";
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "webp": return "image/webp";
            case "avif": return "image/avif";
            case "heic": return "image/heic";
            case "webm": return "video/webm";
            case "mkv": return "video/x-matroska";
            default: return "video/mp4";
        }
    }

    private static int typeFor(String ext) {
        switch (ext) {
            case "gif": return TYPE_GIF;
            case "mp4": case "webm": case "mkv": return TYPE_VIDEO;
            default: return TYPE_IMAGE;
        }
    }

    /**
     * Sends the file to the 69 Media Helper app for conversion (its shared settings decide what happens).
     * Without the helper only the real-format check is done here.
     *
     * @param input           the downloaded temp file
     * @param declaredType    what the post said the media is
     * @param declaredExt     the extension from the original file name (may be null)
     */
    public static Result process(Context context, SharedPreferences prefs, File input,
                                 int declaredType, @Nullable String declaredExt) {
        Result r = Mod69HelperClient.process(context, input, declaredType, declaredExt);
        if (r == null) {
            r = new Result();
            String real = sniff(input);
            if (real == null) {
                real = declaredExt != null ? declaredExt.toLowerCase() : (declaredType == TYPE_VIDEO ? "mp4" : declaredType == TYPE_GIF ? "gif" : "jpg");
                if (real.equals("jpeg")) real = "jpg";
            }
            r.file = input;
            r.extension = real;
            r.mediaType = typeFor(real);
            r.mimeType = mimeFor(real);
        }
        if (prefs.getBoolean(Mod69Prefs.NOTIFICATION_THUMBNAIL, true)) {
            r.thumbnail = makeThumbnail(r.file, r.mediaType);
        }
        return r;
    }

    private static long durationMs(File f) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(f.getAbsolutePath());
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return d == null ? -1 : Long.parseLong(d);
        } catch (Exception e) {
            return -1;
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
    }

    @Nullable
    private static Bitmap frameAt(File video, double fraction) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(video.getAbsolutePath());
            long d = durationMs(video);
            long us = d > 0 ? (long) (d * fraction * 1000) : 0;
            Bitmap b = mmr.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (b == null) b = mmr.getFrameAtTime(0);
            return b;
        } catch (Exception e) {
            return null;
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
    }

    // ---------- notification thumbnail ----------

    @Nullable
    public static Bitmap makeThumbnail(File f, int mediaType) {
        try {
            Bitmap b;
            if (mediaType == TYPE_VIDEO) {
                b = frameAt(f, 0.25);
            } else {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
                int sample = 1;
                while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 256) sample *= 2;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                b = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            }
            if (b == null) return null;
            int max = 768;
            if (b.getWidth() > max) {
                Bitmap s = Bitmap.createScaledBitmap(b, max, Math.max(1, b.getHeight() * max / b.getWidth()), true);
                if (s != b) b.recycle();
                b = s;
            }
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Removes leftover temp files older than a day. */
    public static void cleanOld(Context context) {
        File[] files = workDir(context).listFiles();
        if (files == null) return;
        long cutoff = System.currentTimeMillis() - 24L * 3600 * 1000;
        for (File f : files) {
            if (f.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }
}

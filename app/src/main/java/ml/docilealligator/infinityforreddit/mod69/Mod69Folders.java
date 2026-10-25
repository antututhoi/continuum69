package ml.docilealligator.infinityforreddit.mod69;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 69 mod: keeps subreddit folders small. When a subreddit folder holds the set number of items
 * (default 1000), it is renamed to "name_yymmdd" and a fresh "name" folder is started.
 * The item count is cached, so the folder is only counted once a day instead of on every download.
 */
public final class Mod69Folders {
    private static final String TAG = "Mod69Folders";
    private static final String COUNTS = "mod69_folder_counts";
    private static final long RECOUNT_MS = 24L * 3600 * 1000;

    private Mod69Folders() {}

    public static int limit(Context c) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(c);
        try {
            String v = p.getString(Mod69Prefs.SPLIT_FOLDER_AT, "1000");
            return v == null ? 1000 : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return 1000;
        }
    }

    private static SharedPreferences counts(Context c) {
        return c.getApplicationContext().getSharedPreferences(COUNTS, Context.MODE_PRIVATE);
    }

    /**
     * @return the folder to keep using, or null when it was full and renamed (the caller then makes a new one).
     */
    @Nullable
    public static synchronized Uri splitIfFull(Context c, Uri treeUri, Uri folder, String name) {
        int limit = limit(c);
        if (limit <= 0) return folder;
        String key = folder.toString();
        SharedPreferences p = counts(c);
        int count = p.getInt(key, -1);
        long at = p.getLong(key + "#t", 0);
        if (count < 0 || System.currentTimeMillis() - at > RECOUNT_MS) {
            count = countChildren(c, treeUri, folder);
            p.edit().putInt(key, count).putLong(key + "#t", System.currentTimeMillis()).apply();
        }
        if (count < limit) return folder;
        String stamp = new SimpleDateFormat("yyMMdd", Locale.US).format(new Date());
        for (int i = 1; i <= 9; i++) {
            String newName = name + "_" + stamp + (i == 1 ? "" : "_" + i);
            try {
                Uri renamed = DocumentsContract.renameDocument(c.getContentResolver(), folder, newName);
                if (renamed != null) {
                    p.edit().remove(key).remove(key + "#t").apply();
                    announce(c, name, count, newName);
                    Log.i(TAG, "split " + name + " -> " + newName + " at " + count + " items");
                    return null;
                }
            } catch (Exception e) {
                Log.w(TAG, "rename to " + newName + " failed", e);
            }
        }
        return folder; // could not rename: keep using it
    }

    /** One more item saved directly in this folder. */
    public static synchronized void noteSaved(Context c, @Nullable Uri folder) {
        if (folder == null) return;
        SharedPreferences p = counts(c);
        String key = folder.toString();
        int count = p.getInt(key, -1);
        if (count >= 0) p.edit().putInt(key, count + 1).apply();
    }

    /** Counts only pictures and videos (not sub-folders or other files). */
    private static int countChildren(Context c, Uri treeUri, Uri folder) {
        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getDocumentId(folder));
            int n = 0;
            try (Cursor cur = c.getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
                if (cur == null) return 0;
                while (cur.moveToNext()) {
                    String mime = cur.getString(0);
                    String name = cur.getString(1);
                    if (isMedia(mime, name)) n++;
                }
            }
            return n;
        } catch (Exception e) {
            return 0;
        }
    }

    static boolean isMedia(@Nullable String mime, @Nullable String name) {
        if (mime != null && DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) return false;
        if (mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))) return true;
        if (name == null) return false;
        String n = name.toLowerCase(Locale.US);
        return n.matches(".*\\.(jpe?g|png|gif|webp|avif|heic|mp4|webm|mkv|mov)$");
    }

    private static final String CHANNEL = "mod69_folder_split";

    /** Tells the user a folder was split (notification + the history in Settings > 69 mod). */
    private static void announce(Context c, String name, int count, String newName) {
        String text = c.getString(ml.docilealligator.infinityforreddit.R.string.mod69_split_done_text, name, count, newName);
        SharedPreferences p = counts(c);
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        String old = p.getString("history", "");
        String[] lines = old.isEmpty() ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder(time).append("  ").append(text);
        for (int i = 0; i < Math.min(19, lines.length); i++) sb.append('\n').append(lines[i]);
        p.edit().putString("history", sb.toString()).apply();
        try {
            androidx.core.app.NotificationManagerCompat nm = androidx.core.app.NotificationManagerCompat.from(c);
            nm.createNotificationChannel(new androidx.core.app.NotificationChannelCompat.Builder(CHANNEL,
                    androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(c.getString(ml.docilealligator.infinityforreddit.R.string.mod69_split_channel)).build());
            android.app.Notification n = new androidx.core.app.NotificationCompat.Builder(c, CHANNEL)
                    .setSmallIcon(ml.docilealligator.infinityforreddit.R.drawable.ic_notification)
                    .setContentTitle(c.getString(ml.docilealligator.infinityforreddit.R.string.mod69_split_done, name))
                    .setContentText(text)
                    .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .build();
            if (androidx.core.content.ContextCompat.checkSelfPermission(c, android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33) {
                nm.notify(("split" + name).hashCode(), n);
            }
        } catch (Exception e) {
            Log.w(TAG, "split notification failed", e);
        }
    }

    public static String history(Context c) {
        return counts(c).getString("history", "");
    }

    private static final java.util.Map<String, java.util.Set<String>> LISTS = new java.util.HashMap<>();
    private static final java.util.Map<String, Long> LIST_AT = new java.util.HashMap<>();

    /** Name without extension and without the "_lo" / "_480p" quality ending. */
    static String stem(String name) {
        String n = name.toLowerCase(Locale.US);
        int dot = n.lastIndexOf('.');
        if (dot > 0) n = n.substring(0, dot);
        n = n.replaceAll("(_lo|_hi|_\\d{3,4}p)$", "");
        // only letters and digits count, so small differences in how symbols were replaced don't matter
        return n.replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /**
     * The name of a file in [folder] that is the same download as [fileName] (same name, any extension,
     * any quality ending), or null. The folder list is read at most once a minute.
     */
    @Nullable
    public static synchronized String existingName(Context c, Uri folder, String fileName) {
        String key = folder.toString();
        Long at = LIST_AT.get(key);
        java.util.Set<String> stems = LISTS.get(key);
        if (stems == null || at == null || System.currentTimeMillis() - at > 60_000) {
            stems = new java.util.HashSet<>();
            java.util.Set<String> names = ml.docilealligator.infinityforreddit.utils.DocumentTreeUtils
                    .listChildDisplayNamesLowercase(c, folder);
            for (String n : names) stems.add(stem(n) + "\u0000" + n);
            LISTS.put(key, stems);
            LIST_AT.put(key, System.currentTimeMillis());
        }
        String want = stem(fileName) + "\u0000";
        for (String s : stems) if (s.startsWith(want)) return s.substring(want.length());
        return null;
    }

    /** Remember a file this app just saved, so the next check sees it without re-reading the folder. */
    public static synchronized void noteName(Uri folder, String fileName) {
        java.util.Set<String> stems = LISTS.get(folder.toString());
        if (stems != null) stems.add(stem(fileName) + "\u0000" + fileName.toLowerCase(Locale.US));
    }
}

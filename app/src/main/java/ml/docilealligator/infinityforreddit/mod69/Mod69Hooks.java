package ml.docilealligator.infinityforreddit.mod69;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.services.DownloadMediaService;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import retrofit2.Retrofit;

/** Small shared helpers for the 69 mod: downloaded marks and auto votes. */
public final class Mod69Hooks {
    private static final String TAG = "Mod69Hooks";
    private static final String MARKS_FILE = "mod69_downloaded_marks";
    private static final int MAX_MARKS = 20000;
    private static final Set<String> votedThisSession = new HashSet<>();

    private Mod69Hooks() {}

    /** Adds the post id to a download job so the app knows what was downloaded. */
    public static void tagExtras(PersistableBundle extras, @Nullable Post post) {
        if (post == null) return;
        extras.putString(DownloadMediaService.EXTRA_MOD69_POST_FULLNAME, post.getFullName());
    }

    public static void tagExtras(PersistableBundle extras, @Nullable String postFullName, @Nullable String commentId) {
        if (postFullName != null) extras.putString(DownloadMediaService.EXTRA_MOD69_POST_FULLNAME, postFullName);
        if (commentId != null) extras.putString(DownloadMediaService.EXTRA_MOD69_COMMENT_ID, commentId);
    }

    /** Called after a file was saved: remembers the mark and up-votes if that option is on. */
    public static void onMediaDownloaded(Context context, PersistableBundle extras,
                                         @Nullable Retrofit oauthRetrofit,
                                         @Nullable SharedPreferences currentAccountPrefs,
                                         SharedPreferences defaultPrefs) {
        String post = extras.getString(DownloadMediaService.EXTRA_MOD69_POST_FULLNAME, null);
        String comment = extras.getString(DownloadMediaService.EXTRA_MOD69_COMMENT_ID, null);
        // Continuum's own comment-media downloads carry these
        if (post == null) post = extras.getString(DownloadMediaService.EXTRA_POST_ID, null);
        if (comment == null) comment = extras.getString(DownloadMediaService.EXTRA_COMMENT_ID, null);
        if (post != null && !post.startsWith("t3_")) post = "t3_" + post;
        if (comment != null && comment.startsWith("t1_")) comment = comment.substring(3);
        if (comment != null) {
            markDownloaded(context, "t1_" + comment);
        } else if (post != null) {
            markDownloaded(context, post);
        }
        if (post != null && comment == null && extras.getInt(EXTRA_ALREADY_UPVOTED, 0) == 0 && !UPVOTED_ON_SCREEN.contains(post)
                && defaultPrefs.getBoolean(Mod69Prefs.AUTO_UPVOTE_ON_DOWNLOAD, false)) {
            String result = voteWithResult(oauthRetrofit, currentAccountPrefs, post, APIUtils.DIR_UPVOTE);
            toast(context, result == null ? "Auto up-vote sent" : "Auto up-vote failed: " + result);
        }
    }

    /** Set to 1 on a download job when the post was already up-voted on screen. */
    public static final String EXTRA_ALREADY_UPVOTED = "M69UV";
    private static final Set<String> UPVOTED_ON_SCREEN = java.util.Collections.synchronizedSet(new HashSet<>());

    /** The post was up-voted with its on-screen button when its download started, so don't vote again. */
    public static void markUpvotedOnScreen(String fullName) {
        UPVOTED_ON_SCREEN.add(fullName);
    }

    static void toast(Context context, String text) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(context.getApplicationContext(), text, android.widget.Toast.LENGTH_SHORT).show());
    }

    /** Sends a vote right away (call from a background thread). Returns null when it worked, otherwise why not. */
    @Nullable
    public static String voteWithResult(@Nullable Retrofit oauthRetrofit, @Nullable SharedPreferences currentAccountPrefs,
                                        String fullName, String dir) {
        if (oauthRetrofit == null || currentAccountPrefs == null) return "not ready";
        String account = currentAccountPrefs.getString(SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT);
        String token = currentAccountPrefs.getString(SharedPreferencesUtils.ACCESS_TOKEN, null);
        if (token == null || Account.ANONYMOUS_ACCOUNT.equals(account)) return "not logged in";
        try {
            Map<String, String> params = new HashMap<>();
            params.put(APIUtils.DIR_KEY, dir);
            params.put(APIUtils.ID_KEY, fullName);
            params.put(APIUtils.RANK_KEY, APIUtils.RANK);
            retrofit2.Response<String> r = oauthRetrofit.create(RedditAPI.class)
                    .voteThing(APIUtils.getOAuthHeader(token), params).execute();
            return r.isSuccessful() ? null : "HTTP " + r.code();
        } catch (Exception e) {
            Log.w(TAG, "vote failed", e);
            return e.getClass().getSimpleName();
        }
    }

    /** Sends a vote right away (call from a background thread). */
    public static boolean vote(@Nullable Retrofit oauthRetrofit, @Nullable SharedPreferences currentAccountPrefs,
                               String fullName, String dir) {
        if (oauthRetrofit == null || currentAccountPrefs == null) return false;
        String account = currentAccountPrefs.getString(SharedPreferencesUtils.ACCOUNT_NAME, Account.ANONYMOUS_ACCOUNT);
        String token = currentAccountPrefs.getString(SharedPreferencesUtils.ACCESS_TOKEN, null);
        if (token == null || Account.ANONYMOUS_ACCOUNT.equals(account)) return false;
        String key = fullName + dir;
        synchronized (votedThisSession) {
            if (!votedThisSession.add(key)) return true;
        }
        try {
            Map<String, String> params = new HashMap<>();
            params.put(APIUtils.DIR_KEY, dir);
            params.put(APIUtils.ID_KEY, fullName);
            params.put(APIUtils.RANK_KEY, APIUtils.RANK);
            return oauthRetrofit.create(RedditAPI.class)
                    .voteThing(APIUtils.getOAuthHeader(token), params).execute().isSuccessful();
        } catch (Exception e) {
            Log.w(TAG, "vote failed", e);
            return false;
        }
    }

    private static SharedPreferences marks(Context context) {
        return context.getApplicationContext().getSharedPreferences(MARKS_FILE, Context.MODE_PRIVATE);
    }

    /** @param key a post full name (t3_...) or a comment full name (t1_...) */
    public static void markDownloaded(Context context, String key) {
        SharedPreferences p = marks(context);
        SharedPreferences.Editor e = p.edit().putLong(key, System.currentTimeMillis());
        if (p.getAll().size() > MAX_MARKS) {
            // drop the oldest tenth
            Map<String, ?> all = p.getAll();
            java.util.List<Map.Entry<String, ?>> list = new java.util.ArrayList<>(all.entrySet());
            list.sort((a, b) -> Long.compare(toLong(a.getValue()), toLong(b.getValue())));
            for (int i = 0; i < list.size() / 10; i++) e.remove(list.get(i).getKey());
        }
        e.apply();
        Mod69Events.notifyMarksChanged();
    }

    public static boolean isDownloaded(Context context, @Nullable String key) {
        return key != null && marks(context).contains(key);
    }

    private static long toLong(Object o) {
        return o instanceof Long ? (Long) o : 0L;
    }

    // ---------- saved files (to skip re-downloads and to delete them again) ----------

    private static final String FILES_FILE = "mod69_downloaded_files";

    /** The post/comment a download job belongs to ("t3_..." or "t1_..."), or null. */
    @Nullable
    public static String markKey(PersistableBundle extras) {
        String post = extras.getString(DownloadMediaService.EXTRA_MOD69_POST_FULLNAME, null);
        String comment = extras.getString(DownloadMediaService.EXTRA_MOD69_COMMENT_ID, null);
        if (post == null) post = extras.getString(DownloadMediaService.EXTRA_POST_ID, null);
        if (comment == null) comment = extras.getString(DownloadMediaService.EXTRA_COMMENT_ID, null);
        if (comment != null) return comment.startsWith("t1_") ? comment : "t1_" + comment;
        if (post != null) return post.startsWith("t3_") ? post : "t3_" + post;
        return null;
    }

    private static SharedPreferences files(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILES_FILE, Context.MODE_PRIVATE);
    }

    /** Remembers where a downloaded file was saved. [name] is the name the app first gave it. */
    public static void recordFile(Context c, @Nullable String key, String name, android.net.Uri saved) {
        if (key == null || saved == null) return;
        files(c).edit().putString(key + "|" + name, saved.toString()).apply();
    }

    /** The earlier saved copy of this file, if it is still there. */
    @Nullable
    public static android.net.Uri existingFile(Context c, @Nullable String key, String name) {
        if (key == null) return null;
        String u = files(c).getString(key + "|" + name, null);
        if (u == null) return null;
        android.net.Uri uri = android.net.Uri.parse(u);
        if (exists(c, uri)) return uri;
        files(c).edit().remove(key + "|" + name).apply();
        return null;
    }

    public static boolean exists(Context c, android.net.Uri uri) {
        try {
            if ("file".equals(uri.getScheme()) || uri.getScheme() == null) {
                return new java.io.File(uri.getPath() == null ? uri.toString() : uri.getPath()).exists();
            }
            try (android.os.ParcelFileDescriptor fd = c.getContentResolver().openFileDescriptor(uri, "r")) {
                return fd != null;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /** After a file was deleted: forget it, and clear the downloaded mark when none of that post's files are left. */
    public static void forgetFile(Context c, android.net.Uri uri) {
        SharedPreferences f = files(c);
        String target = uri.toString();
        Set<String> keysTouched = new HashSet<>();
        SharedPreferences.Editor e = f.edit();
        for (Map.Entry<String, ?> en : f.getAll().entrySet()) {
            if (target.equals(en.getValue())) {
                e.remove(en.getKey());
                int bar = en.getKey().indexOf('|');
                if (bar > 0) keysTouched.add(en.getKey().substring(0, bar));
            }
        }
        e.commit();
        for (String key : keysTouched) {
            boolean anyLeft = false;
            for (String k : f.getAll().keySet()) if (k.startsWith(key + "|")) { anyLeft = true; break; }
            if (!anyLeft) {
                marks(c).edit().remove(key).commit();
                Mod69Events.notifyMarksChanged();
            }
        }
    }
}

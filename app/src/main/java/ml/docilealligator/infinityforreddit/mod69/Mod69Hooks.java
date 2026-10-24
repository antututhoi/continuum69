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
        if (post != null && comment == null && defaultPrefs.getBoolean(Mod69Prefs.AUTO_UPVOTE_ON_DOWNLOAD, false)) {
            vote(oauthRetrofit, currentAccountPrefs, post, APIUtils.DIR_UPVOTE);
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
}

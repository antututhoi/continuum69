package ml.docilealligator.infinityforreddit.mod69;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

import ml.docilealligator.infinityforreddit.post.PostType;
import ml.docilealligator.infinityforreddit.thing.SortType;

/**
 * Remembers the furthest post you scrolled to in each feed (subreddit, multireddit, home, user, search,
 * per sort), and starts the feed again right after it, skipping everything above.
 */
public final class Mod69FeedPosition {
    /** Stored in the app's own "scrolled positions" settings file, under keys starting with this. */
    private static final String PREFIX = "mod69_pos|";
    /** Feeds where the user chose "Start from top" until they scroll again. */
    private static final Set<String> skipOnce = new HashSet<>();

    private Mod69FeedPosition() {}

    public static boolean isEnabled(SharedPreferences defaultPrefs) {
        return defaultPrefs != null && defaultPrefs.getBoolean(Mod69Prefs.SAVE_FEED_POSITION, true);
    }

    @Nullable
    public static String key(String accountName, int postType, @Nullable String name, @Nullable String query,
                             @Nullable String where, @Nullable SortType sortType) {
        if (postType == PostType.ANONYMOUS_FRONT_PAGE
                || postType == PostType.ANONYMOUS_MULTIREDDIT
                || postType == PostType.READ_POSTS) {
            // anonymous feeds are built from many names; keep them simple and skip
            return null;
        }
        StringBuilder sb = new StringBuilder(PREFIX);
        sb.append(accountName).append('|').append(postType).append('|')
                .append(name == null ? "" : name.toLowerCase()).append('|')
                .append(query == null ? "" : query).append('|')
                .append(where == null ? "" : where).append('|');
        if (sortType != null) {
            sb.append(sortType.getType() == null ? "" : sortType.getType().name()).append('|')
                    .append(sortType.getTime() == null ? "" : sortType.getTime().name());
        }
        return sb.toString();
    }

    /** The post to start after, or null to start at the top. */
    @Nullable
    public static String startAfter(@Nullable SharedPreferences store, SharedPreferences defaultPrefs, @Nullable String key) {
        if (store == null || key == null || !isEnabled(defaultPrefs)) return null;
        synchronized (skipOnce) {
            if (skipOnce.contains(key)) return null;
        }
        return store.getString(key, null);
    }

    public static boolean hasSaved(@Nullable SharedPreferences store, SharedPreferences defaultPrefs, @Nullable String key) {
        return startAfter(store, defaultPrefs, key) != null;
    }

    public static void save(@Nullable SharedPreferences store, SharedPreferences defaultPrefs, @Nullable String key,
                            @Nullable String postFullName) {
        if (store == null || key == null || postFullName == null || !isEnabled(defaultPrefs)) return;
        synchronized (skipOnce) {
            skipOnce.remove(key);
        }
        store.edit().putString(key, postFullName).apply();
    }

    /** "Start from top": forget the saved spot for this feed. */
    public static void clear(@Nullable SharedPreferences store, @Nullable String key) {
        if (store == null || key == null) return;
        synchronized (skipOnce) {
            skipOnce.add(key);
        }
        store.edit().remove(key).apply();
    }
}

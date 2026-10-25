package ml.docilealligator.infinityforreddit.mod69;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ml.docilealligator.infinityforreddit.post.Post;

/**
 * 69 mod: names files like the Multitool Firefox add-on, so the phone and the PC save the same media under
 * the same name (and the duplicate check finds files saved by either):
 *   single file:            {title} [{id}]        (+ " - 2" for one picture of a multi-picture post)
 *   album folder:           {title} [{id}]
 *   file in an album folder: {index}_{filename}
 * Placeholders: {subreddit} {user} {id} {title} {date} {year} {month} {day} {timestamp} {index} {n} {total} {filename}
 */
public final class Mod69Naming {
    public static final String DEFAULT_SINGLE = "{title} [{id}]";
    public static final String DEFAULT_ALBUM_FILE = "{index}_{filename}";

    @Nullable private static SharedPreferences prefs;

    private Mod69Naming() {}

    public static void init(Context c) {
        prefs = PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());
    }

    public static boolean enabled() {
        SharedPreferences p = prefs;
        return p != null && p.getBoolean(Mod69Prefs.MULTITOOL_NAMES, true);
    }

    private static String pref(String key, String def) {
        SharedPreferences p = prefs;
        String v = p == null ? null : p.getString(key, def);
        return v == null || v.trim().isEmpty() ? def : v.trim();
    }

    static int maxTitle() {
        try {
            return Math.max(20, Integer.parseInt(pref(Mod69Prefs.MAX_TITLE_LENGTH, "80")));
        } catch (Exception e) {
            return 80;
        }
    }

    /** Name for a file saved on its own. @param extension with the dot, e.g. ".jpg" */
    @Nullable
    public static String fileName(Post post, int galleryIndex, @Nullable String url, String extension) {
        if (!enabled()) return null;
        String pattern = lastPart(pref(Mod69Prefs.FILE_NAME_PATTERN, DEFAULT_SINGLE));
        String out = fill(pattern, post, galleryIndex, url);
        boolean multi = post.getPostType() == Post.GALLERY_TYPE && post.getGallery() != null && post.getGallery().size() > 1;
        if (multi && !pattern.contains("{n}") && !pattern.contains("{index}")) {
            out = out + " - " + (galleryIndex + 1);
        }
        return out.isEmpty() ? null : out + extension;
    }

    /** Name for a file inside an album folder (no spaces, the app keeps album names in a space-separated list). */
    @Nullable
    public static String albumFileName(Post post, int galleryIndex, @Nullable String url, String extension) {
        if (!enabled()) return null;
        String out = fill(lastPart(pref(Mod69Prefs.ALBUM_FILE_PATTERN, DEFAULT_ALBUM_FILE)), post, galleryIndex, url)
                .replace(' ', '_');
        return out.isEmpty() ? null : out + extension;
    }

    /** "Post title [id]" for an album folder. */
    public static String albumFolder(Post post) {
        String t = cleanTitle(post.getTitle());
        return (t.isEmpty() ? "" : t + " ") + "[" + post.getId() + "]";
    }

    private static String lastPart(String pattern) {
        int slash = Math.max(pattern.lastIndexOf('/'), pattern.lastIndexOf('\\'));
        String p = slash >= 0 ? pattern.substring(slash + 1) : pattern;
        return p.replace("{ext}", "").replaceAll("\\.[A-Za-z0-9]{2,4}$", "");
    }

    private static String fill(String pattern, Post post, int galleryIndex, @Nullable String url) {
        Date d = new Date(post.getPostTimeMillis());
        int total = post.getGallery() != null && !post.getGallery().isEmpty() ? post.getGallery().size() : 1;
        int n = galleryIndex >= 0 ? galleryIndex + 1 : 1;
        return pattern
                .replace("{subreddit}", clean(post.getSubredditName()))
                .replace("{user}", clean(post.getAuthor()))
                .replace("{id}", clean(post.getId()))
                .replace("{title}", cleanTitle(post.getTitle()))
                .replace("{date}", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d))
                .replace("{year}", new SimpleDateFormat("yyyy", Locale.US).format(d))
                .replace("{month}", new SimpleDateFormat("MM", Locale.US).format(d))
                .replace("{day}", new SimpleDateFormat("dd", Locale.US).format(d))
                .replace("{timestamp}", String.valueOf(post.getPostTimeMillis() / 1000))
                .replace("{index}", String.format(Locale.US, "%02d", n))
                .replace("{n}", String.valueOf(n))
                .replace("{total}", String.valueOf(total))
                .replace("{filename}", clean(redditFileName(url)))
                .trim();
    }

    /** Reddit's own file name: "title-slug-v0-REALID.jpeg" -> "REALID". */
    static String redditFileName(@Nullable String url) {
        if (url == null) return "";
        String u = url;
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        String f = u.substring(u.lastIndexOf('/') + 1);
        int dot = f.lastIndexOf('.');
        if (dot > 0) f = f.substring(0, dot);
        return f.replaceAll("^.*-v0-", "");
    }

    static String clean(@Nullable String s) {
        if (s == null) return "";
        return s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("\\s+", " ").trim()
                .replaceAll("^[. ]+|[. ]+$", "");
    }

    static String cleanTitle(@Nullable String title) {
        String t = clean(title);
        int max = maxTitle();
        if (t.length() > max) t = t.substring(0, max).trim();
        return t.replaceAll("[. ]+$", "");
    }
}

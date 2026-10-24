package ml.docilealligator.infinityforreddit.mod69;

import android.app.job.JobScheduler;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.PersistableBundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.activities.LinkResolverActivity;
import ml.docilealligator.infinityforreddit.comment.Comment;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.services.DownloadMediaService;
import ml.docilealligator.infinityforreddit.services.DownloadRedditVideoService;
import ml.docilealligator.infinityforreddit.thing.MediaMetadata;
import retrofit2.Retrofit;

/**
 * Comment features: the one real media of a comment (never emojis/avatars), its download with
 * "Post title [postID_cCommentID]" names in the subreddit folder, long-press menu, hide/block users.
 */
public final class Mod69Comments {
    private static final String HIDDEN_USERS_FILE = "mod69_hidden_users";
    private static final Pattern DIRECT_MEDIA = Pattern.compile(
            "https?://(?:i\\.redd\\.it|preview\\.redd\\.it|i\\.imgur\\.com)/[^\\s)\\]]+?\\.(?:jpe?g|png|gif|gifv|mp4|webp)(?:\\?[^\\s)\\]]*)?",
            Pattern.CASE_INSENSITIVE);

    @Nullable
    private static volatile Comment lastClickedComment;

    private Mod69Comments() {}

    private static SharedPreferences prefs(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());
    }

    // ---------- which media ----------

    private static boolean isEmote(MediaMetadata m) {
        return m.id != null && m.id.startsWith("emote|");
    }

    /** The comment's real media: uploaded image/GIF/video first, then a GIPHY GIF. Emojis are skipped. */
    @Nullable
    public static MediaMetadata pickMedia(@Nullable Comment comment) {
        if (comment == null) return null;
        Map<String, MediaMetadata> map = comment.getMediaMetadataMap();
        MediaMetadata giphy = null;
        if (map != null) {
            String md = comment.getCommentMarkdown();
            MediaMetadata firstInText = null;
            int firstPos = Integer.MAX_VALUE;
            for (MediaMetadata m : map.values()) {
                if (m == null || m.original == null || m.original.url == null || isEmote(m)) continue;
                if (m.id != null && m.id.startsWith("giphy|")) {
                    if (giphy == null) giphy = m;
                    continue;
                }
                int pos = md != null && m.id != null ? md.indexOf(m.id) : -1;
                if (pos < 0) pos = Integer.MAX_VALUE - 1;
                if (pos < firstPos) {
                    firstPos = pos;
                    firstInText = m;
                }
            }
            if (firstInText != null) return firstInText;
        }
        if (giphy != null) return giphy;
        String link = firstDirectLink(comment);
        if (link != null) return fromLink(link);
        return null;
    }

    @Nullable
    public static String firstDirectLink(@Nullable Comment comment) {
        if (comment == null || comment.getCommentMarkdown() == null) return null;
        Matcher m = DIRECT_MEDIA.matcher(comment.getCommentMarkdown());
        return m.find() ? m.group().replace("&amp;", "&") : null;
    }

    /** Builds media info for a plain image/GIF link written in a comment. */
    public static MediaMetadata fromLink(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        boolean gif = lower.contains(".gif") || lower.contains(".mp4");
        String original = Mod69Download.toOriginal(url);
        String mp4 = null;
        if (lower.contains("i.imgur.com") && (lower.endsWith(".gifv") || lower.endsWith(".gif"))) {
            mp4 = original.replaceAll("(?i)\\.gifv?$", ".mp4");
            original = original.replaceAll("(?i)\\.gifv$", ".gif");
        }
        MediaMetadata.MediaItem item = new MediaMetadata.MediaItem(0, 0, original, mp4);
        String id = "mod69link|" + Integer.toHexString(url.hashCode());
        return new MediaMetadata(id, gif ? "AnimatedImage" : "Image", item, item);
    }

    public static boolean hasMedia(@Nullable Comment comment) {
        return pickMedia(comment) != null;
    }

    // ---------- names ----------

    public static String safeName(@Nullable String s) {
        if (s == null) return "";
        String r = s.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t]", "_").replaceAll("\\s+", " ").trim();
        if (r.length() > 100) r = r.substring(0, 100).trim();
        return r;
    }

    /** "Post title [postID_cCommentID]" */
    public static String baseName(@Nullable Post post, Comment comment) {
        String title = post != null ? safeName(post.getTitle()) : "";
        String postId = post != null ? post.getId() : stripPrefix(comment.getLinkId());
        return (title.isEmpty() ? "" : title + " ") + "[" + postId + "_c" + comment.getId() + "]";
    }

    private static String stripPrefix(@Nullable String fullName) {
        if (fullName == null) return "";
        int i = fullName.indexOf('_');
        return i >= 0 ? fullName.substring(i + 1) : fullName;
    }

    private static String extensionOf(String url, String fallback) {
        String path = Uri.parse(url).getPath();
        if (path != null) {
            int dot = path.lastIndexOf('.');
            if (dot >= 0 && dot > path.lastIndexOf('/')) {
                String ext = path.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (ext.equals("gifv")) return "mp4";
                if (ext.matches("[a-z0-9]{2,4}")) return ext;
            }
        }
        return fallback;
    }

    // ---------- download ----------

    public static void download(BaseActivity activity, @Nullable Post post, Comment comment, @Nullable MediaMetadata chosen) {
        MediaMetadata media = chosen != null && !isEmote(chosen) ? chosen : pickMedia(comment);
        if (media == null) {
            Toast.makeText(activity, R.string.mod69_no_media_in_comment, Toast.LENGTH_SHORT).show();
            return;
        }
        String subreddit = post != null ? post.getSubredditName() : comment.getSubredditName();
        boolean nsfw = post != null && post.isNSFW();
        String base = baseName(post, comment);
        String postFullName = post != null ? post.getFullName() : comment.getLinkId();
        JobScheduler scheduler = (JobScheduler) activity.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        if ("RedditVideo".equalsIgnoreCase(media.e)) {
            String url = MediaMetadata.getDownloadUrlForMarkdownParsedVideo(media.original.url);
            if (url == null) return;
            PersistableBundle extras = new PersistableBundle();
            extras.putString(DownloadRedditVideoService.EXTRA_VIDEO_URL, url);
            extras.putString(DownloadRedditVideoService.EXTRA_POST_ID, post != null ? post.getId() : stripPrefix(comment.getLinkId()));
            extras.putString(DownloadRedditVideoService.EXTRA_SUBREDDIT, subreddit);
            extras.putInt(DownloadRedditVideoService.EXTRA_IS_NSFW, nsfw ? 1 : 0);
            extras.putString(DownloadRedditVideoService.EXTRA_FILE_NAME, base + ".mp4");
            extras.putInt(DownloadMediaService.EXTRA_MOD69_FORCE_SUBFOLDER, 1);
            Mod69Hooks.tagExtras(extras, postFullName, comment.getId());
            scheduler.schedule(DownloadRedditVideoService.constructJobInfo(activity, 5000000, extras));
        } else {
            PersistableBundle extras = new PersistableBundle();
            String url;
            int type;
            String ext;
            // GIF to MP4 is decided by the 69 Media Helper after the download
            boolean wantMp4 = false;
            if (media.isGIF && media.original.mp4Url != null && wantMp4) {
                url = media.original.mp4Url;
                type = DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO;
                ext = "mp4";
            } else if (media.isGIF) {
                url = media.original.url;
                type = DownloadMediaService.EXTRA_MEDIA_TYPE_GIF;
                ext = extensionOf(url, "gif");
                if (ext.equals("mp4")) type = DownloadMediaService.EXTRA_MEDIA_TYPE_VIDEO;
            } else {
                url = media.original.url;
                type = DownloadMediaService.EXTRA_MEDIA_TYPE_IMAGE;
                ext = extensionOf(url, "jpg");
            }
            extras.putString(DownloadMediaService.EXTRA_URL, url);
            if (media.downscaled != null && media.downscaled.url != null && !media.downscaled.url.equals(url)) {
                extras.putString(DownloadMediaService.EXTRA_MOD69_PREVIEW_URL, media.downscaled.url);
            }
            extras.putInt(DownloadMediaService.EXTRA_MEDIA_TYPE, type);
            extras.putString(DownloadMediaService.EXTRA_FILE_NAME, base + "." + ext);
            extras.putString(DownloadMediaService.EXTRA_SUBREDDIT_NAME, subreddit);
            extras.putInt(DownloadMediaService.EXTRA_IS_NSFW, nsfw ? 1 : 0);
            extras.putInt(DownloadMediaService.EXTRA_MOD69_FORCE_SUBFOLDER, 1);
            Mod69Hooks.tagExtras(extras, postFullName, comment.getId());
            scheduler.schedule(DownloadMediaService.constructJobInfo(activity, 5000000, extras));
        }
        Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show();
    }

    // ---------- long press on a comment picture ----------

    /** Remembers which comment a tapped picture belongs to (for file names in the viewer). */
    public static void rememberClicked(View v) {
        View item = Mod69Ui.findItemView(v);
        Object c = item != null ? item.getTag(R.id.mod69_tag_comment) : null;
        lastClickedComment = c instanceof Comment ? (Comment) c : null;
    }

    /** File name for the picture viewer, so its Download button also uses the comment naming. */
    public static String viewerFileName(@Nullable Post post, MediaMetadata media) {
        Comment c = lastClickedComment;
        if (c == null) return media.fileName;
        String ext = extensionOf(media.original.url, media.isGIF ? "gif" : "jpg");
        return baseName(post, c) + "." + ext;
    }

    @Nullable
    public static Comment lastClicked() {
        return lastClickedComment;
    }

    public static boolean onMediaLongPress(View v, MediaMetadata media) {
        View item = Mod69Ui.findItemView(v);
        if (item == null || !(v.getContext() instanceof BaseActivity)) return false;
        Object c = item.getTag(R.id.mod69_tag_comment);
        Object p = item.getTag(R.id.mod69_tag_post_object);
        if (!(c instanceof Comment)) return false;
        BaseActivity activity = (BaseActivity) v.getContext();
        Comment comment = (Comment) c;
        Post post = p instanceof Post ? (Post) p : null;
        String url = media.original != null ? media.original.url : null;
        CharSequence[] items = new CharSequence[]{
                activity.getString(R.string.mod69_download_comment_media),
                activity.getString(R.string.open_link),
                activity.getString(R.string.copy_link),
                activity.getString(R.string.share_link)
        };
        new MaterialAlertDialogBuilder(activity)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            download(activity, post, comment, media);
                            break;
                        case 1:
                            if (url != null) {
                                Intent intent = new Intent(activity, LinkResolverActivity.class);
                                intent.setData(Uri.parse(url));
                                activity.startActivity(intent);
                            }
                            break;
                        case 2:
                            if (url != null) {
                                ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("link", url));
                                Toast.makeText(activity, R.string.copy_success, Toast.LENGTH_SHORT).show();
                            }
                            break;
                        case 3:
                            if (url != null) {
                                Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url);
                                activity.startActivity(Intent.createChooser(send, activity.getString(R.string.share)));
                            }
                            break;
                    }
                })
                .show();
        return true;
    }

    // ---------- hide / block users ----------

    private static SharedPreferences hiddenUsers(Context c) {
        return c.getApplicationContext().getSharedPreferences(HIDDEN_USERS_FILE, Context.MODE_PRIVATE);
    }

    public static boolean isUserHidden(Context c, @Nullable String author) {
        return author != null && hiddenUsers(c).contains(author.toLowerCase(Locale.ROOT));
    }

    public static void hideUser(Context c, String author) {
        hiddenUsers(c).edit().putLong(author.toLowerCase(Locale.ROOT), System.currentTimeMillis()).apply();
    }

    /** Sets up the row's buttons for one comment, and folds away comments of hidden/blocked users. */
    public static void bindRow(@Nullable Mod69Ui.ActionRow row, BaseActivity activity, Retrofit oauthRetrofit,
                               @Nullable Post post, Comment comment, View itemView, Runnable refreshList) {
        itemView.setTag(R.id.mod69_tag_comment, comment);
        itemView.setTag(R.id.mod69_tag_post_object, post);
        boolean collapse = isUserHidden(activity, comment.getAuthor()) || Mod69PostActions.isAuthorBlocked(comment.getAuthor());
        Mod69Ui.setCollapsed(itemView, collapse);
        if (row == null) return;
        boolean show = Mod69PostActions.showButtons(activity);
        boolean media = hasMedia(comment);
        boolean downloaded = Mod69PostActions.showMark(activity) && Mod69Hooks.isDownloaded(activity, "t1_" + comment.getId());
        row.row.setVisibility(show || downloaded ? View.VISIBLE : View.GONE);
        row.download.setVisibility(show && media ? View.VISIBLE : View.GONE);
        row.hide.setVisibility(show ? View.VISIBLE : View.GONE);
        row.block.setVisibility(show ? View.VISIBLE : View.GONE);
        row.hide.setText(R.string.mod69_hide_user_short);
        row.block.setText(R.string.mod69_block_user_short);
        row.mark.setVisibility(downloaded ? View.VISIBLE : View.GONE);
        row.download.setOnClickListener(v -> download(activity, post, comment, null));
        row.hide.setOnClickListener(v -> new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.mod69_hide_user)
                .setMessage("u/" + comment.getAuthor())
                .setPositiveButton(R.string.mod69_hide, (d, w) -> {
                    hideUser(activity, comment.getAuthor());
                    Toast.makeText(activity, R.string.mod69_user_hidden, Toast.LENGTH_SHORT).show();
                    refreshList.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
        row.block.setOnClickListener(v -> Mod69PostActions.blockUser(activity, oauthRetrofit, comment.getAuthor(), refreshList));
    }

    /** Names of users whose comments are hidden (for a future settings list). */
    public static Set<String> hiddenUserNames(Context c) {
        return new HashSet<>(hiddenUsers(c).getAll().keySet());
    }
}

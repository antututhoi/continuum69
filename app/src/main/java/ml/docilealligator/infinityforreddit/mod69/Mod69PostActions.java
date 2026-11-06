package ml.docilealligator.infinityforreddit.mod69;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.activities.BaseActivity;
import ml.docilealligator.infinityforreddit.events.PostUpdateEventToPostDetailFragment;
import ml.docilealligator.infinityforreddit.events.PostUpdateEventToPostList;
import ml.docilealligator.infinityforreddit.post.HidePost;
import ml.docilealligator.infinityforreddit.post.Post;
import ml.docilealligator.infinityforreddit.services.DownloadMediaService;
import ml.docilealligator.infinityforreddit.services.DownloadRedditVideoService;
import ml.docilealligator.infinityforreddit.thing.VoteThing;
import ml.docilealligator.infinityforreddit.user.BlockUser;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import retrofit2.Retrofit;

/** Download / Hide / Block actions for posts (used by the buttons and the post menu). */
public final class Mod69PostActions {
    private static final Set<String> hiddenThisSession = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> blockedAuthors = Collections.synchronizedSet(new HashSet<>());

    private Mod69PostActions() {}

    private static SharedPreferences prefs(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());
    }

    public static boolean showButtons(Context c) {
        return prefs(c).getBoolean(Mod69Prefs.SHOW_ACTION_BUTTONS, true);
    }

    public static boolean showMark(Context c) {
        return prefs(c).getBoolean(Mod69Prefs.SHOW_DOWNLOADED_MARK, true);
    }

    /** True when this post was hidden or its author blocked here, so the feed item should be folded away. */
    public static boolean shouldCollapse(Post post) {
        if (post == null) return false;
        if (hiddenThisSession.contains(post.getFullName())) return true;
        String a = post.getAuthor();
        return a != null && blockedAuthors.contains(a.toLowerCase(Locale.ROOT));
    }

    private static final Set<String> revealed = Collections.synchronizedSet(new HashSet<>());

    /** Spoiler pictures the user chose to see un-blurred (until the app closes). */
    public static boolean isRevealed(Post post) {
        return post != null && revealed.contains(post.getFullName());
    }

    public static void reveal(Post post) {
        revealed.add(post.getFullName());
    }

    public static boolean isAuthorBlocked(@Nullable String author) {
        return author != null && blockedAuthors.contains(author.toLowerCase(Locale.ROOT));
    }

    public static boolean canDownload(Post post) {
        if (post == null) return false;
        int t = post.getPostType();
        return t == Post.IMAGE_TYPE || t == Post.GIF_TYPE || t == Post.VIDEO_TYPE
                || (t == Post.GALLERY_TYPE && post.getGallery() != null && !post.getGallery().isEmpty());
    }

    /** "high"/"low" while a long-press "other quality" download is being started (main thread only). */
    @Nullable private static String sQuality;

    private static void schedule(Context c, JobInfo jobInfo) {
        if (sQuality != null) {
            // JobInfo hands back its own extras, so the quality travels with this one job
            jobInfo.getExtras().putString(DownloadMediaService.EXTRA_MOD69_QUALITY, sQuality);
        }
        ((JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).schedule(jobInfo);
    }

    /** Long-press on Download: pick original or low quality just for this download. */
    static void showQualityMenu(BaseActivity activity, View anchor, Post post, @Nullable View upvoteButton) {
        android.widget.PopupMenu menu = new android.widget.PopupMenu(activity, anchor);
        menu.getMenu().add(0, 1, 0, R.string.mod69_download_original);
        menu.getMenu().add(0, 2, 1, R.string.mod69_download_low);
        boolean album = post.getPostType() == Post.GALLERY_TYPE && post.getGallery() != null && post.getGallery().size() > 1;
        menu.setOnMenuItemClickListener(item -> {
            String q = item.getItemId() == 1 ? "high" : "low";
            upvoteOnScreen(activity, post, upvoteButton);
            if (album) {
                sQuality = q;
                try {
                    Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show();
                    schedule(activity, DownloadMediaService.constructGalleryDownloadAllMediaJobInfo(activity, 5000000L * post.getGallery().size(), post));
                } finally {
                    sQuality = null;
                }
            } else {
                downloadPostWithQuality(activity, post, 0, q);
            }
            return true;
        });
        menu.show();
    }

    public static void downloadPostWithQuality(BaseActivity activity, Post post, int galleryIndex, @Nullable String quality) {
        sQuality = quality;
        try {
            downloadPost(activity, post, galleryIndex);
        } finally {
            sQuality = null;
        }
    }

    /** The Download button: one file, or a picker for albums. */
    public static void download(BaseActivity activity, Post post) {
        if (post.getPostType() == Post.GALLERY_TYPE) {
            showGallerySelect(activity, post);
        } else {
            downloadPost(activity, post, 0);
        }
    }

    public static void downloadPost(BaseActivity activity, Post post, int galleryIndex) {
        Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show();
        if (post.getPostType() == Post.VIDEO_TYPE && post.isNormalVideo()) {
            PersistableBundle extras = new PersistableBundle();
            extras.putString(DownloadRedditVideoService.EXTRA_VIDEO_URL, post.getVideoDownloadUrl());
            extras.putString(DownloadRedditVideoService.EXTRA_POST_ID, post.getId());
            extras.putString(DownloadRedditVideoService.EXTRA_SUBREDDIT, post.getSubredditName());
            extras.putInt(DownloadRedditVideoService.EXTRA_IS_NSFW, post.isNSFW() ? 1 : 0);
            extras.putString(DownloadRedditVideoService.EXTRA_FILE_NAME,
                    ml.docilealligator.infinityforreddit.utils.MediaFileNameUtils.getDownloadFileName(post, 0));
            schedule(activity, DownloadRedditVideoService.constructJobInfo(activity, 5000000, extras));
            return;
        }
        schedule(activity, DownloadMediaService.constructJobInfo(activity, 5000000, post, galleryIndex));
    }

    /** Album picker with thumbnails and checkboxes. */
    public static void showGallerySelect(BaseActivity activity, Post post) {
        ArrayList<Post.Gallery> gallery = post.getGallery();
        if (gallery == null || gallery.isEmpty()) return;
        int pad = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, activity.getResources().getDisplayMetrics()));
        int thumb = pad * 12;

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(pad * 2, pad, pad * 2, pad);
        List<CheckBox> boxes = new ArrayList<>();
        for (int i = 0; i < gallery.size(); i++) {
            Post.Gallery g = gallery.get(i);
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, pad / 2, 0, pad / 2);
            ImageView iv = new ImageView(activity);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(iv, new LinearLayout.LayoutParams(thumb, thumb));
            // 69 mod: the small preview (the full file is often too big / a video and showed nothing)
            String thumbUrl = g.feedPreviewUrl != null && !g.feedPreviewUrl.isEmpty() ? g.feedPreviewUrl : g.url;
            Glide.with(activity).load(thumbUrl).override(thumb, thumb).centerCrop()
                    .error(Glide.with(activity).load(g.fallbackUrl != null ? g.fallbackUrl : g.url).override(thumb, thumb).centerCrop())
                    .into(iv);
            CheckBox cb = new CheckBox(activity);
            String type = g.mediaType == Post.Gallery.TYPE_VIDEO ? "video" : g.mediaType == Post.Gallery.TYPE_GIF ? "GIF" : "image";
            cb.setText("  " + (i + 1) + " (" + type + ")");
            cb.setChecked(true);
            cb.setTextColor(activity.customThemeWrapper.getPrimaryTextColor());
            LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            row.addView(cb, cblp);
            iv.setOnClickListener(v -> cb.toggle());
            boxes.add(cb);
            list.addView(row);
        }
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(list);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.mod69_select_images)
                .setView(scroll)
                .setPositiveButton(R.string.mod69_download, null)
                .setNeutralButton(android.R.string.selectAll, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                boolean anyOff = false;
                for (CheckBox cb : boxes) if (!cb.isChecked()) anyOff = true;
                for (CheckBox cb : boxes) cb.setChecked(anyOff);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                List<Integer> chosen = new ArrayList<>();
                for (int i = 0; i < boxes.size(); i++) if (boxes.get(i).isChecked()) chosen.add(i);
                if (chosen.isEmpty()) return;
                Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show();
                if (chosen.size() == gallery.size()) {
                    schedule(activity, DownloadMediaService.constructGalleryDownloadAllMediaJobInfo(activity, 5000000L * gallery.size(), post));
                } else if (chosen.size() == 1) {
                    schedule(activity, DownloadMediaService.constructJobInfo(activity, 5000000, post, chosen.get(0)));
                } else {
                    schedule(activity, DownloadMediaService.constructGallerySelectedMediaJobInfo(activity, 5000000L * chosen.size(), post, chosen));
                }
                dialog.dismiss();
            });
        });
        dialog.show();
        Mod69Ui.styleButtons(activity, dialog);
    }

    /** Hides the post on Reddit, folds it away here, and down-votes it if that option is on. */
    public static void hide(BaseActivity activity, Retrofit oauthRetrofit, Post post, int position, @Nullable Runnable onHidden) {
        hiddenThisSession.add(post.getFullName());
        post.setHidden(true);
        if (onHidden != null) onHidden.run();
        if (Account.ANONYMOUS_ACCOUNT.equals(activity.accountName)) {
            Toast.makeText(activity, R.string.post_hide_success, Toast.LENGTH_SHORT).show();
            return;
        }
        HidePost.hidePost(oauthRetrofit, activity.accessToken, post.getFullName(), new HidePost.HidePostListener() {
            @Override
            public void success() {
                Toast.makeText(activity, R.string.post_hide_success, Toast.LENGTH_SHORT).show();
                EventBus.getDefault().post(new PostUpdateEventToPostList(post, position));
                EventBus.getDefault().post(new PostUpdateEventToPostDetailFragment(post));
            }

            @Override
            public void failed() {
                Toast.makeText(activity, R.string.post_hide_failed, Toast.LENGTH_SHORT).show();
            }
        });
        autoDownvoteOnHide(activity, oauthRetrofit, post);
    }

    /** Down-votes a post that was just hidden, when "Down-vote a post when hiding it" is on. */
    public static void autoDownvoteOnHide(BaseActivity activity, Retrofit oauthRetrofit, Post post) {
        if (!prefs(activity).getBoolean(Mod69Prefs.AUTO_DOWNVOTE_ON_HIDE, false)) return;
        if (Account.ANONYMOUS_ACCOUNT.equals(activity.accountName) || post.getVoteType() == -1) return;
        VoteThing.voteThing(activity, oauthRetrofit, activity.accessToken, new VoteThing.VoteThingWithoutPositionListener() {
            @Override
            public void onVoteThingSuccess() {
                post.setVoteType(-1);
                Toast.makeText(activity, "Auto down-vote sent", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onVoteThingFail() {
                Toast.makeText(activity, "Auto down-vote failed", Toast.LENGTH_SHORT).show();
            }
        }, post.getFullName(), APIUtils.DIR_DOWNVOTE);
    }

    /** Blocks the post's author (Reddit block) and folds away their posts here. */
    public static void block(BaseActivity activity, Retrofit oauthRetrofit, Post post, @Nullable Runnable onBlocked) {
        block(activity, oauthRetrofit, post, onBlocked, null);
    }

    public static void block(BaseActivity activity, Retrofit oauthRetrofit, Post post, @Nullable Runnable onBlocked,
                             @Nullable Runnable onUndo) {
        blockUser(activity, oauthRetrofit, post.getAuthor(), onBlocked, onUndo);
    }

    public static void blockUser(BaseActivity activity, Retrofit oauthRetrofit, @Nullable String author, @Nullable Runnable onBlocked) {
        blockUser(activity, oauthRetrofit, author, onBlocked, null);
    }

    /**
     * Asks, then hides the user's posts/comments at once and shows "Blocked · UNDO" for a few seconds.
     * The block is only sent to Reddit when that message goes away without Undo, so Undo needs no unblock.
     */
    public static void blockUser(BaseActivity activity, Retrofit oauthRetrofit, @Nullable String author, @Nullable Runnable onBlocked,
                                 @Nullable Runnable onUndo) {
        if (author == null || author.isEmpty() || "[deleted]".equals(author)) return;
        Mod69Ui.show(activity, new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.mod69_block_user)
                .setMessage("u/" + author)
                .setPositiveButton(R.string.mod69_block, (d, w) -> {
                    String key = author.toLowerCase(Locale.ROOT);
                    blockedAuthors.add(key);
                    if (onBlocked != null) onBlocked.run();
                    // 69 mod: no countdown popup; the item shows an UNBLOCK bar instead
                    sendBlock(activity, oauthRetrofit, author);
                })
                .setNegativeButton(android.R.string.cancel, null));
    }

    private static void sendBlock(BaseActivity activity, Retrofit oauthRetrofit, String author) {
        if (Account.ANONYMOUS_ACCOUNT.equals(activity.accountName)) {
            Toast.makeText(activity, R.string.mod69_user_blocked, Toast.LENGTH_SHORT).show();
            return;
        }
        BlockUser.blockUser(oauthRetrofit, activity.accessToken, author, new BlockUser.BlockUserListener() {
            @Override
            public void success() {
                Toast.makeText(activity, R.string.mod69_user_blocked, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void failed() {
                Toast.makeText(activity, R.string.block_user_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Sets up the row's buttons for one post. */
    public static void bindRow(@Nullable Mod69Ui.ActionRow row, BaseActivity activity, Retrofit oauthRetrofit,
                               Post post, int position, View itemView) {
        bindRow(row, activity, oauthRetrofit, post, position, itemView, null, null);
    }

    /** Up-votes with the post's own up-vote button (so the screen shows it) when "up-vote on download" is on. */
    static void upvoteOnScreen(BaseActivity activity, Post post, @Nullable View upvoteButton) {
        if (upvoteButton == null || post.getVoteType() == 1) return;
        if (!prefs(activity).getBoolean(Mod69Prefs.AUTO_UPVOTE_ON_DOWNLOAD, false)) return;
        if (Account.ANONYMOUS_ACCOUNT.equals(activity.accountName)) return;
        Mod69Hooks.markUpvotedOnScreen(post.getFullName());
        upvoteButton.performClick();
    }

    public static void bindRow(@Nullable Mod69Ui.ActionRow row, BaseActivity activity, Retrofit oauthRetrofit,
                               Post post, int position, View itemView, @Nullable View upvoteButton,
                               @Nullable View downvoteButton) {
        bindRow(row, activity, oauthRetrofit, post, position, itemView, upvoteButton, downvoteButton, null, null);
    }

    /** Posts blocked just now: they show an "Unblock" bar instead of vanishing. */
    private static final Set<String> justBlocked = Collections.synchronizedSet(new HashSet<>());

    public static void bindRow(@Nullable Mod69Ui.ActionRow row, BaseActivity activity, Retrofit oauthRetrofit,
                               Post post, int position, View itemView, @Nullable View upvoteButton,
                               @Nullable View downvoteButton, @Nullable TextView scoreView, @Nullable View commentsButton) {
        // A recycled view may still show another post's "Unblock" bar.
        Mod69Ui.setBlockedBar(itemView, null, null);
        if (row == null) return;
        Mod69Ui.bindVotes(activity, row, upvoteButton, downvoteButton, post::getVoteType,
                ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils.ENABLE_SWIPE_ACTION, scoreView);
        boolean show = showButtons(activity);
        row.row.setVisibility(show ? View.VISIBLE : View.GONE);
        if (commentsButton != null) commentsButton.setVisibility(View.VISIBLE);
        Mod69Ui.arrangeToolbar(upvoteButton, scoreView, downvoteButton, commentsButton);
        row.download.setVisibility(canDownload(post) ? View.VISIBLE : View.GONE);
        boolean downloaded = showMark(activity) && Mod69Hooks.isDownloaded(activity, post.getFullName());
        row.setDownloaded(downloaded);
        row.track(post.getFullName());
        boolean album = post.getPostType() == Post.GALLERY_TYPE && post.getGallery() != null && post.getGallery().size() > 1;
        // one Download button: tap = what is on screen, hold = choose
        row.select.setVisibility(View.GONE);
        row.download.setOnClickListener(v -> {
            upvoteOnScreen(activity, post, upvoteButton);
            row.refreshVotesSoon();
            if (album) {
                downloadPost(activity, post, currentGalleryIndex(itemView));
            } else {
                download(activity, post);
            }
        });
        row.download.setOnLongClickListener(v -> {
            showDownloadMenu(activity, v, post, upvoteButton, row);
            return true;
        });
        // 69 mod: hidden / blocked posts become a slim bar with UNHIDE / UNBLOCK right where they were
        row.hide.setOnClickListener(v -> hide(activity, oauthRetrofit, post, position,
                () -> {
                    justHidden.add(post.getFullName());
                    showUnhideBar(activity, oauthRetrofit, post, itemView);
                }));
        row.block.setOnClickListener(v -> block(activity, oauthRetrofit, post, () -> {
            justBlocked.add(post.getFullName());
            showUnblockBar(activity, oauthRetrofit, post, itemView);
        }, () -> {
            justBlocked.remove(post.getFullName());
            Mod69Ui.setBlockedBar(itemView, null, null);
        }));
        if (justBlocked.contains(post.getFullName()) && isAuthorBlocked(post.getAuthor())) {
            showUnblockBar(activity, oauthRetrofit, post, itemView);
        } else if (justHidden.contains(post.getFullName()) && hiddenThisSession.contains(post.getFullName())) {
            showUnhideBar(activity, oauthRetrofit, post, itemView);
        } else if (position >= 0) { // -1 = the post's own page: never folded away
            Mod69Ui.setCollapsed(itemView, shouldCollapse(post));
        }
    }

    private static final Set<String> justHidden = Collections.synchronizedSet(new HashSet<>());

    private static void showUnhideBar(BaseActivity activity, Retrofit oauthRetrofit, Post post, View itemView) {
        String t = post.getTitle() == null ? "" : post.getTitle();
        if (t.length() > 40) t = t.substring(0, 40) + "…";
        Mod69Ui.setBlockedBar(itemView, "Hidden: " + t, "UNHIDE", () -> {
            justHidden.remove(post.getFullName());
            hiddenThisSession.remove(post.getFullName());
            post.setHidden(false);
            Mod69Ui.setBlockedBar(itemView, null, null);
            if (!Account.ANONYMOUS_ACCOUNT.equals(activity.accountName)) {
                HidePost.unhidePost(oauthRetrofit, activity.accessToken, post.getFullName(), new HidePost.HidePostListener() {
                    @Override
                    public void success() {
                        Toast.makeText(activity, "Shown again", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void failed() {
                        Toast.makeText(activity, "Shown again here. Unhiding on Reddit failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private static void showUnblockBar(BaseActivity activity, Retrofit oauthRetrofit, Post post, View itemView) {
        String author = post.getAuthor();
        Mod69Ui.setBlockedBar(itemView, "Blocked u/" + author, () -> {
            justBlocked.remove(post.getFullName());
            Mod69Ui.setBlockedBar(itemView, null, null);
            unblockHere(activity, oauthRetrofit, author);
        });
    }

    /** Shows the user's posts/comments again and unblocks them on Reddit. */
    public static void unblockHere(BaseActivity activity, Retrofit oauthRetrofit, @Nullable String author) {
        if (author == null) return;
        blockedAuthors.remove(author.toLowerCase(Locale.ROOT));
        if (Account.ANONYMOUS_ACCOUNT.equals(activity.accountName)) return;
        ml.docilealligator.infinityforreddit.user.BlockUser.unblockUser(oauthRetrofit, activity.accessToken, author,
                new ml.docilealligator.infinityforreddit.user.BlockUser.BlockUserListener() {
                    @Override
                    public void success() {
                        Toast.makeText(activity, "Unblocked u/" + author, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void failed() {
                        Toast.makeText(activity, "Shown again here. Unblocking on Reddit failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** The gallery page shown in the feed right now (0 when unknown). */
    static int currentGalleryIndex(View itemView) {
        androidx.recyclerview.widget.RecyclerView rv = findHorizontalList(itemView, 0);
        if (rv != null && rv.getLayoutManager() instanceof androidx.recyclerview.widget.LinearLayoutManager) {
            int p = ((androidx.recyclerview.widget.LinearLayoutManager) rv.getLayoutManager()).findFirstCompletelyVisibleItemPosition();
            if (p < 0) p = ((androidx.recyclerview.widget.LinearLayoutManager) rv.getLayoutManager()).findFirstVisibleItemPosition();
            return Math.max(0, p);
        }
        return 0;
    }

    @Nullable
    private static androidx.recyclerview.widget.RecyclerView findHorizontalList(View v, int depth) {
        if (depth > 10) return null;
        if (v instanceof androidx.recyclerview.widget.RecyclerView) {
            androidx.recyclerview.widget.RecyclerView.LayoutManager lm = ((androidx.recyclerview.widget.RecyclerView) v).getLayoutManager();
            if (lm instanceof androidx.recyclerview.widget.LinearLayoutManager
                    && ((androidx.recyclerview.widget.LinearLayoutManager) lm).getOrientation() == androidx.recyclerview.widget.RecyclerView.HORIZONTAL) {
                return (androidx.recyclerview.widget.RecyclerView) v;
            }
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                androidx.recyclerview.widget.RecyclerView r = findHorizontalList(g.getChildAt(i), depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** Hold Download: choose images, the whole album, or a quality. */
    static void showDownloadMenu(BaseActivity activity, View anchor, Post post, @Nullable View upvoteButton,
                                 Mod69Ui.ActionRow row) {
        boolean album = post.getPostType() == Post.GALLERY_TYPE && post.getGallery() != null && post.getGallery().size() > 1;
        android.widget.PopupMenu menu = new android.widget.PopupMenu(activity, anchor);
        if (album) {
            menu.getMenu().add(0, 10, 0, R.string.mod69_select_images);
            menu.getMenu().add(0, 11, 1, "Whole album (" + post.getGallery().size() + ")");
        }
        menu.getMenu().add(0, 1, 2, R.string.mod69_download_original);
        menu.getMenu().add(0, 2, 3, R.string.mod69_download_low);
        menu.setOnMenuItemClickListener(item -> {
            upvoteOnScreen(activity, post, upvoteButton);
            row.refreshVotesSoon();
            int id = item.getItemId();
            if (id == 10) {
                showGallerySelect(activity, post);
            } else if (id == 11) {
                Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show();
                schedule(activity, DownloadMediaService.constructGalleryDownloadAllMediaJobInfo(activity, 5000000L * post.getGallery().size(), post));
            } else {
                String q = id == 1 ? "high" : "low";
                if (album) {
                    sQuality = q;
                    try {
                        Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show();
                        schedule(activity, DownloadMediaService.constructGalleryDownloadAllMediaJobInfo(activity, 5000000L * post.getGallery().size(), post));
                    } finally {
                        sQuality = null;
                    }
                } else {
                    downloadPostWithQuality(activity, post, 0, q);
                }
            }
            return true;
        });
        menu.show();
    }

    /** A tiny "Downloaded" label for layouts without a toolbar. */
    public static void setMark(@Nullable TextView mark, boolean visible) {
        if (mark != null) mark.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}

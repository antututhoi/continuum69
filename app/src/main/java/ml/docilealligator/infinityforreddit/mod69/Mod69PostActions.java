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

    public static boolean isAuthorBlocked(@Nullable String author) {
        return author != null && blockedAuthors.contains(author.toLowerCase(Locale.ROOT));
    }

    public static boolean canDownload(Post post) {
        if (post == null) return false;
        int t = post.getPostType();
        return t == Post.IMAGE_TYPE || t == Post.GIF_TYPE || t == Post.VIDEO_TYPE
                || (t == Post.GALLERY_TYPE && post.getGallery() != null && !post.getGallery().isEmpty());
    }

    private static void schedule(Context c, JobInfo jobInfo) {
        ((JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).schedule(jobInfo);
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
            Glide.with(activity).load(g.url).into(iv);
            CheckBox cb = new CheckBox(activity);
            String type = g.mediaType == Post.Gallery.TYPE_VIDEO ? "video" : g.mediaType == Post.Gallery.TYPE_GIF ? "GIF" : "image";
            cb.setText("  " + (i + 1) + " (" + type + ")");
            cb.setChecked(true);
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
            }

            @Override
            public void onVoteThingFail() {
            }
        }, post.getFullName(), APIUtils.DIR_DOWNVOTE);
    }

    /** Blocks the post's author (Reddit block) and folds away their posts here. */
    public static void block(BaseActivity activity, Retrofit oauthRetrofit, Post post, @Nullable Runnable onBlocked) {
        blockUser(activity, oauthRetrofit, post.getAuthor(), onBlocked);
    }

    public static void blockUser(BaseActivity activity, Retrofit oauthRetrofit, @Nullable String author, @Nullable Runnable onBlocked) {
        if (author == null || author.isEmpty() || "[deleted]".equals(author)) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.mod69_block_user)
                .setMessage("u/" + author)
                .setPositiveButton(R.string.mod69_block, (d, w) -> {
                    blockedAuthors.add(author.toLowerCase(Locale.ROOT));
                    if (onBlocked != null) onBlocked.run();
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
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Sets up the row's buttons for one post. */
    public static void bindRow(@Nullable Mod69Ui.ActionRow row, BaseActivity activity, Retrofit oauthRetrofit,
                               Post post, int position, View itemView) {
        if (row == null) return;
        boolean show = showButtons(activity);
        row.row.setVisibility(show ? View.VISIBLE : View.GONE);
        row.download.setVisibility(canDownload(post) ? View.VISIBLE : View.GONE);
        boolean downloaded = showMark(activity) && Mod69Hooks.isDownloaded(activity, post.getFullName());
        row.mark.setVisibility(downloaded ? View.VISIBLE : View.GONE);
        if (downloaded && !show) row.row.setVisibility(View.VISIBLE);
        row.download.setOnClickListener(v -> download(activity, post));
        row.hide.setOnClickListener(v -> hide(activity, oauthRetrofit, post, position,
                () -> Mod69Ui.setCollapsed(itemView, true)));
        row.block.setOnClickListener(v -> block(activity, oauthRetrofit, post,
                () -> Mod69Ui.setCollapsed(itemView, true)));
        Mod69Ui.setCollapsed(itemView, shouldCollapse(post));
    }

    /** A tiny "Downloaded" label for layouts without a toolbar. */
    public static void setMark(@Nullable TextView mark, boolean visible) {
        if (mark != null) mark.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}

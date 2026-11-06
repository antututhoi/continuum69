package ml.docilealligator.infinityforreddit.mod69;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import ml.docilealligator.infinityforreddit.R;

/** Small view helpers: the Download / Hide / Block row and hiding list items in place. */
public final class Mod69Ui {
    private Mod69Ui() {}

    private static final java.util.WeakHashMap<ActionRow, String> LIVE = new java.util.WeakHashMap<>();
    private static boolean listening;

    /** The row of icon buttons under a post or comment, spread evenly over the full width. */
    public static final class ActionRow {
        public final LinearLayout row;
        public final ImageView download;
        public final ImageView select;
        public final ImageView hide;
        public final ImageView block;
        public final ImageView up;
        public final ImageView down;
        /** 69 mod: the score between the vote arrows, and the comments button + count on the left. */
        public final TextView score;
        public final TextView comments;
        /** Re-colours the vote arrows (after a vote made elsewhere, e.g. auto up-vote). */
        @Nullable Runnable refreshVotes;
        final int iconColor;

        ActionRow(LinearLayout row, ImageView download, ImageView select, ImageView hide, ImageView block,
                  ImageView up, ImageView down, TextView score, TextView comments, int iconColor) {
            this.row = row;
            this.score = score;
            this.comments = comments;
            this.up = up;
            this.down = down;
            this.download = download;
            this.select = select;
            this.hide = hide;
            this.block = block;
            this.iconColor = iconColor;
        }

        /** Which post/comment this row shows now, so the orange mark can update the moment a download finishes. */
        public void track(@Nullable String key) {
            synchronized (LIVE) {
                if (key == null) LIVE.remove(this); else LIVE.put(this, key);
                if (!listening) {
                    listening = true;
                    Mod69Events.add(() -> {
                        java.util.List<java.util.Map.Entry<ActionRow, String>> rows;
                        synchronized (LIVE) {
                            rows = new java.util.ArrayList<>(LIVE.entrySet());
                        }
                        for (java.util.Map.Entry<ActionRow, String> e : rows) {
                            ActionRow r = e.getKey();
                            if (r != null) r.setDownloaded(Mod69PostActions.showMark(r.row.getContext())
                                    && Mod69Hooks.isDownloaded(r.row.getContext(), e.getValue()));
                        }
                    });
                }
            }
        }

        /** Downloaded: orange (Reddit orange) background with a black icon. Otherwise plain. */
        public void refreshVotesSoon() {
            Runnable r = refreshVotes;
            if (r == null) return;
            row.post(r);
            row.postDelayed(r, 600);
            row.postDelayed(r, 2000);
        }

        public void setDownloaded(boolean downloaded) {
            Context c = download.getContext();
            if (downloaded) {
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(0xFFFF4500);
                bg.setCornerRadius(dp(c, 18));
                download.setBackground(bg);
                download.setColorFilter(0xFF000000, PorterDuff.Mode.SRC_IN);
            } else {
                download.setBackground(selectable(c));
                download.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN);
            }
        }
    }

    private static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    private static android.graphics.drawable.Drawable selectable(Context c) {
        TypedArray a = c.obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackgroundBorderless});
        android.graphics.drawable.Drawable d = a.getDrawable(0);
        a.recycle();
        return d;
    }

    private static ImageView icon(Context c, int drawableRes, int descRes, int color) {
        ImageView v = new ImageView(c);
        v.setImageResource(drawableRes);
        v.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        v.setScaleType(ImageView.ScaleType.CENTER);
        v.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8));
        v.setBackground(selectable(c));
        v.setContentDescription(c.getString(descRes));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    private static void addEven(LinearLayout row, ImageView v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(row.getContext(), 40), 1f);
        lp.setMargins(dp(row.getContext(), 6), dp(row.getContext(), 2), dp(row.getContext(), 6), dp(row.getContext(), 2));
        row.addView(v, lp);
    }

    /**
     * Adds the row once, right under the toolbar that holds [anchor] (e.g. the share button).
     * The toolbar is wrapped in a vertical layout that takes its place, so the rest of the item is untouched.
     */
    @Nullable
    public static ActionRow attachRow(@Nullable View anchor, int textColor) {
        if (anchor == null) return null;
        ViewParent p = anchor.getParent();
        if (!(p instanceof ViewGroup)) return null;
        ViewGroup toolbar = (ViewGroup) p;
        Object cached = toolbar.getTag(R.id.mod69_action_row);
        if (cached instanceof ActionRow) return (ActionRow) cached;

        ViewParent gp = toolbar.getParent();
        if (!(gp instanceof ViewGroup)) return null;
        ViewGroup container = (ViewGroup) gp;
        Context c = toolbar.getContext();

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView download = icon(c, R.drawable.mod69_ic_download, R.string.mod69_download, textColor);
        ImageView select = icon(c, R.drawable.mod69_ic_select, R.string.mod69_select_images, textColor);
        ImageView hide = icon(c, R.drawable.mod69_ic_hide, R.string.mod69_hide, textColor);
        ImageView block = icon(c, R.drawable.mod69_ic_block, R.string.mod69_block, textColor);
        ImageView up = icon(c, R.drawable.mod69_ic_up, R.string.upvote, textColor);
        ImageView down = icon(c, R.drawable.mod69_ic_down, R.string.downvote, textColor);
        select.setVisibility(View.GONE);
        TextView score = text(c, textColor);
        TextView comments = text(c, textColor);
        comments.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_comment_grey_24dp, 0, 0, 0);
        comments.setCompoundDrawablePadding(dp(c, 4));
        if (comments.getCompoundDrawablesRelative()[0] != null) {
            comments.getCompoundDrawablesRelative()[0].mutate().setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
        }
        score.setVisibility(View.GONE);
        comments.setVisibility(View.GONE);
        // 69 mod: second row = Hide (left), Download (middle), Block (right), even gaps.
        // The votes and comments stay in the app's own first row (see arrangeToolbar).
        up.setVisibility(View.GONE);
        down.setVisibility(View.GONE);
        addEven(row, hide);
        addEven(row, select);
        addEven(row, download);
        addEven(row, block);
        int index = container.indexOfChild(toolbar);
        ViewGroup.LayoutParams lp = toolbar.getLayoutParams();
        LinearLayout wrapper = new LinearLayout(c);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        // the wrapper takes the toolbar's id so the item's other views keep their positions
        int toolbarId = toolbar.getId();
        container.removeViewAt(index);
        if (toolbarId != View.NO_ID) {
            toolbar.setId(View.generateViewId());
            wrapper.setId(toolbarId);
        }
        wrapper.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                lp != null ? lp.height : ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(wrapper, index, lp);

        ActionRow ar = new ActionRow(row, download, select, hide, block, up, down, score, comments, textColor);
        toolbar.setTag(R.id.mod69_action_row, ar);
        return ar;
    }

    private static TextView text(Context c, int color) {
        TextView t = new TextView(c);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setMinHeight(dp(c, 40));
        t.setPadding(dp(c, 4), 0, dp(c, 4), 0);
        t.setBackground(selectable(c));
        t.setMaxLines(1);
        return t;
    }

    private static void addEvenView(LinearLayout row, View v) {
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    /**
     * 69 mod: right after blocking, the item becomes a slim "Blocked u/x · UNBLOCK" bar instead of
     * disappearing. [onUnblock] null removes the bar again.
     */
    public static void setBlockedBar(View itemView, @Nullable String text, @Nullable Runnable onUnblock) {
        setBlockedBar(itemView, text, "UNBLOCK", onUnblock);
    }

    public static void setBlockedBar(View itemView, @Nullable String text, String buttonText, @Nullable Runnable onUnblock) {
        if (!(itemView instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) itemView;
        Object old = g.getTag(R.id.mod69_blocked_bar);
        if (text == null) {
            if (old instanceof View) {
                g.removeView((View) old);
                g.setTag(R.id.mod69_blocked_bar, null);
                for (int i = 0; i < g.getChildCount(); i++) {
                    View ch = g.getChildAt(i);
                    Object v = ch.getTag(R.id.mod69_saved_visibility);
                    if (v instanceof Integer) {
                        ch.setVisibility((Integer) v);
                        ch.setTag(R.id.mod69_saved_visibility, null);
                    }
                }
            }
            return;
        }
        setCollapsed(itemView, false);
        if (!(old instanceof View)) {
            for (int i = 0; i < g.getChildCount(); i++) {
                View ch = g.getChildAt(i);
                ch.setTag(R.id.mod69_saved_visibility, ch.getVisibility());
                ch.setVisibility(View.GONE);
            }
        }
        Context c = g.getContext();
        LinearLayout bar = old instanceof LinearLayout ? (LinearLayout) old : new LinearLayout(c);
        bar.removeAllViews();
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(c, 16), dp(c, 6), dp(c, 8), dp(c, 6));
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(0xFF9E9E9E);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView un = new TextView(c);
        un.setText(buttonText);
        un.setTextColor(0xFFFF4500);
        un.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        un.setBackground(selectable(c));
        un.setOnClickListener(v -> { if (onUnblock != null) onUnblock.run(); });
        bar.addView(un);
        if (!(old instanceof View)) {
            ViewGroup.LayoutParams lp = g instanceof android.widget.FrameLayout
                    ? new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    : new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            g.addView(bar, lp);
            g.setTag(R.id.mod69_blocked_bar, bar);
        }
        bar.setVisibility(View.VISIBLE);
    }

    /**
     * 69 mod: a "Show spoiler" button in the middle of a blurred picture. [onClick] null removes it.
     * Works when the picture sits in a FrameLayout or ConstraintLayout (the post layouts do).
     */
    public static void setSpoilerButton(@Nullable View media, @Nullable Runnable onClick) {
        if (media == null || !(media.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) media.getParent();
        Object old = media.getTag(R.id.mod69_spoiler_button);
        if (onClick == null) {
            if (old instanceof View) ((View) old).setVisibility(View.GONE);
            return;
        }
        TextView b;
        if (old instanceof TextView) {
            b = (TextView) old;
        } else {
            Context c = media.getContext();
            b = new TextView(c);
            b.setText(R.string.mod69_spoiler_show);
            b.setTextColor(0xFFFFFFFF);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            b.setPadding(dp(c, 18), dp(c, 10), dp(c, 18), dp(c, 10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xCC000000);
            bg.setCornerRadius(dp(c, 22));
            b.setBackground(bg);
            b.setElevation(dp(c, 4));
            if (parent instanceof android.widget.FrameLayout) {
                parent.addView(b, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            } else if (parent instanceof androidx.constraintlayout.widget.ConstraintLayout && media.getId() != View.NO_ID) {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                        new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.startToStart = media.getId();
                lp.endToEnd = media.getId();
                lp.topToTop = media.getId();
                lp.bottomToBottom = media.getId();
                parent.addView(b, lp);
            } else {
                return;
            }
            media.setTag(R.id.mod69_spoiler_button, b);
        }
        b.setVisibility(View.VISIBLE);
        b.bringToFront();
        b.setOnClickListener(v -> onClick.run());
    }

    /** Hides a list item in place (height 0), or shows it again. */
    public static void setCollapsed(View itemView, boolean collapsed) {
        ViewGroup.LayoutParams lp = itemView.getLayoutParams();
        if (lp == null) return;
        Object saved = itemView.getTag(R.id.mod69_saved_layout);
        if (collapsed) {
            if (!(saved instanceof int[])) {
                int[] keep = new int[]{lp.height, 0, 0, 0, 0};
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams m = (ViewGroup.MarginLayoutParams) lp;
                    keep[1] = m.leftMargin; keep[2] = m.topMargin; keep[3] = m.rightMargin; keep[4] = m.bottomMargin;
                    m.setMargins(0, 0, 0, 0);
                }
                itemView.setTag(R.id.mod69_saved_layout, keep);
            }
            lp.height = 0;
            itemView.setVisibility(View.GONE);
        } else if (saved instanceof int[]) {
            int[] keep = (int[]) saved;
            lp.height = keep[0];
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).setMargins(keep[1], keep[2], keep[3], keep[4]);
            }
            itemView.setTag(R.id.mod69_saved_layout, null);
            itemView.setVisibility(View.VISIBLE);
        } else {
            return;
        }
        itemView.setLayoutParams(lp);
    }

    /** Finds the list item view that holds [v]. */
    @Nullable
    public static View findItemView(View v) {
        View cur = v;
        while (cur != null) {
            ViewParent p = cur.getParent();
            if (p instanceof RecyclerView && cur.getTag(R.id.mod69_tag_comment) != null) return cur;
            if (cur.getTag(R.id.mod69_tag_comment) != null) return cur;
            cur = p instanceof View ? (View) p : null;
        }
        return null;
    }

    /** Shows a dialog with readable button text in dark themes (the comment icon/info colour). */
    public static androidx.appcompat.app.AlertDialog show(ml.docilealligator.infinityforreddit.activities.BaseActivity activity,
                                                         com.google.android.material.dialog.MaterialAlertDialogBuilder builder) {
        androidx.appcompat.app.AlertDialog d = builder.create();
        d.show();
        styleButtons(activity, d);
        return d;
    }

    public static void styleButtons(ml.docilealligator.infinityforreddit.activities.BaseActivity activity,
                                    androidx.appcompat.app.AlertDialog d) {
        int color;
        try {
            color = activity.customThemeWrapper.getCommentIconAndInfoColor();
        } catch (Exception e) {
            return;
        }
        for (int which : new int[]{android.content.DialogInterface.BUTTON_POSITIVE,
                android.content.DialogInterface.BUTTON_NEGATIVE, android.content.DialogInterface.BUTTON_NEUTRAL}) {
            android.widget.Button b = d.getButton(which);
            if (b != null) b.setTextColor(color);
        }
    }

    /**
     * The row's own up/down-vote buttons: they press the app's real vote buttons (so everything works
     * the same) and show the vote colour.
     */
    public static void bindVotes(ml.docilealligator.infinityforreddit.activities.BaseActivity activity, ActionRow row,
                                 @Nullable View realUp, @Nullable View realDown, java.util.function.IntSupplier voteType) {
        bindVotes(activity, row, realUp, realDown, voteType, null, null);
    }

    /**
     * @param swipeKey the "swipe actions on" setting for this list: when swipe voting is on, the
     *                 arrows are left out (setting "Hide vote buttons when swiping votes", default on)
     * @param realScore the app's own score text, mirrored between the arrows
     */
    public static void bindVotes(ml.docilealligator.infinityforreddit.activities.BaseActivity activity, ActionRow row,
                                 @Nullable View realUp, @Nullable View realDown, java.util.function.IntSupplier voteType,
                                 @Nullable String swipeKey, @Nullable TextView realScore) {
        // the app's own vote buttons and score are used as they are (they keep their colours after
        // swipe votes and after coming back from a post); only their place changes
        row.up.setVisibility(View.GONE);
        row.down.setVisibility(View.GONE);
        row.score.setVisibility(View.GONE);
        row.comments.setVisibility(View.GONE);
        row.refreshVotes = null;
        android.content.SharedPreferences sp = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity);
        boolean hideArrows = swipeKey != null && sp.getBoolean(swipeKey, false)
                && sp.getBoolean(Mod69Prefs.HIDE_VOTES_WHEN_SWIPING, true);
        if (realUp != null) realUp.setVisibility(hideArrows ? View.GONE : View.VISIBLE);
        if (realDown != null) realDown.setVisibility(hideArrows ? View.GONE : View.VISIBLE);
    }

    /**
     * 69 mod: first row of a post = comments button + count on the left, vote count in the middle
     * (with the arrows next to it when they are shown), the app's other buttons on the right.
     * For comments only the vote count is moved to the middle. Done once per toolbar.
     */
    public static void arrangeToolbar(@Nullable View up, @Nullable TextView score, @Nullable View down, @Nullable View comments) {
        if (score == null || !(score.getParent() instanceof androidx.constraintlayout.widget.ConstraintLayout)) return;
        androidx.constraintlayout.widget.ConstraintLayout bar = (androidx.constraintlayout.widget.ConstraintLayout) score.getParent();
        if (Boolean.TRUE.equals(bar.getTag(R.id.mod69_saved_layout))) return;
        if (score.getId() == View.NO_ID) return;
        if (up != null && (up.getParent() != bar || up.getId() == View.NO_ID)) return;
        if (down != null && (down.getParent() != bar || down.getId() == View.NO_ID)) return;
        if (comments != null && (comments.getParent() != bar || comments.getId() == View.NO_ID)) comments = null;
        androidx.constraintlayout.widget.ConstraintSet cs = new androidx.constraintlayout.widget.ConstraintSet();
        cs.clone(bar);
        int P = androidx.constraintlayout.widget.ConstraintSet.PARENT_ID;
        int S = androidx.constraintlayout.widget.ConstraintSet.START, E = androidx.constraintlayout.widget.ConstraintSet.END;
        cs.clear(score.getId(), S);
        cs.clear(score.getId(), E);
        cs.connect(score.getId(), S, P, S);
        cs.connect(score.getId(), E, P, E);
        cs.setHorizontalBias(score.getId(), 0.5f);
        if (up != null) {
            cs.clear(up.getId(), S);
            cs.clear(up.getId(), E);
            cs.connect(up.getId(), E, score.getId(), S);
        }
        if (down != null) {
            cs.clear(down.getId(), S);
            cs.clear(down.getId(), E);
            cs.connect(down.getId(), S, score.getId(), E);
        }
        if (comments != null) {
            cs.clear(comments.getId(), S);
            cs.connect(comments.getId(), S, P, S);
        }
        cs.applyTo(bar);
        bar.setTag(R.id.mod69_saved_layout, Boolean.TRUE);
    }
}

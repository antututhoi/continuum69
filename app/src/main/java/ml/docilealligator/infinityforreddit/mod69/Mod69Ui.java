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
        final int iconColor;

        ActionRow(LinearLayout row, ImageView download, ImageView select, ImageView hide, ImageView block, int iconColor) {
            this.row = row;
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
        select.setVisibility(View.GONE);
        addEven(row, download);
        addEven(row, select);
        addEven(row, hide);
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

        ActionRow ar = new ActionRow(row, download, select, hide, block, textColor);
        toolbar.setTag(R.id.mod69_action_row, ar);
        return ar;
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
}

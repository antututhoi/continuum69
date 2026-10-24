package ml.docilealligator.infinityforreddit.mod69;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import ml.docilealligator.infinityforreddit.R;

/** Small view helpers: the Download / Hide / Block row and hiding list items in place. */
public final class Mod69Ui {
    private Mod69Ui() {}

    /** The row of mod buttons under a post or comment. */
    public static final class ActionRow {
        public final LinearLayout row;
        public final TextView download;
        public final TextView hide;
        public final TextView block;
        public final TextView mark;

        ActionRow(LinearLayout row, TextView download, TextView hide, TextView block, TextView mark) {
            this.row = row;
            this.download = download;
            this.hide = hide;
            this.block = block;
            this.mark = mark;
        }
    }

    private static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    private static TextView button(Context c, int textRes, int color) {
        TextView t = new TextView(c);
        t.setText(textRes);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        TypedArray a = c.obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
        t.setBackground(a.getDrawable(0));
        a.recycle();
        t.setClickable(true);
        t.setFocusable(true);
        return t;
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
        TextView download = button(c, R.string.mod69_download, textColor);
        TextView hide = button(c, R.string.mod69_hide, textColor);
        TextView block = button(c, R.string.mod69_block, textColor);
        TextView mark = new TextView(c);
        mark.setText("✓ " + c.getString(R.string.mod69_downloaded));
        mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mark.setTextColor(0xFF4CAF50);
        mark.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        mark.setVisibility(View.GONE);
        row.addView(download);
        row.addView(hide);
        row.addView(block);
        View spacer = new View(c);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(mark);

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

        ActionRow ar = new ActionRow(row, download, hide, block, mark);
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

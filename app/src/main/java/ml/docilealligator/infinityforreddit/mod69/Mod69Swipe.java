package ml.docilealligator.infinityforreddit.mod69;

import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import ml.docilealligator.infinityforreddit.customviews.SwipeActionPainter;
import ml.docilealligator.infinityforreddit.utils.SwipeActionLevels;

/**
 * 69 mod: makes swipe actions fire reliably. Continuum 8.3's swipe remembers the action while you drag
 * and runs it when you let go, but that memory gets cleared before the release on some phones, so the
 * colour shows and nothing happens. This keeps its own copy of "the action under the finger" and uses it
 * when Continuum's copy comes back empty.
 */
public final class Mod69Swipe {
    private int last = SwipeActionLevels.NONE;
    @Nullable private RecyclerView.ViewHolder lastHolder;
    private boolean told;

    /** Call on every frame while the finger is down. */
    public void onActive(SwipeActionPainter painter, RecyclerView.ViewHolder holder, float dX) {
        View v = holder.itemView;
        SwipeActionLevels levels = painter.getLevels();
        int level = levels.levelFor(dX, v.getRight() - v.getLeft());
        last = levels.actionFor(dX, level);
        lastHolder = holder;
    }

    /** Call on frames after the finger is lifted; returns the action to run (once) or NONE. */
    public int onRelease(SwipeActionPainter painter, RecyclerView.ViewHolder holder) {
        int action = painter.consumeAction();
        if (action == SwipeActionLevels.NONE && last != SwipeActionLevels.NONE && holder == lastHolder) {
            action = last;
            if (!told) {
                told = true;
                Toast.makeText(holder.itemView.getContext(), "69: swipe fixed", Toast.LENGTH_SHORT).show();
            }
        }
        last = SwipeActionLevels.NONE;
        lastHolder = null;
        return action;
    }
}

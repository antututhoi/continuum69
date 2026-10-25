package ml.docilealligator.infinityforreddit.mod69;

import android.content.Context;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import ml.docilealligator.infinityforreddit.R;

/**
 * 69 mod: finished-download notification with the full details AND a big picture. Android's own
 * picture style cuts the details to one line, so the expanded notification uses its own small layout.
 */
public final class Mod69Notify {
    private Mod69Notify() {}

    public static void apply(Context c, NotificationCompat.Builder builder, @Nullable CharSequence title,
                             CharSequence text, @Nullable Bitmap picture) {
        builder.setContentText(text);
        if (picture == null) {
            builder.setCustomBigContentView(null);
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(text));
            return;
        }
        RemoteViews big = new RemoteViews(c.getPackageName(), R.layout.mod69_notification_big);
        big.setTextViewText(R.id.mod69_notif_title, title == null ? "" : title);
        big.setTextViewText(R.id.mod69_notif_text, text);
        big.setImageViewBitmap(R.id.mod69_notif_image, picture);
        builder.setLargeIcon(picture);
        builder.setStyle(new NotificationCompat.DecoratedCustomViewStyle());
        builder.setCustomBigContentView(big);
    }
}

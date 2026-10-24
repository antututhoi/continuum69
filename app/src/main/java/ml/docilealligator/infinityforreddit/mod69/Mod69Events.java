package ml.docilealligator.infinityforreddit.mod69;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

/** Lets open screens refresh their "downloaded" marks when a download finishes. */
public final class Mod69Events {
    public interface Listener {
        void onMarksChanged();
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static final Handler main = new Handler(Looper.getMainLooper());

    private Mod69Events() {}

    public static void add(Listener l) {
        listeners.addIfAbsent(l);
    }

    public static void remove(Listener l) {
        listeners.remove(l);
    }

    static void notifyMarksChanged() {
        main.post(() -> {
            for (Listener l : listeners) l.onMarksChanged();
        });
    }
}

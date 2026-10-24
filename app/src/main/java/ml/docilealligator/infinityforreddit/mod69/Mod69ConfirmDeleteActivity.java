package ml.docilealligator.infinityforreddit.mod69;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.ArrayList;

/**
 * Opened by the "Delete" button of a download notification: asks "Are you sure?" first,
 * then deletes the file (or every file of an album) and forgets the downloaded mark so it can be downloaded again.
 */
public class Mod69ConfirmDeleteActivity extends Activity {
    public static final String EXTRA_URIS = "mod69_uris";
    public static final String EXTRA_NOTIFICATION_ID = "mod69_notification_id";

    public static Intent intent(Context c, ArrayList<String> uris, int notificationId) {
        return new Intent(c, Mod69ConfirmDeleteActivity.class)
                .putStringArrayListExtra(EXTRA_URIS, uris)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ArrayList<String> uris = getIntent().getStringArrayListExtra(EXTRA_URIS);
        if (uris == null || uris.isEmpty()) {
            finish();
            return;
        }
        int n = uris.size();
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(n == 1 ? "Delete this file?" : "Delete all " + n + " files of this album?")
                .setMessage("This can't be undone. You can download it again afterwards.")
                .setPositiveButton("Delete", (d, w) -> {
                    int deleted = 0;
                    for (String u : uris) if (delete(this, Uri.parse(u))) deleted++;
                    NotificationManager m = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                    if (m != null) m.cancel(getIntent().getIntExtra(EXTRA_NOTIFICATION_ID, -1));
                    Toast.makeText(this, deleted + " deleted", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, (d, w) -> finish())
                .setOnCancelListener(d -> finish())
                .show();
    }

    static boolean delete(Context c, Uri uri) {
        boolean ok = false;
        try {
            ok = c.getContentResolver().delete(uri, null, null) > 0;
        } catch (Exception e) {
            try {
                DocumentFile f = DocumentFile.fromSingleUri(c, uri);
                ok = f != null && f.delete();
            } catch (Exception ignored) {
            }
            if (!ok) ok = new File(uri.getPath() == null ? uri.toString() : uri.getPath()).delete();
        }
        if (!ok) ok = !Mod69Hooks.exists(c, uri);
        if (ok) Mod69Hooks.forgetFile(c, uri);
        return ok;
    }
}

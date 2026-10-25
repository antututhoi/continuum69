package ml.docilealligator.infinityforreddit.mod69;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.preference.CustomFontPreferenceFragmentCompat;

/** The "69 mod" settings screen (its own group in Settings). */
public class Mod69PreferenceFragment extends CustomFontPreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.mod69_preferences, rootKey);
        Preference history = findPreference("mod69_split_history");
        if (history != null) {
            String h = Mod69Folders.history(requireContext());
            history.setSummary(h.isEmpty() ? getString(R.string.mod69_split_history_none) : h);
        }
        Preference helper = findPreference("mod69_open_helper");
        if (helper != null) {
            helper.setOnPreferenceClickListener(p -> {
                if (!Mod69HelperClient.openSettings(requireContext())) {
                    Toast.makeText(requireContext(), R.string.mod69_helper_missing, Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }
    }
}

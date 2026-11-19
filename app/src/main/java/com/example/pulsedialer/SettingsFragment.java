package com.example.pulsedialer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.pulsedialer.util.PrefUtil;

/** Full settings screen with categorised rows and real actions. */
public class SettingsFragment extends Fragment {

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, Bundle saved) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle saved) {
        super.onViewCreated(v, saved);

        // Theme
        setupRow(v.findViewById(R.id.setting_theme),
                R.drawable.ic_settings, getString(R.string.theme),
                currentThemeLabel(),
                this::showThemePicker);

        // Accent
        setupRow(v.findViewById(R.id.setting_accent),
                R.drawable.ic_star, getString(R.string.accent_color),
                currentAccentLabel(),
                this::showAccentPicker);

        // Density
        setupRow(v.findViewById(R.id.setting_density),
                R.drawable.ic_contacts, getString(R.string.list_density),
                PrefUtil.compact(requireContext()) ? getString(R.string.density_compact) : getString(R.string.density_comfortable),
                this::toggleDensity);

        // Haptics
        setupRow(v.findViewById(R.id.setting_haptics),
                R.drawable.ic_dialpad, getString(R.string.haptics),
                PrefUtil.haptics(requireContext()) ? getString(R.string.haptics_on) : getString(R.string.haptics_off),
                this::toggleHaptics);

        // Default phone
        setupRow(v.findViewById(R.id.setting_default_phone),
                R.drawable.ic_call_phone, getString(R.string.default_phone),
                MainActivity.isDefaultDialer(requireContext()) ? getString(R.string.default_status) : getString(R.string.make_default),
                () -> ((MainActivity) requireActivity()).requestDefaultDialerRole());

        // Privacy
        setupRow(v.findViewById(R.id.setting_privacy),
                R.drawable.ic_settings, getString(R.string.privacy),
                getString(R.string.privacy).toLowerCase(),
                () -> new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.privacy)
                        .setMessage(R.string.privacy_body)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());

        // About
        setupRow(v.findViewById(R.id.setting_about),
                R.drawable.ic_launcher, getString(R.string.about),
                getString(R.string.app_name) + " v" + getVersion(),
                () -> new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.about)
                        .setMessage(getString(R.string.about_body, getString(R.string.app_name), getVersion()))
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
    }

    private void setupRow(View row, int iconRes, String title, String subtitle, Runnable action) {
        if (row == null) return;
        ImageView icon = row.findViewById(R.id.setting_icon);
        TextView titleView = row.findViewById(R.id.setting_title);
        TextView subtitleView = row.findViewById(R.id.setting_subtitle);
        if (icon != null) {
            icon.setImageResource(iconRes);
            icon.setColorFilter(requireContext().getColor(R.color.pulse_primary));
        }
        if (titleView != null) titleView.setText(title);
        if (subtitleView != null) subtitleView.setText(subtitle);
        row.setOnClickListener(x -> action.run());
    }

    private String currentThemeLabel() {
        String t = PrefUtil.theme(requireContext());
        switch (t) {
            case "light": return getString(R.string.theme_light);
            case "system": return getString(R.string.theme_system);
            default: return getString(R.string.theme_dark);
        }
    }

    private String currentAccentLabel() {
        int a = PrefUtil.accent(requireContext());
        if (a == 0xFF039BE5) return getString(R.string.accent_sky);
        if (a == 0xFF5C6BC0) return getString(R.string.accent_indigo);
        if (a == 0xFF00897B) return getString(R.string.accent_teal);
        return getString(R.string.accent_blue);
    }

    private void showThemePicker() {
        String[] items = {
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        };
        String[] values = {"system", "light", "dark"};
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.theme)
            .setItems(items, (d, w) -> {
                PrefUtil.setTheme(requireContext(), values[w]);
                applyTheme(values[w]);
                refreshSubtitle(R.id.setting_theme, items[w]);
            })
            .show();
    }

    private void showAccentPicker() {
        String[] items = {
            getString(R.string.accent_blue),
            getString(R.string.accent_sky),
            getString(R.string.accent_indigo),
            getString(R.string.accent_teal)
        };
        int[] values = {0xFF1A73E8, 0xFF039BE5, 0xFF5C6BC0, 0xFF00897B};
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.accent_color)
            .setItems(items, (d, w) -> {
                PrefUtil.setAccent(requireContext(), values[w]);
                refreshSubtitle(R.id.setting_accent, items[w]);
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).applyAccent();
                }
            })
            .show();
    }

    private void toggleDensity() {
        boolean current = PrefUtil.compact(requireContext());
        PrefUtil.setCompact(requireContext(), !current);
        refreshSubtitle(R.id.setting_density,
            !current ? getString(R.string.density_compact) : getString(R.string.density_comfortable));
    }

    private void toggleHaptics() {
        boolean current = PrefUtil.haptics(requireContext());
        PrefUtil.setHaptics(requireContext(), !current);
        refreshSubtitle(R.id.setting_haptics,
            !current ? getString(R.string.haptics_on) : getString(R.string.haptics_off));
        Toast.makeText(requireContext(),
            !current ? R.string.haptics_on : R.string.haptics_off,
            Toast.LENGTH_SHORT).show();
    }

    private void applyTheme(String value) {
        switch (value) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "system":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
    }

    private void refreshSubtitle(int rowId, String text) {
        View view = getView();
        if (view == null) return;
        View row = view.findViewById(rowId);
        if (row == null) return;
        TextView sub = row.findViewById(R.id.setting_subtitle);
        if (sub != null) sub.setText(text);
    }

    private String getVersion() {
        try {
            return requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }
}

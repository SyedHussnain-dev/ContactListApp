package com.example.pulsedialer;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.example.pulsedialer.util.PrefUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Main host activity for Pulse Dialer.
 * Manages bottom navigation, permission flows, and theme application.
 */
public class MainActivity extends AppCompatActivity {

    private int selectedTab = R.id.nav_recents;
    private BottomNavigationView nav;
    private MaterialToolbar toolbar;

    private ActivityResultLauncher<String> permissionRequest;
    private ActivityResultLauncher<String[]> multiPermissionRequest;
    private ActivityResultLauncher<Intent> roleRequest;

    // ── Static helpers ─────────────────────────────────────────────────

    public static boolean hasPermission(Context c, String p) {
        return ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isDefaultDialer(Context c) {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager r = c.getSystemService(RoleManager.class);
            return r != null && r.isRoleHeld(RoleManager.ROLE_DIALER);
        }
        TelecomManager t = (TelecomManager) c.getSystemService(TELECOM_SERVICE);
        return t != null && c.getPackageName().equals(t.getDefaultDialerPackage());
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        // Register launchers before any fragment transaction
        permissionRequest = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                }
                // Refresh the current fragment
                Fragment visible = getSupportFragmentManager().findFragmentById(R.id.content);
                if (visible instanceof DialpadFragment) {
                    ((DialpadFragment) visible).onPermissionChanged();
                } else {
                    showTab(selectedTab);
                }
            });

        multiPermissionRequest = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                showTab(selectedTab);
            });

        roleRequest = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                showTab(selectedTab);
                if (isDefaultDialer(this) && Build.VERSION.SDK_INT >= 33
                        && !hasPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
                    permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            });

        // Apply theme
        applyTheme();

        setContentView(R.layout.activity_main);

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });
        applySystemBarAppearance();

        // Toolbar
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Bottom navigation
        nav = findViewById(R.id.navigation);
        applyAccent();
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            showTab(id);
            return true;
        });

        if (state != null) selectedTab = state.getInt("tab", R.id.nav_recents);
        nav.setSelectedItemId(selectedTab);

        // Handle ACTION_DIAL intent
        Intent intent = getIntent();
        if (Intent.ACTION_DIAL.equals(intent.getAction())) {
            Uri data = intent.getData();
            openDialpad(data == null ? "" : data.getSchemeSpecificPart());
        }
    }

    // ── Theme ──────────────────────────────────────────────────────────

    private void applyTheme() {
        String value = PrefUtil.theme(this);
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

    private void applySystemBarAppearance() {
        boolean light = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
    }

    public void applyAccent() {
        if (nav == null) return;
        int accent = PrefUtil.accent(this);
        ColorStateList tint = new ColorStateList(
            new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
            new int[]{accent, getColor(R.color.pulse_on_surface_secondary)}
        );
        nav.setItemIconTintList(tint);
        nav.setItemTextColor(tint);
    }

    // ── Navigation ─────────────────────────────────────────────────────

    private void showTab(int id) {
        selectedTab = id;
        if (toolbar != null) {
            toolbar.setNavigationIcon(null);
            toolbar.setNavigationOnClickListener(null);
        }
        // Update toolbar title
        if (id == R.id.nav_recents) {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.recents);
        } else if (id == R.id.nav_contacts) {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.contacts);
        } else if (id == R.id.nav_sim) {
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.sim_board);
        }

        Fragment f = ListFragment.newInstance(id);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, f, "tab_" + id)
                .commit();
        invalidateOptionsMenu();
    }

    public void openDialpad(String number) {
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.dial);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        DialpadFragment f = DialpadFragment.newInstance(number);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, f)
                .addToBackStack("dialpad")
                .commit();
    }

    public void openSettings() {
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.settings);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, new SettingsFragment())
                .addToBackStack("settings")
                .commit();
    }

    // ── Permission requests (called by fragments) ──────────────────────

    public void requestContactAccess() {
        if (!hasPermission(this, Manifest.permission.READ_CONTACTS)) {
            permissionRequest.launch(Manifest.permission.READ_CONTACTS);
        } else {
            showTab(R.id.nav_contacts);
        }
    }

    public void requestContactWriteAccess() {
        if (!hasPermission(this, Manifest.permission.WRITE_CONTACTS)) {
            permissionRequest.launch(Manifest.permission.WRITE_CONTACTS);
        }
    }

    public void requestPhoneStateAccess() {
        if (!hasPermission(this, Manifest.permission.READ_PHONE_STATE)) {
            permissionRequest.launch(Manifest.permission.READ_PHONE_STATE);
        }
    }

    public void requestCallLogAccess() {
        if (!isDefaultDialer(this)) {
            requestDefaultDialerRole();
            return;
        }
        if (!hasPermission(this, Manifest.permission.READ_CALL_LOG)) {
            permissionRequest.launch(Manifest.permission.READ_CALL_LOG);
        } else {
            showTab(R.id.nav_recents);
        }
    }

    public void requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager r = getSystemService(RoleManager.class);
            if (r != null && r.isRoleAvailable(RoleManager.ROLE_DIALER)
                    && !r.isRoleHeld(RoleManager.ROLE_DIALER)) {
                roleRequest.launch(r.createRequestRoleIntent(RoleManager.ROLE_DIALER));
            } else {
                Toast.makeText(this, R.string.default_status, Toast.LENGTH_LONG).show();
            }
        } else {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, R.string.action_unavailable, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ── Manage contacts ────────────────────────────────────────────────

    public void showManageContacts() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.content);
        if (current instanceof ListFragment) {
            ((ListFragment) current).showManageContactsSheet();
        }
    }

    // ── Menu ───────────────────────────────────────────────────────────

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putInt("tab", selectedTab);
        super.onSaveInstanceState(out);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.content);
        boolean isContacts = selectedTab == R.id.nav_contacts && current instanceof ListFragment;
        boolean isDialpad = current instanceof DialpadFragment;
        menu.findItem(R.id.action_add).setVisible(isContacts);
        menu.findItem(R.id.action_manage).setVisible(isContacts);
        menu.findItem(R.id.action_search).setVisible(current instanceof ListFragment);
        menu.findItem(R.id.action_paste_number).setVisible(isDialpad);
        menu.findItem(R.id.action_copy_number).setVisible(isDialpad);
        menu.findItem(R.id.action_clear_number).setVisible(isDialpad);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_dialpad) {
            openDialpad("");
            return true;
        }
        if (id == R.id.action_settings) {
            openSettings();
            return true;
        }
        if (id == R.id.action_add) {
            Intent i = new Intent(Intent.ACTION_INSERT,
                    android.provider.ContactsContract.Contacts.CONTENT_URI);
            try {
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(this, R.string.action_unavailable, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        if (id == R.id.action_search) {
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.content);
            if (f instanceof ListFragment) ((ListFragment) f).focusSearch();
            return true;
        }
        if (id == R.id.action_paste_number || id == R.id.action_copy_number
                || id == R.id.action_clear_number) {
            Fragment f = getSupportFragmentManager().findFragmentById(R.id.content);
            if (f instanceof DialpadFragment) {
                DialpadFragment dialpad = (DialpadFragment) f;
                if (id == R.id.action_paste_number) dialpad.pasteNumber();
                else if (id == R.id.action_copy_number) dialpad.copyNumber();
                else dialpad.clearNumber();
            }
            return true;
        }
        if (id == R.id.action_manage) {
            showManageContacts();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Restore toolbar title when popping back
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            toolbar.setNavigationIcon(null);
            toolbar.setNavigationOnClickListener(null);
            // Restore current tab title
            if (selectedTab == R.id.nav_recents && getSupportActionBar() != null)
                getSupportActionBar().setTitle(R.string.recents);
            else if (selectedTab == R.id.nav_contacts && getSupportActionBar() != null)
                getSupportActionBar().setTitle(R.string.contacts);
            else if (selectedTab == R.id.nav_sim && getSupportActionBar() != null)
                getSupportActionBar().setTitle(R.string.sim_board);
            invalidateOptionsMenu();
        } else {
            toolbar.setNavigationIcon(null);
            toolbar.setNavigationOnClickListener(null);
            super.onBackPressed();
        }
    }
}

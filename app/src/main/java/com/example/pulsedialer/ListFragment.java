package com.example.pulsedialer;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pulsedialer.util.AvatarUtil;
import com.example.pulsedialer.util.FormatUtil;
import com.example.pulsedialer.util.PrefUtil;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Unified list fragment for Contacts, Recents, and SIM Board tabs.
 * Premium UI with favorites, alphabetical grouping, date grouping, and bottom-sheet details.
 */
public class ListFragment extends Fragment {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final int TYPE_SECTION = 0;
    private static final int TYPE_CONTACT = 1;
    private static final int TYPE_RECENT = 2;

    private int tab;
    private boolean firstResume = true;
    private String filter = "All";
    private List<ContactRepository.Contact> contacts = new ArrayList<>();
    private List<ContactRepository.Recent> recents = new ArrayList<>();
    private RowAdapter adapter;

    // Views
    private EditText search;
    private ImageView searchClear;
    private LinearLayout filters;
    private LinearLayout emptyState;
    private TextView message;
    private TextView messageSub;
    private MaterialButton actionButton;
    private RecyclerView list;
    private LinearLayout alphaIndex;
    private LinearLayout favoritesSection;
    private RecyclerView favoritesList;
    private FloatingActionButton fabDialpad;

    static ListFragment newInstance(int tab) {
        ListFragment f = new ListFragment();
        Bundle b = new Bundle();
        b.putInt("tab", tab);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        tab = getArguments() == null ? R.id.nav_recents : getArguments().getInt("tab", R.id.nav_recents);
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup p, Bundle b) {
        return i.inflate(R.layout.fragment_list, p, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, Bundle b) {
        super.onViewCreated(v, b);

        // Find views
        search = v.findViewById(R.id.search);
        searchClear = v.findViewById(R.id.search_clear);
        filters = v.findViewById(R.id.filters);
        emptyState = v.findViewById(R.id.empty_state);
        message = v.findViewById(R.id.message);
        messageSub = v.findViewById(R.id.message_sub);
        actionButton = v.findViewById(R.id.action_button);
        list = v.findViewById(R.id.list);
        alphaIndex = v.findViewById(R.id.alpha_index);
        favoritesSection = v.findViewById(R.id.favorites_section);
        favoritesList = v.findViewById(R.id.favorites_list);
        fabDialpad = v.findViewById(R.id.fab_dialpad);

        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RowAdapter();
        list.setAdapter(adapter);

        // FAB
        fabDialpad.setOnClickListener(x -> ((MainActivity) requireActivity()).openDialpad(""));

        // SIM Board tab
        if (tab == R.id.nav_sim) {
            search.setVisibility(View.GONE);
            v.findViewById(R.id.filter_scroll).setVisibility(View.GONE);
            fabDialpad.setVisibility(View.GONE);
            if (!MainActivity.hasPermission(requireContext(), Manifest.permission.READ_PHONE_STATE)) {
                showPermissionState(R.string.sim_permission, () ->
                    ((MainActivity) requireActivity()).requestPhoneStateAccess());
                return;
            }
            loadAccounts();
            return;
        }

        // Search setup
        search.setHint(tab == R.id.nav_contacts ? R.string.search_contacts : R.string.search_calls);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                render();
            }
            public void afterTextChanged(Editable e) {}
        });
        searchClear.setOnClickListener(x -> {
            search.setText("");
            searchClear.setVisibility(View.GONE);
        });

        // Tab-specific setup
        if (tab == R.id.nav_recents) {
            buildFilters();
            if (!MainActivity.isDefaultDialer(requireContext())) {
                showPermissionState(R.string.calls_role_required, () ->
                    ((MainActivity) requireActivity()).requestCallLogAccess());
                return;
            }
            if (!MainActivity.hasPermission(requireContext(), Manifest.permission.READ_CALL_LOG)) {
                showPermissionState(R.string.permission_calls, () ->
                    ((MainActivity) requireActivity()).requestCallLogAccess());
                return;
            }
        } else { // Contacts
            buildAlphaIndex();
        }

        if (tab == R.id.nav_contacts
                && !MainActivity.hasPermission(requireContext(), Manifest.permission.READ_CONTACTS)) {
            showPermissionState(R.string.permission_contacts, () ->
                ((MainActivity) requireActivity()).requestContactAccess());
            return;
        }

        loadData();
    }

    // ── Public accessors ───────────────────────────────────────────────

    public void focusSearch() {
        if (search != null) {
            search.requestFocus();
            // Show keyboard
            android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) requireContext()
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(search, 0);
        }
    }

    public void showManageContactsSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(24));

        // Handle
        View handle = new View(requireContext());
        handle.setBackgroundResource(R.drawable.bg_bottom_sheet_handle);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(40), dp(4));
        handleParams.gravity = Gravity.CENTER;
        handleParams.bottomMargin = dp(16);
        content.addView(handle, handleParams);

        // Title
        TextView title = new TextView(requireContext());
        title.setText(R.string.manage_contacts);
        title.setTextSize(20);
        title.setTextColor(requireContext().getColor(R.color.pulse_on_surface));
        title.setPadding(0, 0, 0, dp(16));
        content.addView(title);

        // Count
        TextView count = new TextView(requireContext());
        count.setText(getString(R.string.contact_count, contacts.size()));
        count.setTextColor(requireContext().getColor(R.color.pulse_on_surface_secondary));
        count.setPadding(0, 0, 0, dp(16));
        content.addView(count);

        // Import
        addManageOption(content, R.string.import_contacts, () -> {
            sheet.dismiss();
            importContacts();
        });

        // Export
        addManageOption(content, R.string.export_contacts, () -> {
            sheet.dismiss();
            exportContacts();
        });

        // Find duplicates
        addManageOption(content, R.string.find_duplicates, () -> {
            sheet.dismiss();
            findDuplicates();
        });

        sheet.setContentView(content);
        sheet.show();
    }

    private void addManageOption(LinearLayout parent, int textRes, Runnable action) {
        MaterialButton btn = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(textRes);
        btn.setAllCaps(false);
        btn.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.bottomMargin = dp(4);
        btn.setOnClickListener(x -> action.run());
        parent.addView(btn, lp);
    }

    // ── Import / Export ────────────────────────────────────────────────

    private final androidx.activity.result.ActivityResultLauncher<Intent> importLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(uri, "text/x-vcard");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(intent);
                        Toast.makeText(requireContext(), R.string.import_success, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), R.string.import_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

    private final androidx.activity.result.ActivityResultLauncher<Intent> exportLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                Uri destUri = result.getData().getData();
                if (destUri != null) {
                    WORKER.execute(() -> {
                        try {
                            // Build vCard from contacts
                            StringBuilder vcf = new StringBuilder();
                            for (ContactRepository.Contact c : contacts) {
                                vcf.append("BEGIN:VCARD\nVERSION:3.0\n");
                                vcf.append("FN:").append(c.name == null ? "" : c.name).append("\n");
                                if (c.number != null)
                                    vcf.append("TEL:").append(c.number).append("\n");
                                vcf.append("END:VCARD\n");
                            }
                            java.io.OutputStream out = requireContext().getContentResolver().openOutputStream(destUri);
                            if (out != null) {
                                out.write(vcf.toString().getBytes());
                                out.close();
                            }
                            if (getActivity() != null) getActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), R.string.export_success, Toast.LENGTH_SHORT).show());
                        } catch (Exception e) {
                            if (getActivity() != null) getActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show());
                        }
                    });
                }
            }
        });

    private void importContacts() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("text/x-vcard");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            importLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.action_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void exportContacts() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/x-vcard");
        intent.putExtra(Intent.EXTRA_TITLE, "contacts.vcf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            exportLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.action_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void findDuplicates() {
        WORKER.execute(() -> {
            List<String> dupes = new ArrayList<>();
            java.util.Map<String, String> seen = new java.util.HashMap<>();
            for (ContactRepository.Contact c : contacts) {
                if (c.number == null) continue;
                String norm = c.number.replaceAll("\\D", "");
                if (norm.length() < 4) continue;
                String key = norm.substring(norm.length() - Math.min(norm.length(), 7));
                if (seen.containsKey(key)) {
                    dupes.add(getString(R.string.duplicate_pair,
                            seen.get(key), c.name == null ? c.number : c.name));
                } else {
                    seen.put(key, c.name == null ? c.number : c.name);
                }
            }
            if (getActivity() != null) getActivity().runOnUiThread(() -> {
                if (dupes.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.no_duplicates, Toast.LENGTH_SHORT).show();
                } else {
                    StringBuilder msg = new StringBuilder(getString(R.string.duplicates_found, dupes.size()));
                    for (String d : dupes) msg.append("\n\n• ").append(d);
                    new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.find_duplicates)
                        .setMessage(msg)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                }
            });
        });
    }

    // ── Permission state ───────────────────────────────────────────────

    private void showPermissionState(int messageRes, Runnable grantAction) {
        emptyState.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
        message.setText(messageRes);
        message.setVisibility(View.VISIBLE);
        actionButton.setVisibility(View.VISIBLE);
        actionButton.setText(R.string.grant_permission);
        actionButton.setOnClickListener(x -> grantAction.run());
    }

    // ── Filters (Recents) ──────────────────────────────────────────────

    private void buildFilters() {
        String[] labels = {"All", "Missed", "Incoming", "Outgoing", "Rejected"};
        int[] stringIds = {R.string.filter_all, R.string.filter_missed, R.string.filter_incoming,
                R.string.filter_outgoing, R.string.filter_rejected};
        for (int i = 0; i < labels.length; i++) {
            final String label = labels[i];
            TextView chip = new TextView(requireContext());
            chip.setText(stringIds[i]);
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            int hPad = dp(16);
            int vPad = dp(8);
            chip.setPadding(hPad, vPad, hPad, vPad);
            chip.setMinHeight(dp(36));
            updateChipState(chip, label.equals(filter));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setOnClickListener(x -> {
                filter = label;
                for (int j = 0; j < filters.getChildCount(); j++) {
                    updateChipState((TextView) filters.getChildAt(j), false);
                }
                updateChipState(chip, true);
                render();
            });
            filters.addView(chip, lp);
        }
    }

    private void updateChipState(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        chip.setTextColor(requireContext().getColor(
                selected ? R.color.chip_selected_text : R.color.chip_unselected_text));
    }

    // ── Alpha index (Contacts) ─────────────────────────────────────────

    private void buildAlphaIndex() {
        alphaIndex.setVisibility(View.VISIBLE);
        for (char letter = 'A'; letter <= 'Z'; letter++) {
            final char target = letter;
            TextView label = new TextView(requireContext());
            label.setText(String.valueOf(letter));
            label.setTextSize(10);
            label.setTextColor(PrefUtil.accent(requireContext()));
            label.setGravity(Gravity.CENTER);
            label.setMinHeight(dp(18));
            label.setContentDescription(getString(R.string.jump_to_letter, String.valueOf(letter)));
            label.setOnClickListener(x -> jumpToLetter(target));
            alphaIndex.addView(label, new LinearLayout.LayoutParams(dp(28), dp(18)));
        }
    }

    private void jumpToLetter(char target) {
        for (int i = 0; i < adapter.rows.size(); i++) {
            Object row = adapter.rows.get(i);
            if (row instanceof String) {
                String section = (String) row;
                if (section.length() == 1 && section.charAt(0) >= target) {
                    list.scrollToPosition(i);
                    return;
                }
            }
        }
        if (adapter.getItemCount() > 0) list.scrollToPosition(adapter.getItemCount() - 1);
    }

    // ── Data loading ───────────────────────────────────────────────────

    private void loadData() {
        showLoading();
        if (tab == R.id.nav_contacts) {
            WORKER.execute(() -> {
                List<ContactRepository.Contact> result =
                    ContactRepository.contacts(requireContext().getApplicationContext());
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    contacts = result;
                    hideLoading();
                    setupFavorites();
                    render();
                });
            });
        } else {
            WORKER.execute(() -> {
                List<ContactRepository.Recent> result =
                    ContactRepository.recents(requireContext().getApplicationContext());
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    recents = result;
                    hideLoading();
                    render();
                });
            });
        }
    }

    private void showLoading() {
        emptyState.setVisibility(View.VISIBLE);
        message.setText(R.string.loading);
        message.setVisibility(View.VISIBLE);
        messageSub.setVisibility(View.GONE);
        actionButton.setVisibility(View.GONE);
    }

    private void hideLoading() {
        emptyState.setVisibility(View.GONE);
    }

    // ── Favorites ──────────────────────────────────────────────────────

    private void setupFavorites() {
        List<ContactRepository.Contact> favs = new ArrayList<>();
        for (ContactRepository.Contact c : contacts) {
            if (c.starred) favs.add(c);
        }
        if (favs.isEmpty()) {
            favoritesSection.setVisibility(View.GONE);
            return;
        }
        favoritesSection.setVisibility(View.VISIBLE);
        favoritesList.setLayoutManager(new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false));
        favoritesList.setAdapter(new FavoriteAdapter(favs));
    }

    // ── Render ─────────────────────────────────────────────────────────

    private void render() {
        if (adapter == null) return;
        adapter.rows.clear();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.getDefault());

        if (tab == R.id.nav_contacts) {
            renderContacts(q);
        } else {
            renderRecents(q);
        }

        adapter.notifyDataSetChanged();
        updateEmptyState(q);
    }

    private void renderContacts(String q) {
        List<ContactRepository.Contact> result = new ArrayList<>();
        for (ContactRepository.Contact c : contacts) {
            if (q.isEmpty()
                || (c.name != null && c.name.toLowerCase(Locale.getDefault()).contains(q))
                || (c.number != null && c.number.toLowerCase(Locale.getDefault()).contains(q))
                || (c.email != null && c.email.toLowerCase(Locale.getDefault()).contains(q))) {
                result.add(c);
            }
        }
        Collator collator = Collator.getInstance();
        Collections.sort(result, (a, b) -> collator.compare(
                a.name == null ? "" : a.name,
                b.name == null ? "" : b.name));

        // Insert section headers
        String lastSection = null;
        for (ContactRepository.Contact c : result) {
            String section = sectionFor(c.name);
            if (!section.equals(lastSection)) {
                adapter.rows.add(section); // Section header
                lastSection = section;
            }
            adapter.rows.add(c);
        }
    }

    private void renderRecents(String q) {
        String lastDate = null;
        for (ContactRepository.Recent c : recents) {
            // Filter
            if (!filter.equals("All") && !filter.equals(c.kind())) continue;
            // Search
            String displayName = c.name == null || c.name.trim().isEmpty() ? c.number : c.name;
            if (!q.isEmpty()
                && (displayName == null || !displayName.toLowerCase(Locale.getDefault()).contains(q))
                && (c.number == null || !c.number.toLowerCase(Locale.getDefault()).contains(q))) {
                continue;
            }
            // Date header
            String dateGroup = FormatUtil.dateGroup(c.date);
            if (!dateGroup.equals(lastDate)) {
                adapter.rows.add("DATE:" + dateGroup);
                lastDate = dateGroup;
            }
            adapter.rows.add(c);
        }
    }

    private String sectionFor(String name) {
        if (name == null || name.trim().isEmpty()) return "#";
        char first = Character.toUpperCase(name.trim().charAt(0));
        if (first >= 'A' && first <= 'Z') return String.valueOf(first);
        return "#";
    }

    private void updateEmptyState(String q) {
        if (adapter.getItemCount() == 0) {
            emptyState.setVisibility(View.VISIBLE);
            if (q.isEmpty()) {
                message.setText(tab == R.id.nav_contacts ? R.string.empty_contacts : R.string.empty_recents);
                messageSub.setText(tab == R.id.nav_contacts ? R.string.empty_contacts_subtitle : R.string.empty_recents_subtitle);
                messageSub.setVisibility(View.VISIBLE);
            } else {
                message.setText(R.string.no_results);
                messageSub.setText(R.string.no_results_subtitle);
                messageSub.setVisibility(View.VISIBLE);
            }
            message.setVisibility(View.VISIBLE);
            actionButton.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (firstResume) { firstResume = false; return; }
        if (adapter != null) {
            if (tab == R.id.nav_contacts && MainActivity.hasPermission(requireContext(), Manifest.permission.READ_CONTACTS))
                loadData();
            if (tab == R.id.nav_recents && MainActivity.isDefaultDialer(requireContext())
                    && MainActivity.hasPermission(requireContext(), Manifest.permission.READ_CALL_LOG))
                loadData();
            if (tab == R.id.nav_sim && MainActivity.hasPermission(requireContext(), Manifest.permission.READ_PHONE_STATE))
                loadAccounts();
        }
    }

    // ── SIM Board ──────────────────────────────────────────────────────

    private void loadAccounts() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            showPermissionState(R.string.sim_permission, () ->
                ((MainActivity) requireActivity()).requestPhoneStateAccess());
            return;
        }
        TelecomManager tm = (TelecomManager) requireContext().getSystemService(Context.TELECOM_SERVICE);
        if (tm == null) {
            showSimEmpty(R.string.no_telecom);
            return;
        }
        try {
            List<PhoneAccountHandle> accounts = tm.getCallCapablePhoneAccounts();
            if (accounts == null || accounts.isEmpty()) {
                showSimEmpty(R.string.no_sim_accounts);
                return;
            }
            emptyState.setVisibility(View.VISIBLE);
            actionButton.setVisibility(View.GONE);
            messageSub.setVisibility(View.GONE);

            StringBuilder info = new StringBuilder();
            info.append(getString(R.string.accounts_heading));
            for (int i = 0; i < accounts.size(); i++) {
                PhoneAccountHandle h = accounts.get(i);
                android.telecom.PhoneAccount a = tm.getPhoneAccount(h);
                info.append("\n\n");
                if (a != null) {
                    info.append("📱 ").append(a.getLabel());
                    if (a.getShortDescription() != null && a.getShortDescription().length() > 0) {
                        info.append("\n").append(a.getShortDescription());
                    }
                } else {
                    info.append(getString(R.string.call_account_label, i + 1));
                }
            }
            info.append("\n\n").append(getString(R.string.sim_contacts_limited));
            message.setText(info);
            message.setVisibility(View.VISIBLE);
            message.setGravity(Gravity.START);

            // Add "Open SIM settings" button
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setText(R.string.open_sim_settings);
            actionButton.setOnClickListener(x -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(requireContext(), R.string.action_unavailable, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (SecurityException e) {
            showSimEmpty(R.string.no_sim_accounts);
        }
    }

    private void showSimEmpty(int msgRes) {
        emptyState.setVisibility(View.VISIBLE);
        message.setText(msgRes);
        message.setVisibility(View.VISIBLE);
        messageSub.setVisibility(View.GONE);
        actionButton.setVisibility(View.GONE);
    }

    // ── Row actions ────────────────────────────────────────────────────

    private void rowAction(Object row) {
        if (row instanceof ContactRepository.Contact) {
            contactAction((ContactRepository.Contact) row);
        } else if (row instanceof ContactRepository.Recent) {
            recentAction((ContactRepository.Recent) row);
        }
    }

    private void contactAction(ContactRepository.Contact c) {
        // Open system contact details
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(c.id))));
        } catch (Exception e) {
            // Fallback to our own dialog
            showContactDialog(c);
        }
    }

    private void showContactDialog(ContactRepository.Contact c) {
        String[] actions = {
            getString(R.string.call),
            getString(R.string.message_action),
            getString(c.starred ? R.string.remove_favorite : R.string.add_favorite),
            getString(R.string.edit),
            getString(R.string.share),
            getString(R.string.delete)
        };
        new AlertDialog.Builder(requireContext())
            .setTitle(c.name)
            .setItems(actions, (d, w) -> {
                switch (w) {
                    case 0: ((MainActivity) requireActivity()).openDialpad(c.number); break;
                    case 1: sendMessage(c.number); break;
                    case 2: toggleFavorite(c); break;
                    case 3: editContact(c); break;
                    case 4: shareContact(c); break;
                    case 5: deleteContact(c); break;
                }
            })
            .show();
    }

    private void quickCall(ContactRepository.Contact c) {
        if (c.number != null && !c.number.isEmpty()) {
            ((MainActivity) requireActivity()).openDialpad(c.number);
        }
    }

    private void recentAction(ContactRepository.Recent c) {
        // Show bottom sheet
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottom_sheet_call_detail, null);

        // Populate
        TextView avatar = content.findViewById(R.id.bs_avatar);
        AvatarUtil.apply(avatar, c.name);

        TextView name = content.findViewById(R.id.bs_name);
        name.setText(c.name == null || c.name.isEmpty() ? getString(R.string.unknown_number) : c.name);

        TextView number = content.findViewById(R.id.bs_number);
        number.setText(c.number == null ? "" : c.number);

        TextView type = content.findViewById(R.id.bs_type);
        type.setText(getString(R.string.call_type_label, c.kind()));

        TextView time = content.findViewById(R.id.bs_time);
        time.setText(FormatUtil.dateLabel(c.date));

        TextView duration = content.findViewById(R.id.bs_duration);
        duration.setText(getString(R.string.duration_label, FormatUtil.duration(c.duration)));

        TextView accountView = content.findViewById(R.id.bs_account);
        if (c.account != null && !c.account.isEmpty()) {
            accountView.setText(getString(R.string.account_label, c.account));
            accountView.setVisibility(View.VISIBLE);
        }

        // Actions
        content.findViewById(R.id.bs_call).setOnClickListener(x -> {
            sheet.dismiss();
            ((MainActivity) requireActivity()).openDialpad(c.number);
        });
        content.findViewById(R.id.bs_message).setOnClickListener(x -> {
            sheet.dismiss();
            sendMessage(c.number);
        });
        content.findViewById(R.id.bs_contact).setOnClickListener(x -> {
            sheet.dismiss();
            if (c.name != null && !c.name.isEmpty()) {
                // Try to open contact
                try {
                    Uri lookupUri = Uri.withAppendedPath(
                        ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(c.number));
                    try (android.database.Cursor cursor = requireContext().getContentResolver().query(
                            lookupUri,
                            new String[]{ContactsContract.PhoneLookup._ID},
                            null, null, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            long contactId = cursor.getLong(0);
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI,
                                    String.valueOf(contactId))));
                            return;
                        }
                    }
                } catch (Exception ignored) {}
            }
            addContact(c.number);
        });
        content.findViewById(R.id.bs_copy).setOnClickListener(x -> {
            sheet.dismiss();
            ClipboardManager clipboard = (ClipboardManager) requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && c.number != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("number", c.number));
                Toast.makeText(requireContext(), R.string.number_copied, Toast.LENGTH_SHORT).show();
            }
        });

        sheet.setContentView(content);
        sheet.show();
    }

    // ── Contact operations ─────────────────────────────────────────────

    private void sendMessage(String n) {
        try {
            startActivity(new Intent(Intent.ACTION_SENDTO,
                    Uri.parse("smsto:" + Uri.encode(n == null ? "" : n))));
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.action_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void editContact(ContactRepository.Contact c) {
        try {
            startActivity(new Intent(Intent.ACTION_EDIT,
                Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(c.id))));
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.action_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void addContact(String n) {
        try {
            Intent i = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
            i.putExtra(ContactsContract.Intents.Insert.PHONE, n);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.action_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareContact(ContactRepository.Contact c) {
        try {
            Uri vcard = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI,
                    String.valueOf(c.id));
            Intent intent = new Intent(Intent.ACTION_SEND)
                    .setType("text/vcard")
                    .putExtra(Intent.EXTRA_STREAM, vcard)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.action_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteContact(ContactRepository.Contact c) {
        if (!MainActivity.hasPermission(requireContext(), Manifest.permission.WRITE_CONTACTS)) {
            new AlertDialog.Builder(requireContext())
                .setMessage(R.string.favorite_permission)
                .setPositiveButton(R.string.grant_permission, (d, w) ->
                    ((MainActivity) requireActivity()).requestContactWriteAccess())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_contact)
            .setMessage(getString(R.string.delete_confirmation, c.name == null ? "" : c.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete, (d, w) -> {
                Context app = requireContext().getApplicationContext();
                WORKER.execute(() -> {
                    int count = 0;
                    try {
                        count = app.getContentResolver().delete(
                            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI,
                                String.valueOf(c.id)), null, null);
                    } catch (SecurityException ignored) {}
                    int deleted = count;
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(),
                            deleted > 0 ? R.string.contact_deleted : R.string.contact_delete_failed,
                            Toast.LENGTH_SHORT).show();
                        loadData();
                    });
                });
            })
            .show();
    }

    private void toggleFavorite(ContactRepository.Contact c) {
        if (!MainActivity.hasPermission(requireContext(), Manifest.permission.WRITE_CONTACTS)) {
            new AlertDialog.Builder(requireContext())
                .setMessage(R.string.favorite_permission)
                .setPositiveButton(R.string.grant_permission, (d, w) ->
                    ((MainActivity) requireActivity()).requestContactWriteAccess())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }
        ContentValues v = new ContentValues();
        v.put(ContactsContract.Contacts.STARRED, c.starred ? 0 : 1);
        Context app = requireContext().getApplicationContext();
        WORKER.execute(() -> {
            try {
                app.getContentResolver().update(
                    Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(c.id)),
                    v, null, null);
                if (getActivity() != null) getActivity().runOnUiThread(this::loadData);
            } catch (SecurityException ignored) {}
        });
    }

    // ── Utility ────────────────────────────────────────────────────────

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ══════════════════════════════════════════════════════════════════
    // ── ADAPTERS ─────────────────────────────────────────────────────
    // ══════════════════════════════════════════════════════════════════

    /** Main list adapter supporting contacts, recents, and section headers. */
    private class RowAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final List<Object> rows = new ArrayList<>();

        @Override
        public int getItemViewType(int position) {
            Object item = rows.get(position);
            if (item instanceof String) return TYPE_SECTION;
            if (item instanceof ContactRepository.Contact) return TYPE_CONTACT;
            return TYPE_RECENT;
        }

        @Override
        public int getItemCount() { return rows.size(); }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_SECTION) {
                // For contacts: section letter; for recents: date header
                View v = inflater.inflate(R.layout.item_section_header, parent, false);
                return new SectionHolder(v);
            }
            View v = inflater.inflate(R.layout.item_contact, parent, false);
            return new RowHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object item = rows.get(position);
            if (holder instanceof SectionHolder) {
                String text = (String) item;
                if (text.startsWith("DATE:")) text = text.substring(5);
                ((SectionHolder) holder).label.setText(text);
                return;
            }

            RowHolder h = (RowHolder) holder;
            Context context = h.itemView.getContext();
            boolean compact = PrefUtil.compact(context);
            int vertical = dp(compact ? 6 : 12);
            h.itemView.setPadding(h.itemView.getPaddingLeft(), vertical,
                    h.itemView.getPaddingRight(), vertical);

            if (item instanceof ContactRepository.Contact) {
                bindContact(h, (ContactRepository.Contact) item, context);
            } else if (item instanceof ContactRepository.Recent) {
                bindRecent(h, (ContactRepository.Recent) item, context);
            }
        }

        private void bindContact(RowHolder h, ContactRepository.Contact c, Context context) {
            AvatarUtil.apply(h.avatar, c.name);
            h.name.setText(c.name == null || c.name.isEmpty()
                    ? context.getString(R.string.unknown_contact) : c.name);
            h.detail.setText(c.number == null ? context.getString(R.string.no_phone) : c.number);

            // Photo loading
            h.photo.setTag(null);
            h.photo.setImageDrawable(null);
            h.photo.setVisibility(View.GONE);
            if (c.photo != null) {
                String photoTag = c.photo.toString();
                h.photo.setTag(photoTag);
                WORKER.execute(() -> {
                    Bitmap image = null;
                    try (java.io.InputStream in = context.getContentResolver().openInputStream(c.photo)) {
                        if (in != null) image = BitmapFactory.decodeStream(in);
                    } catch (Exception ignored) {}
                    Bitmap result = image;
                    if (result != null) {
                        h.itemView.post(() -> {
                            if (photoTag.equals(h.photo.getTag())) {
                                h.photo.setImageBitmap(result);
                                h.photo.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                });
            }

            // Call type icon hidden for contacts
            h.callTypeIcon.setVisibility(View.GONE);

            // Quick call
            h.quickCall.setVisibility(c.number != null ? View.VISIBLE : View.GONE);
            h.quickCall.setOnClickListener(v -> quickCall(c));

            // Row click → contact details
            h.itemView.setOnClickListener(v -> contactAction(c));

            // Long press → menu
            h.itemView.setOnLongClickListener(v -> {
                showContactDialog(c);
                return true;
            });
        }

        private void bindRecent(RowHolder h, ContactRepository.Recent c, Context context) {
            String displayName = c.name == null || c.name.isEmpty()
                    ? context.getString(R.string.unknown_number) : c.name;
            AvatarUtil.apply(h.avatar, c.name);
            h.name.setText(displayName);

            // Missed calls in red
            if (c.type == CallLog.Calls.MISSED_TYPE || c.type == CallLog.Calls.REJECTED_TYPE) {
                h.name.setTextColor(context.getColor(R.color.call_missed));
            } else {
                h.name.setTextColor(context.getColor(R.color.pulse_on_surface));
            }

            h.detail.setText((c.number == null ? "" : c.number) + " · " + FormatUtil.duration(c.duration));
            h.photo.setVisibility(View.GONE);

            // Call type icon
            h.callTypeIcon.setVisibility(View.VISIBLE);
            switch (c.type) {
                case CallLog.Calls.INCOMING_TYPE:
                    h.callTypeIcon.setImageResource(R.drawable.ic_call_incoming);
                    break;
                case CallLog.Calls.OUTGOING_TYPE:
                    h.callTypeIcon.setImageResource(R.drawable.ic_call_outgoing);
                    break;
                case CallLog.Calls.MISSED_TYPE:
                    h.callTypeIcon.setImageResource(R.drawable.ic_call_missed);
                    break;
                case CallLog.Calls.REJECTED_TYPE:
                    h.callTypeIcon.setImageResource(R.drawable.ic_call_rejected);
                    break;
                default:
                    h.callTypeIcon.setVisibility(View.GONE);
                    break;
            }

            // Quick call
            h.quickCall.setVisibility(c.number != null ? View.VISIBLE : View.GONE);
            h.quickCall.setOnClickListener(v ->
                ((MainActivity) requireActivity()).openDialpad(c.number));

            // Row click → bottom sheet
            h.itemView.setOnClickListener(v -> recentAction(c));
            h.itemView.setOnLongClickListener(null);
        }
    }

    static class SectionHolder extends RecyclerView.ViewHolder {
        final TextView label;
        SectionHolder(View v) {
            super(v);
            label = v.findViewById(R.id.section_letter);
        }
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final TextView name, detail, avatar;
        final ImageView photo, callTypeIcon, quickCall;
        RowHolder(View v) {
            super(v);
            name = v.findViewById(R.id.name);
            detail = v.findViewById(R.id.detail);
            avatar = v.findViewById(R.id.avatar);
            photo = v.findViewById(R.id.photo);
            callTypeIcon = v.findViewById(R.id.call_type_icon);
            quickCall = v.findViewById(R.id.quick_call);
        }
    }

    /** Horizontal favorites adapter. */
    private class FavoriteAdapter extends RecyclerView.Adapter<FavoriteHolder> {
        private final List<ContactRepository.Contact> favs;
        FavoriteAdapter(List<ContactRepository.Contact> favs) { this.favs = favs; }

        @NonNull @Override
        public FavoriteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_favorite, parent, false);
            return new FavoriteHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull FavoriteHolder h, int position) {
            ContactRepository.Contact c = favs.get(position);
            AvatarUtil.apply(h.avatar, c.name);
            h.name.setText(c.name == null ? getString(R.string.unknown_contact) : c.name);

            // Load photo
            h.photo.setVisibility(View.GONE);
            if (c.photo != null) {
                WORKER.execute(() -> {
                    Bitmap img = null;
                    try (java.io.InputStream in = requireContext().getContentResolver().openInputStream(c.photo)) {
                        if (in != null) img = BitmapFactory.decodeStream(in);
                    } catch (Exception ignored) {}
                    Bitmap result = img;
                    if (result != null) {
                        h.itemView.post(() -> {
                            h.photo.setImageBitmap(result);
                            h.photo.setVisibility(View.VISIBLE);
                        });
                    }
                });
            }

            h.itemView.setOnClickListener(v -> contactAction(c));
        }

        @Override
        public int getItemCount() { return favs.size(); }
    }

    static class FavoriteHolder extends RecyclerView.ViewHolder {
        final TextView avatar, name;
        final ImageView photo;
        FavoriteHolder(View v) {
            super(v);
            avatar = v.findViewById(R.id.fav_avatar);
            name = v.findViewById(R.id.fav_name);
            photo = v.findViewById(R.id.fav_photo);
        }
    }
}

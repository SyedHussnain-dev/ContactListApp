package com.example.pulsedialer;

import android.Manifest;
import android.content.Context;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pulsedialer.util.AvatarUtil;
import com.example.pulsedialer.util.PrefUtil;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Premium dialpad with T9 smart search, dual-SIM support, and beautiful keypad. */
public class DialpadFragment extends Fragment {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private EditText number;
    private RecyclerView suggestions;
    private View suggestionsContainer;
    private SuggestionAdapter adapter;
    private List<ContactRepository.Contact> allContacts = new ArrayList<>();
    private List<PhoneAccountHandle> accounts = new ArrayList<>();
    private LinearLayout accountRow;
    private View contactAccessBtn;
    private View accountAccessBtn;
    private PhoneAccountHandle pendingAccount;

    private final ActivityResultLauncher<String> callPermission =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) placeSelected(pendingAccount);
            else startDialFallback();
        });

    static DialpadFragment newInstance(String value) {
        DialpadFragment f = new DialpadFragment();
        Bundle b = new Bundle();
        b.putString("number", value);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater i, @Nullable ViewGroup p, Bundle b) {
        return i.inflate(R.layout.fragment_dialpad, p, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, Bundle b) {
        number = v.findViewById(R.id.number);
        number.setShowSoftInputOnFocus(false);
        number.setText(getArguments() == null ? "" : getArguments().getString("number", ""));
        setHasOptionsMenu(true);

        accountRow = v.findViewById(R.id.sim_actions);
        contactAccessBtn = v.findViewById(R.id.contact_access);
        accountAccessBtn = v.findViewById(R.id.account_access);

        // Backspace
        ImageView backspace = v.findViewById(R.id.backspace);
        backspace.setOnClickListener(x -> {
            int start = number.getSelectionStart();
            int end = number.getSelectionEnd();
            String updated = PhoneNumberFormatter.deleteBackward(number.getText().toString(), start, end);
            int cursor = Math.max(0, Math.min(start == end ? start - (start > 0 ? 1 : 0) : Math.min(start, end), updated.length()));
            number.setText(updated);
            number.setSelection(cursor);
        });
        backspace.setOnLongClickListener(x -> {
            number.setText("");
            return true;
        });

        // Build keypad
        GridLayout grid = v.findViewById(R.id.keypad);
        buildKeypad(grid);

        // T9 suggestions
        suggestionsContainer = v.findViewById(R.id.suggestions_container);
        suggestions = v.findViewById(R.id.suggestions);
        suggestions.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SuggestionAdapter();
        suggestions.setAdapter(adapter);

        // Text change → T9 search
        number.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                findSuggestions(s.toString());
            }
            public void afterTextChanged(Editable e) {}
        });

        // Call button
        v.findViewById(R.id.call).setOnClickListener(x -> placeSelected(null));

        // Contact access
        contactAccessBtn.setOnClickListener(x -> {
            if (!MainActivity.hasPermission(requireContext(), Manifest.permission.READ_CONTACTS)) {
                ((MainActivity) requireActivity()).requestContactAccess();
            } else {
                loadContacts();
            }
        });

        // Account access
        accountAccessBtn.setOnClickListener(x ->
            ((MainActivity) requireActivity()).requestPhoneStateAccess());

        // Load contacts if permitted
        if (MainActivity.hasPermission(requireContext(), Manifest.permission.READ_CONTACTS)) {
            contactAccessBtn.setVisibility(View.GONE);
            loadContacts();
        } else {
            contactAccessBtn.setVisibility(View.VISIBLE);
        }

        // Refresh accounts
        refreshAccounts();
        setupAccountButtons();
    }

    // ── Keypad ─────────────────────────────────────────────────────────

    private void buildKeypad(GridLayout grid) {
        String[][] keys = {
            {"1", ""},    {"2", "ABC"},  {"3", "DEF"},
            {"4", "GHI"}, {"5", "JKL"},  {"6", "MNO"},
            {"7", "PQRS"},{"8", "TUV"},  {"9", "WXYZ"},
            {"*", ""},    {"0", "+"},    {"#", ""}
        };

        for (String[] key : keys) {
            LinearLayout keyView = new LinearLayout(requireContext());
            keyView.setOrientation(LinearLayout.VERTICAL);
            keyView.setGravity(Gravity.CENTER);
            keyView.setBackground(requireContext().getDrawable(R.drawable.bg_dialpad_key));
            keyView.setClickable(true);
            keyView.setFocusable(true);
            keyView.setMinimumHeight(dp(60));

            // Digit
            TextView digit = new TextView(requireContext());
            digit.setText(key[0]);
            digit.setTextSize(24);
            digit.setTextColor(requireContext().getColor(R.color.dialpad_key_text));
            digit.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            digit.setGravity(Gravity.CENTER);
            keyView.addView(digit);

            // Sub text
            if (!key[1].isEmpty()) {
                TextView sub = new TextView(requireContext());
                sub.setText(key[1]);
                sub.setTextSize(10);
                sub.setTextColor(requireContext().getColor(R.color.dialpad_key_sub));
                sub.setGravity(Gravity.CENTER);
                sub.setLetterSpacing(0.15f);
                keyView.addView(sub);
            }

            keyView.setContentDescription(getString(R.string.digit_description, key[0], key[1]));

            keyView.setOnClickListener(z -> {
                if (PrefUtil.haptics(requireContext())) {
                    keyView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                int at = number.getSelectionStart();
                if (at < 0) at = number.length();
                number.getText().insert(at, key[0]);
            });

            keyView.setOnLongClickListener(z -> {
                if ("0".equals(key[0])) {
                    int at = number.getSelectionStart();
                    if (at < 0) at = number.length();
                    if (at == 0 && number.getText().toString().indexOf('+') < 0
                            && number.getSelectionStart() == number.getSelectionEnd()) {
                        number.getText().insert(at, "+");
                    }
                    return true;
                }
                return false;
            });

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = 0;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            int margin = dp(4);
            lp.setMargins(margin, margin, margin, margin);
            grid.addView(keyView, lp);
        }
    }

    // ── T9 Search ──────────────────────────────────────────────────────

    private void loadContacts() {
        WORKER.execute(() -> {
            List<ContactRepository.Contact> result =
                ContactRepository.contacts(requireContext().getApplicationContext());
            if (getActivity() != null) getActivity().runOnUiThread(() -> {
                allContacts = result;
                findSuggestions(number.getText().toString());
            });
        });
    }

    private void findSuggestions(String query) {
        if (adapter == null) return;
        adapter.rows.clear();
        String q = query.trim();
        for (ContactRepository.Contact c : allContacts) {
            boolean match = q.isEmpty() ? c.starred : T9.matches(q, c.name, c.number);
            if (match) {
                adapter.rows.add(c);
                if (adapter.rows.size() == 5) break;
            }
        }
        adapter.notifyDataSetChanged();
        suggestionsContainer.setVisibility(adapter.rows.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ── Accounts ───────────────────────────────────────────────────────

    private void refreshAccounts() {
        accounts = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            TelecomManager tm = (TelecomManager) requireContext().getSystemService(Context.TELECOM_SERVICE);
            accounts = tm == null ? new ArrayList<>() : tm.getCallCapablePhoneAccounts();
        } catch (SecurityException e) {
            accountAccessBtn.setVisibility(View.VISIBLE);
            Toast.makeText(requireContext(), R.string.account_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    void onPermissionChanged() {
        if (MainActivity.hasPermission(requireContext(), Manifest.permission.READ_CONTACTS)) {
            if (contactAccessBtn != null) contactAccessBtn.setVisibility(View.GONE);
            loadContacts();
        }
        refreshAccounts();
        setupAccountButtons();
    }

    private void setupAccountButtons() {
        if (accountRow == null) return;
        accountRow.removeAllViews();

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            accountRow.setVisibility(View.GONE);
            if (accountAccessBtn != null) accountAccessBtn.setVisibility(View.VISIBLE);
            return;
        }
        if (accountAccessBtn != null) accountAccessBtn.setVisibility(View.GONE);

        if (accounts.size() < 2) {
            accountRow.setVisibility(View.GONE);
            return;
        }

        accountRow.setVisibility(View.VISIBLE);
        TelecomManager tm = (TelecomManager) requireContext().getSystemService(Context.TELECOM_SERVICE);

        for (int i = 0; i < accounts.size(); i++) {
            PhoneAccountHandle handle = accounts.get(i);
            android.telecom.PhoneAccount account = tm == null ? null : tm.getPhoneAccount(handle);
            String label = account == null
                    ? getString(R.string.call_account_label, i + 1)
                    : account.getLabel().toString();

            MaterialButton button = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(label);
            button.setAllCaps(false);
            button.setTextSize(12);
            button.setContentDescription(getString(R.string.call_using_account, label));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            accountRow.addView(button, lp);

            button.setOnClickListener(v -> placeSelected(handle));
        }
    }

    // ── Call placement ─────────────────────────────────────────────────

    private void placeSelected(PhoneAccountHandle account) {
        String raw = number.getText().toString().trim();
        String clean = PhoneNumberFormatter.sanitize(raw);
        if (!PhoneNumberFormatter.isDialable(clean)) {
            Toast.makeText(requireContext(), raw.isEmpty()
                    ? R.string.enter_number : R.string.invalid_number, Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = Uri.fromParts("tel", clean, null);

        // Not default dialer → ACTION_DIAL
        if (!MainActivity.isDefaultDialer(requireContext())) {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, uri));
            } catch (Exception e) {
                Toast.makeText(requireContext(), R.string.call_failed, Toast.LENGTH_LONG).show();
            }
            return;
        }

        // Multiple accounts, none selected → choose
        if (accounts.size() > 1 && account == null) {
            String[] labels = new String[accounts.size()];
            TelecomManager tm = (TelecomManager) requireContext().getSystemService(Context.TELECOM_SERVICE);
            for (int i = 0; i < labels.length; i++) {
                android.telecom.PhoneAccount a = tm == null ? null : tm.getPhoneAccount(accounts.get(i));
                labels[i] = a == null
                        ? getString(R.string.call_account_label, i + 1)
                        : a.getLabel().toString();
            }
            new AlertDialog.Builder(requireContext())
                .setTitle(R.string.choose_sim)
                .setItems(labels, (d, w) -> placeSelected(accounts.get(w)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }

        // Need CALL_PHONE permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAccount = account;
            callPermission.launch(Manifest.permission.CALL_PHONE);
            return;
        }

        // Place the call
        try {
            TelecomManager tm = (TelecomManager) requireContext().getSystemService(Context.TELECOM_SERVICE);
            android.os.Bundle extras = new android.os.Bundle();
            if (account != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account);
            tm.placeCall(uri, extras);
        } catch (SecurityException e) {
            startDialFallback();
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.call_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void startDialFallback() {
        String clean = PhoneNumberFormatter.sanitize(number.getText().toString().trim());
        try {
            startActivity(new Intent(Intent.ACTION_DIAL,
                    Uri.fromParts("tel", clean, null)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), R.string.call_failed, Toast.LENGTH_LONG).show();
        }
    }

    void clearNumber() {
        if (number != null) number.setText("");
    }

    void copyNumber() {
        String value = number == null ? "" : number.getText().toString();
        if (value.isEmpty()) {
            Toast.makeText(requireContext(), R.string.enter_number, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.dial), value));
            Toast.makeText(requireContext(), R.string.number_copied, Toast.LENGTH_SHORT).show();
        }
    }

    void pasteNumber() {
        ClipboardManager clipboard = (ClipboardManager) requireContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return;
        ClipData data = clipboard.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) return;
        String pasted = PhoneNumberFormatter.sanitize(
                data.getItemAt(0).coerceToText(requireContext()).toString());
        if (!PhoneNumberFormatter.isDialable(pasted)) {
            Toast.makeText(requireContext(), R.string.invalid_number, Toast.LENGTH_SHORT).show();
            return;
        }
        number.setText(pasted);
        number.setSelection(number.length());
    }

    private int dp(int n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ── Suggestion adapter ─────────────────────────────────────────────

    private class SuggestionAdapter extends RecyclerView.Adapter<SuggestionHolder> {
        final List<ContactRepository.Contact> rows = new ArrayList<>();

        @NonNull @Override
        public SuggestionHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dialpad_suggestion, parent, false);
            return new SuggestionHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull SuggestionHolder h, int position) {
            ContactRepository.Contact c = rows.get(position);
            AvatarUtil.apply(h.avatar, c.name);
            h.name.setText(c.name);
            h.number.setText(c.phoneLabel == null || c.phoneLabel.isEmpty()
                    ? c.number : getString(R.string.contact_number_with_label, c.phoneLabel, c.number));
            h.itemView.setOnClickListener(v -> {
                if (c.number != null) number.setText(c.number);
            });
        }

        @Override
        public int getItemCount() { return rows.size(); }
    }

    private static class SuggestionHolder extends RecyclerView.ViewHolder {
        final TextView avatar, name, number;
        SuggestionHolder(View v) {
            super(v);
            avatar = v.findViewById(R.id.sug_avatar);
            name = v.findViewById(R.id.sug_name);
            number = v.findViewById(R.id.sug_number);
        }
    }
}

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
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Premium dialpad with T9 smart search, dual-SIM support, and responsive keypad. */
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

    private GridLayout keypadGrid;
    private View callContainer;
    private FloatingActionButton callButton;
    private ImageView keypadToggle;
    private boolean isKeypadExpanded = true;
    private boolean isCompactMode = false;

    private static class KeyHolder {
        FrameLayout cell;
        LinearLayout keyView;
        TextView digit;
        TextView sub;
        View spacer;
    }
    private final List<KeyHolder> keyHolders = new ArrayList<>();

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

        keypadGrid = v.findViewById(R.id.keypad);
        callContainer = v.findViewById(R.id.call_container);
        callButton = v.findViewById(R.id.call);
        keypadToggle = v.findViewById(R.id.keypad_toggle);

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
        buildKeypad(keypadGrid);

        // Toggle keypad button (collapses/expands dialpad when search results exist)
        if (keypadToggle != null) {
            keypadToggle.setOnClickListener(x -> setKeypadExpanded(!isKeypadExpanded));
        }

        // Tap number card to restore keypad if it was collapsed
        View numberCard = v.findViewById(R.id.number_card);
        if (numberCard != null) {
            numberCard.setOnClickListener(x -> {
                if (!isKeypadExpanded) setKeypadExpanded(true);
            });
        }
        number.setOnClickListener(x -> {
            if (!isKeypadExpanded) setKeypadExpanded(true);
        });

        // T9 suggestions
        suggestionsContainer = v.findViewById(R.id.suggestions_container);
        suggestions = v.findViewById(R.id.suggestions);
        suggestions.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SuggestionAdapter();
        suggestions.setAdapter(adapter);

        // Scroll listener: scrolling down through search results collapses keypad
        suggestions.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy > 12 && isKeypadExpanded && adapter != null && !adapter.rows.isEmpty()) {
                    setKeypadExpanded(false);
                }
            }
        });

        // Text change → T9 search
        number.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                findSuggestions(s.toString());
            }
            public void afterTextChanged(Editable e) {}
        });

        // Call button
        if (callButton != null) {
            callButton.setOnClickListener(x -> placeSelected(null));
        }

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

        // Initial suggestion check
        findSuggestions(number.getText().toString());
    }

    // ── Keypad ─────────────────────────────────────────────────────────

    private void buildKeypad(GridLayout grid) {
        if (grid == null) return;
        grid.removeAllViews();
        keyHolders.clear();

        String[][] keys = {
            {"1", ""},    {"2", "ABC"},  {"3", "DEF"},
            {"4", "GHI"}, {"5", "JKL"},  {"6", "MNO"},
            {"7", "PQRS"},{"8", "TUV"},  {"9", "WXYZ"},
            {"*", ""},    {"0", "+"},    {"#", ""}
        };

        float density = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int horizontalPad = (int) (40 * density); // layout padding 20dp * 2
        int cellW = (screenW - horizontalPad) / 3;
        int keySize = Math.min((int) (62 * density), (int) (cellW * 0.72f));
        int rowGap = Math.max((int) (3 * density), (cellW - keySize) / 6);
        float digitSp = keySize / density >= 64 ? 28f : 24f;
        float subSp = keySize / density >= 64 ? 10f : 9f;

        for (String[] key : keys) {
            FrameLayout cell = new FrameLayout(requireContext());

            LinearLayout keyView = new LinearLayout(requireContext());
            keyView.setOrientation(LinearLayout.VERTICAL);
            keyView.setGravity(Gravity.CENTER);
            keyView.setBackground(requireContext().getDrawable(R.drawable.bg_dialpad_key));
            keyView.setClickable(true);
            keyView.setFocusable(true);
            keyView.setElevation(density);

            TextView digit = new TextView(requireContext());
            digit.setText(key[0]);
            digit.setTextSize(digitSp);
            digit.setTextColor(requireContext().getColor(R.color.dialpad_key_text));
            digit.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            digit.setGravity(Gravity.CENTER);
            digit.setIncludeFontPadding(false);
            keyView.addView(digit);

            TextView sub = null;
            View spacer = null;
            if (!key[1].isEmpty()) {
                TextView subView = new TextView(requireContext());
                subView.setText(key[1]);
                subView.setTextSize(subSp);
                subView.setTextColor(requireContext().getColor(R.color.dialpad_key_sub));
                subView.setGravity(Gravity.CENTER);
                subView.setLetterSpacing(0.14f);
                subView.setIncludeFontPadding(false);
                subView.setPadding(0, (int) (1 * density), 0, 0);
                keyView.addView(subView);
                sub = subView;
            } else if ("*".equals(key[0]) || "#".equals(key[0])) {
                spacer = new View(requireContext());
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, (int) (subSp * density)));
                keyView.addView(spacer);
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

            FrameLayout.LayoutParams keyLp = new FrameLayout.LayoutParams(keySize, keySize);
            keyLp.gravity = Gravity.CENTER;
            cell.addView(keyView, keyLp);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = keySize + rowGap * 2;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(0, rowGap / 2, 0, rowGap / 2);
            grid.addView(cell, lp);

            KeyHolder holder = new KeyHolder();
            holder.cell = cell;
            holder.keyView = keyView;
            holder.digit = digit;
            holder.sub = sub;
            holder.spacer = spacer;
            keyHolders.add(holder);
        }
    }

    private void applyKeypadMode(boolean compact) {
        if (keyHolders.isEmpty() || !isAdded()) return;
        if (isCompactMode == compact) return;
        isCompactMode = compact;

        if (getView() instanceof ViewGroup) {
            TransitionManager.beginDelayedTransition((ViewGroup) getView());
        }

        float density = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int horizontalPad = (int) (40 * density);
        int cellW = (screenW - horizontalPad) / 3;

        int keySize = compact
                ? Math.min((int) (44 * density), (int) (cellW * 0.52f))
                : Math.min((int) (62 * density), (int) (cellW * 0.72f));
        int rowGap = compact
                ? (int) (2 * density)
                : Math.max((int) (3 * density), (cellW - keySize) / 6);
        float digitSp = compact ? 20f : (keySize / density >= 64 ? 28f : 24f);
        float subSp = compact ? 8f : (keySize / density >= 64 ? 10f : 9f);

        for (KeyHolder h : keyHolders) {
            FrameLayout.LayoutParams keyLp = (FrameLayout.LayoutParams) h.keyView.getLayoutParams();
            if (keyLp != null) {
                keyLp.width = keySize;
                keyLp.height = keySize;
                h.keyView.setLayoutParams(keyLp);
            }
            GridLayout.LayoutParams cellLp = (GridLayout.LayoutParams) h.cell.getLayoutParams();
            if (cellLp != null) {
                cellLp.height = keySize + rowGap * 2;
                cellLp.setMargins(0, rowGap / 2, 0, rowGap / 2);
                h.cell.setLayoutParams(cellLp);
            }
            h.digit.setTextSize(digitSp);
            if (h.sub != null) {
                h.sub.setTextSize(subSp);
            }
            if (h.spacer != null) {
                ViewGroup.LayoutParams spLp = h.spacer.getLayoutParams();
                if (spLp != null) {
                    spLp.height = (int) (subSp * density);
                    h.spacer.setLayoutParams(spLp);
                }
            }
        }

        if (callButton != null) {
            int fabSize = compact ? (int) (50 * density) : (int) (64 * density);
            int iconSize = compact ? (int) (22 * density) : (int) (26 * density);
            callButton.setCustomSize(fabSize);
            callButton.setMaxImageSize(iconSize);
        }
        if (callContainer != null) {
            int pTop = compact ? (int) (4 * density) : (int) (6 * density);
            int pBottom = compact ? (int) (6 * density) : (int) (10 * density);
            callContainer.setPadding(callContainer.getPaddingLeft(), pTop,
                    callContainer.getPaddingRight(), pBottom);
        }
    }

    private void setKeypadExpanded(boolean expanded) {
        if (isKeypadExpanded == expanded) return;
        isKeypadExpanded = expanded;

        if (getView() instanceof ViewGroup) {
            TransitionManager.beginDelayedTransition((ViewGroup) getView());
        }

        if (keypadGrid != null) {
            keypadGrid.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (accountRow != null && accounts.size() >= 2) {
            accountRow.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }
        if (keypadToggle != null) {
            if (expanded) {
                keypadToggle.setImageResource(R.drawable.ic_keyboard_down);
                keypadToggle.setContentDescription(getString(R.string.hide_dialpad));
            } else {
                keypadToggle.setImageResource(R.drawable.ic_dialpad);
                keypadToggle.setContentDescription(getString(R.string.show_dialpad));
            }
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
        if (adapter == null || suggestionsContainer == null) return;
        adapter.rows.clear();
        String q = PhoneNumberFormatter.sanitize(query.trim());
        boolean hasQuery = !query.trim().isEmpty() || !q.isEmpty();

        int limit = 12;
        if (hasQuery) {
            for (ContactRepository.Contact c : allContacts) {
                boolean match = T9.matches(query, c.name, c.number)
                        || (!q.isEmpty() && T9.matches(q, c.name, c.number));
                if (match) {
                    adapter.rows.add(c);
                    if (adapter.rows.size() == limit) break;
                }
            }
        }
        adapter.notifyDataSetChanged();

        boolean hasResults = !adapter.rows.isEmpty();
        if (hasResults) {
            // When search results are shown:
            // Pad buttons go down into compact layout so results are clearly visible
            // and all buttons (keys + call button) are seen properly.
            suggestionsContainer.setVisibility(View.VISIBLE);
            applyKeypadMode(true);
            if (keypadToggle != null) {
                keypadToggle.setVisibility(View.VISIBLE);
            }
            suggestions.post(() -> suggestions.scrollToPosition(0));
        } else {
            // When no search results are shown:
            // Middle zone stays invisible spacer keeping keypad comfortably at the bottom,
            // and keypad returns to full normal size.
            suggestionsContainer.setVisibility(View.INVISIBLE);
            applyKeypadMode(false);
            setKeypadExpanded(true);
            if (keypadToggle != null) {
                keypadToggle.setVisibility(View.GONE);
            }
        }
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
            View.OnClickListener fillNumber = v -> {
                if (c.number != null) {
                    number.setText(c.number);
                    number.setSelection(number.length());
                }
            };
            h.itemView.setOnClickListener(fillNumber);
            if (h.call != null) {
                h.call.setOnClickListener(v -> {
                    if (c.number != null) {
                        number.setText(c.number);
                        number.setSelection(number.length());
                        placeSelected(null);
                    }
                });
            }
        }

        @Override
        public int getItemCount() { return rows.size(); }
    }

    private static class SuggestionHolder extends RecyclerView.ViewHolder {
        final TextView avatar, name, number;
        final ImageView call;
        SuggestionHolder(View v) {
            super(v);
            avatar = v.findViewById(R.id.sug_avatar);
            name = v.findViewById(R.id.sug_name);
            number = v.findViewById(R.id.sug_number);
            call = v.findViewById(R.id.sug_call);
        }
    }
}

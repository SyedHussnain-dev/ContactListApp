package com.example.pulsedialer;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.pulsedialer.util.AvatarUtil;
import com.example.pulsedialer.util.FormatUtil;

import java.lang.ref.WeakReference;

/** Premium in-call UI. Only operates on calls supplied by Telecom. */
public class CallActivity extends AppCompatActivity {

    private static volatile WeakReference<CallActivity> active = new WeakReference<>(null);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView caller, callerAvatar, state, timer;
    private ImageView answer, decline, endCall, mute, speaker, hold, showKeypad;
    private TextView muteLabel, speakerLabel, holdLabel;
    private LinearLayout activeControls;
    private boolean muted = false, speakerOn = false;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refreshUi();
            if (!isFinishing()) handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        active = new WeakReference<>(this);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_call);

        // Edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        boolean light = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.call_root), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(dp(24), dp(24) + bars.top, dp(24), dp(24) + bars.bottom);
            return insets;
        });

        // Find views
        callerAvatar = findViewById(R.id.caller_avatar);
        caller = findViewById(R.id.caller);
        state = findViewById(R.id.call_state);
        timer = findViewById(R.id.call_timer);
        answer = findViewById(R.id.answer);
        decline = findViewById(R.id.decline);
        endCall = findViewById(R.id.end_call);
        mute = findViewById(R.id.mute);
        speaker = findViewById(R.id.speaker);
        hold = findViewById(R.id.hold);
        showKeypad = findViewById(R.id.show_keypad);
        muteLabel = findViewById(R.id.mute_label);
        speakerLabel = findViewById(R.id.speaker_label);
        holdLabel = findViewById(R.id.hold_label);
        activeControls = findViewById(R.id.active_controls);

        // Actions
        answer.setOnClickListener(v -> {
            Call c = PulseInCallService.currentCall;
            if (c != null) c.answer(VideoProfile.STATE_AUDIO_ONLY);
        });
        decline.setOnClickListener(v -> {
            Call c = PulseInCallService.currentCall;
            if (c != null) c.reject(false, null);
            finish();
        });
        endCall.setOnClickListener(v -> {
            Call c = PulseInCallService.currentCall;
            if (c != null) c.disconnect();
            finish();
        });
        mute.setOnClickListener(v -> {
            PulseInCallService s = PulseInCallService.instance;
            if (s != null) {
                muted = !muted;
                s.setMuted(muted);
                muteLabel.setText(muted ? R.string.unmute : R.string.mute);
            }
        });
        speaker.setOnClickListener(v -> {
            PulseInCallService s = PulseInCallService.instance;
            if (s != null) {
                speakerOn = !speakerOn;
                s.setAudioRoute(speakerOn ? CallAudioState.ROUTE_SPEAKER : CallAudioState.ROUTE_EARPIECE);
                speakerLabel.setText(speakerOn ? R.string.speaker_on : R.string.speaker);
            }
        });
        hold.setOnClickListener(v -> {
            Call c = PulseInCallService.currentCall;
            if (c != null) {
                if (c.getState() == Call.STATE_HOLDING) c.unhold();
                else c.hold();
            }
        });
        showKeypad.setOnClickListener(v -> showDtmfKeypad());

        refreshUi();
    }

    private void showDtmfKeypad() {
        String[] digits = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};
        new AlertDialog.Builder(this)
            .setTitle(R.string.dtmf_keypad)
            .setItems(digits, (d, w) -> {
                Call c = PulseInCallService.currentCall;
                if (c != null) {
                    char ch = digits[w].charAt(0);
                    c.playDtmfTone(ch);
                    handler.postDelayed(() -> {
                        Call live = PulseInCallService.currentCall;
                        if (live != null) live.stopDtmfTone();
                    }, 180);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (active.get() == this) active.clear();
        super.onDestroy();
    }

    private void refreshUi() {
        Call c = PulseInCallService.currentCall;
        if (c == null) { finish(); return; }

        Call.Details details = c.getDetails();
        CharSequence callerName = details == null || details.getHandle() == null
                ? null : details.getHandle().getSchemeSpecificPart();
        String name = (callerName == null || callerName.length() == 0)
                ? getString(R.string.unknown_number) : callerName.toString();
        caller.setText(name);
        AvatarUtil.apply(callerAvatar, name);

        int s = c.getState();
        state.setText(stateLabel(s));

        boolean ringing = s == Call.STATE_RINGING;
        answer.setVisibility(ringing ? View.VISIBLE : View.GONE);
        decline.setVisibility(ringing ? View.VISIBLE : View.GONE);
        endCall.setVisibility(ringing ? View.GONE : View.VISIBLE);
        activeControls.setVisibility(ringing ? View.GONE : View.VISIBLE);

        int caps = details == null ? 0 : details.getCallCapabilities();
        boolean canHold = (caps & Call.Details.CAPABILITY_HOLD) != 0;
        hold.setVisibility(canHold ? View.VISIBLE : View.GONE);
        holdLabel.setVisibility(canHold ? View.VISIBLE : View.GONE);
        holdLabel.setText(s == Call.STATE_HOLDING ? R.string.resume : R.string.hold);

        long start = details == null ? 0 : details.getConnectTimeMillis();
        timer.setText(start > 0 && s == Call.STATE_ACTIVE
                ? FormatUtil.duration(Math.max(0, (System.currentTimeMillis() - start) / 1000))
                : "");
    }

    private String stateLabel(int s) {
        switch (s) {
            case Call.STATE_RINGING:       return getString(R.string.call_ringing);
            case Call.STATE_DIALING:       return getString(R.string.call_dialing);
            case Call.STATE_CONNECTING:    return getString(R.string.call_connecting);
            case Call.STATE_ACTIVE:        return getString(R.string.call_active);
            case Call.STATE_HOLDING:       return getString(R.string.call_holding);
            case Call.STATE_DISCONNECTED:  return getString(R.string.call_disconnected);
            default:                       return getString(R.string.call_ongoing);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    static void refresh(android.content.Context c) {
        CallActivity screen = active.get();
        if (screen != null) screen.runOnUiThread(screen::refreshUi);
    }
}

package com.example.pulsedialer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telecom.Call;
import android.telecom.VideoProfile;

/** Handles explicit answer and decline choices from the full-screen call notification. */
public class CallActionReceiver extends BroadcastReceiver {
    public static final String ANSWER="com.example.pulsedialer.ANSWER";
    public static final String DECLINE="com.example.pulsedialer.DECLINE";
    @Override public void onReceive(Context context,Intent intent){Call call=PulseInCallService.currentCall;if(call==null||call.getState()!=Call.STATE_RINGING)return;if(ANSWER.equals(intent.getAction()))call.answer(VideoProfile.STATE_AUDIO_ONLY);else if(DECLINE.equals(intent.getAction()))call.reject(false,null);}
}

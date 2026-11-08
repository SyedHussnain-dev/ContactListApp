package com.example.pulsedialer;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.Configuration;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import java.lang.ref.WeakReference;
import androidx.core.view.WindowInsetsCompat;

/** Minimal in-call UI which only operates on a Call supplied by Telecom. */
public class CallActivity extends AppCompatActivity {
    private static volatile WeakReference<CallActivity> active=new WeakReference<>(null);
    private final Handler handler=new Handler(Looper.getMainLooper());private TextView caller,state,timer;private Button answer,decline,end,mute,speaker,hold;private boolean muted=false,speakerOn=false;private final Runnable tick=new Runnable(){@Override public void run(){refreshUi();if(!isFinishing())handler.postDelayed(this,1000);}};
    @Override protected void onCreate(Bundle b){super.onCreate(b);active=new WeakReference<>(this);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);setContentView(R.layout.activity_call);WindowCompat.setDecorFitsSystemWindows(getWindow(),false);boolean light=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES;WindowInsetsControllerCompat controller=WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());controller.setAppearanceLightStatusBars(light);controller.setAppearanceLightNavigationBars(light);ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.call_root),(view,insets)->{androidx.core.graphics.Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());view.setPadding(dp(28),dp(28)+bars.top,dp(28),dp(28)+bars.bottom);return insets;});caller=findViewById(R.id.caller);state=findViewById(R.id.call_state);timer=findViewById(R.id.call_timer);answer=findViewById(R.id.answer);decline=findViewById(R.id.decline);end=findViewById(R.id.end_call);mute=findViewById(R.id.mute);speaker=findViewById(R.id.speaker);hold=findViewById(R.id.hold);
        answer.setOnClickListener(v->{Call c=PulseInCallService.currentCall;if(c!=null)c.answer(VideoProfile.STATE_AUDIO_ONLY);});decline.setOnClickListener(v->{Call c=PulseInCallService.currentCall;if(c!=null)c.reject(false,null);finish();});end.setOnClickListener(v->{Call c=PulseInCallService.currentCall;if(c!=null)c.disconnect();finish();});mute.setOnClickListener(v->{PulseInCallService s=PulseInCallService.instance;if(s!=null){muted=!muted;s.setMuted(muted);mute.setText(muted?R.string.unmute:R.string.mute);}});speaker.setOnClickListener(v->{PulseInCallService s=PulseInCallService.instance;if(s!=null){speakerOn=!speakerOn;s.setAudioRoute(speakerOn?android.telecom.CallAudioState.ROUTE_SPEAKER:android.telecom.CallAudioState.ROUTE_EARPIECE);speaker.setText(speakerOn?R.string.speaker_on:R.string.speaker);}});hold.setOnClickListener(v->{Call c=PulseInCallService.currentCall;if(c!=null){if(c.getState()==Call.STATE_HOLDING)c.unhold();else c.hold();}});findViewById(R.id.show_keypad).setOnClickListener(v->keypad());refreshUi();}
    private void keypad(){String[] digits={"1","2","3","4","5","6","7","8","9","*","0","#"};new androidx.appcompat.app.AlertDialog.Builder(this).setTitle(R.string.dtmf_keypad).setItems(digits,(d,w)->{Call c=PulseInCallService.currentCall;if(c!=null){char ch=digits[w].charAt(0);c.playDtmfTone(ch);handler.postDelayed(()->{Call live=PulseInCallService.currentCall;if(live!=null)live.stopDtmfTone();},180);}}).setNegativeButton(android.R.string.cancel,null).show();}
    @Override protected void onResume(){super.onResume();handler.post(tick);}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){if(active.get()==this)active.clear();super.onDestroy();}
    private void refreshUi(){Call c=PulseInCallService.currentCall;if(c==null){finish();return;}Call.Details details=c.getDetails();CharSequence name=details==null||details.getHandle()==null?null:details.getHandle().getSchemeSpecificPart();caller.setText(name==null||name.length()==0?getString(R.string.unknown_number):name);int s=c.getState();state.setText(stateLabel(s));boolean ringing=s==Call.STATE_RINGING;answer.setVisibility(ringing?View.VISIBLE:View.GONE);decline.setVisibility(ringing?View.VISIBLE:View.GONE);end.setVisibility(ringing?View.GONE:View.VISIBLE);int caps=details==null?0:details.getCallCapabilities();boolean canHold=(caps&Call.Details.CAPABILITY_HOLD)!=0;hold.setVisibility(canHold?View.VISIBLE:View.GONE);hold.setText(s==Call.STATE_HOLDING?R.string.resume:R.string.hold);long start=details==null?0:details.getConnectTimeMillis();timer.setText(start>0&&s==Call.STATE_ACTIVE?ContactRepository.duration(Math.max(0,(System.currentTimeMillis()-start)/1000)):"");}
    private String stateLabel(int s){switch(s){case Call.STATE_RINGING:return getString(R.string.call_ringing);case Call.STATE_DIALING:return getString(R.string.call_dialing);case Call.STATE_CONNECTING:return getString(R.string.call_connecting);case Call.STATE_ACTIVE:return getString(R.string.call_active);case Call.STATE_HOLDING:return getString(R.string.call_holding);case Call.STATE_DISCONNECTED:return getString(R.string.call_disconnected);default:return getString(R.string.call_ongoing);}}
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    static void refresh(android.content.Context c){CallActivity screen=active.get();if(screen!=null)screen.runOnUiThread(screen::refreshUi);}
}

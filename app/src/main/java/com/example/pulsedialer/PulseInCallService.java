package com.example.pulsedialer;

import android.content.Intent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import android.telecom.Call;
import android.telecom.InCallService;
import androidx.core.content.ContextCompat;

/** Telecom-owned call UI integration. Call state is always sourced from the framework. */
public class PulseInCallService extends InCallService {
    static volatile Call currentCall;
    static volatile PulseInCallService instance;
    private static final String CHANNEL="pulse_calls";
    private final Call.Callback callback=new Call.Callback(){@Override public void onStateChanged(Call call,int state){CallActivity.refresh(getApplicationContext());}@Override public void onDetailsChanged(Call call,Call.Details details){CallActivity.refresh(getApplicationContext());}};
    @Override public void onCreate(){super.onCreate();instance=this;}
    @Override public void onCallAdded(Call call){super.onCallAdded(call);currentCall=call;call.registerCallback(callback);if(call.getState()==Call.STATE_RINGING)showIncomingNotification();else launchCallUi();}
    private void launchCallUi(){Intent intent=new Intent(this,CallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);try{startActivity(intent);}catch(RuntimeException ignored){}}
    private void showIncomingNotification(){
        if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=getSystemService(NotificationManager.class);if(manager==null)return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL,getString(R.string.incoming_calls),NotificationManager.IMPORTANCE_HIGH));
        Intent screen=new Intent(this,CallActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent full=PendingIntent.getActivity(this,4,screen,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Intent answer=new Intent(this,CallActionReceiver.class).setAction(CallActionReceiver.ANSWER);
        Intent decline=new Intent(this,CallActionReceiver.class).setAction(CallActionReceiver.DECLINE);
        PendingIntent answerAction=PendingIntent.getBroadcast(this,5,answer,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent declineAction=PendingIntent.getBroadcast(this,6,decline,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        CharSequence who="";Call.Details details=currentCall==null?null:currentCall.getDetails();if(details!=null&&details.getHandle()!=null)who=details.getHandle().getSchemeSpecificPart();
        Notification.Builder builder=new Notification.Builder(this,CHANNEL);
        Notification n=builder.setSmallIcon(android.R.drawable.sym_action_call).setContentTitle(getString(R.string.call_ringing)).setContentText(who==null||who.length()==0?getString(R.string.unknown_number):who).setCategory(Notification.CATEGORY_CALL).setPriority(Notification.PRIORITY_MAX).setOngoing(true).setFullScreenIntent(full,true).setContentIntent(full).addAction(new Notification.Action.Builder(0,getString(R.string.answer),answerAction).build()).addAction(new Notification.Action.Builder(0,getString(R.string.decline),declineAction).build()).build();
        manager.notify(41,n);
    }
    @Override public void onCallRemoved(Call call){call.unregisterCallback(callback);if(currentCall==call)currentCall=null;NotificationManager nm=getSystemService(NotificationManager.class);if(nm!=null)nm.cancel(41);CallActivity.refresh(getApplicationContext());super.onCallRemoved(call);}
    @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}
}

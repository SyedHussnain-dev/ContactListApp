package com.example.pulsedialer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Device-provider access. Call from a worker thread; provider data is never cached remotely. */
public final class ContactRepository {
    private ContactRepository() {}
    public static final class Contact {
        public final long id; public final String name, number; public final Uri photo; public final boolean starred;
        Contact(long id,String name,String number,Uri photo,boolean starred){this.id=id;this.name=name;this.number=number;this.photo=photo;this.starred=starred;}
    }
    public static final class Recent {
        public final String name,number; public final int type; public final long date,duration; public final String account;
        Recent(String n,String p,int t,long d,long seconds,String a){name=n;number=p;type=t;date=d;duration=seconds;account=a;}
        public String kind(){switch(type){case CallLog.Calls.INCOMING_TYPE:return "Incoming";case CallLog.Calls.OUTGOING_TYPE:return "Outgoing";case CallLog.Calls.MISSED_TYPE:return "Missed";case CallLog.Calls.REJECTED_TYPE:return "Rejected";default:return "Call";}}
    }
    public static List<Contact> contacts(Context c) {
        List<Contact> rows=new ArrayList<>(); Uri uri=ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection={ContactsContract.CommonDataKinds.Phone.CONTACT_ID,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.PHOTO_URI,ContactsContract.Contacts.STARRED};
        try(Cursor x=c.getContentResolver().query(uri,projection,null,null,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" COLLATE LOCALIZED ASC")){
            if(x!=null)while(x.moveToNext())rows.add(new Contact(x.getLong(0),x.getString(1),x.getString(2),x.isNull(3)?null:Uri.parse(x.getString(3)),x.getInt(4)!=0));
        }catch(SecurityException ignored){}
        String[] plain={ContactsContract.Contacts._ID,ContactsContract.Contacts.DISPLAY_NAME,ContactsContract.Contacts.PHOTO_URI,ContactsContract.Contacts.STARRED};
        try(Cursor x=c.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,plain,ContactsContract.Contacts.HAS_PHONE_NUMBER+"=0",null,ContactsContract.Contacts.DISPLAY_NAME+" COLLATE LOCALIZED ASC")){
            if(x!=null)while(x.moveToNext())rows.add(new Contact(x.getLong(0),x.getString(1),null,x.isNull(2)?null:Uri.parse(x.getString(2)),x.getInt(3)!=0));
        }catch(SecurityException ignored){}
        return rows;
    }
    public static List<Recent> recents(Context c) {
        List<Recent> rows=new ArrayList<>(); String[] p={CallLog.Calls.CACHED_NAME,CallLog.Calls.NUMBER,CallLog.Calls.TYPE,CallLog.Calls.DATE,CallLog.Calls.DURATION,CallLog.Calls.PHONE_ACCOUNT_ID};
        try(Cursor x=c.getContentResolver().query(CallLog.Calls.CONTENT_URI,p,null,null,CallLog.Calls.DATE+" DESC")){
            if(x!=null)while(x.moveToNext())rows.add(new Recent(x.getString(0),x.getString(1),x.getInt(2),x.getLong(3),x.getLong(4),x.getString(5)));
        }catch(SecurityException ignored){} return rows;
    }
    public static String dateLabel(long millis){Date now=new Date();java.text.SimpleDateFormat day=new java.text.SimpleDateFormat("yyyyMMdd",Locale.getDefault());java.util.Calendar c=java.util.Calendar.getInstance();c.setTime(now);String today=day.format(now);c.add(java.util.Calendar.DAY_OF_YEAR,-1);String yesterday=day.format(c.getTime());String value=day.format(new Date(millis));if(value.equals(today))return "Today · "+DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(millis));if(value.equals(yesterday))return "Yesterday · "+DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(millis));return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(millis));}
    public static String duration(long seconds){return String.format(Locale.getDefault(),"%d:%02d",seconds/60,seconds%60);}
}

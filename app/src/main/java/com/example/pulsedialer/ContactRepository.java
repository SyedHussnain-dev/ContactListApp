package com.example.pulsedialer;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.provider.ContactsContract;

import com.example.pulsedialer.util.FormatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Device-provider access for contacts and call history.
 * All methods must be called from a background thread.
 * Data is never cached remotely or sent to any server.
 */
public final class ContactRepository {
    private ContactRepository() {}

    // ── Contact model ──────────────────────────────────────────────────

    public static final class Contact {
        public final long id;
        public final String name;
        public final String number;
        public final Uri photo;
        public final boolean starred;
        public final String email;

        Contact(long id, String name, String number, Uri photo, boolean starred, String email) {
            this.id = id;
            this.name = name;
            this.number = number;
            this.photo = photo;
            this.starred = starred;
            this.email = email;
        }
    }

    // ── Recent call model ──────────────────────────────────────────────

    public static final class Recent {
        public final String name;
        public final String number;
        public final int type;
        public final long date;
        public final long duration;
        public final String account;

        Recent(String n, String p, int t, long d, long seconds, String a) {
            name = n; number = p; type = t; date = d; duration = seconds; account = a;
        }

        public String kind() {
            switch (type) {
                case CallLog.Calls.INCOMING_TYPE: return "Incoming";
                case CallLog.Calls.OUTGOING_TYPE: return "Outgoing";
                case CallLog.Calls.MISSED_TYPE:   return "Missed";
                case CallLog.Calls.REJECTED_TYPE: return "Rejected";
                default: return "Call";
            }
        }

        public boolean isMissed() { return type == CallLog.Calls.MISSED_TYPE; }
        public boolean isRejected() { return type == CallLog.Calls.REJECTED_TYPE; }
    }

    // ── Contacts query ─────────────────────────────────────────────────

    public static List<Contact> contacts(Context c) {
        List<Contact> rows = new ArrayList<>();

        // Contacts with phone numbers
        Uri phoneUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] phoneProjection = {
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.Contacts.STARRED
        };
        try (Cursor cursor = c.getContentResolver().query(phoneUri, phoneProjection,
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    rows.add(new Contact(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.isNull(3) ? null : Uri.parse(cursor.getString(3)),
                        cursor.getInt(4) != 0,
                        null
                    ));
                }
            }
        } catch (SecurityException ignored) {}

        // Contacts without phone numbers (email-only, etc.)
        String[] contactProjection = {
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.STARRED
        };
        try (Cursor cursor = c.getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI, contactProjection,
                ContactsContract.Contacts.HAS_PHONE_NUMBER + "=0",
                null,
                ContactsContract.Contacts.DISPLAY_NAME + " COLLATE LOCALIZED ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    rows.add(new Contact(
                        cursor.getLong(0),
                        cursor.getString(1),
                        null,
                        cursor.isNull(2) ? null : Uri.parse(cursor.getString(2)),
                        cursor.getInt(3) != 0,
                        null
                    ));
                }
            }
        } catch (SecurityException ignored) {}

        return rows;
    }

    /** Load emails for a specific contact. */
    public static List<String> emailsForContact(Context c, long contactId) {
        List<String> emails = new ArrayList<>();
        Uri uri = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
        String[] proj = { ContactsContract.CommonDataKinds.Email.ADDRESS };
        String sel = ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?";
        try (Cursor cursor = c.getContentResolver().query(uri, proj, sel,
                new String[]{String.valueOf(contactId)}, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String email = cursor.getString(0);
                    if (email != null && !email.isEmpty()) emails.add(email);
                }
            }
        } catch (SecurityException ignored) {}
        return emails;
    }

    /** Load all phone numbers with labels for a contact. */
    public static List<String[]> phonesForContact(Context c, long contactId) {
        List<String[]> phones = new ArrayList<>();
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] proj = {
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        };
        String sel = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?";
        try (Cursor cursor = c.getContentResolver().query(uri, proj, sel,
                new String[]{String.valueOf(contactId)}, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String number = cursor.getString(0);
                    int typeInt = cursor.getInt(1);
                    String customLabel = cursor.getString(2);
                    String label = (String) ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            c.getResources(), typeInt, customLabel);
                    phones.add(new String[]{number, label});
                }
            }
        } catch (SecurityException ignored) {}
        return phones;
    }

    // ── Recents query ──────────────────────────────────────────────────

    public static List<Recent> recents(Context c) {
        List<Recent> rows = new ArrayList<>();
        String[] projection = {
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_ID
        };
        try (Cursor cursor = c.getContentResolver().query(
                CallLog.Calls.CONTENT_URI, projection,
                null, null,
                CallLog.Calls.DATE + " DESC")) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    rows.add(new Recent(
                        cursor.getString(0),
                        cursor.getString(1),
                        cursor.getInt(2),
                        cursor.getLong(3),
                        cursor.getLong(4),
                        cursor.getString(5)
                    ));
                }
            }
        } catch (SecurityException ignored) {}
        return rows;
    }

    // ── Formatting delegates ───────────────────────────────────────────

    public static String dateLabel(long millis) { return FormatUtil.dateLabel(millis); }
    public static String duration(long seconds) { return FormatUtil.duration(seconds); }
}

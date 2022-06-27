package gr.ictpro.jsalatas.agendawidget.model.calendar;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.support.annotation.ColorInt;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import gr.ictpro.jsalatas.agendawidget.application.AgendaWidgetApplication;
import gr.ictpro.jsalatas.agendawidget.model.EventItem;
import gr.ictpro.jsalatas.agendawidget.model.task.TaskEvent;
import gr.ictpro.jsalatas.agendawidget.ui.AgendaWidgetConfigureActivity;
import gr.ictpro.jsalatas.agendawidget.utils.DateUtils;
import gr.ictpro.jsalatas.agendawidget.utils.ISO8601Utilities;

import static gr.ictpro.jsalatas.agendawidget.utils.ISO8601Utilities.dateToSeconds;
import static gr.ictpro.jsalatas.agendawidget.utils.ISO8601Utilities.secondsToDate;
import static java.lang.Long.max;

public class ExtendedCalendarEvent extends CalendarEvent {

    /* [MB] data exchange with calendar providers */
    //finel static String EXTENDED_PROPERTIES_DEFAULT_NAMESPACE = "vnd.android.cursor.item/vnd.ical4android.unknown-property";
    public static final String EXTENDED_PROPERTIES_DEFAULT_NAMESPACE = "vnd.android.cursor.item/vnd.i4a.unkp";   // [MB] shortened due to limitations of Google Calendar provider

    final static String infinityDateStr="99991231T235859Z";
    final static long infinityDateMillis=253402300739000L;


    // absoluteTS==-1L means reminder is relative;
    // if absoluteTS!=-1L, then minutes should be disregarded when scheduling the notifications
    public static class Reminder {
        public final static int REMINDER_NOTIFICATION=0;
        public final static int REMINDER_EMAIL=1;

        int minutes;
        int type;
        long ack=-1L;
        long absoluteTS=-1L;

        public Reminder(int minutes,int type) {
            this.minutes=minutes;
            this.type=type;
        }

        public Reminder(int minutes,int type,long ack, long absoluteTS) {
            this.minutes=minutes;
            this.type=type;
            this.ack=ack;
            this.absoluteTS=absoluteTS;
        }
    }


    private final int calendarId;
    private final String rrule;
    private final List<Reminder> reminders;
    private final Date trigger;

    protected ExtendedCalendarEvent(long id, int color, String title, String location, String description, Date startDate, Date endDate, boolean allDay, int calendarId, String rrule,List<Reminder> reminders, Date trigger) {
        super(id, color, title, location, description, startDate, endDate, allDay);
        this.calendarId=calendarId;
        this.rrule=rrule;
        this.reminders=reminders;
        this.trigger=trigger;
    }

    public int getCalendarId() {
        return(calendarId);
    }

    public String getRrule() {
        return(rrule);
    }

    public List<Reminder> getReminders() {
        return(reminders);
    }

    public Date getTrigger() { return(trigger); }

    public boolean isTriggered() {
        Date now = GregorianCalendar.getInstance().getTime();
        return(now.compareTo(getTrigger())>=0);
    }


    public void dumpExtendedPropertiesForEvent() {
        Log.v("MYCALENDAR", "Reminders for event titled " + getTitle());
        for(Reminder r:getReminders()) {
            Log.v("MYCALENDAR", "minutes="+r.minutes+"; type="+r.type+"; ack="+r.ack+"; absoluteTS="+r.absoluteTS);
        }
        Log.v("MYCALENDAR", "============================");

        String ext_selection = CalendarContract.ExtendedProperties.EVENT_ID + " = " + getId();
        final String[] ext_projection = new String[]{
                CalendarContract.ExtendedProperties._ID,
                CalendarContract.ExtendedProperties.NAME,
                CalendarContract.ExtendedProperties.VALUE,
        };
        ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();
        Uri.Builder builder = CalendarContract.ExtendedProperties.CONTENT_URI.buildUpon();
        Log.v("MYCALENDAR", "Extended properties for event titled " + getTitle());
        Cursor cur = cr.query(builder.build(), ext_projection, ext_selection, null, null);
        while (cur.moveToNext()) {
            long id = cur.getLong(0);
            String n = cur.getString(1);
            String v = cur.getString(2);
            Log.v("MYCALENDAR", "id="+id+"; name="+n+"; value="+v);
        }
        cur.close();
        Log.v("MYCALENDAR", "============================");
    }

    private static boolean checkPermissions() {
        int permissionCheck1 = ContextCompat.checkSelfPermission(AgendaWidgetApplication.getContext(), Manifest.permission.READ_CALENDAR);
        int permissionCheck2 = ContextCompat.checkSelfPermission(AgendaWidgetApplication.getContext(), Manifest.permission.WRITE_CALENDAR);
        return (permissionCheck1 != PackageManager.PERMISSION_DENIED) && (permissionCheck2 != PackageManager.PERMISSION_DENIED);
    }

    public void runTests() {
        if (!checkPermissions()) {
            return;
        }

        // enable run5+run6 to remove the ack from a Test event
        boolean run1=false;
        boolean run2=false;
        boolean run3=false;
        boolean run4=false;
        boolean run5=true;
        boolean run6=true;
        boolean run7=false;

        if (run1) {
            long curTimeSec = System.currentTimeMillis() / 1000L;

            boolean is_repeat = (getRrule() != null);
            Log.v("MYCALENDAR", "in dismissCalDAVEventReminders(): setting properties for event " + getTitle() + "; (is_repeat=" + is_repeat + ")");

            String dtStr = infinityDateStr;
            if (is_repeat) {
                dtStr = ISO8601Utilities.formatDateTimeCompact(secondsToDate(max(dateToSeconds(getEndDate()), curTimeSec) + 1));
            }
            addOrUpdateExtendedProperty("private:X-MOZ-LASTACK", dtStr);
            addOrUpdateExtendedPropertyJSonEncoded("X-MOZ-LASTACKJSON", dtStr);
        }

        if (run2) {
            String s="a:1\r\nb:2\r\nc:3\r\nd:4\r\n";

            String t="b:2\r\nc:3\r\nd:4";
            String r;
            if ((r=deleteFromMultilinePropertyValue(s, "a")).equals(t)) {
                Log.v("MYCALENDAR", "Test 1 PASSED");
            }
            else {
                Log.v("MYCALENDAR", "Test 1 FAILED. Returned: "+r+"; expected: "+t);
            }

            t="a:1\r\nc:3\r\nd:4";
            if ((r=deleteFromMultilinePropertyValue(s, "b")).equals(t)) {
                Log.v("MYCALENDAR", "Test 2 PASSED");
            }
            else {
                Log.v("MYCALENDAR", "Test 2 FAILED. Returned: "+r+"; expected: "+t);
            }

            t="a:1\r\nb:2\r\nc:3";
            if ((r=deleteFromMultilinePropertyValue(s, "d")).equals(t)) {
                Log.v("MYCALENDAR", "Test 3 PASSED");
            }
            else {
                Log.v("MYCALENDAR", "Test 3 FAILED. Returned: "+r+"; expected: "+t);
            }

            if ((r=deleteFromMultilinePropertyValue(s, "z")).equals(s)) {
                Log.v("MYCALENDAR", "Test 4 PASSED");
            }
            else {
                Log.v("MYCALENDAR", "Test 4 FAILED. Returned: "+r+"; expected: "+s);
            }
        }

        if (run3) {
            Log.v("MYCALENDAR", "deleteExtendedPropertyJSonEncoded with name X-MOZ-LASTACK");
            deleteExtendedPropertyJSonEncoded("X-MOZ-LASTACK", null);
            Log.v("MYCALENDAR", "done with deleteExtendedPropertyJSonEncoded with name X-MOZ-LASTACK. Run dump to check that it's gone");
        }

        if (run4) {
            Log.v("MYCALENDAR", "update private:X-MOZ-LASTACK to 123");
            addOrUpdateExtendedProperty("private:X-MOZ-LASTACK","123");
            Log.v("MYCALENDAR", "done with update private:X-MOZ-LASTACK to 123. Run dump to check that it's updated");
        }

        if (run5) {
            Log.v("MYCALENDAR", "update private:X-MOZ-LASTACK to 123");
            long id=findExtendedProperty("private:X-MOZ-LASTACK",null);
            deleteExtendedProperty(id);
            Log.v("MYCALENDAR", "done with update private:X-MOZ-LASTACK to 123. Run dump to check that it's updated");
        }

        if (run6) {
            Log.v("MYCALENDAR", "run deleteDavx5PropertiesFromGoogleCalendar()");
            deleteDavx5PropertiesFromGoogleCalendar();
            Log.v("MYCALENDAR", "done with deleteDavx5PropertiesFromGoogleCalendar. Run dump to check that it has no properties with name "+EXTENDED_PROPERTIES_DEFAULT_NAMESPACE);
        }

        if (run7) {
            //secondsToDate(max(dateToSeconds(calendarEvent.getEndDate()), curTimeSec) + 1)
            Date dt=new Date();
            Log.v("MYCALENDAR", "full/compact date formatting test. Date="+dt);
            String dtStr;
            dtStr=ISO8601Utilities.formatDateTime(dt);
            Log.v("MYCALENDAR", "full date formatting test returned "+dtStr);
            dtStr=ISO8601Utilities.formatDateTimeCompact(dt);
            Log.v("MYCALENDAR", "compact date formatting test returned "+dtStr);
        }
    }


    long findExtendedProperty(String name, String value) {
        Log.v("MYCALENDAR", "in findExtendedProperty(" + name + "," + value + ")");

        String ext_selection = CalendarContract.ExtendedProperties.EVENT_ID + " = " + getId() + " AND " + CalendarContract.ExtendedProperties.NAME + " = \"" + name + "\"";

        final String[] ext_projection = new String[]{
                CalendarContract.ExtendedProperties._ID,
                CalendarContract.ExtendedProperties.VALUE,
        };

        ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();

        long prop_id = -1L;
        Uri.Builder builder = CalendarContract.ExtendedProperties.CONTENT_URI.buildUpon();

        Cursor cur = cr.query(builder.build(), ext_projection, ext_selection, null, null);
        while (prop_id == -1L && cur.moveToNext()) {
            String v = cur.getString(1);

            Log.v("MYCALENDAR", "in findExtendedProperty(" + name + "," + value + ",id=" + getId() + "): comparing " + value + " with " + v);
            if (value == null || v.equals(value)) {
                Log.v("MYCALENDAR", "in findExtendedProperty(" + name + "," + value + ",id=" + getId() + "): property found");
                prop_id = cur.getLong(0);
            }
        }
        cur.close();
        if (prop_id == -1L) {
            Log.v("MYCALENDAR", "in findExtendedProperty(" + name + "," + value + ",id=" + getId() + "): property not found. Aborting");
        }
        return (prop_id);
    }

    // Returns: -1L if not found; otherwise, it returns the property's ID
    long findExtendedPropertyJSonEncoded(String name, String value) {
        Log.v("MYCALENDAR", "in findExtendedProperty(" + name + "," + value + ")");

        String ext_selection = CalendarContract.ExtendedProperties.EVENT_ID + " = " + getId() + " AND " + CalendarContract.ExtendedProperties.NAME + " = \"" + EXTENDED_PROPERTIES_DEFAULT_NAMESPACE + "\"";

        final String[] ext_projection = new String[]{
                CalendarContract.ExtendedProperties._ID,
                CalendarContract.ExtendedProperties.VALUE,
        };

        ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();

        long prop_id = -1L;
        String t = "[\"" + name + "\"," + ((value == null) ? "" : ("\"" + value + "\"]"));
        Uri.Builder builder = CalendarContract.ExtendedProperties.CONTENT_URI.buildUpon();

        Cursor cur = cr.query(builder.build(), ext_projection, ext_selection, null, null);
        while (prop_id == -1L && cur.moveToNext()) {
            String v = cur.getString(1);

            Log.v("MYCALENDAR", "in findExtendedPropertyJSonEncoded(" + name + "," + value + "): comparing "+t+" with " + v);
            if (v.startsWith(t)) {
                Log.v("MYCALENDAR", "in findExtendedPropertyJSonEncoded(" + name + "," + value + "): property found");
                prop_id = cur.getLong(0);
            }
            else {
                Log.v("MYCALENDAR", "in findExtendedProperty(" + name + "," + value + ",id=" + getId() + "): comparing " + value + " with " + v);
                if (value == null || v.equals(value)) {
                    Log.v("MYCALENDAR", "in findExtendedProperty(" + name + "," + value + ",id=" + getId() + "): property found");
                    prop_id = cur.getLong(0);
                }
            }
        }
        cur.close();
        if (prop_id == -1L) {
            Log.v("MYCALENDAR", "in findExtendedPropertyJSonEncoded(" + name + "," + value + "): property not found. Aborting");
        }
        return (prop_id);
    }

    static String getAccountType(String accountName) {
        String selection = CalendarContract.Calendars.ACCOUNT_NAME + " = \"" + accountName + "\"";

        final String[] projection = new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.ACCOUNT_TYPE,
        };

        final ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();
        final Uri uri = CalendarContract.Calendars.CONTENT_URI;
        Cursor cur = cr.query(uri, projection, selection, null, null);

        String t=null;
        while (t==null & cur.moveToNext()) {
            t = cur.getString(1);
        }
        cur.close();
        return(t);
    }

    Uri.Builder syncAdapterURI(android.net.Uri uri, int calendarId) {
        String acct_name=null;
        String acct_type=null;
        for (gr.ictpro.jsalatas.agendawidget.model.calendar.Calendar c : Calendars.getCalendarList()) {
            if (c.getId()==calendarId) {
                acct_name=c.getAccountName();
                //acct_type=c.get
                break;
            }
        }
        if (acct_name==null) {
            Log.e("MYCALENDAR", "in syncAdapterURI(): calendar with id "+calendarId+" is unknown");
            return(null);
        }
        return(uri.buildUpon()
                .appendQueryParameter(android.provider.ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, acct_name))
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, getAccountType(acct_name));
    }

    public void addExtendedProperty(String name, String value) {
        ContentValues content = new ContentValues();
        content.put(CalendarContract.ExtendedProperties.NAME, name);
        content.put(CalendarContract.ExtendedProperties.VALUE, value);
        content.put(CalendarContract.ExtendedProperties.EVENT_ID, getId());

        ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();

        Uri.Builder builder = syncAdapterURI(CalendarContract.ExtendedProperties.CONTENT_URI, getCalendarId());
        if (builder==null) {
            Log.e("MYCALENDAR", "in addExtendedProperty(): syncAdapterURI() failed. Aborting insertion in event " + getId() + " of name=" + name + "; value=" + value);
            return;
        }
        try {
            cr.insert(builder.build(), content);
        }
        catch (Exception x) {
            Log.e("MYCALENDAR", "in addExtendedProperty(): inserting in event "+getId()+" failed for name="+name+"; value="+value);
            Log.e("MYCALENDAR", "in addExtendedProperty(): failure exception="+x.toString());
        }

    }

    public void updateExtendedProperty(long exp_prop_id,String value) {
        ContentValues content = new ContentValues();
        content.put(CalendarContract.ExtendedProperties.VALUE, value);

        ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();

        Uri u= ContentUris.withAppendedId(CalendarContract.ExtendedProperties.CONTENT_URI, exp_prop_id);

        Uri.Builder builder = syncAdapterURI(u, getCalendarId());
        if (builder==null) {
            Log.e("MYCALENDAR", "in updateExtendedProperty(): syncAdapterURI() failed. Aborting update in event " + getId() + " to value=" + value);
            return;
        }
        try {
            // TODO: see if it is correct that we call update() with empty where arg. Maybe that's where we need to put the ext_prop_id?
            cr.update(builder.build(), content, null, null);
        }
        catch (Exception x) {
            Log.e("MYCALENDAR", "in updateExtendedProperty(): updating event "+getId()+" failed for value="+value);
        }

    }

    public void addOrUpdateExtendedProperty(String name, String v) {
        long prop_id=findExtendedProperty(name, null);

        if (prop_id==-1L) {
            addExtendedProperty(name, v);
        }
        else {
            updateExtendedProperty(prop_id, v);
        }
    }

    void addOrUpdateExtendedPropertyJSonEncoded(String name, String v) {
        long prop_id=findExtendedPropertyJSonEncoded(name, null);

        if (prop_id==-1L) {
            addExtendedProperty(EXTENDED_PROPERTIES_DEFAULT_NAMESPACE,"[\""+name+"\",\""+v+"\"]");
        }
        else {
            updateExtendedProperty(prop_id, "[\""+name+"\",\""+v+"\"]");
        }
    }

    static String deleteFromMultilinePropertyValue(String blob, String key) {
        boolean found=false;

        String[] lines=blob.replace("\r", "").split("\n");
        List<String> result = new ArrayList(Arrays.asList(lines));
        for (int index=lines.length-1; index>=0;index--) {
            if (lines[index].startsWith(key + ":")) {
                result.remove(index);
                found=true;
            }
        }
        if (!found) return(blob); // save time and allow callers to use String.equals() to check for changes

        StringBuffer sb = new StringBuffer();
        String sep="";
        for (String s : result) {
            sb.append(sep);
            sb.append(s);
            sep="\r\n";
        }
        return(sb.toString());
    }

    public void deleteExtendedProperty(long prop_id) {
        if (prop_id==-1L) return;

        Log.v("MYCALENDAR", "in deleteExtendedProperty(" + prop_id + ")");

        ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();

        Uri u=ContentUris.withAppendedId(CalendarContract.ExtendedProperties.CONTENT_URI, prop_id);

        Uri.Builder builder = syncAdapterURI(u, getCalendarId());
        if (builder==null) {
            Log.e("MYCALENDAR", "in deleteExtendedProperty(): syncAdapterURI() failed. Aborting update in event " + getId());
            return;
        }
        try {
            // TODO: see if it is correct that we call update() with empty where arg. Maybe that's where we need to put the ext_prop_id?
            cr.delete(builder.build(), null, null);
            Log.v("MYCALENDAR", "in deleteExtendedProperty(" + prop_id + "): done");
        }
        catch (Exception x) {
            Log.e("MYCALENDAR", "in deleteExtendedProperty(" + prop_id + "): failed");
        }
    }

    public void deleteExtendedPropertyJSonEncoded(String name, String value) {
        Log.v("MYCALENDAR", "in deleteExtendedPropertyJSonEncoded(" + name + "," + value + ")");
        long prop_id = findExtendedPropertyJSonEncoded(name, value);
        if (prop_id == -1L) return;
        deleteExtendedProperty(prop_id);
        Log.v("MYCALENDAR", "in deleteExtendedPropertyJSonEncoded(" + name + "," + value + "): done");
    }

    // Returns: -1L if no ABSOLUTE extended property was found; otherwise: the property's ID
    static long getAbsoluteExtPropID(long eventRemoteID, int method, int minutes) {
        String ext_selection = CalendarContract.ExtendedProperties.EVENT_ID + " = " + eventRemoteID + " AND " + CalendarContract.ExtendedProperties.NAME + " = \"" + EXTENDED_PROPERTIES_DEFAULT_NAMESPACE + "\"";

        final String[] ext_projection = new String[]{
                CalendarContract.ExtendedProperties._ID,
                CalendarContract.ExtendedProperties.VALUE,
        };

        String ACK_SEP = ";";
        boolean is_absolute = false;
        long absolute_extprop_id = -1L;
        ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();
        Uri.Builder builder = CalendarContract.ExtendedProperties.CONTENT_URI.buildUpon();
        Cursor cur = cr.query(builder.build(), ext_projection, ext_selection, null, null);
        while (absolute_extprop_id == -1L && cur.moveToNext()) {
            String v = cur.getString(1);
            Log.v("MYCALENDAR", "absolute check. Looking at: " + v);

            if (v.equals("[\"X-CALDAV-ANDROID-ABSOLUTE\",\"" + method + ACK_SEP + minutes + "\"]")) {
                Log.v("MYCALENDAR", "it is absolute! method=" + method + "; min=" + minutes);
                is_absolute = true;
                absolute_extprop_id = cur.getLong(0);
            }
        }
        cur.close();
        if (!is_absolute)
            Log.v("MYCALENDAR", "it is NOT absolute! method=" + method + "; min=" + minutes);
        return(absolute_extprop_id);
    }

    /* Adding davx5-encoded extended properties to Google Calendar calendars results in
     * those properties being automatically renamed private:<EXTENDED_PROPERTIES_DEFAULT_NAMESPACE>
     * by the Google server. Those properties are useless, so here we remove them to avoid
     * polluting the event.
     */
    public void deleteDavx5PropertiesFromGoogleCalendar() {
        Log.v("MYCALENDAR", "in deleteDavx5PropertiesFromGoogleCalendar(): beginning to remove davx5 properties if this is a Google Calendar");

        // TODO: make sure the event ID is the right one. It was different in the old code
        String ext_selection = CalendarContract.ExtendedProperties.EVENT_ID + " = " + getId() + " AND " + CalendarContract.ExtendedProperties.NAME + " = \"" + EXTENDED_PROPERTIES_DEFAULT_NAMESPACE + "\"";

        final String[] ext_projection = new String[]{
                CalendarContract.ExtendedProperties._ID,
                CalendarContract.ExtendedProperties.VALUE,
        };

        ContentResolver cr = AgendaWidgetApplication.getContext().getContentResolver();
        Uri.Builder builder = CalendarContract.ExtendedProperties.CONTENT_URI.buildUpon();
        Cursor cur = cr.query(builder.build(), ext_projection, ext_selection, null, null);
        while (cur.moveToNext()) {
            deleteExtendedProperty(cur.getLong(0));
            Log.v("MYCALENDAR", "in deleteDavx5PropertiesFromGoogleCalendar(): deleting extended property with name "+EXTENDED_PROPERTIES_DEFAULT_NAMESPACE+", value="+cur.getString(1));
        }
        cur.close();
        Log.v("MYCALENDAR", "in deleteDavx5PropertiesFromGoogleCalendar(): done removing davx5 properties if this is a Google Calendar");
    }


    @Override
    public int compareTo(EventItem o) {
        if (!(o instanceof ExtendedCalendarEvent)) {
            return(super.compareTo(o));
        }

        // triggered events come first
        boolean tr1=isTriggered();
        boolean tr2=((ExtendedCalendarEvent)o).isTriggered();
        if (tr1==tr2) return(super.compareTo(o));
        if (tr1) return(-1);
        return(1);
    }

}

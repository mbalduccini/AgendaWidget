package gr.ictpro.jsalatas.agendawidget.utils;

import android.util.Log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/*
 * https://stackoverflow.com/questions/6543174/how-can-i-parse-utc-date-time-string-into-something-more-readable
 * https://web.archive.org/web/20180711023953/http://developer.marklogic.com:80/learn/2004-09-dates
 */

public class ISO8601Utilities {
    private static SimpleDateFormat fullFormat=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static SimpleDateFormat compactFormat=new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");

    public static Date parseDateTime(String datetime,SimpleDateFormat m_ISO8601Local) {
        m_ISO8601Local.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            return(m_ISO8601Local.parse(datetime));
        }
        catch(Exception x) {
            Log.w("MYCALENDAR","ptd4: "+datetime+"; "+m_ISO8601Local);
            return(null);
        }
    }

    public static Date parseDateTime(String datetime) {
        return(parseDateTime(datetime,fullFormat));
    }

    public static Date parseDateTimeCompact(String datetime) {
        return(parseDateTime(datetime,compactFormat));
    }

    public static String formatDateTime() {
        return formatDateTime(new Date());
    }

    public static String formatDateTime(Date date) {
        return formatDateTime(date,fullFormat);
    }

    public static String formatDateTimeCompact() {
        return formatDateTimeCompact(new Date());
    }

    public static String formatDateTimeCompact(Date date) {
        return formatDateTime(date,compactFormat);
    }

    public static String formatDateTime(Date date,SimpleDateFormat m_ISO8601Local) {
        if (date == null) {
            return formatDateTime(new Date());
        }

        // format in (almost) ISO8601 format
        String dateStr = m_ISO8601Local.format(date);

        return(dateStr);
        /* COMMENTED: we do not use time zones, just "Z" (I think)

        // remap the timezone from 0000 to 00:00 (starts at char 22)
        return dateStr.substring(0, 22)
                + ":" + dateStr.substring(22);
         */
    }

    public static long dateToMillis(Date d) {
        return((long)(d.getTime()));
    }

    public static long dateToSeconds(Date d) {
        return((long)(d.getTime()/1000L));
    }

    public static Date millisToDate(long ts) {
        java.util.Calendar calendarInstance = GregorianCalendar.getInstance();
        calendarInstance.setTimeInMillis(ts);
        return(calendarInstance.getTime());
    }

    public static Date secondsToDate(long ts) {
        return(millisToDate(ts*1000L));
    }
}

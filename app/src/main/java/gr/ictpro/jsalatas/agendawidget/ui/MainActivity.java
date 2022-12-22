package gr.ictpro.jsalatas.agendawidget.ui;

import android.app.Activity;
import android.app.Notification;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.CalendarContract;
import android.support.annotation.ColorInt;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Timer;
import java.util.TimerTask;

import gr.ictpro.jsalatas.agendawidget.BuildConfig;
import gr.ictpro.jsalatas.agendawidget.R;
import gr.ictpro.jsalatas.agendawidget.application.AgendaWidgetApplication;
import gr.ictpro.jsalatas.agendawidget.model.EventItem;
import gr.ictpro.jsalatas.agendawidget.model.calendar.CalendarEvent;
import gr.ictpro.jsalatas.agendawidget.model.calendar.DayGroup;
import gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendarEvent;
import gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendars;
import gr.ictpro.jsalatas.agendawidget.model.settings.Settings;
import gr.ictpro.jsalatas.agendawidget.model.task.TaskContract;
import gr.ictpro.jsalatas.agendawidget.model.task.TaskEvent;
import gr.ictpro.jsalatas.agendawidget.model.task.TaskProvider;
import gr.ictpro.jsalatas.agendawidget.utils.DateUtils;

import static gr.ictpro.jsalatas.agendawidget.ui.AgendaUpdateService.NOTIFICATION_CHANNEL_ID;
import static java.lang.Integer.min;

public class MainActivity extends AppCompatActivity {
    private Context context;

    private static final String ACTION_FORCE_UPDATE = "gr.ictpro.jsalatas.agendawidget.action.FORCE_UPDATE";
    private static final String ACTION_PROVIDER_REMOVED = "gr.ictpro.jsalatas.agendawidget.action.PROVIDER_REMOVED";
    private static final String EXTRA_PACKAGE_NAME = "gr.ictpro.jsalatas.agendawidget.action.EXTRA_PACKAGE_NAME";

    // TODO for settings; remove
//    public final static int appWidgetId = AgendaUpdateService.appWidgetId;

    boolean mBounded;
    AgendaUpdateService mServer;

    // Stay open when keyboard is connected/disconnected
    // https://stackoverflow.com/questions/4116058/avoiding-application-restart-when-hardware-keyboard-opens
    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        // Ignore orientation change to keep activity from restarting
        super.onConfigurationChanged(newConfig);
    }

    static SpannableString formatDate(Context appContext, CalendarEvent calendarEvent) {
        return(formatDate(appContext, calendarEvent, true));
    }

    static SpannableString formatDate(Context appContext, CalendarEvent calendarEvent, boolean used_as_prefix) {
        StringBuilder sb = new StringBuilder();
        //boolean startIsToday = DateUtils.isInSameDay(calendarEvent.getStartDate(), now);
        boolean startIsToday = false;
        boolean addSpace = false;

        boolean isTask = calendarEvent instanceof TaskEvent;

        if (!isTask) {
            if (startIsToday && !calendarEvent.isAllDay()) {
                sb.append(Settings.formatTime(Settings.getStringPref(appContext, "timeFormat", AgendaUpdateService.appWidgetId), calendarEvent.getStartDate()));
            } else /*if (calendarEvent.getStartDate().compareTo(now) > 0)*/ {
                if (!Settings.getBoolPref(appContext, "groupByDate", AgendaUpdateService.appWidgetId)) {
                    sb.append(Settings.formatDate(Settings.getStringPref(appContext, "shortDateFormat", AgendaUpdateService.appWidgetId), calendarEvent.getStartDate()));
                }
                if (!calendarEvent.isAllDay() && DateUtils.dayFloor(calendarEvent.getStartDate()).compareTo(calendarEvent.getStartDate()) != 0) {
                    if (!Settings.getBoolPref(appContext, "groupByDate", AgendaUpdateService.appWidgetId)) {
                        sb.append(" ");
                    }
                    sb.append(Settings.formatTime(Settings.getStringPref(appContext, "timeFormat", AgendaUpdateService.appWidgetId), calendarEvent.getStartDate()));
                }
                addSpace = true;
            }
            if (calendarEvent.isAllDay()) {
                if (Settings.getBoolPref(appContext, "showAllDay", AgendaUpdateService.appWidgetId)) {
                    if (addSpace) {
                        sb.append(" ");
                    }
                    sb.append("(").append(appContext.getString(R.string.all_day)).append(")");
                }
            } else {
                sb.append(" -");
            }
        }

        if (calendarEvent.getEndDate().getTime() != 0) {
            //boolean endIsToday = DateUtils.isInSameDay(calendarEvent.getEndDate(), now);
            boolean endIsToday = false;
            boolean showDue = calendarEvent instanceof TaskEvent && !endIsToday;
            if (endIsToday && !calendarEvent.isAllDay()) {
                if (!isTask) {
                    sb.append(" ");
                }
                sb.append(Settings.formatTime(Settings.getStringPref(appContext, "timeFormat", AgendaUpdateService.appWidgetId), calendarEvent.getEndDate()));
            } else if (!calendarEvent.isAllDay() || isTask) {
                if (showDue || (!DateUtils.isInSameDay(calendarEvent.getStartDate(), calendarEvent.getEndDate()) && !Settings.getBoolPref(appContext, "repeatMultidayEvents", AgendaUpdateService.appWidgetId))) {
                    if (!isTask) {
                        sb.append(" ");
                    }
                    sb.append(Settings.formatDate(Settings.getStringPref(appContext, "shortDateFormat", AgendaUpdateService.appWidgetId), calendarEvent.getEndDate()));
                }
                if (DateUtils.dayFloor(calendarEvent.getEndDate()).compareTo(calendarEvent.getEndDate()) != 0) {
                    if (!sb.toString().isEmpty()) {
                        sb.append(" ");
                    }
                    sb.append(Settings.formatTime(Settings.getStringPref(appContext, "timeFormat", AgendaUpdateService.appWidgetId), calendarEvent.getEndDate()));
                }
            }

            if (sb.toString().endsWith("-")) {
                sb.append(" ");
            }
        }
        if (used_as_prefix && !sb.toString().isEmpty()) {
            sb.append(": ");
        }

        return(new SpannableString(sb.toString()));
    }

    static SpannableString formatTitle(Context appContext, CalendarEvent calendarEvent) {
        SpannableString spanTitle = new SpannableString(calendarEvent.getTitle());
        return(spanTitle);
    }

    public class MySimpleArrayAdapter extends ArrayAdapter<String> {
        private final Context context;
        private final String[] values;

        private void setOnClick(final Button btn, final int val){
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    // Do whatever you want(str can be used here)

                }
            });
        }

        public MySimpleArrayAdapter(Context context,String[] values) {
            super(context, R.layout.calendar_event_layout, values);
            this.context = context;
            this.values = values;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            if (mServer==null) return(null);

            /*
            if (position==0) {
                newNotifications=new ArrayList<>();
            }
             */

            if (position>=mServer.getEvents().size())
                return(inflater.inflate(R.layout.calendar_event_layout, parent, false));

            EventItem item = mServer.getEvents().get(position);

            Context appContext = context;
            View v;

            if (item instanceof DayGroup) {
                v = inflater.inflate(R.layout.calendar_event_header_layout, parent, false);
                ((TextView) v.findViewById(R.id.tvDate)).setText(Settings.formatDate(Settings.getStringPref(appContext, "shortDateFormat", AgendaUpdateService.appWidgetId), item.getStartDate()));
            } else {
                // FIXME: This is a mess. I wish I knew how to make it cleaner :*
                CalendarEvent calendarEvent = (CalendarEvent) item;
                boolean isTask = calendarEvent instanceof TaskEvent;
                if (isTask) {
                    v = inflater.inflate(R.layout.task_event_layout, parent, false);
                    TaskContract tasks = TaskProvider.getTaskContract(Settings.getStringPref(AgendaWidgetApplication.getContext(), "taskProvider", AgendaUpdateService.appWidgetId));
                    //v.setInt(R.id.imgTaskPriority, "setColorFilter", tasks.getPriorityColor((TaskEvent)calendarEvent));
                    ((ImageButton) v.findViewById(R.id.imgTaskPriority)).setColorFilter(tasks.getPriorityColor((TaskEvent) calendarEvent));

                } else { //item instanceof CalendarEvent
                    v = inflater.inflate(R.layout.calendar_event_layout, parent, false);
                    //v.setInt(R.id.viewCalendarColor, "setBackgroundColor", calendarEvent.getColor());
                    ((LinearLayout) v.findViewById(R.id.viewCalendarColor)).setBackgroundColor(calendarEvent.getColor());
                }

                Date now = GregorianCalendar.getInstance().getTime();

                /*
                boolean isToday = (isTask && calendarEvent.getEndDate().getTime() != 0
                        && ((Settings.getBoolPref(AgendaWidgetApplication.getContext(), "showOverdueTasks", appWidgetId) && calendarEvent.getEndDate().compareTo(now) <= 0)
                        || (calendarEvent.getEndDate().compareTo(now) <= 0 && DateUtils.isInSameDay(calendarEvent.getEndDate(), now)))) ||
                        (!isTask && (calendarEvent.containsDate(now) ||
                                DateUtils.isInSameDay(calendarEvent.getStartDate(), now) ||
                                DateUtils.isInSameDay(calendarEvent.getEndDate(), now)));
                 */
                boolean isToday=false;

                @ColorInt int dateTitleColor = Color.parseColor(isToday ?
                        Settings.getStringPref(AgendaWidgetApplication.getContext(), "todayDateTitleColor", AgendaUpdateService.appWidgetId) :
                        Settings.getStringPref(AgendaWidgetApplication.getContext(), "dateTitleColor", AgendaUpdateService.appWidgetId));

                //v.setInt(R.id.tvDate, "setTextColor", dateTitleColor);
                ((TextView)v.findViewById(R.id.tvDate)).setTextColor(dateTitleColor);
                //v.setInt(R.id.tvTitle, "setTextColor", dateTitleColor);
                ((TextView)v.findViewById(R.id.tvTitle)).setTextColor(dateTitleColor);

                SpannableString spanDate = formatDate(appContext,calendarEvent); //new SpannableString(sb.toString());
                SpannableString spanTitle = formatTitle(appContext,calendarEvent); //new SpannableString(calendarEvent.getTitle());
                if (Settings.getBoolPref(appContext, "todayBold", AgendaUpdateService.appWidgetId) && isToday) {
                    spanDate.setSpan(new StyleSpan(Typeface.BOLD), 0, spanDate.toString().length()/*sb.toString().length()*/, 0);
                    spanTitle.setSpan(new StyleSpan(Typeface.BOLD), 0, spanTitle.toString().length()/*calendarEvent.getTitle().length()*/, 0);
                }
                //v.setTextViewText(R.id.tvDate, spanDate);
                ((TextView) v.findViewById(R.id.tvDate)).setText(spanDate);
                //v.setTextViewText(R.id.tvTitle, spanTitle);
                ((TextView) v.findViewById(R.id.tvTitle)).setText(spanTitle);

                @ColorInt int locationNotesColor = Color.parseColor(isToday ?
                        Settings.getStringPref(AgendaWidgetApplication.getContext(), "todayLocationNotesColor", AgendaUpdateService.appWidgetId) :
                        Settings.getStringPref(AgendaWidgetApplication.getContext(), "locationNotesColor", AgendaUpdateService.appWidgetId));

                //v.setInt(R.id.tvLocation, "setTextColor", locationNotesColor);
                ((TextView) v.findViewById(R.id.tvLocation)).setTextColor(locationNotesColor);
                //v.setInt(R.id.imgLocation, "setColorFilter", locationNotesColor);
                ((ImageButton) v.findViewById(R.id.imgLocation)).setColorFilter(locationNotesColor);
                //v.setInt(R.id.tvNotes, "setTextColor", locationNotesColor);
                ((TextView)v.findViewById(R.id.tvNotes)).setTextColor(locationNotesColor);
                //v.setInt(R.id.imgNotes, "setColorFilter", locationNotesColor);
                ((ImageButton) v.findViewById(R.id.imgNotes)).setColorFilter(locationNotesColor);


                boolean showLocation = calendarEvent instanceof TaskEvent && !Settings.getBoolPref(appContext, "useCalendarLayoutOptions", AgendaUpdateService.appWidgetId) ? Settings.getBoolPref(appContext, "showLocationTasks", AgendaUpdateService.appWidgetId) : Settings.getBoolPref(appContext, "showLocation", AgendaUpdateService.appWidgetId);
                if (showLocation && calendarEvent.getLocation() != null && !calendarEvent.getLocation().isEmpty()) {
                    //v.setTextViewText(R.id.tvLocation, calendarEvent.getLocation());
                    ((TextView) v.findViewById(R.id.tvLocation)).setText(calendarEvent.getLocation());
                    //v.setInt(R.id.tvLocation, "setVisibility", View.VISIBLE);
                    ((TextView) v.findViewById(R.id.tvLocation)).setVisibility(View.VISIBLE);
                    //v.setInt(R.id.imgLocation, "setVisibility", View.VISIBLE);
                    ((ImageButton) v.findViewById(R.id.imgLocation)).setVisibility(View.VISIBLE);
                } else {
                    //v.setTextViewText(R.id.tvLocation, "");
                    ((TextView) v.findViewById(R.id.tvLocation)).setText("");
                    //v.setInt(R.id.tvLocation, "setVisibility", View.GONE);
                    ((TextView) v.findViewById(R.id.tvLocation)).setVisibility(View.GONE);
                    //v.setInt(R.id.imgLocation, "setVisibility", View.GONE);
                    ((ImageButton) v.findViewById(R.id.imgLocation)).setVisibility(View.GONE);
                }

                boolean showNotes = calendarEvent instanceof TaskEvent && !Settings.getBoolPref(appContext, "useCalendarLayoutOptions", AgendaUpdateService.appWidgetId) ? Settings.getBoolPref(appContext, "showNotesTasks", AgendaUpdateService.appWidgetId) : Settings.getBoolPref(appContext, "showNotes", AgendaUpdateService.appWidgetId);
                if (showNotes && calendarEvent.getDescription() != null && !calendarEvent.getDescription().isEmpty()) {
                    //v.setTextViewText(R.id.tvNotes, calendarEvent.getDescription());
                    ((TextView) v.findViewById(R.id.tvNotes)).setText(calendarEvent.getDescription());
                    //v.setInt(R.id.tvNotes, "setVisibility", View.VISIBLE);
                    ((TextView) v.findViewById(R.id.tvNotes)).setVisibility(View.VISIBLE);
                    //v.setInt(R.id.imgNotes, "setVisibility", View.VISIBLE);
                    ((ImageButton) v.findViewById(R.id.imgNotes)).setVisibility(View.VISIBLE);
                    int notesMaxLines = calendarEvent instanceof TaskEvent && !Settings.getBoolPref(appContext, "useCalendarLayoutOptions", AgendaUpdateService.appWidgetId) ? Settings.getIntPref(appContext, "notesMaxLinesTasks", AgendaUpdateService.appWidgetId) : Settings.getIntPref(appContext, "notesMaxLines", AgendaUpdateService.appWidgetId);
                    //v.setInt(R.id.tvNotes, "setMaxLines", notesMaxLines);
                    ((TextView) v.findViewById(R.id.tvNotes)).setMaxLines(notesMaxLines);
                } else {
                    //v.setTextViewText(R.id.tvNotes, "");
                    ((TextView) v.findViewById(R.id.tvNotes)).setText("");
                    //v.setInt(R.id.tvNotes, "setVisibility", View.GONE);
                    ((TextView) v.findViewById(R.id.tvNotes)).setVisibility(View.GONE);
                    //v.setInt(R.id.imgNotes, "setVisibility", View.GONE);
                    ((ImageButton) v.findViewById(R.id.imgNotes)).setVisibility(View.GONE);
                }

                if (calendarEvent instanceof TaskEvent) {
                    TaskContract tasks = TaskProvider.getTaskContract(Settings.getStringPref(AgendaWidgetApplication.getContext(), "taskProvider", AgendaUpdateService.appWidgetId));
                    //v.setOnClickFillInIntent(R.id.viewCalendarEvent, tasks.getIntent((TaskEvent) calendarEvent));
                } else {
                    Uri contentUri = CalendarContract.Events.CONTENT_URI;
                    Uri uri = ContentUris.withAppendedId(contentUri, calendarEvent.getId());
                    Intent intent = new Intent(Intent.ACTION_VIEW).setData(uri);
                    //v.setOnClickFillInIntent(R.id.viewCalendarEvent, intent);

                }

                Button ibtn=v.findViewById(R.id.btn_snooze);
                ibtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.w("MYCALENDAR","snooze button clicked for entry #"+position);
                        if (mServer==null) return;
                        EventItem item = mServer.getEvents().get(position);
                        Log.w("MYCALENDAR","entry is ExtendedCalendarEvent? "+(item instanceof ExtendedCalendarEvent));

                        if (item instanceof ExtendedCalendarEvent) {
                            mServer.handleSnooze(context, (ExtendedCalendarEvent)item);
                            mServer.removeNotification(context,((ExtendedCalendarEvent) item).getId());
                            synchronized(this) {
                                //mServer.getEvents() = ExtendedCalendars.refreshOneEvent(AgendaUpdateService.appWidgetId, ((ExtendedCalendarEvent) item).getId(), mServer.getEvents());
                                mServer.refreshOneEvent(AgendaUpdateService.appWidgetId, ((ExtendedCalendarEvent) item).getId());
                                refreshListCached();
                            }
                        }
                    }
                });
                Button btn=((Button) v.findViewById(R.id.btn_dismiss));
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.w("MYCALENDAR","dismiss button clicked for entry #"+position);
                        if (mServer==null) return;
                        EventItem item = mServer.getEvents().get(position);

                        if (item instanceof ExtendedCalendarEvent) {
                            ExtendedCalendars.dismissCalDAVEventReminders(item);
                            //events.remove(position);
                            mServer.removeNotification(context,((ExtendedCalendarEvent) item).getId());
                            synchronized(this) {
                                //mServer.getEvents() = ExtendedCalendars.refreshOneEvent(AgendaUpdateService.appWidgetId, ((ExtendedCalendarEvent) item).getId(), mServer.getEvents());
                                mServer.refreshOneEvent(AgendaUpdateService.appWidgetId, ((ExtendedCalendarEvent) item).getId());
                                refreshListCached();
                            }
                        }
                    }
                });
                btn=((Button) v.findViewById(R.id.btn_dump));
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.w("MYCALENDAR","dump button clicked for entry #"+position);
                        EventItem item = mServer.getEvents().get(position);
                        if (item instanceof ExtendedCalendarEvent) {
                            ((ExtendedCalendarEvent)item).dumpExtendedPropertiesForEvent();
                        }
                    }
                });
                btn=((Button) v.findViewById(R.id.btn_test));
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(context, "You must set up code for the Test button!", Toast.LENGTH_LONG).show();
                        if (1==1) return;

/*
                        if (!AgendaWidgetConfigureActivity.checkForPermission(AgendaWidgetApplication.getActivity(context), Manifest.permission.READ_CALENDAR, AgendaWidgetConfigureActivity.PERMISSIONS_REQUEST_READ_CALENDAR, false)) {
                            ActivityCompat.requestPermissions(AgendaWidgetApplication.getActivity(context), new String[]{Manifest.permission.READ_CALENDAR}, AgendaWidgetConfigureActivity.PERMISSIONS_REQUEST_READ_CALENDAR);
                            return;
                        }
                        if (!AgendaWidgetConfigureActivity.checkForPermission(AgendaWidgetApplication.getActivity(context), Manifest.permission.WRITE_CALENDAR, AgendaWidgetConfigureActivity.PERMISSIONS_REQUEST_WRITE_CALENDAR, false)) {
                            ActivityCompat.requestPermissions(AgendaWidgetApplication.getActivity(context), new String[]{Manifest.permission.WRITE_CALENDAR}, AgendaWidgetConfigureActivity.PERMISSIONS_REQUEST_WRITE_CALENDAR);
                            return;
                        }
*/

                        /*
                        Log.w("MYCALENDAR","run-tests button clicked for entry #"+position);
                        EventItem item = mServer.getEvents().get(position);
                        if (item instanceof ExtendedCalendarEvent) {
                            ((ExtendedCalendarEvent)item).runTests();
                        }
                        */
                    }
                });

                /*
                // create the notification
                SpannableString spanDate = formatDate(appContext,calendarEvent); //new SpannableString(sb.toString());
                SpannableString spanTitle = formatTitle(appContext,calendarEvent); //new SpannableString(calendarEvent.getTitle());
                createNotification(calendarEvent.getId(),spanTitle.toString(),spanDate.toString());
                newNotifications.add(calendarEvent.getId());
                 */
            }

            /*
            if (position==events.size()-1) {
                // We are done with the last element. Remove all leftover notifications
                for(Long eventId : currentNotifications) {
                    if (!newNotifications.contains(eventId)) {
                        removeNotification(eventId.longValue());
                    }
                }
                currentNotifications=new ArrayList<>(newNotifications);
                if (currentNotifications.size()>1) {
                    createSummaryNotification();
                }
                else {
                    removeSummaryNotification();
                }
            }
            */

            return(v);
        }
    }

    public class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            /*
            if (intent.getAction().equals(ACTION_DISMISS)) {
                int id=intent.getIntExtra(EXTRA_NOTIFICATION_ID,-1);
                if (id==-1) return;
                Log.v("MYCALENDAR","got dismiss for id="+id);
                long eventId=intent.getLongExtra(EXTRA_EVENT_ID,-1);
                if (eventId==-1) return;
                Log.v("MYCALENDAR","got dismiss for eventid="+eventId);

                for(EventItem item : mServer.getEvents()) {
                    if (item instanceof ExtendedCalendarEvent) {
                        ExtendedCalendarEvent event=(ExtendedCalendarEvent)item;
                        if (event.getId()==eventId) {
                            ExtendedCalendars.dismissCalDAVEventReminders(event);
                            removeNotification(context,event.getId());
                            synchronized (this) {
                                mServer.getEvents() = refreshOneEvent(appWidgetId, event.getId(), mServer.getEvents());
                                refreshListCached();
                            }
                            return;
                        }
                    }
                }
                // TODO: this should never happen
                Log.e("MYCALENDAR","Dismiss clicked for event with id="+eventId+", but no such id was found in events. Ignoring");
            }
            else if (intent.getAction().equals(ACTION_VIEW)) {
                //int id=intent.getIntExtra(EXTRA_NOTIFICATION_ID,-1);
                //if (id==-1) return;
                long eventId=intent.getLongExtra(EXTRA_EVENT_ID,-1);
                if (eventId==-1) return;
                Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
                Intent viewIntent = new Intent(Intent.ACTION_VIEW)
                        .setData(uri);
                startActivity(viewIntent);
            }
            else {
                Log.v("MYCALENDAR","Received a broadcast!");
                StringBuilder sb = new StringBuilder();
                sb.append("Action: " + intent.getAction() + "\n");
                sb.append("URI: " + intent.toUri(Intent.URI_INTENT_SCHEME).toString() + "\n");
                sb.append("Extras:"+intent.getExtras());
                String log = sb.toString();
                Log.d("MYCALENDAR", log);
                Toast.makeText(context, log, Toast.LENGTH_LONG).show();
            }
            */
        }
    }

    /*
    void setupBroadcastReceiver() {
        if (AgendaUpdateService.br==null) {
            AgendaUpdateService.br=new MyBroadcastReceiver();
            IntentFilter filter = new IntentFilter();
//            IntentFilter filter = new IntentFilter(ACTION_VIEW);
            filter.addAction(ACTION_VIEW);
//            this.registerReceiver(AgendaUpdateService.br, filter);
//            filter = new IntentFilter(ACTION_SNOOZE);
            filter.addAction(ACTION_SNOOZE);
//            this.registerReceiver(AgendaUpdateService.br, filter);
//            filter = new IntentFilter(ACTION_DISMISS);
            filter.addAction(ACTION_DISMISS);
            this.registerReceiver(AgendaUpdateService.br, filter);
        }
    }
     */

    static final String TEST_NOTIFICATION_CHANNEL_ID=NOTIFICATION_CHANNEL_ID;//"Test Channel";
    static void createTestNotification(Context context) {

        Log.v("MYCALENDAR","Creating test notification on "+TEST_NOTIFICATION_CHANNEL_ID);
        //createNotificationChannel(context,TEST_NOTIFICATION_CHANNEL_ID);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, TEST_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notes)
                .setContentTitle("Test notification")
                .setContentText("Test description")
//                .setStyle(new NotificationCompat.BigTextStyle()
//                        .bigText("Much longer text that cannot fit one line..."))
                .setPriority(NotificationCompat.PRIORITY_MAX) //PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setStyle(new NotificationCompat.InboxStyle())
//                        .addLine(str1)
//                        .addLine(str2)
//                        .setContentTitle("")
//                        .setSummaryText("+3 more"))
// Do not trigger an alert if the notification is already showing and is updated
                .setOnlyAlertOnce(true)
                // Apparently this causes it to be displayed as Heads-up notification
                // https://stackoverflow.com/questions/26451893/heads-up-notification-android-lollipop
                // https://stackoverflow.com/questions/33510861/how-to-show-heads-up-notifications-android
                //.setDefaults(Notification.DEFAULT_VIBRATE)
                .setDefaults(Notification.DEFAULT_ALL)
                // Group the notifications
                // https://developer.android.com/training/notify-user/group
                // By default, notifications are sorted according to when they were posted, but you can change order by calling setSortKey().
                //
                // If alerts for a notification's group should be handled by a different notification, call setGroupAlertBehavior(). For example, if you want only the summary of your group to make noise, all children in the group should have the group alert behavior GROUP_ALERT_SUMMARY. The other options are GROUP_ALERT_ALL and GROUP_ALERT_CHILDREN.
                //
                //.setGroup(MY_NOTIFICATION_GROUP_ID)
                // Set the intent that will fire when the user taps the notification
                //.setOngoing(true) // prevent the user from swiping it away
// Automatically remove the notification when the user clicks on it
//                .setAutoCancel(true)
                ;

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // id is a unique int for each notification that you must define
        notificationManager.notify(777, builder.build());
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        context = getApplicationContext();

        // hide the title bar
        // https://stackoverflow.com/questions/14475109/remove-android-app-title-bar
        // reverted so I can add a menu button
        // For an alternative without the bar, see https://stackoverflow.com/questions/30417223/how-to-add-menu-button-without-action-bar
        getSupportActionBar().hide();
//        ActionBar toolbar = getSupportActionBar();
//        toolbar.setTitle("MY Toolbar!!!");

        setContentView(R.layout.activity_main);

        requestOverlayPermission();

        // NO OTHER CODE HERE: starting and setting up the service as needed is done in onServiceConnected()

        // TODO: let's see if I can shut down the service that is stealing my broadcasts
        //Context context = AgendaWidgetApplication.getContext();
        //context.stopService(new Intent(context, AgendaUpdateService.class));


        /*
        new Timer().scheduleAtFixedRate(new TimerTask() {
            int elapsedMin=0;
            boolean firstRun=true;
            @Override
            public void run() {
                if (firstRun) {
                    firstRun=false;
                    return;
                }

                if (++elapsedMin>=ExtendedCalendars.LOOKAHEAD_MINUTES) {
                    elapsedMin=0;
                    refreshList();
                    refreshListInUI();
                }
                else {
                    refreshListCached();
                }
            }
        }, 0, (60L*1000L)); // every 1 min
        */
    }

    // https://stackoverflow.com/questions/40355344/how-to-programmatically-grant-the-draw-over-other-apps-permission-in-android
    // https://stackoverflow.com/questions/40437721/how-to-give-screen-overlay-permission-on-my-activity
    private void requestOverlayPermission() {
        if (android.provider.Settings.canDrawOverlays(this)) return;

        // TODO transition app to androix so I can use Snackbar
        Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri);
        startActivity(intent);

        /*
        Snackbar.make(findViewById(android.R.id.content), "Permission needed!", Snackbar.LENGTH_INDEFINITE)
                .setAction("Settings", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {

                        try {
                            Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
                            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri);
                            startActivity(intent);
                        } catch (Exception ex) {
                            Intent intent = new Intent();
                            intent.setAction(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                            startActivity(intent);
                        }
                    }
                })
                .show();
         */
    }

    // https://stackoverflow.com/questions/39256501/check-if-battery-optimization-is-enabled-or-not-for-an-app
    /*
    private void requestIgnoreBatteryOptimizationPermission() {
        //final int IGNORE_BATTERY_OPTIMIZATION_REQUEST = 1002;

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Snackbar.make(findViewById(android.R.id.content), "Permission needed!", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Settings", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {

                                try {
                                    Uri uri = Uri.parse("package:" + BuildConfig.APPLICATION_ID);
                                    Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri);
                                    startActivity(intent);
                                } catch (Exception ex) {
                                    Intent intent = new Intent();
                                    intent.setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                    startActivity(intent);
                                }
                            }
                        })
                        .show();
            }
        }
    }
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_menu_2, menu);

        return true;
    }

/*
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement

        if (id == R.id.action_settings) {
            // https://stackoverflow.com/questions/9880841/using-list-preference-in-android
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.frameLayout, new MySettingsFragment())
                    .addToBackStack("settings")
                    .commit();
            onMainPageCovered();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
*/

    public void refreshListInUI() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (mServer==null) return;
                String[] vals=new String[mServer.numTriggeredEvents()];
                for(int i=0;i<vals.length;vals[i++]="");

                ListView l = findViewById(R.id.lvEvents);
                MySimpleArrayAdapter adapter = new MySimpleArrayAdapter(context,vals);
                l.setAdapter(adapter);
            }
        });
    }

    public void refreshListCached() {
        if (mServer==null) return;
        Log.w("MYCALENDAR","got cached events: "+mServer.getEvents().size()+"; triggered="+mServer.numTriggeredEvents());

        mServer.updateNotifications(context);
        String[] vals=new String[mServer.numTriggeredEvents()];
        for(int i=0;i<vals.length;vals[i++]="");

        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                ListView l = findViewById(R.id.lvEvents);
                MySimpleArrayAdapter adapter = new MySimpleArrayAdapter(context,vals);
                l.setAdapter(adapter);
            }
        });
    }

    public void startSettings(View view) {
        int appWidgetId=1;
        Uri data = Uri.withAppendedPath(Uri.parse("agenda://widget/id/"), String.valueOf(appWidgetId));

        // Bind widget configuration button
        Intent configIntent = new Intent(context, AgendaWidgetConfigureActivity.class);
        configIntent.setData(data);
        configIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        startActivity(configIntent);
    }

    public void runGetEvents(View view) {
        Log.w("MYCALENDAR","about to get events");
        //events = Events.getEvents(appWidgetId);
        if (mServer==null) return;
        mServer.clearObserverTimer();
        mServer.refreshList(context);
        refreshListInUI();
        Log.w("MYCALENDAR","got events: "+mServer.getEvents().size());
    }


    // https://androidwave.com/foreground-service-android-example/
    public void startService(/*String mode*/) {
        Intent serviceIntent = new Intent(this, AgendaUpdateService.class);
        //serviceIntent.putExtra("inputExtra", mode);
        startForegroundService(serviceIntent);
    }

    public void stopService() {
        Intent serviceIntent = new Intent(this, AgendaUpdateService.class);
        stopService(serviceIntent);
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.v("MYCALENDAR","in onStart()");
        Intent mIntent = new Intent(this, AgendaUpdateService.class);
        bindService(mIntent, mConnection, BIND_AUTO_CREATE);
    };

    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Toast.makeText(MainActivity.this, "Service is disconnected", Toast.LENGTH_LONG).show();
            Log.v("MYCALENDAR","Service is disconnected");
            mBounded = false;
            mServer = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Toast.makeText(MainActivity.this, "Service is connected", Toast.LENGTH_LONG).show();
            Log.v("MYCALENDAR","Service is connected");
            mBounded = true;
            AgendaUpdateService.LocalBinder mLocalBinder = (AgendaUpdateService.LocalBinder)service;
            mServer = mLocalBinder.getServerInstance();

            if (!mServer.isRunning()) {
                startService();
            }
            // TODO: this is primitive. We should receive a refresh-UI intent from the service instead
            new Timer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    refreshListInUI();
                }
            }, 0, (60L*1000L)); // every 1 min

        }
    };

    @Override
    protected void onStop() {
        super.onStop();
        Log.v("MYCALENDAR","in onStop()!!!!!!!!!!!!!!!");
        if(mBounded) {
            unbindService(mConnection);
            mBounded = false;
        }
    };


    public static class SnoozeReminderActivity extends Activity {

        @Override
        public void onCreate(Bundle icicle) {
            super.onCreate(icicle);

//            context = getApplicationContext();

            // hide the title bar
            // https://stackoverflow.com/questions/14475109/remove-android-app-title-bar
//            getSupportActionBar().hide();

//            setContentView(R.layout.activity_main);

//            AgendaUpdateService.observer.clearTimer();
            setupWindow();
        }

        public void setupWindow() {
            /*
            View view = View.inflate(SnoozeReminderActivity.this, R.layout.time_dialog, null);
            final NumberPicker numberPickerHour = view.findViewById(R.id.numpicker_hours);
            numberPickerHour.setMaxValue(23);
            numberPickerHour.setValue(0);//sharedPreferences.getInt("Hours", 0));
            final NumberPicker numberPickerMinutes = view.findViewById(R.id.numpicker_minutes);
            numberPickerMinutes.setMaxValue(59);
            numberPickerMinutes.setValue(0);//sharedPreferences.getInt("Minutes", 0));
            final NumberPicker numberPickerSeconds = view.findViewById(R.id.numpicker_seconds);
            numberPickerSeconds.setMaxValue(59);
            numberPickerSeconds.setValue(0);//sharedPreferences.getInt("Seconds", 0));
            Button cancel = view.findViewById(R.id.cancel);
            Button ok = view.findViewById(R.id.ok);
            AlertDialog.Builder builder = new AlertDialog.Builder(SnoozeReminderActivity.this);
            builder.setView(view);
            final AlertDialog alertDialog = builder.create();
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    alertDialog.dismiss();
                    finish();
                }
            });
            ok.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.v("MYCALENDAR","time picked: "+numberPickerHour.getValue() + ":" + numberPickerMinutes.getValue() + ":" + numberPickerSeconds.getValue());
//                        timeTV.setText(String.format("%1$d:%2$02d:%3$02d", numberPickerHour.getValue(), numberPickerMinutes.getValue(), numberPickerSeconds.getValue()));
//                    SharedPreferences.Editor editor = sharedPreferences.edit();
//                    editor.putInt("Hours", numberPickerHour.getValue());
//                    editor.putInt("Minutes", numberPickerMinutes.getValue());
//                    editor.putInt("Seconds", numberPickerSeconds.getValue());
//                    editor.apply();

                    alertDialog.dismiss();
                    finish();
                }
            });
            alertDialog.show();
            */
        }
    }
}

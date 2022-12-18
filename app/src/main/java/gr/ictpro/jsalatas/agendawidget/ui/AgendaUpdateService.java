package gr.ictpro.jsalatas.agendawidget.ui;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.CalendarContract;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.SpannableString;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import gr.ictpro.jsalatas.agendawidget.BuildConfig;
import gr.ictpro.jsalatas.agendawidget.R;
import gr.ictpro.jsalatas.agendawidget.application.AgendaWidgetApplication;
import gr.ictpro.jsalatas.agendawidget.model.EventItem;
import gr.ictpro.jsalatas.agendawidget.model.Events;
import gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendarEvent;
import gr.ictpro.jsalatas.agendawidget.model.calendar.ExtendedCalendars;
import gr.ictpro.jsalatas.agendawidget.model.settings.TaskProviderListAdapter;
import gr.ictpro.jsalatas.agendawidget.model.task.TaskContract;
import gr.ictpro.jsalatas.agendawidget.model.task.TaskProvider;

import static android.app.Notification.EXTRA_NOTIFICATION_ID;
import static gr.ictpro.jsalatas.agendawidget.ui.MainActivity.formatDate;
import static gr.ictpro.jsalatas.agendawidget.ui.MainActivity.formatTitle;
import static gr.ictpro.jsalatas.agendawidget.utils.ISO8601Utilities.secondsToDate;

/*
    https://stackoverflow.com/questions/9440993/how-to-get-calendar-change-events-is-calendar-observer-working
    https://stackoverflow.com/questions/22245604/detect-changes-on-a-native-android-calendar
    https://www.grokkingandroid.com/use-contentobserver-to-listen-to-changes/
    https://stackoverflow.com/questions/4618591/android-cursor-registercontentobserver
    https://stackoverflow.com/questions/34724101/cursor-registercontentobserver-vs-contentresolver-registercontentobserver

    IMPORTANT: to set up notifications: https://stackoverflow.com/questions/21168766/listen-for-new-calendar-events

MORE CODE RELATED TO REGISTRATION OF OBSERVERS
        refreshCalendarList();

        String[] calendarsList = Settings.getStringPref(AgendaWidgetApplication.getContext(), "calendars", appWidgetId).split("@@@");
        if (calendarsList.length == 1 && calendarsList[0].isEmpty()) {
            Log.v("MYCALENDAR","No calendars -- not registering any observers");
            return;
        }
        for (String calendar : calendarsList) {
            CalendarObserver observer = new CalendarObserver(calendar,new Handler());
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    Uri MYAPP_CONTENT_URI = CalendarContract.Calendars.CONTENT_URI.buildUpon().appendQueryParameter(CalendarContract.Calendars._ID, calendar).build();
                    Log.v("MYCALENDAR","Registering for "+MYAPP_CONTENT_URI);
                    getContentResolver().registerContentObserver(MYAPP_CONTENT_URI, false, observer);
                }
            });
        }

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                getContentResolver().registerContentObserver(CalendarContract.Calendars.CONTENT_URI, false, observer); //true
            }
        });


        String[] projection = new String[] { "_id" };
        Uri calendars = Uri.parse("content://calendar/calendars");

        Cursor managedCursor = managedQuery(calendars, projection, null, null, null);
        managedCursor.registerContentObserver(new CalendarObserver(new Handler()));


        getContentResolver().
                registerContentObserver(
                        calendars,
                        true,
                        new CalendarObserver(new Handler()));

    FROM Simple Calendar

    fun Context.syncCalDAVCalendars(callback: () -> Unit) {
        calDAVRefreshCallback = callback
        ensureBackgroundThread {
            val uri = CalendarContract.Calendars.CONTENT_URI
            contentResolver.unregisterContentObserver(calDAVSyncObserver)
            contentResolver.registerContentObserver(uri, false, calDAVSyncObserver)
            refreshCalDAVCalendars(config.caldavSyncedCalendarIds, true)
        }
    }

    // caldav refresh content observer triggers multiple times in a row at updating, so call the callback only a few seconds after the (hopefully) last one
    private val calDAVSyncObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            if (!selfChange) {
                calDAVRefreshHandler.removeCallbacksAndMessages(null)
                calDAVRefreshHandler.postDelayed({
                    ensureBackgroundThread {
                        unregisterObserver()
                        calDAVRefreshCallback?.invoke()
                        calDAVRefreshCallback = null
                    }
                }, CALDAV_REFRESH_DELAY)
            }
        }
    }
*/
public class AgendaUpdateService extends Service {
    final static boolean PERSISTENT_NOTIFICATION = true;
    final static boolean PERSISTENT_NOTIFICATION_IS_SUMMARY = true;

    public final static boolean NEW_INTENTS = true;// this CANNOT be false. To switch to false, find a way to re-enable the block under condition "!NEW_INTENTS" that is currently commented out

    // Notification intent actions
    //public final static String ACTION_OPEN_APP = "open_app";
    public final static String ACTION_VIEW = "view";
    public final static String ACTION_SNOOZE = "snooze";
    public final static String ACTION_DISMISS = "dismiss";
    private final static String ACTION_UPDATE = "gr.ictpro.jsalatas.agendawidget.action.UPDATE";

    final static String NOTIFICATION_CHANNEL_ID = "AgendaWidget Notification Channel";
    final static String SERV_NOTIFICATION_CHANNEL_ID = NOTIFICATION_CHANNEL_ID; //"Fg Service Notification Channel";
    final static String EXTRA_EVENT_ID = "eventId";
    // ID for grouping notifications
    final static String MY_NOTIFICATION_GROUP_ID = "Notification Group";
    final static int SUMMARY_NOTIFICATION_ID = -1;
    final static int SERV_NOTIFICATION_ID = -2;
    NotificationChannel notificationChannel = null;

    private IntentFilter intentFilter;
    private IntentFilter intentFilter2 = null;

    //// Look in AgendaWidget for a possibly better CalendarObserver
    //CalendarObserver observer = new CalendarObserver("", new Handler());
    private final static long CALENDAR_REFRESH_DELAY = 60; // how many seconds to wait before refreshing the list when a calendar change event is received
    private final static long CALENDAR_MAX_REFRESH_WAIT = 120; // how many seconds we accept to wait when change notifications keep coming in

    private final AgendaWidget.CalendarObserver calendarObserver = new AgendaWidget.CalendarObserver(new Handler());
    private AgendaUpdateService.CalendarObserver observer;

    private AgendaWidget.TaskObserver[] taskObservers;

    //BroadcastReceiver br = null;
    List<Long> currentNotifications = new ArrayList<>();
    List<Long> newNotifications = new ArrayList<>();
    List<EventItem> events = new ArrayList<>();
    List<Long> notifiedEventIDs = new ArrayList<>();

    public final static int appWidgetId = 1;

    boolean updateInProgress = false;

    private Object mainSyncObject = new Object();

    boolean serviceRunning = false;

    public class CalendarObserver extends ContentObserver {
        String calendar;
        AgendaUpdateService.CalendarObserver myself;
        Timer scheduledTimer;
        long curTimeSecAtWaitStart;

        public CalendarObserver(String calendar, Handler handler) {
            super(handler);
            this.calendar = calendar;
            this.myself = this;
            this.scheduledTimer = null;
            curTimeSecAtWaitStart = -1;
        }

        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        public void clearTimer() {
            Log.v("MYCALENDAR", "clearing any pending CalendarObserver timers");
            if (scheduledTimer != null) {
                scheduledTimer.cancel();
                scheduledTimer = null;
            }
        }

        void executeRefresh() {
            Log.v("MYCALENDAR", "Inside timer's async task");
            //getContentResolver().unregisterContentObserver(myself);
            Context context = AgendaWidgetApplication.getContext();
            refreshList(context);
            // TODO: send an intent to the main app's UI and tell it to refresh the UI
            //refreshListInUI();
            //getContentResolver().registerContentObserver(CalendarContract.Calendars.CONTENT_URI, false/*true*/, myself);
            Log.v("MYCALENDAR", "Done with timer's async task");
        }

        // Implement the onChange(boolean, Uri) method to take advantage of the new Uri argument.
        @Override
        public synchronized void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange);
            if (!selfChange) {
                Log.v("MYCALENDAR", "Calendar changes; selfChange=" + selfChange + "; uri=" + uri + "; calendar=" + calendar);
                if (scheduledTimer == null) {
                    curTimeSecAtWaitStart = System.currentTimeMillis() / 1000L;
                    Log.v("MYCALENDAR", "First notification of a change at " + secondsToDate(curTimeSecAtWaitStart) + "; will wait for " + CALENDAR_MAX_REFRESH_WAIT + " sec max");
                } else {
                    clearTimer();
                    if (((System.currentTimeMillis() / 1000L) - curTimeSecAtWaitStart) >= CALENDAR_MAX_REFRESH_WAIT) {
                        Log.v("MYCALENDAR", "We waited more than " + CALENDAR_MAX_REFRESH_WAIT + " sec for changes to stop. Canceling timer and forcing a refresh");
                        executeRefresh();
                        return;
                    }
                }
                scheduledTimer = new Timer();
                Log.v("MYCALENDAR", "Scheduling timer for " + CALENDAR_REFRESH_DELAY + " sec");
                scheduledTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Log.v("MYCALENDAR", "Timer run method is starting. About to execute async task");
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                clearTimer();
                                executeRefresh();
                            }
                        });
                    }
                }, CALENDAR_REFRESH_DELAY * 1000L);
            }
        }
    }

    // TODO remove this and fold into the other receiver -- or decide once and for all to keep them separate
    private final BroadcastReceiver agendaChangedReceiver = new
            BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.v("MYCALENDAR", "agendaChangedReceiver() --GENERAL STUFF-- onReceive(); intent=" + intent);
                    if (intent.getAction().equals(Intent.ACTION_MY_PACKAGE_REPLACED)) {
                        // TODO: figure out if I need to restore these lines
                        //AgendaWidget.updateTaskObservers();
                    } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED) && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        String packageName = intent.getData().toString().replace("package:", "");
                        // TODO: figure out if I need to restore these lines
                        //updateTaskProviders(context, packageName);
                        //MainActicity.updateTaskObservers();
                    } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {
                        // TODO: figure out if I need to restore these lines
                        //AgendaWidget.updateTaskObservers();
                    }

                    // TODO sendupdate() is probably useless
                    Log.v("MYCALENDAR", "sendupdate() is probably useless");
                    sendUpdate(context, intent);
                }
            };

    public static class StartMyServiceAtBootReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
//                    Intent serviceIntent = new Intent(context, MyService.class);
//                    context.startService(serviceIntent);
                // TODO restore start-at-boot functionality
                Log.v("MYCALENDAR", "received BOOT_COMPLETED event");
                Log.v("MYCALENDAR", "===BAD!!! MUST FIGURE OUT HOW TO CALL updateTaskObservers()!!!!!!!!!!!!!!!!");
                //updateTaskObservers();
            }
        }
    }

    private final BroadcastReceiver br2 = new
            BroadcastReceiver() {
                void handleDismiss(Context context, ExtendedCalendarEvent event) {
                    Log.v("MYCALENDAR", "in handleDismiss(); id=" + event.getId());
                    ExtendedCalendars.dismissCalDAVEventReminders(event);
                    removeNotification(context, event.getId());
                    // TODO: find a way to update the app's main window from here. Probably an intent?
                        /*
                                synchronized (this) {
                                    events = refreshOneEvent(appWidgetId, event.getId(), events);
                                    refreshListCached();
                                }
                        */
                }

                void handleSnooze(Context context, ExtendedCalendarEvent event) {

                    // The code below is a combination of:
                    //   https://stackoverflow.com/questions/3599563/alert-dialog-from-android-service
                    //   https://stackoverflow.com/questions/7918571/how-to-display-a-dialog-from-a-service
                    //   https://stackoverflow.com/questions/7847623/how-to-pick-a-second-using-timepicker-android
                    // Note: user must give "draw over other apps / appear on top" permission
                    // in Settings > Apps > Special access
                    // or else the app will CRASH
/*                        AlertDialog alertDialog = new AlertDialog.Builder(context)
                            .setTitle("Title")
                            .setMessage("Are you sure?")
                            .create();
*/
                    Log.v("MYCALENDAR", "in handleSnoze() event; id=" + event.getId());
                    View view = View.inflate(context, R.layout.time_dialog, null);
                    final NumberPicker numberPickerAmount = view.findViewById(R.id.numpicker_amount);
                    numberPickerAmount.setMinValue(1);
                    numberPickerAmount.setMaxValue(99);
                    numberPickerAmount.setValue(1);//sharedPreferences.getInt("Seconds", 0));
                    // https://stackoverflow.com/questions/8227073/using-numberpicker-widget-with-strings
                    final NumberPicker numberPickerUnit = view.findViewById(R.id.numpicker_unit);
                    numberPickerUnit.setMinValue(0);
                    numberPickerUnit.setMaxValue(3);
                    final String[] units = new String[]{"minutes", "hours", "days", "weeks"};
                    final int[] minsPerUnit = new int[]{1, 60, 60 * 24, 60 * 24 * 7};
                    numberPickerUnit.setDisplayedValues(new String[]{"minutes", "hours", "days", "weeks"});

                    Button cancel = view.findViewById(R.id.cancel);
                    Button ok = view.findViewById(R.id.ok);
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setView(view);
                    final AlertDialog alertDialog = builder.create();
                    cancel.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            alertDialog.dismiss();
                        }
                    });
                    ok.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Log.v("MYCALENDAR", "snooze picked: " + numberPickerAmount.getValue() + " in " + numberPickerUnit.getValue() + "; mins=" + ((long) numberPickerAmount.getValue()) * ((long) minsPerUnit[numberPickerUnit.getValue()]));
//                        timeTV.setText(String.format("%1$d:%2$02d:%3$02d", numberPickerHour.getValue(), numberPickerMinutes.getValue(), numberPickerSeconds.getValue()));
//                    SharedPreferences.Editor editor = sharedPreferences.edit();
//                    editor.putInt("Hours", numberPickerHour.getValue());
//                    editor.putInt("Minutes", numberPickerMinutes.getValue());
//                    editor.putInt("Seconds", numberPickerSeconds.getValue());
//                    editor.apply();
                            alertDialog.dismiss();

                            long snoozeMinutes = ((long) numberPickerAmount.getValue()) * ((long) minsPerUnit[numberPickerUnit.getValue()]);
                            ExtendedCalendars.snoozeCalDAVEventReminders(event, snoozeMinutes);
                            removeNotification(context, event.getId());
                            // TODO: find a way to update the app's main window from here. Probably an intent?
                                /*
                                synchronized (this) {
                                    events = refreshOneEvent(appWidgetId, event.getId(), events);
                                    refreshListCached();
                                }
                                */
                        }
                    });
                    alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);//.TYPE_SYSTEM_ALERT);
                    alertDialog.show();

                }

                ExtendedCalendarEvent eventFromIntent(Intent intent) {
                    int id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1);
                    if (id == -1) return (null);
                    Log.v("MYCALENDAR", "br2: got dismiss for id=" + id);
                    long eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1);
                    if (eventId == -1) return (null);
                    Log.v("MYCALENDAR", "br2: got dismiss for eventid=" + eventId);

                    ExtendedCalendarEvent event = null;
                    for (EventItem item : events) {
                        if (item instanceof ExtendedCalendarEvent) {
                            event = (ExtendedCalendarEvent) item;
                            if (event.getId() == eventId) break;
                        }
                    }
                    if (event == null) {
                        // TODO: this should never happen
                        Log.e("MYCALENDAR", "br2: operation clicked for event with id=" + eventId + ", but no such id was found in events. Ignoring");
                    }
                    return (event);
                }

                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent.getAction().equals(ACTION_DISMISS)) {
                        Log.v("MYCALENDAR", "received dismiss event");
//                        Log.v("MYCALENDAR", "BAILING OUT!!!");
//                        if (1 == 1) return;
                        ExtendedCalendarEvent event = eventFromIntent(intent);
                        if (event != null) {
                            handleDismiss(context, event);
                        }
                    } else if (intent.getAction().equals(ACTION_SNOOZE)) {
                        {
                            Log.v("MYCALENDAR", "received snooze event");
                            StringBuilder sb = new StringBuilder();
                            sb.append("Action: " + intent.getAction() + "\n");
                            sb.append("URI: " + intent.toUri(Intent.URI_INTENT_SCHEME).toString() + "\n");
                            sb.append("Extras:" + intent.getExtras());
                            String log = sb.toString();
                            Log.d("MYCALENDAR", log);
                            Toast.makeText(context, log, Toast.LENGTH_LONG).show();
                        }

                        ExtendedCalendarEvent event = eventFromIntent(intent);
                        if (event != null) {
                            handleSnooze(context, event);
                        }
                    } else if (intent.getAction().equals(ACTION_VIEW)) {
                        Log.v("MYCALENDAR", "received view event: " + intent);
                        Bundle bundle = intent.getExtras();
                        if (bundle != null) {
                            for (String key : bundle.keySet()) {
                                Log.e("MYCALENDAR", key + " : " + (bundle.get(key) != null ? bundle.get(key) : "NULL"));
                            }
                        }
                        Log.v("MYCALENDAR","BAILING OUT!");
                        if (1==1) return;
                        //int id=intent.getIntExtra(EXTRA_NOTIFICATION_ID,-1);
                        //if (id==-1) return;
                        long eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1);
                        if (eventId == -1) return;
                        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
                        Intent viewIntent = new Intent(Intent.ACTION_VIEW)
                                .setData(uri);
                        // https://stackoverflow.com/questions/3689581/calling-startactivity-from-outside-of-an-activity
                        viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(viewIntent);
                    } else {
                        Log.v("MYCALENDAR", "br2: Received a broadcast!");
                        StringBuilder sb = new StringBuilder();
                        sb.append("br2: Action: " + intent.getAction() + "\n");
                        sb.append("URI: " + intent.toUri(Intent.URI_INTENT_SCHEME).toString() + "\n");
                        sb.append("Extras:" + intent.getExtras());
                        String log = sb.toString();
                        Log.d("MYCALENDAR", log);
                        Toast.makeText(context, log, Toast.LENGTH_LONG).show();
                    }
                }
            };

    private void setupService(Intent intent, int flags, int startId) {
        Context context = this; //AgendaWidgetApplication.getContext();
//        Thread mainThread = new Thread() {
//            public void run() {
                observer = new AgendaUpdateService.CalendarObserver("", new Handler());
                intentFilter = new IntentFilter();
                intentFilter.addAction(Intent.ACTION_TIME_TICK);
                intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
                intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
                intentFilter.addAction(Intent.ACTION_SCREEN_ON);
                intentFilter.addAction(Intent.ACTION_DATE_CHANGED);
                intentFilter.addAction(Intent.ACTION_MY_PACKAGE_REPLACED);
                intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
                intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
                intentFilter.addDataScheme("package");
                intentFilter.addAction(ACTION_UPDATE);

                if (NEW_INTENTS) {
                    intentFilter2 = new IntentFilter();
                    intentFilter2.addAction(ACTION_VIEW);
                    intentFilter2.addAction(ACTION_SNOOZE);
                    intentFilter2.addAction(ACTION_DISMISS);
                }

                Log.v("MYCALENDAR", "inside setupService()");
                updateTaskObservers();

                try {
                    getContentResolver().registerContentObserver(CalendarContract.Events.CONTENT_URI, true, calendarObserver);
                } catch (SecurityException e) {
                    // java.lang.SecurityException: Permission Denial: opening provider com.android.providers.calendar.CalendarProvider2
                    Toast toast = Toast.makeText(context, context.getString(R.string.select_calendars), Toast.LENGTH_LONG);
                    toast.show();
                }
                for (AgendaWidget.TaskObserver taskObserver : taskObservers) {
                    getContentResolver().registerContentObserver(Uri.parse("content://" + taskObserver.uri), true, taskObserver);
                }

                registerReceiver(agendaChangedReceiver, intentFilter);
                if (intent != null && intent.getAction() != null) {
                    if (intent.getAction().equals(ACTION_UPDATE)) {
                        agendaChangedReceiver.onReceive(context, intent);
                    }
                }

                getContentResolver().registerContentObserver(CalendarContract.Calendars.CONTENT_URI, false/*true*/, observer);

        /*
        TEMPORARY ATTEMPT WITH PERMANENT BROADCASTRECEIVER
         */
                if (NEW_INTENTS) {
                    Log.v("MYCALENDAR", "Registering receiver br2!!!");
                    registerReceiver(br2, intentFilter2);
                }
                /*===*/

                refreshList(context);
                // refresh the notifications at every minute
                // TODO: to avoid waking up the service unnecessarily, it would be better to schedule the next notification update based on the event that will be triggered first
                new Timer().scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        updateNotifications(context);
                    }
                }, 0, (60L * 1000L)); // every 1 min
//            }
//        };
//        mainThread.run();
    }

    // TODO remove
    private void setupServiceFULL(Intent intent, int flags, int startId) {
        Context context = this; //AgendaWidgetApplication.getContext();
        Thread mainThread = new Thread() {
            public void run() {
        observer = new AgendaUpdateService.CalendarObserver("", new Handler());
        intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_TIME_TICK);
        intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        intentFilter.addAction(Intent.ACTION_TIME_CHANGED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_DATE_CHANGED);
        intentFilter.addAction(Intent.ACTION_MY_PACKAGE_REPLACED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addDataScheme("package");
        intentFilter.addAction(ACTION_UPDATE);

        if (NEW_INTENTS) {
            intentFilter2 = new IntentFilter();
            intentFilter2.addAction(ACTION_VIEW);
            intentFilter2.addAction(ACTION_SNOOZE);
            intentFilter2.addAction(ACTION_DISMISS);
        }

        Log.v("MYCALENDAR", "inside setupService()");
        updateTaskObservers();

        try {
            getContentResolver().registerContentObserver(CalendarContract.Events.CONTENT_URI, true, calendarObserver);
        } catch (SecurityException e) {
            // java.lang.SecurityException: Permission Denial: opening provider com.android.providers.calendar.CalendarProvider2
            Toast toast = Toast.makeText(context, context.getString(R.string.select_calendars), Toast.LENGTH_LONG);
            toast.show();
        }
        for (AgendaWidget.TaskObserver taskObserver : taskObservers) {
            getContentResolver().registerContentObserver(Uri.parse("content://" + taskObserver.uri), true, taskObserver);
        }
        registerReceiver(agendaChangedReceiver, intentFilter);
        if (intent != null && intent.getAction() != null) {
            if (intent.getAction().equals(ACTION_UPDATE)) {
                agendaChangedReceiver.onReceive(context, intent);
            }
        }

        getContentResolver().registerContentObserver(CalendarContract.Calendars.CONTENT_URI, false/*true*/, observer);

        /*
        TEMPORARY ATTEMPT WITH PERMANENT BROADCASTRECEIVER
         */
        if (NEW_INTENTS) {
            Log.v("MYCALENDAR", "Registering receiver br2!!!");
            registerReceiver(br2, intentFilter2);
        }
        /*===*/

        refreshList(context);
        // refresh the notifications at every minute
        // TODO: to avoid waking up the service unnecessarily, it would be better to schedule the next notification update based on the event that will be triggered first
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateNotifications(context);
            }
        }, 0, (60L * 1000L)); // every 1 min
            }
        };
        mainThread.run();
    }

    /*
    @Override
    public void onCreate() {
        super.onCreate();
        Log.v("MYCALENDAR","in service's onCreate!!!!!!!!");
    }
    */

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v("MYCALENDAR","Service started");
        serviceRunning=true;
        if (PERSISTENT_NOTIFICATION) {
            createNotificationChannel(this,NOTIFICATION_CHANNEL_ID);
            createNotificationChannel(this,SERV_NOTIFICATION_CHANNEL_ID);
            if (PERSISTENT_NOTIFICATION_IS_SUMMARY) {
                Notification n = createSummaryNotification(this, NOTIFICATION_CHANNEL_ID);
                startForeground(SUMMARY_NOTIFICATION_ID, n);
            }
            else {
                Notification n=createForegroundServiceNotification(this);
                startForeground(SERV_NOTIFICATION_ID,n);
            }
            Log.v("MYCALENDAR","calling startForeground()");
        }

        setupService(intent, flags, startId);

        clearObserverTimer();
        // all refreshList() calls moved to the service
//                refreshList(context);
//                refreshListInUI();

        // TODO had to put it in a timer. If it's not delayed, it causes an exception because startForeground() was not called in time
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                // TODO: see if it's correct to call this one here. I don't think the widget does it
                Log.v("MYCALENDAR","in TimerTask; calling updateTaskObservers()");
                updateTaskObservers();
            }
        }, 5000);

        return START_STICKY;
    }

    int generateNotificationId(long eventId) {
        synchronized(notifiedEventIDs) {
            int firstEmpty = -1;
            for (int i = 0; i < notifiedEventIDs.size(); i++) {
                if (notifiedEventIDs.get(i) == null) {
                    if (firstEmpty!=-1) firstEmpty = i;
                }
                else if (notifiedEventIDs.get(i) == eventId) {
                    return (i);
                }
            }
            if (firstEmpty == -1) {
                firstEmpty = notifiedEventIDs.size();
                notifiedEventIDs.add(null);
            }
            notifiedEventIDs.set(firstEmpty, eventId);
            return (firstEmpty);
        }
    }

    PendingIntent createActionIntent(Context context,int id,long eventId,String action) {
        //if (!NEW_INTENTS) setupBroadcastReceiver();

        Log.v("MYCALENDAR","creating intent with id="+id+" and event id="+eventId+" for action "+action);

        Intent actionIntent = new Intent(action);
        actionIntent.setAction(action);
        actionIntent.putExtra(EXTRA_NOTIFICATION_ID, id);
        actionIntent.putExtra(EXTRA_EVENT_ID, eventId);
        // TODO remove this line
        PendingIntent actionPendingIntent =
                PendingIntent.getBroadcast(context, id, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        // Note: In Android 10 (API level 29) and higher, the platform automatically generates notification action buttons if an app does not provide its own. If you don't want your app's notifications to display any suggested replies or actions, you can opt-out of system-generated replies and actions by using setAllowGeneratedReplies() and setAllowSystemGeneratedContextualActions().

        return(actionPendingIntent);
    }

    // TODO: remove if unused
    PendingIntent createActivityActionIntent(Context context,int id,long eventId,String action) {
        //if (!NEW_INTENTS) setupBroadcastReceiver();

        Log.v("MYCALENDAR","creating activity intent with id="+id+" and event id="+eventId+" for action "+action);

        Intent actionIntent = new Intent(context, MainActivity.SnoozeReminderActivity.class);//action);
        actionIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // https://stackoverflow.com/questions/9772927/flag-activity-new-task-clarification-needed
        //startActivity(actionIntent);
        actionIntent.setAction(action);
        actionIntent.putExtra(EXTRA_NOTIFICATION_ID, id);
        actionIntent.putExtra(EXTRA_EVENT_ID, eventId);
        PendingIntent actionPendingIntent =
                PendingIntent.getActivity(context, id, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        // Note: In Android 10 (API level 29) and higher, the platform automatically generates notification action buttons if an app does not provide its own. If you don't want your app's notifications to display any suggested replies or actions, you can opt-out of system-generated replies and actions by using setAllowGeneratedReplies() and setAllowSystemGeneratedContextualActions().

        return(actionPendingIntent);
    }

    private void createNotificationChannel(Context context,String CHANNEL_ID) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "CalDAV Event Notifications";
            String description = "Channel for the event notifications about CalDAV events";
            int importance = NotificationManager.IMPORTANCE_HIGH; //IMPORTANCE_DEFAULT;
            notificationChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            notificationChannel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    void createNotification(Context context,long eventId,String title,String descr,long startTime) {
        //createNotificationChannel(context,NOTIFICATION_CHANNEL_ID);

        int id=generateNotificationId(eventId);
        PendingIntent tapIntent=createActionIntent(context,id,eventId,ACTION_VIEW);
        PendingIntent snoozePendingIntent=createActionIntent(context,id,eventId,ACTION_SNOOZE);
//        PendingIntent snoozePendingIntent=createActivityActionIntent(context,id,eventId,ACTION_SNOOZE);
        PendingIntent dismissPendingIntent=createActionIntent(context,id,eventId,ACTION_DISMISS);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notes)
                .setContentTitle(title)
                .setContentText(descr)
                .setWhen(startTime)
                .setShowWhen(false) // we only use the "when" attribute for sorting
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
                // Set the intent that will fire when the user taps the notification
                .setContentIntent(tapIntent)
                .addAction(R.drawable.ic_calendar_event/*ic_snooze*/, context.getString(R.string.snooze),
                        snoozePendingIntent)
                .addAction(R.drawable.ic_calendar_event/*ic_snooze*/, context.getString(R.string.dismiss),
                        dismissPendingIntent)
                .setOngoing(true) // prevent the user from swiping it away
// Automatically remove the notification when the user clicks on it
//                .setAutoCancel(true)
                // https://stackoverflow.com/questions/73074639/android-foreground-service-notification-delayed
                // but it requires API level 31
                //.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                ;

        if (PERSISTENT_NOTIFICATION_IS_SUMMARY)
                // Group the notifications
                // https://developer.android.com/training/notify-user/group
                // By default, notifications are sorted according to when they were posted, but you can change order by calling setSortKey().
                //
                // If alerts for a notification's group should be handled by a different notification, call setGroupAlertBehavior(). For example, if you want only the summary of your group to make noise, all children in the group should have the group alert behavior GROUP_ALERT_SUMMARY. The other options are GROUP_ALERT_ALL and GROUP_ALERT_CHILDREN.
                //
            builder=builder.setGroup(MY_NOTIFICATION_GROUP_ID);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // id is a unique int for each notification that you must define
        notificationManager.notify(id, builder.build());
    }

    public Notification createSummaryNotification(Context appContext,String channel) {
        return(createSummaryNotification(appContext, channel, false));
    }

    public Notification createSummaryNotification(Context appContext,String channel, boolean doDisplayNotification) {
        //createNotificationChannel(appContext,channel);
        //PendingIntent tapIntent=createActionIntent(appContext,0,0,ACTION_OPEN_APP);
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
        PendingIntent tapIntent = PendingIntent.getActivity(this, 0, launchIntent, 0);
        Notification summaryNotification =
                new NotificationCompat.Builder(appContext, channel)
                        .setContentTitle("Reminders")
                        //set content text to support devices running API level < 24
                        .setContentText("Reminders available")
                        .setSmallIcon(R.drawable.ic_notes)
/*
                        //build summary info into InboxStyle template
                        .setStyle(new NotificationCompat.InboxStyle()
                                .addLine("Alex Faarborg  Check this out")
                                .addLine("Jeff Chang    Launch Party")
                                .setBigContentTitle("2 new messages")
                                .setSummaryText("janedoe@example.com"))
 */
                        .setPriority(NotificationCompat.PRIORITY_MAX)//PRIORITY_DEFAULT)
                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                        .setContentIntent(tapIntent)
                        //.setStyle(new NotificationCompat.InboxStyle())
                        // Do not trigger an alert if the notification is already showing and is updated
                        .setOnlyAlertOnce(true)
                        // Group the notifications
                        // https://developer.android.com/training/notify-user/group
                        // By default, notifications are sorted according to when they were posted, but you can change order by calling setSortKey().
                        //
                        // If alerts for a notification's group should be handled by a different notification, call setGroupAlertBehavior(). For example, if you want only the summary of your group to make noise, all children in the group should have the group alert behavior GROUP_ALERT_SUMMARY. The other options are GROUP_ALERT_ALL and GROUP_ALERT_CHILDREN.
                        //
                        .setGroup(MY_NOTIFICATION_GROUP_ID)
                        //set this notification as the summary for the group
                        .setGroupSummary(true)
                        .build();
        if (doDisplayNotification) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(appContext);
            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification);
        }
        return(summaryNotification);
    }

    public Notification createForegroundServiceNotification(Context appContext) {
        return(createForegroundServiceNotification(appContext, false));
    }

    public Notification createForegroundServiceNotification(Context appContext, boolean doDisplayNotification) {
        //createNotificationChannel(appContext,SERV_NOTIFICATION_CHANNEL_ID);
        Log.v("MYCALENDAR","Creating foreground service notification on "+SERV_NOTIFICATION_CHANNEL_ID);
        //PendingIntent tapIntent=createActionIntent(appContext,0,0,ACTION_OPEN_APP);
        // Ensure that the activity is started only once
        // https://stackoverflow.com/questions/9598348/android-how-do-i-avoid-starting-activity-which-is-already-in-stack
        PackageManager pm = getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(BuildConfig.APPLICATION_ID);
        PendingIntent tapIntent = PendingIntent.getActivity(this, 0, launchIntent, 0);
        Notification summaryNotification =
                new NotificationCompat.Builder(appContext, SERV_NOTIFICATION_CHANNEL_ID)
                        .setContentTitle("AgendaWidget Service")
                        //set content text to support devices running API level < 24
                        .setContentText("Android requires that this notification be displayed. You can swipe it away now.")
                        .setSmallIcon(R.drawable.ic_notes)
/*
                        //build summary info into InboxStyle template
                        .setStyle(new NotificationCompat.InboxStyle()
                                .addLine("Alex Faarborg  Check this out")
                                .addLine("Jeff Chang    Launch Party")
                                .setBigContentTitle("2 new messages")
                                .setSummaryText("janedoe@example.com"))
 */
                        .setPriority(NotificationCompat.PRIORITY_MAX)//PRIORITY_DEFAULT)
                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                        .setContentIntent(tapIntent)
                        //.setStyle(new NotificationCompat.InboxStyle())
                        // Do not trigger an alert if the notification is already showing and is updated
                        .setOnlyAlertOnce(true)
                        // Group the notifications
                        // https://developer.android.com/training/notify-user/group
                        // By default, notifications are sorted according to when they were posted, but you can change order by calling setSortKey().
                        //
                        // If alerts for a notification's group should be handled by a different notification, call setGroupAlertBehavior(). For example, if you want only the summary of your group to make noise, all children in the group should have the group alert behavior GROUP_ALERT_SUMMARY. The other options are GROUP_ALERT_ALL and GROUP_ALERT_CHILDREN.
                        //
                        //.setGroup(MY_NOTIFICATION_GROUP_ID)
                        //set this notification as the summary for the group
                        //.setGroupSummary(true)
                        .setOngoing(false) // allow the user to swipe it away
                        .build();
        if (doDisplayNotification) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(appContext);
            notificationManager.notify(SERV_NOTIFICATION_ID, summaryNotification);
        }
        return(summaryNotification);
    }

    public void removeSummaryNotification(Context appContext) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(appContext);
        notificationManager.cancel(SUMMARY_NOTIFICATION_ID);
    }


    public void updateNotifications(Context context) {
        synchronized(mainSyncObject) {
            if (updateInProgress) {
                Log.w("MYCALENDAR", "updateNotifications(): update already in progress; ignoring the additional updateNotifications request");
                return;
            }
            updateInProgress = true;
        }

        // Create the notifications
        newNotifications=new ArrayList<>();
        for(EventItem event : events) {
            if (event instanceof ExtendedCalendarEvent) {
                ExtendedCalendarEvent calendarEvent = (ExtendedCalendarEvent) event;

                if (calendarEvent.isTriggered()) {
                    SpannableString spanDate = formatDate(context, calendarEvent, false); //new SpannableString(sb.toString());
                    SpannableString spanTitle = formatTitle(context, calendarEvent); //new SpannableString(calendarEvent.getTitle());
                    createNotification(context, calendarEvent.getId(), spanTitle.toString(), spanDate.toString(), calendarEvent.getStartDate().getTime());
                    newNotifications.add(calendarEvent.getId());
                }
            }
        }
        // Remove all leftover notifications
        for(Long eventId : currentNotifications) {
            if (!newNotifications.contains(eventId)) {
                removeNotification(context,eventId.longValue());
            }
        }
        currentNotifications=new ArrayList<>(newNotifications);
        if (!AgendaUpdateService.PERSISTENT_NOTIFICATION /*|| !AgendaUpdateService.PERSISTENT_NOTIFICATION_IS_SUMMARY*/) {
            if (currentNotifications.size()>1) {
                createSummaryNotification(context,NOTIFICATION_CHANNEL_ID);
            }
            else {
                removeSummaryNotification(context);
            }
        }

        synchronized(mainSyncObject) {
            updateInProgress = false;
        }
    }

    int numTriggeredEvents() {
        int cnt=0;
        for(EventItem e : events) {
            if ((!(e instanceof ExtendedCalendarEvent)) || ((ExtendedCalendarEvent)e).isTriggered()) cnt++;
        }
        return(cnt);
    }

    public void refreshOneEvent(int appWidgetId,long ids) {
        events=ExtendedCalendars.refreshOneEvent(appWidgetId,ids,events);
    }

    public void refreshList(Context context) {
        synchronized(mainSyncObject) {
            if (updateInProgress) {
                Log.w("MYCALENDAR", "refreshList(): update already in progress; ignoring the additional refreshList request");
                return;
            }
            updateInProgress = true;
        }
        Log.w("MYCALENDAR","about to get events");
        events = Events.getEvents(appWidgetId);
        Log.w("MYCALENDAR","got events: "+events.size()+"; triggered="+numTriggeredEvents());

        synchronized(mainSyncObject) {
            updateInProgress = false;
        }

        updateNotifications(context);
    }

    public synchronized void updateTaskObservers() {
        List<TaskContract> providersInUser = TaskProvider.getProvidersInUse();
        List<String> taskProviderURIs = new ArrayList<>();
        for (TaskContract t : providersInUser) {
            try {
                if (!t.getProviderURI().isEmpty() && TaskProviderListAdapter.providerExists(t)) {
                    taskProviderURIs.add(t.getProviderURI());
                }
            } catch (SecurityException e) {
                // Do nothing
            }
        }

        //Context context = AgendaWidgetApplication.getContext();
        //context.stopService(new Intent(context, AgendaUpdateService.class));

        taskObservers = new AgendaWidget.TaskObserver[taskProviderURIs.size()];
        int i = 0;
        for (String uri : taskProviderURIs) {
            taskObservers[i] = new AgendaWidget.TaskObserver(new Handler(), uri);
            i++;
        }
    }

    private void sendUpdate(Context context, Intent intent) {
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(), MainActivity.class.getName());
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
        Intent widgetUpdateIntent = new Intent();
        // TODO: see if I need to change the classes used below
        widgetUpdateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        widgetUpdateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        if (!intent.getAction().equals(Intent.ACTION_TIME_TICK)) {
            widgetUpdateIntent.putExtra(AgendaWidget.ACTION_FORCE_UPDATE, true);
        }

        context.sendBroadcast(widgetUpdateIntent);
    }

    void removeNotification(Context ctx,long eventId) {
        int id = -1;
        synchronized(notifiedEventIDs) {
            for (int i = 0; i < notifiedEventIDs.size(); i++) {
                if (notifiedEventIDs.get(i)!=null && notifiedEventIDs.get(i) == eventId) {
                    id=i;
                    break;
                }
            }
        }
        if (id!=-1) {
            removeNotificationId(id);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(ctx);
            notificationManager.cancel(id);
        }
    }

    void removeNotificationId(int id) {
        synchronized(notifiedEventIDs) {
            if (id < notifiedEventIDs.size()) {
                notifiedEventIDs.set(id, null);
            }
        }
    }

    /* external API =====> */

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.v("MYCALENDAR", "in destroy of SERVICE!!!");
        try {
            unregisterReceiver(agendaChangedReceiver);
            if (NEW_INTENTS) {
                registerReceiver(br2, intentFilter2);
            }
        } catch (IllegalArgumentException e) {
            // java.lang.IllegalArgumentException: Receiver not registered: gr.ictpro.jsalatas.agendawidget.ui.AgendaWidget$AgendaUpdateService
            // do nothing
        }
        for (AgendaWidget.TaskObserver taskObserver : taskObservers) {
            getContentResolver().unregisterContentObserver(taskObserver);
        }
        getContentResolver().unregisterContentObserver(observer);
        getContentResolver().unregisterContentObserver(calendarObserver);
        super.onDestroy();
    }

    IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public AgendaUpdateService getServerInstance() {
            Log.v("MYCALENDAR","in getServerInstance()");
            return AgendaUpdateService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.v("MYCALENDAR","in onBind()");
        return mBinder;
    }

    public void clearObserverTimer() {
        observer.clearTimer();
    }

    public List<EventItem> getEvents() {
        return(events);
    }

    public boolean isRunning() {
        return(serviceRunning);
    }
    /* <===== external API */
}

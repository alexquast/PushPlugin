package com.plugin.gcm;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Random;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;
import com.openexchange.mobile.mailapp.enterprise.R;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

    private static final String TAG = "OXMailIntentService";

    public GCMIntentService() {
        super("GCMIntentService");
    }

    @Override
    public void onRegistered(Context context, String regId) {

        Log.v(TAG, "onRegistered: " + regId);

        //BEGIN initialize custom notification handling

        //need a new random notification ID, to update the current notification
        Random rand = new Random();
        int notificationId = rand.nextInt();
        SharedPreferences preferences = context.getSharedPreferences("notifications", Context.MODE_PRIVATE);
        SharedPreferences.Editor store = preferences.edit();
        store
                .clear()
                .putInt("id", notificationId)
                .putInt("messageCount", 0);
        store.apply();

        //DONE initialize custom notification handling

        JSONObject json;

        try
        {
            json = new JSONObject().put("event", "registered");
            json.put("regid", regId);

            Log.v(TAG, "onRegistered: " + json.toString());

            // Send this JSON data to the JavaScript application above EVENT should be set to the msg type
            // In this case this is the registration ID
            PushPlugin.sendJavascript( json );

        }
        catch( JSONException e)
        {
            // No message to the user is sent, JSON failed
            Log.e(TAG, "onRegistered: JSON exception");
        }
    }

    @Override
    public void onUnregistered(Context context, String regId) {
        Log.d(TAG, "onUnregistered - regId: " + regId);
    }

    @Override
    protected void onMessage(Context context, Intent intent) {
        Log.d(TAG, "onMessage - context: " + context);

        // Extract the payload from the message
        Bundle extras = intent.getExtras();
        if (extras != null)
        {
            Log.d(TAG, "got extras from push");
            // if we are in the foreground, just surface the payload, else post it to the statusbar
            if (PushPlugin.isInForeground()) {
                extras.putBoolean("foreground", true);
                PushPlugin.sendExtras(extras);
            }
            else {
                extras.putBoolean("foreground", false);
                // standard case, a new mail arrives. Build notification and show it
                createNotification(context, extras);
            }
        }
    }



    public void createNotification(Context context, Bundle extras) {
        SharedPreferences preferences = context.getSharedPreferences("notifications", Context.MODE_PRIVATE);
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String appName = getAppName(this);
        Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Boolean unreadInfoFromServer = false;

        int messageCount = 0;

        // mixed handling for system that send unread count and those who dont send unread count
        if (extras.containsKey("unread")) {
            unreadInfoFromServer = true;
            Log.d(TAG, "Server tells unread count:" + extras.getString("unread"));
            messageCount = Integer.parseInt(extras.getString("unread", "0"));
        } else {
            messageCount = preferences.getInt("messageCount", 0);
        }

        if (messageCount > 0) {
            //remove cid from extras, so App opens in default folder
            extras.remove("cid");
        }
        notificationIntent.putExtra("pushBundle", extras);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        int defaults = Notification.DEFAULT_ALL;

        if (extras.getString("defaults") != null) {
            try {
                defaults = Integer.parseInt(extras.getString("defaults"));
            } catch (NumberFormatException e) {}
        }

        String message = extras.getString("message");

        // handle different versions of push messages here
        String subject = extras.getString("subject");
        String sender = extras.getString("sender");
        if (sender == null && subject == null) {

            // check if refresh event
            String sync_event = extras.getString("SYNC_EVENT");

            if (sync_event != null && sync_event.equals("MAIL")) {
                Log.d(TAG, "Got refresh event for mail in background, doing nothing");
                return;
            }

            // use old style message format, just for backwards compatibility
            String subjectAndSender[] = message.split("\\n");
            subject = subjectAndSender[1];
            sender = subjectAndSender[0];
        }


        // small Icon is the small one placed on bottom right on the large one
        // Large Icon could be the contact image, small icon the app icon
        // ATM the large icon is the app icon, small icon is not needed

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context)
                        .setDefaults(defaults)
                        .setSmallIcon(R.drawable.ic_action_email)
                        .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.icon))
                        //.setSmallIcon(context.getApplicationInfo().icon)
                        .setWhen(System.currentTimeMillis())
                        .setTicker(sender)
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true);

        Integer color = getNotificationColour(context);
        if (color == null){
            mBuilder.setLights(0xFF0000FF, 0, 2000);
        } else {
            Log.v(TAG, "setLights to color: " + color);
            mBuilder.setLights(color, 1000, 2000);
        }

        if (subject == null) {
            subject = "";
            Log.d(TAG, "Missing subject or sender for message");
        }
        if (!unreadInfoFromServer) {
            messageCount++;
            SharedPreferences.Editor store = preferences.edit();
            store.putInt("messageCount", messageCount);
            store.apply();
        }

        if (messageCount > 1) {
            mBuilder.setContentTitle(getResources().getText(R.string.new_messages));
            String content = getResources().getQuantityString(R.plurals.got_number_new_messages, messageCount, messageCount);
            mBuilder.setContentText(content);
            Log.d(TAG, "Switch to unspecific notification: " + content);
        } else {
            mBuilder
                    .setContentTitle(sender)
                    .setContentText(subject);
        }

        Notification notification = mBuilder.build();
        notification.defaults = 0;
        notification.flags |= Notification.FLAG_SHOW_LIGHTS;
        // read sound and vibrate settings from shared preferences
        String vibrate = getPrefValue("pushVibrate", context);
        String sound = getPrefValue("pushSound", context);
        if (vibrate.equals("true") && sound.equals("true")) {
            notification.defaults = Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE;
        } else if (vibrate.equals("true") && sound.equals("false")) {
            notification.defaults = Notification.DEFAULT_VIBRATE;
        } else if (vibrate.equals("false") && sound.equals("true")) {
            notification.defaults = Notification.DEFAULT_SOUND;
        } else {
            Log.d(TAG, "No sound, no vibrate");
        }

        int notificationId = preferences.getInt("id", 0);
        if (notificationId != 0) {
            mNotificationManager.notify(notificationId, notification);
        } else {
            Log.wtf(TAG, "No notification ID found in SharedPreferences");
        }
    }

    private String getPrefValue(String key, Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("Preferences", Context.MODE_PRIVATE);
        return sharedPreferences.getString(key, "nix");
    }

    private static String getAppName(Context context)
    {
        CharSequence appName =
                context
                        .getPackageManager()
                        .getApplicationLabel(context.getApplicationInfo());

        return (String)appName;
    }


    /*
     * Load the notification light color which is stored in the shared preferences under key "notificationColor" amd check if it is prober RGB color in RGB
     */

    private Integer getNotificationColour (Context context){
        SharedPreferences sharedPreferences = context.getSharedPreferences("Preferences", Context.MODE_PRIVATE);
        String value = sharedPreferences.getString("notificationColor", null);
        Log.v(TAG, "loaded: " + value);
        Integer color = null;
        Log.v(TAG, "Color: " + color);
        if(value != null){
            try {
                //color = Integer.parseInt(value, 16);
                color = Color.parseColor(value);
                Log.v(TAG, "ParsedColor: " + color);
            } catch (IllegalArgumentException e) {
                color = null;
                Log.v(TAG, "ongetNotificationColor: IAException " + e.getMessage());
            }
        }
        Log.v(TAG, "return: " + color);
        return color;
    }

    @Override
    public void onError(Context context, String errorId) {
        Log.e(TAG, "onError - errorId: " + errorId);
    }

    public static void resetNewMessageCounter(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("notifications", Context.MODE_PRIVATE);
        SharedPreferences.Editor store = preferences.edit();
        store.putInt("messageCount", 0);
        store.apply();
    }
}

package com.plugin.gcm;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.app.Activity;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import android.support.v4.content.WakefulBroadcastReceiver;

/*
 * Implementation of GCMBroadcastReceiver that hard-wires the intent service to be
 * com.plugin.gcm.GcmntentService, instead of your_package.GcmIntentService
 */
public class CordovaGCMBroadcastReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = "GcmIntentService";

    @Override
    public void onReceive(Context context, Intent intent) {

        Log.d(TAG, "onHandleIntent - context: " + context);

        // Extract the payload from the message
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
        String messageType = gcm.getMessageType(intent);

        if (extras != null)
        {
            try {
                if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                    JSONObject json = new JSONObject();

                    json.put("event", "error");
                    json.put("message", extras.toString());
                    PushPlugin.sendJavascript(json);
                } else if (GoogleCloudMessaging.MESSAGE_TYPE_DELETED.equals(messageType)) {
                    JSONObject json = new JSONObject();
                    json.put("event", "deleted");
                    json.put("message", extras.toString());
                    PushPlugin.sendJavascript(json);
                } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
                    // if we are in the foreground, just surface the payload, else post it to the statusbar
                    if (PushPlugin.isInForeground()) {
                        extras.putBoolean("foreground", true);
                        PushPlugin.sendExtras(extras);
                    }
                    else {
                        extras.putBoolean("foreground", false);

                        // Send a notification if there is a message
                        if (extras.getString("message") != null && extras.getString("message").length() != 0) {
                            createNotification(context, extras);
                        }
                    }
                }
            } catch (JSONException exception) {
                Log.d(TAG, "JSON Exception was had!");
            }
        }
    }
    public void createNotification(Context context, Bundle extras)
    {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String appName = getAppName(context);

        Intent notificationIntent = new Intent(context, PushHandlerActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.putExtra("pushBundle", extras);

        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        int defaults = Notification.DEFAULT_ALL;

        if (extras.getString("defaults") != null) {
            try {
                defaults = Integer.parseInt(extras.getString("defaults"));
            } catch (NumberFormatException e) {}
        }

        NotificationCompat.Builder mBuilder =
            new NotificationCompat.Builder(context)
                .setDefaults(defaults)
                .setSmallIcon(context.getApplicationInfo().icon)
                .setWhen(System.currentTimeMillis())
                .setContentTitle(extras.getString("title"))
                .setTicker(extras.getString("title"))
                .setContentIntent(contentIntent)
                .setAutoCancel(true);

        String message = extras.getString("message");
        if (message != null) {
            mBuilder.setContentText(message);
        } else {
            mBuilder.setContentText("<missing message content>");
        }

        String msgcnt = extras.getString("msgcnt");
        if (msgcnt != null) {
            mBuilder.setNumber(Integer.parseInt(msgcnt));
        }

        int notId = 0;

        try {
            notId = Integer.parseInt(extras.getString("notId"));
        }
        catch(NumberFormatException e) {
            Log.e(TAG, "Number format exception - Error parsing Notification ID: " + e.getMessage());
        }
        catch(Exception e) {
            Log.e(TAG, "Number format exception - Error parsing Notification ID" + e.getMessage());
        }

        mNotificationManager.notify((String) appName, notId, mBuilder.build());
    }

    private static String getAppName(Context context)
    {
        CharSequence appName =
                context
                    .getPackageManager()
                    .getApplicationLabel(context.getApplicationInfo());

        return (String)appName;
    }

    // @Override
    public void onError(String errorId) {
        Log.e(TAG, "onError - errorId: " + errorId);
    }

}

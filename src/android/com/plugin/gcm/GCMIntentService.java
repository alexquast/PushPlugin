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
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;
import com.openexchange.mobile.mailapp.enterprise.R;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

	private static final String TAG = "GCMIntentService";
	private static int notificationId = 0;
	private static short messageCount = 0;

	public GCMIntentService() {
		super("GCMIntentService");
	}

	@Override
	public void onRegistered(Context context, String regId) {

		Log.v(TAG, "onRegistered: "+ regId);

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
				notificationId = 0;
				messageCount = 0;
				extras.putBoolean("foreground", true);
                PushPlugin.sendExtras(extras);
			}
			else {
				if (notificationId == 0) {
					//need a new random notification ID, to update the current notification
					Random rand = new Random();
					notificationId = rand.nextInt();
				}
				if (messageCount > 0) {
					//remove cid from extras, so App opens in default folder
					extras.remove("cid");
				}
				extras.putBoolean("foreground", false);
               	// standard case, a new mail arrives. Build notification and show it
               	createNotification(context, extras);
            }
        }
	}



	public void createNotification(Context context, Bundle extras) {
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		String appName = getAppName(this);
		Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		notificationIntent.putExtra("pushBundle", extras);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		int defaults = Notification.DEFAULT_ALL;

		if (extras.getString("defaults") != null) {
			try {
				defaults = Integer.parseInt(extras.getString("defaults"));
			} catch (NumberFormatException e) {}
		}

		Log.d(TAG, "Got a message");

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
			
			Boolean isRefresh = message.equals("You've received a new login");
			if (isRefresh) {
				Log.d(TAG, "Got a relogin message, this should not happen as it is deprecated.");
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

		if (subject == null) {
			subject = "";
			Log.d(TAG, "Missing subject or sender for message");
		}

		messageCount++;
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

		if (notificationId != 0) {
			mNotificationManager.notify(notificationId, mBuilder.build());
		}
	}

	private static String getAppName(Context context)
	{
		CharSequence appName =
				context
					.getPackageManager()
					.getApplicationLabel(context.getApplicationInfo());

		return (String)appName;
	}

	@Override
	public void onError(Context context, String errorId) {
		Log.e(TAG, "onError - errorId: " + errorId);
	}

}

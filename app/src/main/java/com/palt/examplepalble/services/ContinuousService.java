package com.palt.examplepalble.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.palt.examplepalble.ExampleApplication;
import com.palt.examplepalble.MainActivity;
import com.palt.examplepalble.R;
import com.paltechnologies.pal8.devices.PalDevice;
import com.paltechnologies.pal8.devices.activator_micro.ActivatorMicroService;
import com.paltechnologies.pal8.devices.activator_micro.PalActivatorMicro;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class ContinuousService extends ActivatorMicroService {
    private static final String TAG = "ContinuousService";

    private NotificationManager notificationManager;
    private Vibrator vibrator;

    private LocalBroadcastManager localBroadcastManager;
    public static final String SERVICE_RESULT = "com.paltechnologies.activatorrxrt.services.activatormicroservice.RESULT";
    public static final String SERVICE_MESSAGE = "com.paltechnologies.activatorrxrt.services.activatormicroservice.MESSAGE";
    public static final String MESSAGE_CONNECTED = "com.paltechnologies.activatorrxrt.services.activatormicroservice.MESSAGE_CONNECTED";
    public static final String MESSAGE_DISCONNECTED = "com.paltechnologies.activatorrxrt.services.activatormicroservice.MESSAGE_DISCONNECTED";
    public static final String MESSAGE_STEPS = "com.paltechnologies.activatorrxrt.services.activatormicroservice.MESSAGE_STEPS";
    public static final String EXTRA_STEPS = "com.paltechnologies.activatorrxrt.services.activatormicroservice.EXTRA_STEPS";


    @Override
    public void onCreate() {
        super.onCreate();

        localBroadcastManager = LocalBroadcastManager.getInstance(this);

        //Fetch reference to the single instance of PalBle preserved across the lifetime of the app
        palBle = ExampleApplication.getPalBle(this);
        palBle.setListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Starting");

        /* A foreground service is used to prevent (significantly reduce the chance of)
        Android shutting for the service and closing the connection. The foreground service
        provides a perminate notification box that lets the user know the service is running
        and may also be used for prompts and communication.

        This example application response to every event with a notification message and
        vibration. This is highly unrecommended for a released application.
         */
        createNotificationChannel();
        startForeground(1, getNotification(
                intent.getStringExtra("titleExtra"),
                intent.getStringExtra("textExtra")));

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onConnected(PalDevice device) {
        Log.i(TAG, "onConnected: " + device.getSerial());

        updateNotification(device.getName() + " " + device.getSerial(), "Connected");
        sendResult(MESSAGE_CONNECTED);

        long[] pattern = {500, 250, 500, 250, 500, 250, 500, 250};
        vibrate(pattern);
    }

    @Override
    public void onDisconnected(PalDevice device, String task) {
        Log.i(TAG, "onDisconnected: " + device.getSerial());

        updateNotificationText("Disconnected");
        sendResult(MESSAGE_DISCONNECTED);

        long[] pattern = {1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000, 500, 1000, 500,1000, 500 ,1000, 500};
        vibrate(pattern);

        super.onDisconnected(device, task);
    }

    @Override
    public void onStepEvent(PalActivatorMicro device, int stepCount) {
        Log.i(TAG, "onStepEvent: " + Integer.toString(stepCount));

        updateNotificationText("Steps: " + Integer.toString(stepCount));
        sendSteps(stepCount);

        super.onStepEvent(device, stepCount);
    }

    @Override
    public void onSedentaryAlarm(PalActivatorMicro device) {
        updateNotificationText("Get up and move!");
        long[] pattern = {750, 250, 750, 250, 750, 250, 750, 250, 750, 250, 750, 250};
        vibrate(pattern);
    }

    /* ***** Notification *******/
    private String title;
    private String text;

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(serviceChannel);
        } else {
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        }
    }

    private void updateNotification(String title, String text) {
        notificationManager.notify(1, getNotification(title, text));
    }

    private void updateNotificationTitle(String title) {
        notificationManager.notify(1, getNotification(title, text));
    }

    private void updateNotificationText(String text) {
        notificationManager.notify(1, getNotification(title, text));
    }

    private Notification getNotification(String title, String string) {
        this.title = title;
        this.text = text;

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(string)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .build();
    }

    @Override
    protected void sendStatusMessage(String message) {
        super.sendStatusMessage(message);
        updateNotificationText(message);
        saveMessage(message);
        if(message.contains("Battery") || message.contains("Charging"))
            saveBattery(message);
    }

    private void vibrate(long[] pattern) {
        vibrate(pattern, -1);
    }

    private void vibrate(long[] pattern, int repeat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat));
        else
            vibrator.vibrate(pattern, -1);
    }
    /* *** Notification END *****/

    /* ******* Broadcast ********/
    private void sendResult(String message) {
        Intent intent = new Intent(SERVICE_RESULT);
        if(message != null)
            intent.putExtra(SERVICE_MESSAGE, message);
        localBroadcastManager.sendBroadcast(intent);
    }

    private void sendSteps(int stepCount) {
        Intent intent = new Intent(SERVICE_RESULT);
        intent.putExtra(SERVICE_MESSAGE, MESSAGE_STEPS);
        intent.putExtra(EXTRA_STEPS, stepCount);
        localBroadcastManager.sendBroadcast(intent);
    }
    /* ***** Broadcast END ******/

    /* ****** Data Saving *******/
    private void saveMessage(String message) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                    new FileOutputStream(getPublicStorageDir("uActivator/continuous.log"), true));
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            SimpleDateFormat format = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss");
            String time = format.format(calendar.getTime()) + ":\t";
            time += message + "\n";
            outputStreamWriter.write(time);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    private void saveBattery(String battery) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                    new FileOutputStream(getPublicStorageDir("uActivator/continuous_battery.log"), true));
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            SimpleDateFormat format = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss");
            String time = format.format(calendar.getTime()) + ":\t";
            time += battery + "\n";
            outputStreamWriter.write(time);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    private File getPublicStorageDir(String fileName) {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "uActivator/");
        if(!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Directory not created");
            }
        }

        File path = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), fileName);
        return path;
    }
    /* **** Data Saving END *****/
}

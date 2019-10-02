package com.palt.examplepalble;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.palt.examplepalble.services.ContinuousService;
import com.paltechnologies.pal8.PalBle;
import com.paltechnologies.pal8.PalHelper;
import com.paltechnologies.pal8.ScanListener;
import com.paltechnologies.pal8.ScanParameters;
import com.paltechnologies.pal8.devices.DownloadInfo;
import com.paltechnologies.pal8.devices.PalDevice;
import com.paltechnologies.pal8.devices.activator_micro.PalActivatorMicro;
import com.paltechnologies.pal8.devices.activator_micro.PalActivatorMicroListener;
import com.paltechnologies.pal8.devices.activator_micro.data.ActivatorMicroConnectionInfo;
import com.paltechnologies.pal8.devices.activator_micro.data.ActivatorMicroSummaries;
import com.paltechnologies.pal8.devices.common.DeviceWake;
import com.paltechnologies.pal8.exceptions.EncryptionException;
import com.paltechnologies.pal8.exceptions.ListenerException;
import com.polidea.rxandroidble2.exceptions.BleScanException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements ScanListener, PalActivatorMicroListener {
    private final String TAG = "MainActivity";

    private static final int STORAGE_REQ = 1;
    private static final String lastSerial = "com.palt.examplepalble.LAST_NEW_SERIAL";

    private EditText serialEt;
    private FloatingActionButton continuousBtn;
    private FloatingActionButton summaryBtn;
    private TextView summaryTv;
    private TextView messageTv;
    private Switch dfuSw;

    private PalBle palBle;

    private String serial;
    private final String key = "ciMhPxZXoB_0C7htKpejCw==";
    private ActivatorMicroSummaries summaries;

    private BroadcastReceiver broadcastReceiver;

    ArrayList<Pair<String, Long>> timingLogger;
    boolean storageAllowed = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initiliseUI();

        //This example app stores data and logging infomation on the device
        // at localstorage/documents/uActivator - Therefore storage permissions are required
        requestStoragePermission();

        //All this activity to receive messages from the continuous connection service
        initiliseBroadcastReceiver();

        //Fetch reference to the single instance of PalBle preserved across the lifetime of the app
        palBle = ExampleApplication.getPalBle(this);
        palBle.setListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();

        LocalBroadcastManager.getInstance(this).registerReceiver((broadcastReceiver),
                new IntentFilter(ContinuousService.SERVICE_RESULT));
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);

        super.onStop();
    }


    /* ********** One-Off Pull Connection ************/
    private void onSummaryClick(View v) {
        summaries = null;
        getSerial(v);
        setMessage("Scanning for " + serial);

        timingLogger = new ArrayList<>();
        timingLogger.add(new Pair<>("Connection", System.currentTimeMillis()));

        //Scan for device based upon serial number and device type
        ScanParameters scanParameters = new ScanParameters();
        scanParameters.deviceFamily = ScanParameters.ACTIVATORMICRO;
        scanParameters.serialList.add(serial);
        palBle.startScan(scanParameters);

        //Directly connect to device with serial number and encryption key
        //palBle.connect(serial, key);
    }

    /*          ScanListener          */
    @Override
    public void onScanResultsChanged(PalDevice device) {
        if (device instanceof PalActivatorMicro) {
            timingLogger.add(new Pair<>("found", System.currentTimeMillis()));
            palBle.stopScan();
            setMessageOnUI(serial + " found");
            establishConnection((PalActivatorMicro) device);
        }
    }

    @Override
    public void onScanTimeout() {
        setMessageOnUI(serial + " not found");
    }

    @Override
    public void onScanError(BleScanException scanException) {
        setMessageOnUI(scanException.getMessage());
    }
    /*        ScanListener END        */

    private void establishConnection(PalActivatorMicro device) {
        try {
            device.setListener(this);
        } catch (ListenerException e) {
            setMessageOnUI(e.getMessage());
            return;
        }

        try {
            //Start a connection to the device to gather the summary data
            device.connectForSummary(key);
        } catch (EncryptionException e) {
            setMessageOnUI(e.getMessage());
        }
    }

    /*   PalActivatorMicroListener    */
    @Override
    public void onSummary(PalActivatorMicro device, ActivatorMicroSummaries summaries) {
        timingLogger.add(new Pair<>("summaryGot", System.currentTimeMillis()));
        this.summaries = summaries;
        String summaryString = "Hour\n\tsteps: " + summaries.getHour().getStepCount()
                + "\n\tlying: " + summaries.getHour().getLyingSeconds()
                + "\n\tsitting: " + summaries.getHour().getSittingSeconds()
                + "\n\tupright: " + summaries.getHour().getUprightSeconds()
                + "\n\tstepping: " + summaries.getHour().getSteppingSeconds()
                + "\nToday\n\tsteps: " + summaries.getToday().getStepCount()
                + "\n\tlying: " + summaries.getToday().getLyingSeconds()
                + "\n\tsitting: " + summaries.getToday().getSittingSeconds()
                + "\n\tupright: " + summaries.getToday().getUprightSeconds()
                + "\n\tstepping: " + summaries.getToday().getSteppingSeconds()
                + "\n\tbattery: " + device.getConnectionInfo().getCurrentBattery();
        if (device.getConnectionInfo() instanceof ActivatorMicroConnectionInfo) {
            ActivatorMicroConnectionInfo connectionInfo = (ActivatorMicroConnectionInfo) device.getConnectionInfo();
            summaryString +=
                    "\n\tposture: "
                            + (connectionInfo.isCurrentUpright() ? "upright" : "sedentary")
                            + " for " + Integer.toString(connectionInfo.getCurrentPostureTime()) + " s";
            if(connectionInfo.isHibernating())
                summaryString += "\n\tHibernating";
        }
        setSummaryOnUI(device.getSerial() + " Summaries\n" + summaryString);
        saveSummary(summaryString);
        saveBattery(Integer.toString(device.getConnectionInfo().getCurrentBattery()));
    }
    
    @Override
    public void onWakeCompleted(PalDevice device) {
        timingLogger.add(new Pair<>("woken", System.currentTimeMillis()));
        setMessageOnUI(serial + " woken");
    }

    @Override
    public void onWakeRetrying(PalDevice device, int attemptsRemaining) {
        setMessageOnUI(serial + " retrying wake - " + Integer.toString(attemptsRemaining));
    }

    @Override
    public void onWakeStatus(PalDevice device, DeviceWake.WakeStatus status) {
        setMessageOnUI(serial + " wake status - " + DeviceWake.statusToString(status));
    }

    @Override
    public void onWakeFailed(PalDevice device, String explanation) {
        setMessageOnUI(serial + " wake failed - " + explanation);
    }

    @Override
    public void onWakeError(PalDevice device, Throwable throwable) {
        setMessageOnUI(serial + " wake error - " + throwable.getMessage());
    }

    @Override
    public void onConnected(PalDevice device) {
        timingLogger.add(new Pair<>("connected", System.currentTimeMillis()));
        setMessageOnUI(serial + " connected");
    }

    @Override
    public void onDisconnected(PalDevice device, String task) {
        if(task.equalsIgnoreCase(PalActivatorMicro.P8C_TASK_SUMMARY)) {
            timingLogger.add(new Pair<>("summaryDisconnected", System.currentTimeMillis()));
            setMessageOnUI(serial + " disconnected - Summary");
            if (summaries != null) {
                if (device instanceof PalActivatorMicro) {
                    setMessageOnUI(serial + " starting download");
                    try {
                        ((PalActivatorMicro) device).connectForDownload();
                    } catch (EncryptionException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else if(task.equalsIgnoreCase(PalActivatorMicro.P8C_TASK_DOWNLOAD)) {
            timingLogger.add(new Pair<>("downloadDisconnected", System.currentTimeMillis()));
            saveTiming(device.getDownloadInfo().getPageCount());

            if(dfuSw.isChecked())
                device.enableDFU();
        } else {
            setMessageOnUI(serial + " disconnected - " + task);
        }
    }

    @Override
    public void onRetrying(PalDevice device, String task, int triesRemaining) {
        setMessageOnUI(serial + " retrying - " + task + " (" + triesRemaining + ")");
    }

    @Override
    public void onFailed(PalDevice device, String task, String explanation) {
        setMessageOnUI(serial + " failed - " + task + "\n" + explanation);
    }

    @Override
    public void onDeviceError(PalDevice device, String task, Throwable throwable) {
        setMessageOnUI(serial + " error - " + task + "\n" + throwable.getMessage());
    }

    @Override
    public void onEncrypted(PalDevice device, String key) {
        timingLogger.add(new Pair<>("encrypted", System.currentTimeMillis()));
        setMessageOnUI(serial + " encrypted\n" + key);
    }

    @Override
    public void onEncryptionKeyNeeded(PalDevice device, PalDevice.KeyType keyType) {
        setMessageOnUI(serial + " encryption key needed - " + keyType.toString());
    }

    @Override
    public void onInvalidEncryptionKey(PalDevice device) {
        setMessageOnUI(serial + " invalid key");
    }

    @Override
    public void onDownloadStarting(PalDevice device, int pagesToDownload) {
        timingLogger.add(new Pair<>("downloadStarting", System.currentTimeMillis()));
        setMessageOnUI(serial + " downloading " + Integer.toString(pagesToDownload) + " pages");
    }

    @Override
    public void onDownloadProgress(PalDevice device, int pagesDownloaded, int pagesChange) {
        setMessageOnUI(serial + " downloaded: " +
                Integer.toString(pagesDownloaded) + "/" +
                Integer.toString(device.getDownloadInfo().getPagesToDownload()) +
                " pages");
    }

    @Override
    public void onDownloadFinished(PalDevice device, DownloadInfo downloadInfo) {
        timingLogger.add(new Pair<>("downloadFinished", System.currentTimeMillis()));
        try {
            setMessageOnUI(serial + " finished downloading" +
                    "\n" + PalHelper.toHexString(downloadInfo.getPage(0)));
            saveData(device.getSerial(), downloadInfo);
        } catch (IndexOutOfBoundsException e) {
            setMessageOnUI(serial + " No pages available");
        }
    }

    @Override
    public void onDfuEnabled(PalDevice device) {
        Log.i(TAG, "onDfuEnabled: ");
        device.startDFU(this);
    }
    /* PalActivatorMicroListener END  */

    /* ******** One-Off Pull Connection END **********/
    
    
    /* ******** Continuous Push Connection ***********/
    private void onContinuousClick(View v) {
        getSerial(v);
        setMessage("Starting continuous connection to " + serial);

        startContinuousConnectionService();
    }

    private void startContinuousConnectionService() {
        Intent serviceIntent = new Intent(this, ContinuousService.class);
        serviceIntent.putExtra("titleExtra", "Activator Micro");
        serviceIntent.putExtra("textExtra", "Connecting...");
        serviceIntent.putExtra("serialExtra", serial);
        serviceIntent.putExtra("keyExtra", key);

        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void initiliseBroadcastReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String s = intent.getStringExtra(ContinuousService.SERVICE_MESSAGE);

                switch (s) {
                    case ContinuousService.MESSAGE_CONNECTED:
                        setMessageOnUI("Connected");
                        break;
                    case ContinuousService.MESSAGE_DISCONNECTED:
                        setMessageOnUI("Disconnected");
                        break;
                    case ContinuousService.MESSAGE_STEPS:
                        setMessageOnUI("Steps "
                                + intent.getIntExtra(ContinuousService.EXTRA_STEPS, 0));
                        break;
                }
            }
        };
    }
    /* ****** Continuous Push Connection END *********/


    /*         UI Interaction         */
    private void initiliseUI() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        serial = preferences.getString(lastSerial, "982100");
        serialEt = findViewById(R.id.et_micro_serial);
        serialEt.setText(serial);

        continuousBtn = findViewById(R.id.fb_micro_continuous);
        continuousBtn.setOnClickListener(v -> onContinuousClick(v));

        summaryBtn = findViewById(R.id.fb_micro_summary);
        summaryBtn.setOnClickListener(v -> onSummaryClick(v));

        summaryTv = findViewById(R.id.tv_micro_summary);

        messageTv = findViewById(R.id.tv_micro_message);

        dfuSw = findViewById(R.id.sw_micro_dfu);
    }

    private void getSerial(View v) {
        serial = serialEt.getText().toString();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit().putString(lastSerial, serial).apply();
        InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(v.getApplicationWindowToken(),0);
    }

    private void setMessageOnUI(String message) {
        runOnUiThread(() -> setMessage(message));
    }

    private void setMessage(String message) {
        Log.i(TAG, "setMessage: " + message);
        messageTv.setText(message);
    }

    private void setSummaryOnUI(String message) {
        runOnUiThread(() -> setSummary(message));
    }

    private void setSummary(String message) {
        Log.i(TAG, "setSummary: " + message);
        summaryTv.setText(message);
    }
    /*       UI Interaction END       */


    /*       Data and Log Saving      */
    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Log.i(TAG, "requestStoragePermission: ah");
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        STORAGE_REQ);
            }
        } else {
            storageAllowed = true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case STORAGE_REQ: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    storageAllowed = true;
                } else {
                    storageAllowed = true;
                }
                return;
            }
        }
    }

    private void saveData(String serial, DownloadInfo download) {
        byte temp[] = new byte[download.getDownloadSize()];
        for (int i = 0; i < download.getPageCount(); ++i) {
            System.arraycopy(download.getPage(i), 0, temp, i*256, 256);
        }
        saveData(serial, temp);
    }

    private void saveData(String serial, List<Byte> data) {
        Byte temp[] = data.toArray(new Byte[data.size()]);
        byte bigArray[] = new byte[temp.length];
        for(int i = 0; i < temp.length; i++)
            bigArray[i] = temp[i];
        saveData(serial, bigArray);
    }

    private void saveTiming(int pageCount) {
        if(!storageAllowed)
            return;
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                    new FileOutputStream(getPublicStorageDir("uActivator/timing.log"), true));
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            SimpleDateFormat format = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss");
            String time = "\n\n" + format.format(calendar.getTime()) + "\n";
            Long startTime = timingLogger.get(0).second;
            for(int i = 1; i < timingLogger.size(); i++) {
                time += "\t" + timingLogger.get(i).first + ": " + Long.toString(timingLogger.get(i).second - startTime) + "\n";
            }
            time += "\tpageCount: " + Integer.toString(pageCount);
            outputStreamWriter.write(time);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    private void saveSummary(String summary) {
        if(!storageAllowed)
            return;
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                    new FileOutputStream(getPublicStorageDir("uActivator/summary.log"), true));
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            SimpleDateFormat format = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss");
            String time = "\n\n" + format.format(calendar.getTime()) + "\n";
            time += summary;
            outputStreamWriter.write(time);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    private void saveBattery(String battery) {
        if(!storageAllowed)
            return;
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                    new FileOutputStream(getPublicStorageDir("uActivator/battery.log"), true));
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

    private void saveData(String serial, byte[] data) {
        if(!storageAllowed)
            return;
        if (isExternalStorageWritable()) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            SimpleDateFormat format = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss");
            String date = format.format(calendar.getTime());

            File file = getPublicStorageDir("uActivator/ua_" + serial + "_" + date + ".datx");

            try {
                file.createNewFile();
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(data);
                fos.flush();
                fos.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                this.runOnUiThread(() -> {
                    Log.w(TAG, "onDownloadFinished: failed to save", e);
                });
            } catch (IOException e) {
                e.printStackTrace();
                this.runOnUiThread(() -> {
                    Log.w(TAG, "onDownloadFinished: failed to save", e);
                });
            }
            Log.i(TAG, "onDownloadCompleted: Download saved");
            this.runOnUiThread(() -> {
                Log.i(TAG, "onDownloadFinished: Saved!");
            });
        } else {
            Log.w(TAG, "onDownloadFinished: unable to save");
        }
    }

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
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
    /*     Data and Log Saving END    */
}
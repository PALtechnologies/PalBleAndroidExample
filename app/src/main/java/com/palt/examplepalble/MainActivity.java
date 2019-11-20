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
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.palt.examplepalble.services.ContinuousService;
import com.paltechnologies.pal8.PalBle.PalBle;
import com.paltechnologies.pal8.PalBle.PalScanListener;
import com.paltechnologies.pal8.PalBle.ScanParameters;
import com.paltechnologies.pal8.PalHelper;
import com.paltechnologies.pal8.data.info.DownloadInfo;
import com.paltechnologies.pal8.devices.PalDevice;
import com.paltechnologies.pal8.devices.activator_micro.PalActivatorMicro;
import com.paltechnologies.pal8.devices.activator_micro.PalActivatorMicroListener;
import com.paltechnologies.pal8.devices.activator_micro.data.ActivatorMicroConnectionInfo;
import com.paltechnologies.pal8.devices.activator_micro.data.ActivatorMicroEpochs;
import com.paltechnologies.pal8.devices.activator_micro.data.ActivatorMicroSummaries;
import com.paltechnologies.pal8.devices.activator_micro.data.ActivatorMicroSummary;
import com.paltechnologies.pal8.devices.common.helpers.Waker;
import com.paltechnologies.pal8.exceptions.EncryptionException;
import com.paltechnologies.pal8.exceptions.ListenerException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements PalScanListener, PalActivatorMicroListener {
    private final String TAG = "MainActivity";

    private static final String lastSerial = "com.palt.examplepalble.LAST_NEW_SERIAL";
    private static final int PERMISSIONS_REQUEST_WRITE = 43;

    private String serial;
    private final String key = "ciMhPxZXoB_0C7htKpejCw==";

    private EditText serialEt;
    private TextView summaryTv;
    private TextView epochTv;
    private TextView downloadTv;
    private TextView messageTv;
    private Switch downloadSw;
    private Switch dfuSw;

    private PalBle palBle;

    private boolean deleting = false;

    private BroadcastReceiver broadcastReceiver;

    ArrayList<Pair<String, Long>> timingLogger;
    boolean storageAllowed = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initialiseUI();

        //This example app stores data and logging information on the device
        // at localstorage/documents/uActivator - Therefore storage permissions are required
        requestStoragePermission();

        //All this activity to receive messages from the continuous connection service
        initialiseBroadcastReceiver();

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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_WRITE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                storageAllowed = true;
            else
                setMessageOnUI("Write permissions are required to save logs and data");
        } else {
            //Required to allow library to handle BLE permissions
            palBle.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //Required to allow library to handle BLE activation
        palBle.onActivityResult(requestCode, resultCode, data);
    }

    /* ********** One-Off Pull Connection ************/
    private void onSummaryClick(View v) {
        deleting = false;
        getSerial(v);
        setMessage("Scanning for " + serial);

        timingLogger = new ArrayList<>();
        timingLogger.add(new Pair<>("Connection", System.currentTimeMillis()));

        startScan();
    }

    private void onDeleteClick(View v) {
        deleting = true;
        getSerial(v);
        setMessage("Scanning for " + serial);

        startScan();
    }

    private void startScan() {
        //Scan for device based upon serial number and device type
        ScanParameters scanParameters = new ScanParameters();
        scanParameters.deviceFamily = ScanParameters.ACTIVATORMICRO;
        scanParameters.serialList.add(serial);
        palBle.startScan(scanParameters);
    }

    /*        PalScanListener         */
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
    public void onScanError(Throwable throwable) {
        setMessageOnUI(throwable.getMessage());
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
            if(!deleting) {
                //Start a connection to the device to gather the summary data
                device.connectForSummary(key, true);    //NOTE: ensure you enable hour epochs at connection
            } else {
                //Start a connection to the device to delete all stored data
                device.connectForDelete(key);
            }
        } catch (EncryptionException e) {
            setMessageOnUI(e.getMessage());
        }
    }

    /*   PalActivatorMicroListener    */
    @Override
    public void onConnected(PalDevice device) {
        timingLogger.add(new Pair<>("onConnected", System.currentTimeMillis()));
        setMessageOnUI(device.getSerial() + " connected");
    }

    @Override
    public void onDisconnected(PalDevice device, String task) {
        timingLogger.add(new Pair<>("onDisconnected", System.currentTimeMillis()));
        setMessageOnUI(device.getSerial() + " disconnected");
    }

    /*          Summary         */
    @Override
    public void onSummary(PalActivatorMicro device, ActivatorMicroSummaries summaries) {
        timingLogger.add(new Pair<>("onSummary", System.currentTimeMillis()));

        String summaryString = device.getSerial() + " Hour\nsteps: " + summaries.getHour().getStepCount()
                + "\nlying: " + summaries.getHour().getLyingSeconds()
                + "\nsitting: " + summaries.getHour().getSittingSeconds()
                + "\nupright: " + summaries.getHour().getUprightSeconds()
                + "\nstepping: " + summaries.getHour().getSteppingSeconds()
                + "\nToday\nsteps: " + summaries.getToday().getStepCount()
                + "\nlying: " + summaries.getToday().getLyingSeconds()
                + "\nsitting: " + summaries.getToday().getSittingSeconds()
                + "\nupright: " + summaries.getToday().getUprightSeconds()
                + "\nstepping: " + summaries.getToday().getSteppingSeconds()
                + "\nbattery: " + device.getConnectionInfo().getCurrentBattery();
        if (device.getConnectionInfo() instanceof ActivatorMicroConnectionInfo) {
            ActivatorMicroConnectionInfo connectionInfo = (ActivatorMicroConnectionInfo) device.getConnectionInfo();
            summaryString +=
                    "\nposture: "
                            + (connectionInfo.isCurrentUpright() ? "upright" : "sedentary")
                            + " for " + connectionInfo.getCurrentPostureTime() + " s";
            if(connectionInfo.isHibernating())
                summaryString += "\nHibernating";
        }
        setSummaryOnUI(device.getSerial() + " Summaries\n" + summaryString);
        saveSummary(summaryString);
        saveBattery(Integer.toString(device.getConnectionInfo().getCurrentBattery()));
    }

    @Override
    public void onSummaryEpochs(PalActivatorMicro device, ActivatorMicroEpochs epochs) {
        timingLogger.add(new Pair<>("onSummaryEpochs", System.currentTimeMillis()));

        ActivatorMicroSummary summary = epochs.getSummary(5);
        String epochString = device.getSerial() + " Epochs\nLast 5 Minutes"
                + "\nsteps: " + summary.getStepCount()
                + "\nlying: " + summary.getLyingSeconds()
                + "\nsitting: " + summary.getSittingSeconds()
                + "\nupright: " + summary.getUprightSeconds()
                + "\nstepping: " + summary.getSteppingSeconds();

        summary = epochs.getSummary(5, 10);
        epochString += "\n\nBetween 5-10 Minutes"
                + "\nsteps: " + summary.getStepCount()
                + "\nlying: " + summary.getLyingSeconds()
                + "\nsitting: " + summary.getSittingSeconds()
                + "\nupright: " + summary.getUprightSeconds()
                + "\nstepping: " + summary.getSteppingSeconds();

        setEpochOnUI(epochString);
    }

    @Override
    public void onSummaryCompleted(PalActivatorMicro device) {
        timingLogger.add(new Pair<>("onSummaryCompleted", System.currentTimeMillis()));

        if(downloadSw.isChecked()) {
            setMessageOnUI(device.getSerial() + " starting download");
            try {
                device.connectForDownload();
            } catch (EncryptionException e) {
                Log.w(TAG, "onSummaryCompleted: ", e);
                setMessageOnUI(device.getSerial() + " Encryption failed while downloading");
            }
        } else if(dfuSw.isChecked()) {
            setMessageOnUI(device.getSerial() + " starting DFU");
            device.enableDFU(getApplicationContext(), "/uActivator/fw/activator_micro_0_3_4_3.zip");
        } else {
            setMessageOnUI(device.getSerial() + " disconnected");
            saveTiming(0);
        }
    }

    @Override
    public void onSummaryRetry(PalActivatorMicro device, int attemptsRemaining) {
        setMessageOnUI(device.getSerial()
                + " summary fetch failed - retry(" + attemptsRemaining + ")");
    }

    @Override
    public void onSummaryFailed(PalActivatorMicro device, Throwable throwable) {
        Log.w(TAG, "onSummaryFailed: ", throwable);
        setMessageOnUI(device.getSerial() + " - " + throwable.getMessage());
    }
    /*        Summary END       */

    /*           Wake           */
    @Override
    public void onWakeStatus(PalDevice device, Waker.WakeStatus wakeStatus) {
        setMessageOnUI(device.getSerial() + " wake status - " + wakeStatus.toString());
    }

    @Override
    public void onWakeCompleted(PalDevice device) {
        timingLogger.add(new Pair<>("woken", System.currentTimeMillis()));
        setMessageOnUI(device.getSerial() + " woken");
    }

    @Override
    public void onWakeRetry(PalDevice device, int attemptsRemaining) {
        setMessageOnUI(device.getSerial() + " retrying wake - " + attemptsRemaining);
    }

    @Override
    public void onWakeFailed(PalDevice device, Throwable throwable) {
        setMessageOnUI(device.getSerial() + " wake error - " + throwable.getMessage());
    }
    /*         Wake END         */

    /*        Encryption        */
    @Override
    public void onEncrypted(PalDevice device, String key) {
        timingLogger.add(new Pair<>("encrypted", System.currentTimeMillis()));
        setMessageOnUI(device.getSerial() + " encrypted\n" + key);
    }

    @Override
    public void onEncryptionKeyNeeded(PalDevice device, PalDevice.KeyType keyType) {
        setMessageOnUI(device.getSerial() + " encryption key needed - " + keyType.toString());
    }

    @Override
    public void onInvalidEncryptionKey(PalDevice device) {
        setMessageOnUI(device.getSerial() + " invalid key");
    }
    /*      Encryption END      */

    /*         Download         */
    @Override
    public void onDownloadStarting(PalDevice device, int pagesToDownload) {
        timingLogger.add(new Pair<>("downloadStarting", System.currentTimeMillis()));
        setMessageOnUI(device.getSerial() + " downloading " + pagesToDownload + " pages");
    }

    @Override
    public void onDownloadProgress(PalDevice device, int pagesDownloaded, int pagesChange) {
        setMessageOnUI(device.getSerial() + " downloaded: " +
                pagesDownloaded + "/" +
                device.getDownloadInfo().getPagesToDownload() +
                " pages");
    }


    @Override
    public void onDownloadNewPageAvailable(PalDevice device, int page) {

    }

    @Override
    public void onDownloadCompleted(PalDevice device, DownloadInfo downloadInfo) {
        timingLogger.add(new Pair<>("downloadFinished", System.currentTimeMillis()));
        try {
            setMessageOnUI(serial + " download completed");
            String downloadString = PalHelper.toHexString(
                    downloadInfo.getPage(downloadInfo.getPageCount()-1)).substring(5);
            setDownloadOnUI(downloadString);
            saveData(device.getSerial(), downloadInfo);
        } catch (IndexOutOfBoundsException e) {
            setMessageOnUI(device.getSerial() + " No pages available");
        }

        if (dfuSw.isChecked()) {
            setMessageOnUI(device.getSerial() + " starting DFU");
            device.enableDFU(getApplicationContext(), "/uActivator/fw/activator_micro_0_3_0_3.zip");
        } else {
            setMessageOnUI(device.getSerial() + " Download completed");
            saveTiming(downloadInfo.getPageCount());
        }
    }

    @Override
    public void onDownloadFailed(PalDevice device, Throwable throwable) {
        Log.w(TAG, "onDownloadFailed: ", throwable);
        setMessageOnUI(device.getSerial() + " - " + throwable.getMessage());
    }
    /*       Download END       */

    /*          Delete          */
    @Override
    public void onDeleteArmed(PalDevice device) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Delete All Data?");
            builder.setMessage("This will delete all data stored upon "
                    + device.getSerial()
                    + " , are you sure?");

            builder.setPositiveButton("Delete",
                    (dialog, which) -> device.delete());
            builder.setNegativeButton("Cancel",
                    ((dialog, which) -> device.disarm()));

            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    @Override
    public void onDeleteDisarmed(PalDevice device) {
        setMessageOnUI(device.getSerial() + " memory delete disarmed");
    }

    @Override
    public void onDeleteStarting(PalDevice device) {
        setMessageOnUI(device.getSerial() + " deleting memory...");
    }

    @Override
    public void onDeleteCompleted(PalDevice device) {
        setMessageOnUI(device.getSerial() + " memory deleted");
    }

    @Override
    public void onDeleteFailed(PalDevice device, Throwable throwable) {
        Log.w(TAG, "onDeleteFailed: ", throwable);
        setMessageOnUI(device.getSerial() + " - " + throwable.getMessage());
    }
    /*        Delete END        */

    /*            DFU           */
    @Override
    public void onDfuProgress(PalDevice device, int percent, float avgSpeed) {
        setMessageOnUI(device.getSerial() + " DFU Progress: " + percent + "%");
    }

    @Override
    public void onDfuCompleted(PalDevice device) {
        setMessageOnUI(device.getSerial() + " DFU Completed");
        saveTiming(0);
    }

    @Override
    public void onDfuError(PalDevice device, Throwable throwable) {
        Log.w(TAG, "onDfuError: ", throwable);
        setMessageOnUI(device.getSerial() + " - " + throwable.getMessage());
    }
    /*          DFU END         */

    /*          General         */
    @Override
    public void onRetry(PalDevice device, int attemptsRemaining, String description) {
        setMessageOnUI(device.getSerial() + " retrying - " + description + " (" + attemptsRemaining + ")");
    }

    @Override
    public void onError(PalDevice device, Throwable throwable, String description) {
        setMessageOnUI(device.getSerial() + " failed - " + description + "\n" + throwable.getMessage());
    }

    @Override
    public void onFailed(PalDevice device, Throwable throwable, String description) {
        setMessageOnUI(device.getSerial() + " error - " + description + "\n" + throwable.getMessage());
    }
    /*        General END       */
    /* PalActivatorMicroListener END  */

    /* ******** One-Off Pull Connection END **********/
    
    
    /* ******** Continuous Push Connection ***********/
    private void onContinuousClick(View v) {
        deleting = false;
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

    private void initialiseBroadcastReceiver() {
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
    private void initialiseUI() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        serial = preferences.getString(lastSerial, "982100");
        serialEt = findViewById(R.id.et_micro_serial);
        serialEt.setText(serial);

        FloatingActionButton continuousBtn = findViewById(R.id.fb_micro_continuous);
        continuousBtn.setOnClickListener(this::onContinuousClick);

        FloatingActionButton summaryBtn = findViewById(R.id.fb_micro_summary);
        summaryBtn.setOnClickListener(this::onSummaryClick);

        FloatingActionButton deleteBtn = findViewById(R.id.fb_micro_delete);
        deleteBtn.setOnClickListener(this::onDeleteClick);

        summaryTv = findViewById(R.id.tv_micro_summary);
        epochTv = findViewById(R.id.tv_micro_epoch);
        downloadTv = findViewById(R.id.tv_micro_download);
        messageTv = findViewById(R.id.tv_micro_message);

        downloadSw = findViewById(R.id.sw_micro_download);
        dfuSw = findViewById(R.id.sw_micro_dfu);
    }

    private void getSerial(View v) {
        serial = serialEt.getText().toString();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit().putString(lastSerial, serial).apply();
        InputMethodManager inputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.hideSoftInputFromWindow(v.getApplicationWindowToken(),0);
        }
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

    private void setEpochOnUI(String message) {
        runOnUiThread(() -> setEpoch(message));
    }

    private void setEpoch(String message) {
        Log.i(TAG, "setSummary: " + message);
        epochTv.setText(message);
    }

    private void setDownloadOnUI(String message) {
        runOnUiThread(() -> setDownload(message));
    }

    private void setDownload(String message) {
        Log.i(TAG, "setSummary: " + message);
        downloadTv.setText(message);
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
                        PERMISSIONS_REQUEST_WRITE);
            }
        } else
            storageAllowed = true;
    }

    private void saveData(String serial, DownloadInfo download) {
        try {
            saveData(serial, download.getDatx());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private void saveTiming(int pageCount) {
        if(!storageAllowed)
            return;
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                    new FileOutputStream(getPublicStorageDir("uActivator/timing.log"), true));
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            SimpleDateFormat format = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss", Locale.ENGLISH);
            StringBuilder time = new StringBuilder("\n\n" + format.format(calendar.getTime()) + "\n");
            Long startTime = timingLogger.get(0).second;
            for(int i = 1; i < timingLogger.size(); i++) {
                time.append("\t").append(timingLogger.get(i).first).append(": ").append((timingLogger.get(i).second - startTime)).append("\n");
            }
            time.append("\tpageCount: ").append(pageCount);
            outputStreamWriter.write(time.toString());
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
            SimpleDateFormat format = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss", Locale.ENGLISH);
            String string = "\n\n" + format.format(calendar.getTime()) + "\n"
                    + summary;
            outputStreamWriter.write(string);
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
            SimpleDateFormat format = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss", Locale.ENGLISH);
            String string = format.format(calendar.getTime()) + ":\t"
                    + battery + "\n";
            outputStreamWriter.write(string);
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
            SimpleDateFormat format = new SimpleDateFormat("yyyy_MM_dd__HH_mm_ss", Locale.ENGLISH);
            String date = format.format(calendar.getTime());

            File file = getPublicStorageDir("uActivator/ua_" + serial + "_" + date + ".datx");

            try {
                if(file.createNewFile())
                    Log.i(TAG, "saveData: New file created");
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(data);
                fos.flush();
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
                Log.w(TAG, "saveData: failed to save", e);
            }
            Log.i(TAG, "saveData: Download saved");
        } else {
            Log.w(TAG, "saveData: Unable to save");
        }
    }

    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private File getPublicStorageDir(String fileName) {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "uActivator/");
        if(!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "getPublicStorageDir: Directory not created");
            }
        }

        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), fileName);
    }
    /*     Data and Log Saving END    */
}
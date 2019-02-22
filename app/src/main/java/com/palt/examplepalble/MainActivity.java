package com.palt.examplepalble;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.paltechnologies.pal8.PalBle;
import com.paltechnologies.pal8.ScanListener;
import com.paltechnologies.pal8.ScanParameters;
import com.paltechnologies.pal8.devices.DeviceListener;
import com.paltechnologies.pal8.devices.PalDevice;
import com.paltechnologies.pal8.devices.activator.ActivatorListener;
import com.paltechnologies.pal8.devices.activator.PalActivator;
import com.paltechnologies.pal8.devices.activator.data.PalActivatorData;
import com.polidea.rxandroidble2.exceptions.BleScanException;

import java.util.List;

public class MainActivity extends AppCompatActivity
        implements ScanListener, ActivatorListener {
    private static final String TAG = "MainActivity";

    //All transferred data is encrypted using a 16 Byte random key
    // This is passed to and from our server using Base64 format.
    private final String key = "ciNNPxZXoB_0C7htKpejCw==";

    private Button scanBtn;
    private TextView resultTv;
    private Button connectBtn;

    //If to be used across multiple activities, this is best placed
    // at the application level and referenced
    private PalBle palBle;

    private PalActivator activator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scanBtn = (Button) findViewById(R.id.main_btn_scan);
        scanBtn.setOnClickListener(v -> startScan());

        resultTv = (TextView) findViewById(R.id.main_tv_results);
        resultTv.setMovementMethod(new ScrollingMovementMethod());

        connectBtn = (Button) findViewById(R.id.main_btn_connect);
        connectBtn.setOnClickListener(v -> connectToFirst());

        palBle = new PalBle(this);
        palBle.setListener(this);
    }

    //Start a general scan using the scanParameters to set
    // the scan time to 5 seconds instead of the default 15
    private void startScan() {
        ScanParameters scanParameters = new ScanParameters();
        scanParameters.scanTimeout = 5;
        palBle.startScan(scanParameters);
    }

    //For simplicity this example connects to the given serial number if found.
    private void connectToFirst() {
        List<PalDevice> devices = palBle.getScanResults();
        for(PalDevice d : devices) {
            if(d.getSerial().equals("780000")) {
                //This uses the PalBle.connect as opposed to the PalDevice.connect method
                // this combines scan and connect
                palBle.connect(d.getSerial(), key);
            }
        }
    }

    //This method is called each time the scan detects a change.
    // This might be a new device, or some change (eg signal strength)
    // in an existing device.
    @Override
    public void onScanResultsChanged(PalDevice palDevice) {
        Log.i(TAG, "onScanResultsChanged: " + palDevice.getSerial() + " - " + palBle.getScanResults().size());

        //As the callbacks are triggered from a separate thread
        // it's necessary to call on the UI thread explicitly
        runOnUiThread(() -> {
            resultTv.setVisibility(View.VISIBLE);
            resultTv.setText(Integer.toString(palBle.getScanResults().size()) + " PAL devices found");
        });
    }

    @Override
    public void onScanTimeout() {
        Log.i(TAG, "onScanTimeout");

        runOnUiThread(() -> {
            String result = "No PAL devices found";

            if(resultTv.getVisibility() == View.VISIBLE) {
                List<PalDevice> devices = palBle.getScanResults();
                if(devices.size() != 0) {
                    result = "Found PAL devices:\n";
                    for (PalDevice d : devices)
                        result += d.getName() + " " + d.getSerial() + "(" + Integer.toString(d.getFirmwareVersion()) + ")" + "\n";
                    scanBtn.setVisibility(View.GONE);
                    connectBtn.setVisibility(View.VISIBLE);
                }
            } else
                resultTv.setVisibility(View.VISIBLE);

            resultTv.setText(result);
        });
    }

    @Override
    public void onScanError(BleScanException e) {
        Log.i(TAG, "onScanError: " + e.getMessage());
    }

    @Override
    public void onWaking() {
        Log.i(TAG, "onWaking: ");
    }

    @Override
    public void onWoken() {
        Log.i(TAG, "onWoken: ");
    }

    //Called when all summary data has been fetched
    @Override
    public void onSummariesRetrieved() {
        Log.i(TAG, "onSummariesRetrieved: ");
        PalActivatorData data = activator.getSummaries();
        runOnUiThread(() -> {
            String result = "Summaries retrieved\n\n";

            if (data != null)
                result += "Steps: " + data.getDaySummaries().get(0).getSteps() +
                        "\nUpright: " + data.getDaySummaries().get(0).getUpright() +
                        "\nSedentary: " + data.getDaySummaries().get(0).getSedentary() +
                        "\nBattery: " + Integer.toString(data.getCurrentBattery()) +
                        "\nDate: " + data.getCurrentDeviceDateString();
            else
                result = "Summaries are null";
            resultTv.setText(result);
        });
    }

    //Called when a new device has not yet been encrypted
    @Override
    public void onNewEncryptionKeyNeeded() {
        Log.i(TAG, "onNewEncryptionKeyNeeded: ");
        activator.setEncryptionKey(key);
    }

    @Override
    public void onExistingEncryptionKeyNeeded() {
        Log.i(TAG, "onExistingEncryptionKeyNeeded: ");
    }

    @Override
    public void onHapticSet(boolean b) {

    }

    @Override
    public void onDataDownload(byte[] bytes) {

    }

    @Override
    public void onDataError(Throwable throwable) {

    }

    @Override
    public void onDataDone() {

    }

    //This is returned by the PalBle.connect method, and gives reference to the found device
    // here we cast this to an activator (without type checking) for later use.
    @Override
    public void onConnected(PalDevice palDevice) {
        Log.i(TAG, "onConnected: " + palDevice.getSerial());
        activator = (PalActivator) palDevice;
    }

    @Override
    public void onDisconnected(PalDevice palDevice) {

    }

    @Override
    public void onRetrying(int i) {

    }

    @Override
    public void onInvalidEncryptionKey() {

    }

    @Override
    public void onDeviceError(Throwable throwable) {

    }

    @Override
    public void onDfuEnabled(PalDevice palDevice) {

    }
}

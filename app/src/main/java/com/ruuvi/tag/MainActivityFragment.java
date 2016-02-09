package com.ruuvi.tag;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.preference.PreferenceManager;
import android.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * create an instance of this fragment.
 */
public class MainActivityFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String TAG = "MainActivityFragment";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 2;

    private static final Handler handler = new Handler(Looper.getMainLooper());

    // The Eddystone Service UUID, 0xFEAA.
    private static final ParcelUuid EDDYSTONE_SERVICE_UUID =
            ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");

    /* from settings activity */
    static final String ON_LOST_TIMEOUT_SECS_KEY = "onLostTimeoutSecs";
    static final String SHOW_DEBUG_INFO_KEY = "showDebugInfo";

    // An aggressive scan for nearby devices that reports immediately.
    private static final ScanSettings SCAN_SETTINGS =
            new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0)
                    .build();

    /* bl stuff */
    private BluetoothLeScanner scanner;
    //private List<ScanFilter> scanFilters;
    private ScanCallback scanCallback;
    private Map<String /* device address */, Beacon> deviceToBeaconMap = new HashMap<>();

    private ArrayList<Beacon> arrayList;

    private SharedPreferences sharedPreferences;
    private int onLostTimeoutMillis;

    public MainActivityFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "running init");
        init();
        arrayList = new ArrayList<>();
        //arrayAdapter = new BeaconArrayAdapter(getActivity(), R.layout.beacon_list_item, arrayList);

        //scanFilters.add(new ScanFilter.Builder().setServiceUuid(EDDYSTONE_SERVICE_UUID).build());
        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                ScanRecord scanRecord = result.getScanRecord();

                if (scanRecord == null) {
                    return;
                }

                String deviceAddress = result.getDevice().getAddress();
                Log.i(TAG, "DeviceAddress: " + deviceAddress);
                Beacon beacon;
                if (!deviceToBeaconMap.containsKey(deviceAddress)) {
                    beacon = new Beacon(deviceAddress, result.getRssi());
                    deviceToBeaconMap.put(deviceAddress, beacon);
                    arrayList.add(beacon);
                } else {
                    deviceToBeaconMap.get(deviceAddress).lastSeenTimestamp = System.currentTimeMillis();
                    deviceToBeaconMap.get(deviceAddress).rssi = result.getRssi();
                }

                byte[] serviceData = scanRecord.getServiceData(EDDYSTONE_SERVICE_UUID);
                Log.i(TAG, String.valueOf(serviceData));
            }

            @Override
            public void onScanFailed(int errorCode) {
                switch (errorCode) {
                    case SCAN_FAILED_ALREADY_STARTED:
                        Log.i(TAG, "SCAN_FAILED_ALREADY_STARTED");
                        break;
                    case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                        Log.i(TAG, "SCAN_FAILED_APPLICATION_REGISTRATION_FAILED");
                        break;
                    case SCAN_FAILED_FEATURE_UNSUPPORTED:
                        Log.i(TAG, "SCAN_FAILED_FEATURE_UNSUPPORTED");
                        break;
                    case SCAN_FAILED_INTERNAL_ERROR:
                        Log.i(TAG, "SCAN_FAILED_INTERNAL_ERROR");
                        break;
                    default:
                        Log.i(TAG, "Scan failed, unknown error code");
                        break;
                }
            }
        };

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        onLostTimeoutMillis =
                sharedPreferences.getInt(ON_LOST_TIMEOUT_SECS_KEY, 5) * 1000;
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        ListView listView = (ListView) view.findViewById(R.id.listView);
        //listView.setAdapter(arrayList);
        //listView.setEmptyView(view.findViewById(R.id.placeholder));
        return view;
    }


    @Override
    public void onPause() {
        super.onPause();
        if (scanner != null) {
            Log.i(TAG, "scanner paused");
            scanner.stopScan(scanCallback);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        handler.removeCallbacksAndMessages(null);

        int timeoutMillis =
                sharedPreferences.getInt(ON_LOST_TIMEOUT_SECS_KEY, 5) * 1000;

        if (timeoutMillis > 0) {  // 0 is special and means don't remove anything.
            onLostTimeoutMillis = timeoutMillis;
            setOnLostRunnable();
        }

        if (sharedPreferences.getBoolean(SHOW_DEBUG_INFO_KEY, false)) {
            Runnable updateTitleWithNumberSightedBeacons = new Runnable() {
                final String appName = getActivity().getString(R.string.app_name);

                @Override
                public void run() {
                    getActivity().setTitle(appName + " (" + deviceToBeaconMap.size() + ")");
                    handler.postDelayed(this, 1000);
                }
            };
            handler.postDelayed(updateTitleWithNumberSightedBeacons, 1000);
        } else {
            getActivity().setTitle(getActivity().getString(R.string.app_name));
        }

        if (scanner != null) {
            scanner.startScan(scanCallback);
        }
    }

    private void setOnLostRunnable() {
        Runnable removeLostDevices = new Runnable() {
            @Override
            public void run() {
                long time = System.currentTimeMillis();
                Iterator<Map.Entry<String, Beacon>> itr = deviceToBeaconMap.entrySet().iterator();
                while (itr.hasNext()) {
                    Beacon beacon = itr.next().getValue();
                    if ((time - beacon.lastSeenTimestamp) > onLostTimeoutMillis) {
                        itr.remove();
                        //arrayAdapter.remove(beacon);
                    }
                }
                handler.postDelayed(this, onLostTimeoutMillis);
            }
        };
        handler.postDelayed(removeLostDevices, onLostTimeoutMillis);
    }


    /* private methods */
    private void init() {
        BluetoothManager manager = (BluetoothManager) getActivity().getApplicationContext()
                .getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = manager.getAdapter();
        if (btAdapter == null) {
            Log.i(TAG, "btAdapter null");
        } else if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            this.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
        } else {
            scanner = btAdapter.getBluetoothLeScanner();
        }
    }
}

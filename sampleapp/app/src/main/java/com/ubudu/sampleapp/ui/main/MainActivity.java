package com.ubudu.sampleapp.ui.main;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ListView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.ubudu.indoorlocation.UbuduBeacon;
import com.ubudu.indoorlocation.UbuduCoordinates2D;
import com.ubudu.indoorlocation.UbuduPoint;
import com.ubudu.indoorlocation.UbuduZone;
import com.ubudu.sampleapp.MyApplication;
import com.ubudu.sampleapp.R;
import com.ubudu.sampleapp.map.Map;
import com.ubudu.sampleapp.map.MapEventListener;
import com.ubudu.sampleapp.map.MapInterface;
import com.ubudu.sampleapp.ubudu.UbuduManager;
import com.ubudu.sampleapp.utils.LogListAdapter;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;


public class MainActivity extends AppCompatActivity implements MapInterface {

    private static final int REQUEST_ENABLE_BT_FOR_IL = 1014;

    private MapView mGoogleMapView;

    private MaterialDialog mB;

    private Map mLocationMap;

    private FloatingActionButton fab;

    private UbuduManager mUbuduManager;

    private LogListAdapter adapter;

    private ListView lv;

    private MapEventListener mMapEventListener = new MapEventListener(){
        @Override
        public void notifyRescalingImageStarted() {

        }

        @Override
        public void notifyMapOverlayDownloadProgress(int progressPercent) {

        }

        @Override
        public void notifyMapOverlayFetchedSuccessfully() {
            // Map overlay has been successfully fetched from file/server
        }

        @Override
        public void onMapReady() {
            android.util.Log.e("main","map ready!!!!!!!!!!!!!!");
            // map display is 100% ready
            dismissDialog();
            // display beacons markers on map
            mLocationMap.setMapBeaconsMarkers(mUbuduManager.mapBeacons());
        }

        @Override
        public void notifyMapOverlayFetchingError(String errorMsg) {
            // error while fetching the map overlay
            dismissDialog();
            Snackbar.make(fab, errorMsg, Snackbar.LENGTH_LONG).setAction("Error", null).show();
        }
    };

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == MyApplication.PERMISSION_ACCESS_FINE_LOCATION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                finish();
            } else {
                this.requestPermissions(new String[]{Manifest.permission.BLUETOOTH},  MyApplication.PERMISSION_BLUETOOTH);
            }
        } else if (requestCode == MyApplication.PERMISSION_BLUETOOTH) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                finish();
            } else {
                this.requestPermissions(new String[]{Manifest.permission.INTERNET}, MyApplication.PERMISSION_INTERNET);
            }
        } else if (requestCode == MyApplication.PERMISSION_INTERNET
                && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            finish();
        }
    }

    public boolean isLocationServiceEnabled(){
        LocationManager locationManager = null;
        boolean gps_enabled= false,network_enabled = false;

        if(locationManager ==null)
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        try{
            gps_enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        }catch(Exception ex){
            //do nothing...
        }

        try{
            network_enabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }catch(Exception ex){
            //do nothing...
        }

        return gps_enabled || network_enabled;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MyApplication.PERMISSION_ACCESS_FINE_LOCATION);
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setVisibility(View.GONE);
        //setSupportActionBar(toolbar);

        lv = (ListView) findViewById(R.id.log);
        adapter = new LogListAdapter(getApplicationContext(),
                R.layout.loglistitem);

        lv.setAdapter(adapter);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button_off)));
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleUbuduIndoorLocationSDK();
            }
        });

                // Gets the MapView from the XML layout and creates it
        mGoogleMapView = (MapView) findViewById(R.id.map);
        mGoogleMapView.onCreate(savedInstanceState);
        mGoogleMapView.onResume(); //without this, map showed but was empty

        mLocationMap = new Map(mMapEventListener, getApplicationContext());

        mUbuduManager = UbuduManager.getInstance();
        mUbuduManager.setDelegateAppInterface(this);

        mLocationMap.setReferenceToGoogleMap(mGoogleMapView.getMap());
    }

    public void toggleUbuduIndoorLocationSDK() {
        if (!mUbuduManager.isStarted()) {
            if (BluetoothAdapter.getDefaultAdapter().getState() != BluetoothAdapter.STATE_ON) {
                this.activateBluetooth(REQUEST_ENABLE_BT_FOR_IL);
            } else {
                startLoadingDialog();
                fab.setEnabled(false);
                start();
            }
        } else {
            stop();
        }
    }

    private void stop() {
        mUbuduManager.stop();
        fab.clearAnimation();
        fab.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button_off)));
        printLog("Indoor location stopped");
    }

    private void start() {
        Animation rotation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fab_rotate);
        rotation.setRepeatCount(Animation.INFINITE);
        fab.startAnimation(rotation);
        mUbuduManager.start();
    }

    private boolean activateBluetooth(int requestCode) {
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        // Check Availability of bluetooth
        if (bt == null) {
            dismissDialog();
            Snackbar.make(fab, "Bluetooth Not Available on this device.", Snackbar.LENGTH_LONG).setAction("Action", null).show();
            return false;
        } else {
            if (!bt.isEnabled()) {
                Intent intentBtEnabled = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intentBtEnabled, requestCode);
            }
            return true;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT_FOR_IL) {
            if (BluetoothAdapter.getDefaultAdapter().getState() == BluetoothAdapter.STATE_ON) {
                Snackbar.make(fab, "Bluetooth start success.", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                toggleUbuduIndoorLocationSDK();
            } else{
                Snackbar.make(fab, "Bluetooth Not Available on this device.", Snackbar.LENGTH_LONG).setAction("Action", null).show();
            }
        } else if(requestCode == MyApplication.TURN_ON_LOCATION_SERVICES) {
            if(isLocationServiceEnabled()){
                start();
            } else {
                fab.setEnabled(true);
            }
        }
    }

    private void startLoadingDialog() {
        mB = new MaterialDialog.Builder(this)
                .content(getResources().getString(R.string.starting_il))
                .progress(true, 0)
                .autoDismiss(false)
                .cancelable(false)
                .show();
    }

    protected void dismissDialog() {
        try {
            if (mB != null)
                mB.dismiss();
        } catch(IllegalArgumentException e){
            mB = null;
        }
    }

    public void started(){
        if(mLocationMap !=null){
            // start loading dialog indicating that map contents are beeing loaded
            dismissDialog();
            //showLoadingDialogForPreparingMapDisplay();
        }
    }

    @Override
    public void reloadMapOverlay(String uuid, boolean force) {
        if (mUbuduManager.mapOverlaysFetchingEnabled() &&  mUbuduManager.isMapAvailable()) {
            if(!mLocationMap.isMapOverlayLoaded() || (mLocationMap.isMapOverlayLoaded() && mLocationMap.getLoadedMapUuid()!=null && !mLocationMap.getLoadedMapUuid().equals(uuid)) || force ) {
                mLocationMap.setLoadedMapUuid(null);
                // start loading dialog indicating that map contents are being loaded
                showLoadingDialogForPreparingMapDisplay();

                // reset map
                mLocationMap.reset();

                UbuduCoordinates2D bottomRightAnchorCoordinates = mUbuduManager.bottomRightAnchorCoordinates();
                UbuduCoordinates2D topLeftAnchorCoordinates = mUbuduManager.topLeftAnchorCoordinates();

                // Set overlay image bounds
                mLocationMap.setMapOverlayBounds(new LatLng(bottomRightAnchorCoordinates.latitude(), topLeftAnchorCoordinates.longitude()),
                        new LatLng(topLeftAnchorCoordinates.latitude(), bottomRightAnchorCoordinates.longitude()));

                android.util.Log.e("mainActivity", "map height:"
                        + distanceBetweenCoordinates(new UbuduCoordinates2D(bottomRightAnchorCoordinates.toRadians().latitude()
                        , topLeftAnchorCoordinates.toRadians().longitude())
                        , topLeftAnchorCoordinates));

                android.util.Log.e("mainActivity", "map width:"
                        + distanceBetweenCoordinates(new UbuduCoordinates2D(topLeftAnchorCoordinates.toRadians().latitude()
                        ,bottomRightAnchorCoordinates.toRadians().longitude())
                        , topLeftAnchorCoordinates));

                // Fetch map from file/url and display
                mLocationMap.initMapOverlayFromInputStream(mUbuduManager.getMapOverlayInputStream());
            }
        } else {
            mMapEventListener.onMapReady();
        }
        mLocationMap.setLoadedMapUuid(uuid);
        dismissDialog();
    }

    private Handler stepDetectionHandler;
    private long lastStepDetectedTime = 0L;
    private long MOVEMENT_TIMEOUT = 5000L;

    @Override
    public void stepDetected() {
        if (lastStepDetectedTime + MOVEMENT_TIMEOUT < System.currentTimeMillis()) {
            lastStepDetectedTime = System.currentTimeMillis();

            printLog("Walking detected");

            if (stepDetectionHandler == null) {
                stepDetectionHandler = new Handler(getMainLooper());
            }
            stepDetectionHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (lastStepDetectedTime + MOVEMENT_TIMEOUT < System.currentTimeMillis()) {
                        printLog("No more motion");
                    } else
                        stepDetectionHandler.postDelayed(this, 1000L);
                }
            });
        }
    }

    /**
     * Calculate great circle distance between points on a sphere using the Haversine Formula. Input geographical coordinates must be given in radians.
     *
     * @param c1 start point coordinates
     * @param c2 end point coordinates
     */
    public static double distanceBetweenCoordinates(UbuduCoordinates2D c1,UbuduCoordinates2D c2){
        if(c1!=null && c2!=null) {
            double delta_latitude = c2.latitude() - c1.latitude();
            double delta_longitude = c2.longitude() - c1.longitude();
            double a = Math.sin(delta_latitude / 2.0) * Math.sin(delta_latitude / 2.0)
                    + Math.cos(c1.latitude()) * Math.cos(c2.latitude()) * Math.sin(delta_longitude / 2.0) * Math.sin(delta_longitude / 2.0);
            double result = 6378137.0 * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
            return result;
        } else return -1.0d;
    }

    private void showLoadingDialogForPreparingMapDisplay() {
        dismissDialog();
        mB = new MaterialDialog.Builder(this)
                .progress(true, 0)
                .content(getResources().getString(R.string.fetching_map))
                .autoDismiss(false)
                .cancelable(false)
                .show();
    }

    @Override
    public void indoorLocationStarted() {
        fab.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.button_on)));
        fab.clearAnimation();
        fab.setEnabled(true);
        printLog("Indoor location started successfully");
        started();
    }

    @Override
    public void indoorLocationStartFailed() {
        printLog("Indoor location start failed");
        dismissDialog();
        fab.setEnabled(true);
    }

    @Override
    public void highlightZones(List<UbuduZone> list) {
        if(mLocationMap !=null) {
            mLocationMap.clearHighlightedZones();
            Iterator<UbuduZone> iter = list.iterator();
            while (iter.hasNext()) {
                UbuduZone zone = iter.next();
                List<LatLng> coords = new ArrayList<>();
                Iterator<UbuduPoint> iter1 = zone.coordinates().iterator();
                while (iter1.hasNext()) {
                    UbuduPoint p = iter1.next();
                    UbuduCoordinates2D c = mUbuduManager.getGeoCoordinates(p);
                    if(c!=null)
                        coords.add(new LatLng(c.latitude(), c.longitude()));
                }
                mLocationMap.highlightZone(coords, zone.name());
            }
        }
    }

    @Override
    public void updateActiveBeacons(List<UbuduBeacon> activeBeacons) {
        if (mLocationMap != null)
            mLocationMap.setActiveBeaconsMarkers(activeBeacons);
    }

    @Override
    public boolean readyForPositionUpdates() {
        if (mLocationMap != null && mLocationMap.isMapOverlayLoaded())
            return true;
        else return false;
    }

    @Override
    public void setLocationOnMap(double latitude, double longitude) {
        if(readyForPositionUpdates()){
            boolean success = mLocationMap.setLocationOnMap(latitude,longitude);
            if(!success)
                printLog("Could not mark position. Google Maps are offline.");
        }
    }

    @Override
    public void drawPath(List<LatLng> pathGeoCoords) {
        if(readyForPositionUpdates()){
            mLocationMap.drawPath(pathGeoCoords);
        }
    }

    @Override
    public void onResume(){
        super.onResume();
        if(mLocationMap != null)
            mLocationMap.onResume();

        mUbuduManager.onResume();
    }

    @Override
    public void onPause(){
        if(mLocationMap != null)
            mLocationMap.onPause();
        super.onPause();
    }

    @Override
    public void onDestroy(){
        if(mLocationMap != null)
            mLocationMap.onDestroy();
        super.onDestroy();
    }

    /*
    Delegate <-> app interface methods
     */
    @Override
    public void printLog(final String formatControl, final Object... arguments) {
        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                final String newText = String.format(formatControl, arguments);
                synchronized (adapter) {
                    Date d = new Date();
                    adapter.add("[" + d.getHours() + ":" + d.getMinutes() + ":" + d.getSeconds() + "] " + newText);
                }
            }
        });

    }

    public void clearLog() {
        synchronized (adapter) {
            adapter.clear();
            adapter = new LogListAdapter(getApplicationContext(),
                    R.layout.loglistitem);

            lv.setAdapter(adapter);
            lv.invalidateViews();
        }
    }
}

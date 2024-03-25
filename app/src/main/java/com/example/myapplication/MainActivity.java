package com.example.myapplication;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.ScaleBarOverlay;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TextView accelerometerTextView;
    private TextView gpsTextView;
    private MapView mapView;
    private Marker currentMarker;

    ActivityResultLauncher<String[]> mPermissionResultLauncher;
    private boolean isWritePermissionGranted = false;
    private boolean isLocationPermissionGranted = false;
    private boolean isServiceRunning = false;
    private MyBroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);

        // Инициализация текстовых полей
        gpsTextView = findViewById(R.id.gpsTextView);
        accelerometerTextView = findViewById(R.id.accelerometerTextView);

        isServiceRunning = foregroundServiceRunning();
        final Button btnToggle = findViewById(R.id.button_toggle);

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isServiceRunning) {
                    stopService(new Intent(MainActivity.this, MyService.class));
                    isServiceRunning = false;
                    btnToggle.setText("Запустить службу");
                } else {
                    if (!checkLocationEnabled()) showLocationAlert();
                    else {
                        startForegroundService(new Intent(MainActivity.this, MyService.class));

                        isServiceRunning = true;
                        btnToggle.setText("Остановить службу");
                    }
                }
            }
        });

        // Регистрация приемника широковещательных сообщений
        receiver = new MyBroadcastReceiver(new Handler()); // Create the receiver
        registerReceiver(receiver, new IntentFilter("gpsDataUpdated")); // Register receiver
        registerReceiver(receiver, new IntentFilter("accelerometerDataUpdated")); // Register receiver

        // Инициализация карты
        Configuration.getInstance().setUserAgentValue(getPackageName());
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        ScaleBarOverlay myScaleBarOverlay = new ScaleBarOverlay(mapView);
        mapView.getOverlays().add(myScaleBarOverlay);
        MapController mapController = (MapController) mapView.getController();
        mapController.setZoom(13);

        ActivityResultCallback<Map<String, Boolean>> callback = new ActivityResultCallback<Map<String, Boolean>>() {
            @Override
            public void onActivityResult(Map<String, Boolean> result) {
                if(result.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) != null) {
                    isWritePermissionGranted = result.get(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                if(result.get(Manifest.permission.ACCESS_FINE_LOCATION) != null) {
                    isLocationPermissionGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                }

                if (isWritePermissionGranted && isLocationPermissionGranted ) {
                    btnToggle.performClick();
                } else {
                    requestPermission();
                }
            }
        };

        mPermissionResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), callback);
        requestPermission();

        if (!checkLocationEnabled()) showLocationAlert();

        btnToggle.performClick();
    }

    private boolean checkLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            return false;
        }
        else return true;
    }

    private void showLocationAlert() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        dialog.setMessage("Местоположение отключено. Хотите включить его?");
        dialog.setPositiveButton("Да", (dialogInterface, i) -> {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        });
        dialog.setNegativeButton("Отмена", (dialogInterface, i) -> {
            finish();
        });
        dialog.show();
    }

    public  boolean foregroundServiceRunning() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        for(ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)){
            if(MyService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void requestPermission(){
        isWritePermissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;

        isLocationPermissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        List<String> permissionRequest = new ArrayList<String>();
        if(!isWritePermissionGranted){
            permissionRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if(!isLocationPermissionGranted){
            permissionRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissionRequest.isEmpty()){
            mPermissionResultLauncher.launch(permissionRequest.toArray(new String[0]));
        }

    }

    public class MyBroadcastReceiver extends BroadcastReceiver {
        private final Handler handler; // Handler used to execute code on the UI thread

        public MyBroadcastReceiver(Handler handler) {
            this.handler = handler;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("gpsDataUpdated")) {
                double latitude = intent.getDoubleExtra("latitude",0);
                double longitude = intent.getDoubleExtra("longitude",0);
                double speed = intent.getDoubleExtra("speed", 0);
                updateUIWithGPSData(latitude, longitude, speed);

            } else if (intent.getAction().equals("accelerometerDataUpdated")) {
                float[] accelerometerValue = intent.getFloatArrayExtra("accelerometerValue");
                float[] linearAccelerometerValue = intent.getFloatArrayExtra("linearAccelerometerValue");
                float[] gravityValue = intent.getFloatArrayExtra("gravityValue");
                updateUIWithAccelerometerData(accelerometerValue, linearAccelerometerValue, gravityValue);
            }
        }
    }

    private void updateUIWithGPSData(double latitude, double longitude, double speed) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("main", String.valueOf(latitude));
                gpsTextView.setText("GPS\n=>Latitude: " + latitude +
                        "\n=>Longitude: " + longitude
                +"\n=>Speed: " + speed);

                // Если маркер еще не создан, создаем новый маркер
                if (currentMarker == null) {
                    currentMarker = new Marker(mapView);
                    currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    mapView.getOverlays().add(currentMarker);
                }
                // Обновление позиции маркера
                currentMarker.setPosition(new GeoPoint(latitude, longitude));
                mapView.getController().setCenter(new GeoPoint(latitude, longitude));
                mapView.invalidate(); // Обновление карты
            }
        });
    }

    private void updateUIWithAccelerometerData(float[] accelerometerValue, float[] linearAccelerometerValue, float[] gravityValue) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("main", "String.valueOf(accelerometerValue[0])");
                DecimalFormat df = new DecimalFormat("#.#####");
                accelerometerTextView.setText("Accelerometer\n=>X: " + df.format(accelerometerValue[0]) +
                        "\n=>Y: " + df.format(accelerometerValue[1]) +
                        "\n=>Z: " + df.format(accelerometerValue[2])+
                        "\n"+df.format(gravityValue[0])+
                        "\n"+df.format(gravityValue[1])+
                        "\n"+ df.format(gravityValue[2])+
                        "\n=>Xl: " + df.format(linearAccelerometerValue[0]) +
                        "\n=>Yl: " + df.format(linearAccelerometerValue[1]) +
                        "\n=>Zl: " + df.format(linearAccelerometerValue[2])
                );
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("systeminfo", "onResume");

        // Регистрация приемника широковещательных сообщений
        receiver = new MyBroadcastReceiver(new Handler()); // Create the receiver
        registerReceiver(receiver, new IntentFilter("gpsDataUpdated")); // Register receiver
        registerReceiver(receiver, new IntentFilter("accelerometerDataUpdated")); // Register receiver
        mapView.onResume();

        if (!checkLocationEnabled()) showLocationAlert();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("systeminfo", "onPause");

        unregisterReceiver(receiver);
        mapView.onPause();

        if (!checkLocationEnabled()) showLocationAlert();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("systeminfo", "onDestroy");

        unregisterReceiver(receiver);
    }
}
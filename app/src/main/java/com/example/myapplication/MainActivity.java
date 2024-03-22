package com.example.myapplication;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import android.os.Parcelable;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.ScaleBarOverlay;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainActivity extends AppCompatActivity {

    private TextView accelerometerTextView;
    private TextView gpsTextView;
    private MapView mapView;
    private MapController mapController;


    private Marker currentMarker;


    ActivityResultLauncher<String[]> mPermissionResultLauncher;
    private boolean isWritePermissionGranted = false;
    private boolean isLocationPermissionGranted = false;

    private boolean isServiceRunning = false;
    private MyBroadcastReceiver receiver;

    @SuppressLint("InvalidWakeLockTag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Инициализация текстовых полей
        gpsTextView = findViewById(R.id.gpsTextView);
        accelerometerTextView = findViewById(R.id.accelerometerTextView);

        final Button btnToggle = findViewById(R.id.button_toggle);
        if (isServiceRunning){
            btnToggle.setText("Остановить службу");
        }
        else {
            btnToggle.setText("Запустить службу");
        }

        isServiceRunning = foregroundServiceRunning();


        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isServiceRunning) {
                    stopService(new Intent(MainActivity.this, MyService.class));
                    isServiceRunning = false;
                    btnToggle.setText("Запустить службу");
                } else {
                    startForegroundService(new Intent(MainActivity.this, MyService.class));
                    isServiceRunning = true;
                    btnToggle.setText("Остановить службу");
                }
            }
        });

        // Регистрация приемника широковещательных сообщений
        receiver = new MyBroadcastReceiver(new Handler()); // Create the receiver
        registerReceiver(receiver, new IntentFilter("gps_data_updated")); // Register receiver
        registerReceiver(receiver, new IntentFilter("accelerometr_data_updated")); // Register receiver

        // Инициализация карты
        Configuration.getInstance().setUserAgentValue(getPackageName());
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        ScaleBarOverlay myScaleBarOverlay = new ScaleBarOverlay(mapView);
        mapView.getOverlays().add(myScaleBarOverlay);

        ActivityResultCallback<Map<String, Boolean>> callback = new ActivityResultCallback<Map<String, Boolean>>() {
            @Override
            public void onActivityResult(Map<String, Boolean> result) {
                if(result.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) != null) {
                    isWritePermissionGranted = result.get(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }

                if(result.get(Manifest.permission.ACCESS_FINE_LOCATION) != null) {
                    isLocationPermissionGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                }

                if (isWritePermissionGranted && isLocationPermissionGranted) {
                    startForegroundService(new Intent(MainActivity.this, MyService.class));
                } else {
                    requestPermission();
                }
            }
        };

        mPermissionResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), callback);
        requestPermission();


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
            if (intent.getAction().equals("gps_data_updated")) {
                float latitude = intent.getFloatExtra("latitude",0);
                float longitude = intent.getFloatExtra("accelerometer_value",0);
                float speed = intent.getFloatExtra("speed", 0);
                updateUIWithGPSData(latitude, longitude, speed);
            } else if (intent.getAction().equals("accelerometr_data_updated")) {

                float accelerometr_x = intent.getFloatExtra("accelerometr_x",0);
                float accelerometr_y = intent.getFloatExtra("accelerometr_y",0);
                float accelerometr_z = intent.getFloatExtra("accelerometr_z",0);

                updateUIWithAccelerometerData(accelerometr_x, accelerometr_y, accelerometr_z);
            }
        }
    }

    private void updateUIWithGPSData(double latitude, double longitude, float speed) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gpsTextView.setText("GPS\n=>Latitude: " + latitude + "\n=>Longitude: " + longitude
                +"\n=>Speed: " + speed);


                // Other UI updates
                ///////////////////////////////////
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
                /////////////////////////////////
            }
        });
    }

    private void updateUIWithAccelerometerData(float accelerometr_x, float accelerometr_y, float accelerometr_z) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                accelerometerTextView.setText("Accelerometer\n=>X: " + accelerometr_x + "\n=>Y: " + accelerometr_y + "\n=>Z: " + accelerometr_z);
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d("systeminfo", "onResume");

        // Регистрация приемника широковещательных сообщений
        receiver = new MyBroadcastReceiver(new Handler()); // Create the receiver
        registerReceiver(receiver, new IntentFilter("gps_data_updated")); // Register receiver
        registerReceiver(receiver, new IntentFilter("accelerometr_data_updated")); // Register receiver

        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("systeminfo", "onPause");
        unregisterReceiver(receiver);

        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("systeminfo", "onDestroy");
        unregisterReceiver(receiver);
    }
}
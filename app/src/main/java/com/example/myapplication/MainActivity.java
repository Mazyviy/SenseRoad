package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.annotation.SuppressLint;
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

import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

//public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {
public class MainActivity extends AppCompatActivity {

    private TextView accelerometerTextView, accelerometerLinearTextView;
    private TextView gpsTextView;
    private TextView speedTextView;
    private MapView mapView;
    private MapController mapController;

    private PowerManager.WakeLock mWakeLock;

    private Marker currentMarker;

    private List<String> MY_PERMISSIONS = Arrays.asList(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.WAKE_LOCK
    );


    private MyBroadcastReceiver receiver;

    @SuppressLint("InvalidWakeLockTag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //wake lock
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WakeLockSensRoad");
        mWakeLock.acquire();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Инициализация текстовых полей
        accelerometerTextView = findViewById(R.id.accelerometerTextView);
        gpsTextView = findViewById(R.id.gpsTextView);
        speedTextView = findViewById(R.id.speedTextView);

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


        if (!isGpsEnabled()) {
            showGpsReminderNotification();
        }

        // permission
        if (!checkPermissions()) {
            // Если нет, то запрашиваем разрешения
            requestPermissions();
        }

        Context context = getApplicationContext();
        Intent intent = new Intent(this, MyService.class); // Build the intent for the service
        context.startForegroundService(intent);

        //startService(new Intent(this, MyService.class));


    }

    private boolean isGpsEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void showGpsReminderNotification() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
            alertDialogBuilder.setMessage("Включите GPS для получения местоположения")
                    .setCancelable(false)
                    .setPositiveButton("Включить GPS",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                    startActivity(intent);
                                }
                            });
            AlertDialog alert = alertDialogBuilder.create();
            alert.show();
    }}


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

                float accelerometer_linear_x = intent.getFloatExtra("accelerometer_linear_x",0);
                float accelerometer_linear_y = intent.getFloatExtra("accelerometer_linear_y",0);
                float accelerometer_linear_z = intent.getFloatExtra("accelerometer_linear_z",0);

                updateUIWithAccelerometerData(accelerometr_x, accelerometr_y, accelerometr_z, accelerometer_linear_x, accelerometer_linear_y, accelerometer_linear_z);
            }
        }
    }


    // Метод, который проверяет, получены ли все необходимые разрешения
    private boolean checkPermissions() {
        for (String permission : MY_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // Метод, который запрашивает необходимые разрешения у пользователя
    private void requestPermissions() {
        List<String> remainingPermissions = new ArrayList<>();

        // Создаем список разрешений, которых еще не получили от пользователя
        for (String permission : MY_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                remainingPermissions.add(permission);
            }
        }
        // Запрашиваем разрешения у пользователя
        ActivityCompat.requestPermissions(this, remainingPermissions.toArray(new String[0]), 123456);
    }
    // Метод, который обрабатывает ответ пользователя на запрос разрешений
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Проверяем код запроса, чтобы убедиться, что это наше разрешение
        if (requestCode == 123456) {

            // Проверяем, все ли разрешения были получены пользователем
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            // Если все разрешения получены, выполняем остальные действия приложения
            if (allPermissionsGranted) {
                // ...
            } else {
                // Если разрешения не получены, можно показать диалог с объяснением,
                // для чего нужны разрешения и попросить пользователя предоставить их снова.
                requestPermissions();
            }
        }
    }



    private void updateUIWithGPSData(double latitude, double longitude, float speed) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gpsTextView.setText("Latitude: " + latitude + ", Longitude: " + longitude);
                speedTextView.setText("Speed: " + speed);

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

    private void updateUIWithAccelerometerData(float accelerometr_x, float accelerometr_y, float accelerometr_z,
                                               float accelerometer_linear_x, float accelerometer_linear_y, float accelerometer_linear_z) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                accelerometerTextView.setText("X: " + accelerometr_x + ", Y: " + accelerometr_y + ", Z: " + accelerometr_z);
                accelerometerLinearTextView.setText("X_l: " + accelerometer_linear_x + ", Y_l: " + accelerometer_linear_y + ", Z_l: " + accelerometer_linear_z);
            }
        });
    }



    @Override
    protected void onResume() {
        super.onResume();
        Log.d("systeminfo", "onResume");

        if (!isGpsEnabled()) {
            showGpsReminderNotification();
        }

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
        //unregisterReceiver(receiver);

        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("systeminfo", "onDestroy");
        // Остановка службы
        //stopService(new Intent(this, MyService.class));

        // Отмена регистрации приемника при уничтожении активности
        unregisterReceiver(receiver);
    }

}
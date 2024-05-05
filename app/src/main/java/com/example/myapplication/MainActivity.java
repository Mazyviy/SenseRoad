package com.example.myapplication;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Environment;
import android.os.Handler;
import androidx.core.content.ContextCompat;

import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
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
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private TextView accelerometerTextView;
    private TextView gpsTextView, urlTextView;
    private MapView mapView;
    private Marker currentMarker;

    private String URL = "http://89.179.33.18:27012";

    ActivityResultLauncher<String[]> mPermissionResultLauncher;
    private boolean isWritePermissionGranted = false;
    private boolean isLocationPermissionGranted = false;
    private boolean isReadPermissionGranted = false;
    private boolean isServiceRunning = false;
    private MyBroadcastReceiver receiver;

    private Context context;
    private Activity theActivity;
    private SharedPreferences applicationPrefs;
    private static final String URL_KEY = "url_key";
    private boolean isInternetConnected;
    private boolean isServerConnected;

    private Handler handler = new Handler();
    private ExecutorService pool = Executors.newSingleThreadExecutor();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_main);
        context = this;
        theActivity = (Activity)context;

        // Инициализация текстовых полей
        urlTextView = findViewById(R.id.urlTextView);
        gpsTextView = findViewById(R.id.gpsTextView);
        accelerometerTextView = findViewById(R.id.accelerometerTextView);

        isServiceRunning = foregroundServiceRunning();
        final Button btnStartService = findViewById(R.id.btnStartService);

        applicationPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!applicationPrefs.contains(URL_KEY))
            applicationPrefs.edit().putString(URL_KEY, URL).apply();
        else URL = applicationPrefs.getString(URL_KEY, URL);

        btnStartService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isServiceRunning) {
                    accelerometerTextView.setText("");
                    gpsTextView.setText("");
                    stopService(new Intent(MainActivity.this, MyService.class));
                    isServiceRunning = false;
                    btnStartService.setText("Запустить сервис");
                } else {
                    if (!checkLocationEnabled()) showLocationAlert();
                    else {
                        startForegroundService(new Intent(MainActivity.this, MyService.class));
                        isServiceRunning = true;
                        btnStartService.setText("Остановить сервис");
                    }
                }
            }
        });

        // Инициализация карты
        Configuration.getInstance().setUserAgentValue(getPackageName());
        mapView = findViewById(R.id.mapView);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        ScaleBarOverlay myScaleBarOverlay = new ScaleBarOverlay(mapView);
        mapView.getOverlays().add(myScaleBarOverlay);
        MapController mapController = (MapController) mapView.getController();
        mapController.setZoom(3);

        // Регистрация приемника широковещательных сообщений
        receiver = new MyBroadcastReceiver(new Handler()); // Create the receiver
        registerReceiver(receiver, new IntentFilter("gpsDataUpdated")); // Register receiver
        registerReceiver(receiver, new IntentFilter("accelerometerDataUpdated")); // Register receiver

        ActivityResultCallback<Map<String, Boolean>> callback = new ActivityResultCallback<Map<String, Boolean>>() {
            @Override
            public void onActivityResult(Map<String, Boolean> result) {
                if(result.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) != null) {
                    isWritePermissionGranted = result.get(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
                if(result.get(Manifest.permission.ACCESS_FINE_LOCATION) != null) {
                    isLocationPermissionGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                }
                if(result.get(Manifest.permission.READ_EXTERNAL_STORAGE) != null) {
                    isReadPermissionGranted = result.get(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
                if (isWritePermissionGranted && isLocationPermissionGranted ) {
                    btnStartService.performClick();
                } else {
                    requestPermission();
                }
            }
        };

        mPermissionResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), callback);
        requestPermission();

        urlTextView.setText(URL);

        checkInternetConnection();
        checkServerConnection();
        uploadHoleServer();

        readHoleServer();

        if (!checkLocationEnabled()) showLocationAlert();
    }


    private void checkInternetConnection() {
        pool.submit(new Runnable() {
            @Override
            public void run() {
                ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                isInternetConnected = networkInfo != null && networkInfo.isConnected();
            }
        });
    }


    public void checkServerConnection() {
        pool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    URL serverUrl = new URL(URL);
                    HttpURLConnection connection = (HttpURLConnection) serverUrl.openConnection();
                    connection.setRequestMethod("GET");
                    connection.connect();

                    int responseCode = connection.getResponseCode();
                    isServerConnected = (responseCode == HttpURLConnection.HTTP_FORBIDDEN);
                } catch (IOException e) {
                    isServerConnected = false;
                }
            }
        });
    }


    public static void requestStringInDialog(String title, String message, String defaultString, EditText input,
                                             DialogInterface.OnClickListener onPositiveListener, Activity activity, Context context){
        input.setText(defaultString);
        input.selectAll();
        AlertDialog alert = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null) // dismisses by default
                .setPositiveButton(android.R.string.ok, onPositiveListener)
                .create();
        alert.show();
        if(input.requestFocus())
            alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }


    public void updateUrl(View view){
        final EditText input = new EditText(context);
        requestStringInDialog("server URL", "Edit server address", URL, input,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        URL = input.getText().toString();
                        urlTextView.setText(URL);
                        applicationPrefs.edit().putString(URL_KEY, URL).apply();
                    }
                }, theActivity, context);
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


    public void uploadHole(View view) {
        readHoleServer();
    }


    private void uploadHoleServer(){
        pool.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    URL serverUrl = new URL(URL);
                    HttpURLConnection connection = (HttpURLConnection) serverUrl.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setDoOutput(true);
                    JSONObject jsonRequest = new JSONObject();
                    jsonRequest.put("hole", "hole");

                    try (OutputStream os = connection.getOutputStream()) {
                        byte[] input = jsonRequest.toString().getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }

                    // Получение кода ответа
                    int responseCode = connection.getResponseCode();
                    // Проверка кода ответа
                    if (responseCode != 200) {
                        throw new RuntimeException("Ошибка HTTP: " + responseCode);
                    }

                    // Получение тела ответа
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String responseBody = reader.readLine();

                    // Преобразование тела ответа в объект JSON
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    JSONArray dataArray = jsonResponse.getJSONArray("hole");

                    // create database
                    File directory = new File(Environment.getExternalStorageDirectory() + "/RoadSense/hole/");
                    if (!directory.exists()) {
                        directory.mkdirs(); // Создание каталога, если он не существует
                    }
                    File dbFile = new File(directory, "sense.db");

                    SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);

                    Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='hole'", null);
                    boolean tableExists = cursor.moveToFirst();
                    if (tableExists) {
                        db.execSQL("DELETE FROM hole");
                    }
                    db.execSQL("CREATE TABLE IF NOT EXISTS hole (date TEXT, id_user TEXT, latitude REAL, longitude REAL, speed REAL, value_hole REAL)");

                    for (int i = 0; i < dataArray.length(); i++) {
                        JSONArray obj = dataArray.getJSONArray(i);
                        db.execSQL("INSERT OR IGNORE INTO hole VALUES ('" + obj.getString(0) + "', '" + obj.getString(1) + "', " +
                                obj.getDouble(2) + ", " + obj.getDouble(3) + ", " + obj.getDouble(4) + ", " + obj.getDouble(5) + ");");
                    }

                    db.close();

                } catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    private void readHoleServer(){
        pool.submit(new Runnable() {
            @Override
            public void run() {
                File directory = new File(Environment.getExternalStorageDirectory() + "/RoadSense/hole/");
                File dbFile = new File(directory, "sense.db");
                SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);

                // Очистка существующих маркеров на карте
                mapView.getOverlays().clear();
                currentMarker=null;

                // print database
                Cursor query = db.rawQuery("SELECT * FROM hole;", null);
                while (query.moveToNext()) {
                    Double lat = query.getDouble(2);
                    Double lon = query.getDouble(3);
                    Double value = query.getDouble(5);

                    /// Создаем объект OverlayItem
                    Marker startMarker = new Marker(mapView);
                    startMarker.setDraggable(false);
                    startMarker.setAnchor(0.5f, 0.5f);
                    // Включить аппаратное ускорение для карты
                    mapView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                    startMarker.setIcon(getResources().getDrawable(R.drawable.hole24));
                    startMarker.setPosition(new GeoPoint(lat, lon));
                    startMarker.setTitle(String.valueOf(value));

                    // Добавление маркера на карту
                    mapView.getOverlays().add(startMarker);
                }
                query.close();
                db.close();

                mapView.invalidate();
            }
        });
    }


    private void requestPermission(){
        isWritePermissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;

        isReadPermissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;

        isLocationPermissionGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        List<String> permissionRequest = new ArrayList<String>();
        if(!isWritePermissionGranted){
            permissionRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if(!isReadPermissionGranted){
            permissionRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
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
                updateUIWithAccelerometerData(accelerometerValue, linearAccelerometerValue);
            }
        }
    }


    private boolean isUpdating = true;
    private final Object lock = new Object();
    private Handler handler1 = new Handler();

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                synchronized (lock) {
                    isUpdating = false;
                }
                handler.removeCallbacksAndMessages(null); // Очистить все предыдущие задания
                break;
            case MotionEvent.ACTION_MOVE:
                // Ничего не делать при движении, чтобы избежать дополнительных обновлений UI
                synchronized (lock) {
                    isUpdating = false;
                }
                handler.removeCallbacksAndMessages(null); // Очистить все предыдущие задания
                break;
            case MotionEvent.ACTION_UP:

                handler.postDelayed(() -> {
                    synchronized (lock) {
                        isUpdating = true;
                    } // Обновление UI после отпускания экрана
                }, 3000);
                break;
        }
        return true;
    }


    private void updateUIWithGPSData(double latitude, double longitude, double speed) {
        runOnUiThread(new Runnable() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                gpsTextView.setText("GPS\n=>Latitude: " + latitude +
                        "\n=>Longitude: " + longitude
                        +"\n=>Speed: " + speed/1000*3600);

                boolean updating;
                synchronized (lock) {
                    updating = isUpdating;
                }

                if (updating && isServiceRunning) {
                    // Если маркер еще не создан, создаем новый маркер
                    if (currentMarker == null) {
                        currentMarker = new Marker(mapView);
                        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                        mapView.getOverlays().add(currentMarker);
                    }
                    // Обновление позиции маркера
                    currentMarker.setPosition(new GeoPoint(latitude, longitude));
                    mapView.getController().setCenter(new GeoPoint(latitude, longitude));
                    mapView.invalidate(); // Обновление карты
                }
            }
        });
    }


    private void updateUIWithAccelerometerData(float[] accelerometerValue, float[] linearAccelerometerValue) {
        runOnUiThread(new Runnable() {
            @SuppressLint("SetTextI18n")
            @Override
            public void run() {
                DecimalFormat dF = new DecimalFormat( "#.#####" );
                accelerometerTextView.setText("Accelerometer\n=>X: " + dF.format(accelerometerValue[0]) +
                        "\n=>Y: " + dF.format(accelerometerValue[1]) +
                        "\n=>Z: " + dF.format(accelerometerValue[2])+
                        "\n=>Xl: " + dF.format(linearAccelerometerValue[0]) +
                        "\n=>Yl: " + dF.format(linearAccelerometerValue[1]) +
                        "\n=>Zl: " + dF.format(linearAccelerometerValue[2])
                );
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
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
        unregisterReceiver(receiver);
        mapView.onPause();
        accelerometerTextView.setText("");
        gpsTextView.setText("");

        if (!checkLocationEnabled()) showLocationAlert();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onPause();
        unregisterReceiver(receiver);
    }
}
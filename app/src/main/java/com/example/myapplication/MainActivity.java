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
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
    private static final int REQUEST_PERMISSIONS_CODE = 1;
    private static final long ACCELEROMETER_UPDATE_INTERVAL = 150; // в миллисекундах
    private static final long GPS_UPDATE_INTERVAL = 1000; // в миллисекундах
    private static final long SEND_SAVE_UPDATE_INTERVAL = 3000; // в миллисекундах

    private float accelerometr_x, accelerometr_y, accelerometr_z;
    private double latitude, longitude;
    private float speed;


    private TextView accelerometerTextView;
    private TextView gpsTextView;
    private TextView speedTextView;
    private MapView mapView;
    private CopyOnWriteArrayList<String> dataArrayList;

    private PowerManager.WakeLock mWakeLock;


    private Marker currentMarker;
    private boolean isInternetConnected;
    private boolean isServerConnected;
    private static final String URL = "https://upp.bestmon.keenetic.pro";
    private SharedPreferences sharedPreferences;
    private static final String EMAIL_PREF = "email_pref";
    private static final String EMAIL_KEY = "email_key";
    private static final String ID_KEY = "id_key";
    private boolean isEmailSet;
    private String ID_USER;

    private List<String> MY_PERMISSIONS = Arrays.asList(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.WAKE_LOCK
    );

    private static final int REQUEST_CODE_PERMISSIONS = 123;


   // private Handler handler = new Handler(Looper.getMainLooper());

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

        startService(new Intent(this, MyService.class));

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


        if (!isGpsEnabled()) {
            showGpsReminderNotification();
        }


        //setupSharedPreferences();
        if (!checkPermissions()) {
            // Если нет, то запрашиваем разрешения
            requestPermissions();
        } else {
            // Если все разрешения уже получены, выполняем остальные действия приложения
            // ...
        }


        // Инициализация списка данных
        //dataArrayList = new CopyOnWriteArrayList<>();

        /*handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendData();
            }
        }, SEND_SAVE_UPDATE_INTERVAL);*/

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
                latitude = intent.getFloatExtra("latitude",0);
                longitude = intent.getFloatExtra("accelerometer_value",0);
                speed = intent.getFloatExtra("speed", 0);

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

            } else if (intent.getAction().equals("accelerometr_data_updated")) {
                accelerometr_x = intent.getFloatExtra("accelerometr_x",0);
                accelerometr_y = intent.getFloatExtra("accelerometr_y",0);
                accelerometr_z = intent.getFloatExtra("accelerometr_z",0);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        accelerometerTextView.setText("X: " + accelerometr_x + ", Y: " + accelerometr_y + ", Z: " + accelerometr_z);
                    }
                });
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
            }
        }
    }




    private void updateUIWithGPSData(double latitude, double longitude, float speed) {

    }

    private void updateUIWithAccelerometerData(float accelerometer_x, float accelerometer_y, float accelerometer_z) {

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
        unregisterReceiver(receiver);

        // Отмена регистрации слушателей датчиков
        //sensorManager.unregisterListener(this);
        //locationManager.removeUpdates(this);
        //mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("systeminfo", "onDestroy");
        // Остановка службы
        stopService(new Intent(this, MyService.class));

        // Отмена регистрации приемника при уничтожении активности
        unregisterReceiver(receiver);
    }





    /*private void addDataToLists() {
        if (accelerometerData != null && gpsData != null && speed > 1.5) {
            dataArrayList.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(System.currentTimeMillis())
                    + "; " + gpsData + "; " + accelerometerData + "\n");
        }
    }*/

    /*@Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Не используется
    }*/

    /*@Override
    public void onProviderEnabled(String provider) {
        // Не используется
    }

    @Override
    public void onProviderDisabled(String provider) {
        // Не используется
    }

    private void updateAccelerometer() {
        // Обновление значений акселерометра
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void updateLocation() {
        // Обновление местоположения
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_UPDATE_INTERVAL, 0, this);
        }
    }*/

    private void setupSharedPreferences() {
        sharedPreferences = getSharedPreferences(EMAIL_PREF, MODE_PRIVATE);
        String userEmail = sharedPreferences.getString(EMAIL_KEY, null);
        String idUser = sharedPreferences.getString(ID_KEY, null);
        isEmailSet = !TextUtils.isEmpty(userEmail);
        if (isEmailSet) {
            ID_USER = idUser;
        }
    }

    /*private void saveEmail(String email, String id) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(EMAIL_KEY, email);
        editor.putString(ID_KEY, id);
        editor.apply();
    }

    private void sendData() {
        checkInternetConnection();
        checkServerConnection();
        // Отправка данных на сервер
        if (!dataArrayList.isEmpty()){
            StringBuilder dataBuilder = new StringBuilder();
            for (String arrData : dataArrayList) {
                dataBuilder.append(arrData);
            }
            String data = dataBuilder.toString();
            if (isInternetConnected && isServerConnected) {
                sendSensorDataToServer(data);
                sendDataFromFilesToServer();
            } else {
                // Сохранение данных в памяти устройства
                saveDataToFile(data);
            }

            dataArrayList.clear();
        }
    }*/

    /*private void saveDataToFile(String data) {
//        String fileName = new SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(new Date()) + ".txt";
//        File directory = getFilesDir();
//        File file = new File(directory, fileName);
//
//        try {
//            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
//            writer.append(data);
//            writer.append("\n");
//            writer.flush();
//            writer.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        String fileName = new SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(new Date()) + ".txt";
        File documentsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);

        if (!documentsDirectory.exists()) {
            documentsDirectory.mkdirs(); // Создание каталога, если он не существует
        }

        File file = new File(documentsDirectory, fileName);

        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.append(data);
            writer.append("\n");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private String readFileContents(File file) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append("\n");
            }
            reader.close();
        } catch (IOException e) {
        }
        return stringBuilder.toString();
    }
    private void sendDataFromFilesToServer() {
        File directory = getFilesDir();
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    String data = readFileContents(file);
                    sendSensorDataToServer(data); // Call the AsyncTask to send data to the server
                    file.delete();
                }
            }
        }
    }*/

    /*private void sendSensorDataToServer(String data) {
        try {
            URL serverUrl = new URL(URL);
            HttpURLConnection connection = (HttpURLConnection) serverUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            JSONObject jsonRequest = new JSONObject();
            jsonRequest.put("id_user", ID_USER);
            jsonRequest.put("data", data);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonRequest.toString().getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }

        } catch (IOException e) {
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkInternetConnection() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        isInternetConnected = networkInfo != null && networkInfo.isConnected();
    }
    public void checkServerConnection() {
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
    }*/


    /*private void requestPermissions() {
        // Запрос разрешений у пользователя
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS_CODE);
        }
    }*/
}
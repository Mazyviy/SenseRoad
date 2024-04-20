package com.example.myapplication;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyService extends Service implements SensorEventListener, LocationListener {
    private SensorManager sensorManager;
    private LocationManager locationManager=null;
    private Sensor accelerometer, gravity;

    private float[] accelerometerValue = new float[3];
    private float[] gravityValue = new float[3];
    private float[] linearAccelerometerValue = new float[3];
    private double latitude, longitude, speed;

    private String accelerometerData, gpsData;

    private boolean isInternetConnected;
    private boolean isServerConnected;

    private static final long GPS_UPDATE_INTERVAL = 1000; // в миллисекундах
    private static final long SEND_OR_SAVE_INTERVAL = 1000*60*3; // в миллисекундах

    private String URL = "http://89.179.33.18:27012";
    private CopyOnWriteArrayList<String> dataArrayList;

    private SharedPreferences sharedPreferences;
    private static final String EMAIL_PREF = "email_pref";
    private static final String ID_KEY = "id_key";
    private static final String URL_KEY = "url_key";
    private boolean isIdSet, isUrlSet;
    private String ID_USER;

    private Handler handler = new Handler();
    private ExecutorService pool = Executors.newSingleThreadExecutor();

    private static final String TAG = MyService.class.getSimpleName();
    private PowerManager.WakeLock wakeLock;

    public void onCreate() {
        super.onCreate();
        // Инициализация датчиков
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        dataArrayList = new CopyOnWriteArrayList<>();
        // get a wakelock from the power manager
        final PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String idUser = sharedPreferences.getString(ID_KEY, null);
        String userUrl = sharedPreferences.getString(URL_KEY, null);
        isIdSet = !TextUtils.isEmpty(idUser);
        if (isIdSet) {
            ID_USER = idUser;
        }
        isUrlSet = !TextUtils.isEmpty(userUrl);
        if (isIdSet) {
            URL = userUrl;
        }
    }

    public int onStartCommand(Intent intent, int flag, int startId) {
        // Регистрация слушателей датчиков
        sensorManager.registerListener((SensorEventListener) this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener((SensorEventListener) this,gravity,SensorManager.SENSOR_DELAY_NORMAL);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_UPDATE_INTERVAL, 0, (LocationListener) this);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, GPS_UPDATE_INTERVAL, 0, (LocationListener) this);

        // Проверка подключения
        checkInternetConnection();
        checkServerConnection();
        handler.postDelayed(runnable, SEND_OR_SAVE_INTERVAL);

        final String CHANNEL_ID = "Foreground service";
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        Notification.Builder notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Run SensRoad")
                .setContentText("Запущена служба SensRoad")
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setOngoing(true); // Установка уведомления как постоянного

        startForeground(1001, notification.build());

        // acquire wakelock
        wakeLock.acquire();

        return super.onStartCommand(intent, flag, startId);
    }

    private void checkLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(getApplicationContext(), "Служба локации выключена, приложение будет закрыто", Toast.LENGTH_SHORT).show();
            stopSelf();
        }
    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            checkLocationEnabled();
            sendData();
            handler.postDelayed(this, SEND_OR_SAVE_INTERVAL); // Запуск задачи через 3 минуты снова
        }
    };

    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);

        handler.removeCallbacks(runnable);
        stopForeground(true);
        stopSelf();

        //release wakelock if it is held
        if (null != wakeLock && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Обработка изменений значений акселерометра
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometerValue[0] = event.values[0];
            accelerometerValue[1] = event.values[1];
            accelerometerValue[2] = event.values[2];

            float ALPHA = 0.8f;
            // Фильтрация данных
            gravityValue[0] = ALPHA * gravityValue[0] + (1 - ALPHA) * accelerometerValue[0];
            gravityValue[1] = ALPHA * gravityValue[1] + (1 - ALPHA) * accelerometerValue[1];
            gravityValue[2] = ALPHA * gravityValue[2] + (1 - ALPHA) * accelerometerValue[2];

            linearAccelerometerValue[0] = accelerometerValue[0] - gravityValue[0];
            linearAccelerometerValue[1] = accelerometerValue[1] - gravityValue[1];
            linearAccelerometerValue[2] = accelerometerValue[2] - gravityValue[2];

            accelerometerData = accelerometerValue[0] + "; " +
                                accelerometerValue[1] + "; " +
                                accelerometerValue[2] + "; " +
                                linearAccelerometerValue[0] + "; " +
                                linearAccelerometerValue[1]+ "; " +
                                linearAccelerometerValue[2];
        }

        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            gravityValue[0] = event.values[0];
            gravityValue[1] = event.values[1];
            gravityValue[2] = event.values[2];
        }

        addDataToLists();
        sendAccelerometerBroadcast();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    @Override
    public void onLocationChanged(Location location) {
        // Обработка изменений местоположения
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        speed = location.getSpeed();
        gpsData = latitude + "; " + longitude+ "; " + speed;
        sendGPSToBroadcast();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Ваша логика обработки изменения статуса
    }

    @Override
    public void onProviderEnabled(String provider) {
        // Ваша логика обработки включения провайдера
    }

    @Override
    public void onProviderDisabled(String provider) {
        // Ваша логика обработки отключения провайдера
    }

    private void sendGPSToBroadcast() {
        Intent broadcastIntent = new Intent("gpsDataUpdated");
        broadcastIntent.putExtra("latitude", latitude);
        broadcastIntent.putExtra("longitude", longitude);
        broadcastIntent.putExtra("speed", speed);
        sendBroadcast(broadcastIntent);
    }

    private void sendAccelerometerBroadcast() {
        // Отправка широковещательного сообщения с целочисленным значением
        Intent broadcastIntent = new Intent("accelerometerDataUpdated");
        broadcastIntent.putExtra("accelerometerValue", accelerometerValue);
        broadcastIntent.putExtra("linearAccelerometerValue", linearAccelerometerValue);
        sendBroadcast(broadcastIntent);
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

    private void addDataToLists() {
        if (accelerometerData != null && gpsData != null && speed > 1.5) {
            dataArrayList.add(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(System.currentTimeMillis())
                    + "; " + gpsData + "; " + accelerometerData +"\n");
        }
    }

    private void sendData() {
        pool.submit(new Runnable() {
            @Override
            public void run() {
                checkInternetConnection();
                checkServerConnection();
                // Отправка данных на сервер
                if (!dataArrayList.isEmpty()) {
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
            }
        });
    }

    private void saveDataToFile(String data) {
        pool.submit(new Runnable() {
            @Override
            public void run() {

                String fileName = new SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).format(new Date()) + ".txt";
                File directory = getFilesDir();
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                File user_dir = new File(directory, "user_data");
                if (!user_dir.exists()) {
                    user_dir.mkdirs();
                }
                File file_user = new File(user_dir, fileName);

                try {
                    BufferedWriter writer1 = new BufferedWriter(new FileWriter(file_user));
                    writer1.append(data);
                    writer1.append("\n");
                    writer1.flush();
                    writer1.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
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
        pool.submit(new Runnable() {
            @Override
            public void run() {
                File directory = getFilesDir();
                File user_dir = new File(directory, "user_data");
                File[] files = user_dir.listFiles();

                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            String data = readFileContents(file);
                            sendSensorDataToServer(data);
                            file.delete();
                        }
                    }
                }
            }
        });
    }

    private void sendSensorDataToServer(String data) {
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
                    jsonRequest.put("id_user", ID_USER);
                    jsonRequest.put("data", data);

                    try (OutputStream os = connection.getOutputStream()) {
                        byte[] input = jsonRequest.toString().getBytes("utf-8");
                        os.write(input, 0, input.length);
                    }

                    String response = String.valueOf(connection.getResponseMessage());
                    connection.disconnect();
                }
                catch (IOException | JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
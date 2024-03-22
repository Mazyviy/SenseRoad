package com.example.myapplication;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MyService extends Service implements SensorEventListener, LocationListener {
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private Sensor accelerometer;

    private float accelerometr_x, accelerometr_y, accelerometr_z;
    private double latitude, longitude;
    private float speed;

    private String accelerometerData, gpsData;

    private boolean isInternetConnected;
    private boolean isServerConnected;

    private static final long GPS_UPDATE_INTERVAL = 1000; // в миллисекундах
    private static final long SEND_OR_SAVE_INTERVAL = 1000*1*15; // в миллисекундах

    private String URL = "http://89.179.33.18:27011";
    private CopyOnWriteArrayList<String> dataArrayList;

    private static final String EMAIL_PREF = "email_pref";
    private static final String EMAIL_KEY = "email_key";
    private static final String ID_KEY = "id_key";
    private boolean isEmailSet;
    private String ID_USER;

    private Handler handler = new Handler();
    private ExecutorService pool = Executors.newSingleThreadExecutor();

    public void onCreate() {
        super.onCreate();
        Log.d("LOG_TAG", "create_service");

        // Инициализация датчиков
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        dataArrayList = new CopyOnWriteArrayList<>();
    }

    public int onStartCommand(Intent intent, int flag, int startId) {
        // Регистрация слушателей датчиков
        sensorManager.registerListener((SensorEventListener) this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_UPDATE_INTERVAL, 0, (LocationListener) this);

//////////////////////
        // Проверка подключения
        new Thread(new Runnable() {
            @Override
            public void run() {
                checkInternetConnection();
                checkServerConnection();
                handler.postDelayed(runnable, SEND_OR_SAVE_INTERVAL);
            }
        }).start();
//////////////////

        final String CHANNEL_ID = "Foreground service";
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);

        Notification.Builder notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Run SensRoad")
                .setContentText("Запущена служба SensRoad");

        startForeground(1001, notification.build());

        return super.onStartCommand(intent, flag, startId);
    }

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            sendData();
            handler.postDelayed(this, SEND_OR_SAVE_INTERVAL); // Запуск задачи через 3 минуты снова
        }
    };

    public void onDestroy() {
        super.onDestroy();
        Log.d("LOG_TAG", "destroy_service");
        sensorManager.unregisterListener(this);

        stopForeground(true);
        stopSelf();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d("serv", "acceleration");
        // Обработка изменений значений акселерометра
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelerometr_x = event.values[0];
            accelerometr_y = event.values[1];
            accelerometr_z = event.values[2];
            accelerometerData = accelerometr_x + "; " + accelerometr_y + "; " + accelerometr_z;
        }

        addDataToLists();
        sendAccelerometerBroadcast();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d("serv", "location");
        // Обработка изменений местоположения
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        speed = location.getSpeed();
        gpsData = latitude + "; " + longitude;
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
        Intent broadcastIntent = new Intent("gps_data_updated");
        broadcastIntent.putExtra("latitude", latitude);
        broadcastIntent.putExtra("longitude", longitude);
        broadcastIntent.putExtra("speed", speed);
        sendBroadcast(broadcastIntent);
    }

    private void sendAccelerometerBroadcast() {
        // Отправка широковещательного сообщения с целочисленным значением
        Intent broadcastIntent = new Intent("accelerometr_data_updated");
        broadcastIntent.putExtra("accelerometr_x", accelerometr_x);
        broadcastIntent.putExtra("accelerometr_y", accelerometr_y);
        broadcastIntent.putExtra("accelerometr_z", accelerometr_z);

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
                    + "; " + gpsData + "; " + accelerometerData + "\n");
        }
    }

    private void sendData() {
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
                Log.d("syst", "send");
                sendSensorDataToServer(data);
                sendDataFromFilesToServer();
                Toast.makeText(this, "send_data", Toast.LENGTH_SHORT).show();
            } else {
                // Сохранение данных в памяти устройства
                saveDataToFile(data);
                Toast.makeText(this, "save_data", Toast.LENGTH_SHORT).show();
            }

            dataArrayList.clear();
        }
    }

    private void saveDataToFile(String data) {
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
    }

    private void sendSensorDataToServer(String data) {
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
}
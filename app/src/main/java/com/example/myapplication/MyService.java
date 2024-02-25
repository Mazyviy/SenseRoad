package com.example.myapplication;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.concurrent.TimeUnit;

public class MyService extends Service implements SensorEventListener, LocationListener {
    private SensorManager sensorManager;
    private LocationManager locationManager;
    private Sensor accelerometer;

    private PowerManager.WakeLock mWakeLock;

    private String SYSTEM_LOG = "SYSTEM_LOG";

    private float accelerometr_x, accelerometr_y, accelerometr_z;

    private double latitude, longitude;
    private float speed;

    public static int count;
    public static String count_str;
    private String accelerometerData, gpsData;

    NotificationManager nm;

    private static final int REQUEST_PERMISSIONS_CODE = 1;
    private static final long ACCELEROMETER_UPDATE_INTERVAL = 150; // в миллисекундах
    private static final long GPS_UPDATE_INTERVAL = 1000; // в миллисекундах
    private static final long SEND_SAVE_UPDATE_INTERVAL = 3000; // в миллисекундах


    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID = "SensRoadChannelId";

    private NotificationManager notificationManager;


    public void onCreate() {
        super.onCreate();
        Log.d("LOG_TAG", "create_service");

        // Инициализация датчиков
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);


    }

    public int onStartCommand(Intent intent, int flag, int startId) {

        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());


        // Регистрация слушателей датчиков
        sensorManager.registerListener((SensorEventListener) this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, GPS_UPDATE_INTERVAL, 0, (LocationListener) this);

        if (intent != null && intent.hasExtra("SensRoad")) {
            boolean isServiceRunning = intent.getBooleanExtra("SensRoad", false);
            if (isServiceRunning) {
                // Сервис уже запущен, проигнорируем действие
                return START_NOT_STICKY;
            }
        }

        sendNotif();
        Log.d("LOG_TAG", "start_service");
        //return super.onStartCommand(intent, flag,startId);
        return START_NOT_STICKY;
    }

    void sendNotif() {
        // Создание канала уведомлений (необходимо только для API 26 и выше)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "SensRoadChannel";
            String description = "Channel for SensRoad notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("SensRoadChannelId", name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // Создание уведомления с использованием NotificationCompat.Builder
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "SensRoadChannelId")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Run SensRoad")
                .setContentText("Запущена служба SensRoad")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        // Создание явного намерения для запуска MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("SensRoad", isServiceRunning());
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        builder.setContentIntent(pendingIntent);


        // Отправка уведомления
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(1, builder.build());
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (MyService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void onDestroy() {
        super.onDestroy();
        Log.d("LOG_TAG", "destroy_service");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Обработка изменений значений акселерометра
        accelerometr_x = event.values[0];
        accelerometr_y = event.values[1];
        accelerometr_z = event.values[2];

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

        sendGPSToBroadcast();
    }


    private void sendGPSToBroadcast() {
                Intent broadcastIntent = new Intent("gps_data_updated");
                broadcastIntent.putExtra("latitude", latitude);
                broadcastIntent.putExtra("longitude", longitude);
                broadcastIntent.putExtra("speed", speed);
                sendBroadcast(broadcastIntent);
                //String gpsData = latitude + "; " + longitude;
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


    private void sendAccelerometerBroadcast() {
                // Отправка широковещательного сообщения с целочисленным значением
                //String accelerometerData = accelerometer_x + "; " + accelerometer_y + "; " + accelerometer_z;
                Intent broadcastIntent = new Intent("accelerometr_data_updated");
                broadcastIntent.putExtra("accelerometr_x", accelerometr_x);
                broadcastIntent.putExtra("accelerometr_y", accelerometr_y);
                broadcastIntent.putExtra("accelerometr_z", accelerometr_z);
                sendBroadcast(broadcastIntent);
    }
}
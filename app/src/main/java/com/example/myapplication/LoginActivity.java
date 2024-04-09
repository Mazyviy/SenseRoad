package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity implements View.OnClickListener {

    private String URL = "http://89.179.33.18:27011";
    private boolean isInternetConnected;
    private boolean isServerConnected;
    private SharedPreferences sharedPreferences;
    private static final String EMAIL_PREF = "email_pref";
    private static final String EMAIL_KEY = "email_key";
    private static final String ID_KEY = "id_key";
    private boolean isEmailSet;
    private String ID_USER;
    private EditText email;

    private ExecutorService pool = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.activity_login);

        email = (EditText) findViewById(R.id.editTextTextEmailAddress);
        Button btnSend = (Button) findViewById(R.id.buttonSendEmail);
        btnSend.setOnClickListener((View.OnClickListener) this);


        sharedPreferences = getSharedPreferences(EMAIL_PREF, MODE_PRIVATE);
        String userEmail = sharedPreferences.getString(EMAIL_KEY, null);
        String idUser = sharedPreferences.getString(ID_KEY, null);
        isEmailSet = !TextUtils.isEmpty(userEmail);
        if (isEmailSet) {
            ID_USER = idUser;
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            startActivity(intent);
        }
    }

    public void onClick(View v) {
        String emailText = String.valueOf(email.getText());

        checkInternetConnection();
        checkServerConnection();

        if (isInternetConnected==false) {
            Toast.makeText(LoginActivity.this, "Нет доступа к интернету! Для регистрации требуется интернет", Toast.LENGTH_SHORT).show();
        }
        if (isInternetConnected==true & isServerConnected==false) {
            Toast.makeText(LoginActivity.this, "Сервер недоступен, приносим свои извинения. Мы постараемся как можно быстрее исправить инцидент.", Toast.LENGTH_SHORT).show();
        }
        if (isServerConnected==true & isInternetConnected==true) {
            new CheckEmailTask().execute(emailText);
        }
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

    private class CheckEmailTask extends AsyncTask<String, Void, String> {
        private String email;
        final ProgressDialog dialog = new ProgressDialog(LoginActivity.this);

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog.setMessage("Processing...");
            dialog.show();
        }

        @Override
        protected String doInBackground(String... params) {
            this.email = params[0];

            try {
                URL serverUrl = new URL(URL);
                HttpURLConnection connection = (HttpURLConnection) serverUrl.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                JSONObject jsonRequest = new JSONObject();
                jsonRequest.put("email", email);

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

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    return jsonResponse.getString("id_user");
                }
            } catch (IOException | JSONException e) {
                return null;
            }
        }

        protected void onPostExecute(String result) {
            if (result != null) {
                Toast.makeText(LoginActivity.this, "Email зарегистрирован", Toast.LENGTH_SHORT).show();

                saveEmail(this.email, result);
                isEmailSet = true;

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);

            } else {
                Toast.makeText(LoginActivity.this, "Данный Email уже зарегистрирован", Toast.LENGTH_SHORT).show();
                dialog.cancel();
            }
        }
    };

    private void saveEmail(String email, String id) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(EMAIL_KEY, email);
        editor.putString(ID_KEY, id);
        editor.apply();
    }
}
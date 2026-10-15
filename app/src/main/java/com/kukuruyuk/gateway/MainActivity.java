package com.kukuruyuk.gateway;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus, tvLogs;
    private EditText etServerUrl;
    private Button btnIzinNotif, btnIzinBaterai, btnSimpanUrl, btnTest;
    private SharedPreferences prefs;

    private final BroadcastReceiver logReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String msg = intent.getStringExtra("log_message");
            if (msg != null) {
                tambahLog(msg);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("KukuruyukPrefs", Context.MODE_PRIVATE);

        tvStatus = findViewById(R.id.tvStatus);
        tvLogs = findViewById(R.id.tvLogs);
        etServerUrl = findViewById(R.id.etServerUrl);
        btnIzinNotif = findViewById(R.id.btnIzinNotif);
        btnIzinBaterai = findViewById(R.id.btnIzinBaterai);
        btnSimpanUrl = findViewById(R.id.btnSimpanUrl);
        btnTest = findViewById(R.id.btnTest);

        String defaultUrl = "https://script.google.com/macros/s/AKfycbxMEdqB4pQXAISIMcGOaeE5EhMYtsHjE4_rwXRKGN5s_IpOv1bbTu8bDZXxm3Ce8Q5g/exec";
        etServerUrl.setText(prefs.getString("server_url", defaultUrl));

        btnSimpanUrl.setOnClickListener(v -> {
            String url = etServerUrl.getText().toString().trim();
            prefs.edit().putString("server_url", url).apply();
            Toast.makeText(this, "URL Server Berhasil Disimpan!", Toast.LENGTH_SHORT).show();
            tambahLog("Pengaturan URL disimpan.");
        });

        btnIzinNotif.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        btnIzinBaterai.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(fallback);
                }
            }
        });

        btnTest.setOnClickListener(v -> {
            tambahLog("Mengirim simulasi tes Rp 10.000...");
            new Thread(() -> {
                try {
                    String url = etServerUrl.getText().toString().trim() + "?notif=" + Uri.encode("Berhasil menerima uang Rp 10.000 dari Penguji");
                    okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                    okhttp3.Request req = new okhttp3.Request.Builder().url(url).build();
                    okhttp3.Response res = client.newCall(req).execute();
                    String body = res.body() != null ? res.body().string() : "";
                    runOnUiThread(() -> tambahLog("Respon Tes Google: " + body));
                } catch (Exception e) {
                    runOnUiThread(() -> tambahLog("Gagal Tes: " + e.getMessage()));
                }
            }).start();
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, new IntentFilter("com.kukuruyuk.gateway.LOG_UPDATE"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(logReceiver, new IntentFilter("com.kukuruyuk.gateway.LOG_UPDATE"));
        }

        tambahLog("Aplikasi Kukuruyuk Gateway siap.");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(logReceiver);
        } catch (Exception ignored) {}
    }

    private void updateStatus() {
        boolean notifGranted = isNotificationServiceEnabled();
        boolean batteryIgnored = isBatteryOptimizationIgnored();

        if (notifGranted && batteryIgnored) {
            tvStatus.setText("AKTIF 24 JAM (SIAP)");
            tvStatus.setTextColor(0xFF16A34A);
        } else if (notifGranted) {
            tvStatus.setText("AKTIF (Baterai Belum Diatur)");
            tvStatus.setTextColor(0xFFEAB308);
        } else {
            tvStatus.setText("TIDAK AKTIF (Izin Belum Diberikan)");
            tvStatus.setTextColor(0xFFDC2626);
        }
    }

    private boolean isNotificationServiceEnabled() {
        ComponentName cn = new ComponentName(this, DanaNotificationListener.class);
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(cn.flattenToString());
    }

    private boolean isBatteryOptimizationIgnored() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    private void tambahLog(String pesan) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = "[" + time + "] " + pesan + "\n";
        tvLogs.append(line);
    }
}

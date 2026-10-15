package com.kukuruyuk.gateway;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DanaNotificationListener extends NotificationListenerService {

    private static final String TAG = "KukuruyukGateway";
    private OkHttpClient httpClient;

    @Override
    public void onCreate() {
        super.onCreate();
        httpClient = new OkHttpClient();
        Log.d(TAG, "DanaNotificationListener Service Started");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        String packageName = sbn.getPackageName();
        if (!"id.dana".equalsIgnoreCase(packageName)) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "");
        CharSequence textChar = extras.getCharSequence(Notification.EXTRA_TEXT);
        String text = (textChar != null) ? textChar.toString() : "";

        String fullText = (title + " " + text).trim();
        Log.d(TAG, "DANA Notif Received: " + fullText);

        String lower = fullText.toLowerCase();
        if (lower.contains("rp") || lower.contains("berhasil") || lower.contains("menerima") ||
            lower.contains("10.000") || lower.contains("5.000") || lower.contains("10000") || lower.contains("5000") ||
            lower.contains("15.000") || lower.contains("20.000") || lower.contains("30.000") || lower.contains("50.000")) {

            kirimKeGoogleAppsScript(fullText);
        }
    }

    private void kirimKeGoogleAppsScript(String notifText) {
        SharedPreferences prefs = getSharedPreferences("KukuruyukPrefs", Context.MODE_PRIVATE);
        String defaultUrl = "https://script.google.com/macros/s/AKfycbxMEdqB4pQXAISIMcGOaeE5EhMYtsHjE4_rwXRKGN5s_IpOv1bbTu8bDZXxm3Ce8Q5g/exec";
        String serverUrl = prefs.getString("server_url", defaultUrl);

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Kukuruyuk::WakeLock");
            wakeLock.acquire(15000);
        }

        try {
            String encodedNotif = URLEncoder.encode(notifText, StandardCharsets.UTF_8.name());
            String fullUrl = serverUrl + "?notif=" + encodedNotif;

            Request request = new Request.Builder()
                    .url(fullUrl)
                    .get()
                    .build();

            final PowerManager.WakeLock finalWakeLock = wakeLock;
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Gagal kirim ke Google: " + e.getMessage());
                    broadcastLog("Gagal kirim: " + e.getMessage());
                    if (finalWakeLock != null && finalWakeLock.isHeld()) {
                        finalWakeLock.release();
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String resBody = (response.body() != null) ? response.body().string() : "";
                    Log.d(TAG, "Respon Google: " + resBody);
                    broadcastLog("Sukses terkirim ke Google! Respon: " + resBody);
                    if (finalWakeLock != null && finalWakeLock.isHeld()) {
                        finalWakeLock.release();
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private void broadcastLog(String message) {
        Intent intent = new Intent("com.kukuruyuk.gateway.LOG_UPDATE");
        intent.putExtra("log_message", message);
        sendBroadcast(intent);
    }
}

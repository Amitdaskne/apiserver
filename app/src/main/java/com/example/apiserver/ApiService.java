package com.example.apiserver;

import android.app.*;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import fi.iki.elonen.NanoHTTPD;

public class ApiService extends Service {
    public static final String ACTION_EVENT = "com.example.apiserver.EVENT";
    private ApiServer server;
    private CloudflaredTunnel tunnel;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopEverything();
            return START_NOT_STICKY;
        }

        createChannel();
        Notification n = new NotificationCompat.Builder(this, "api")
                .setContentTitle("Video API Active")
                .setContentText("Tunnel is running...")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(1, n);

        new Thread(() -> {
            try {
                emit("status", "Starting HTTP server on 8080...");
                server = new ApiServer(8080, this); // Naya context wala server
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                emit("status", "HTTP OK: Localhost:8080");

                emit("status", "Starting Cloudflare Tunnel...");
                tunnel = new CloudflaredTunnel();
                tunnel.start(this, 8080,
                        s -> emit("status", s),
                        u -> emit("url", u));
            } catch (Exception e) {
                emit("error", "Fatal: " + e.getMessage());
            }
        }).start();
        return START_STICKY;
    }

    private void stopEverything() {
        if (server != null) { server.stop(); server = null; }
        if (tunnel != null) { tunnel.stop(); tunnel = null; }
        emit("stopped", "Service stopped");
        stopForeground(true);
        stopSelf();
    }

    private void emit(String type, String msg) {
        Intent i = new Intent(ACTION_EVENT);
        i.setPackage(getPackageName());
        i.putExtra("type", type);
        i.putExtra("message", msg);
        sendBroadcast(i);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("api", "API Server", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        if (server != null) server.stop();
        if (tunnel != null) tunnel.stop();
        super.onDestroy();
    }
}

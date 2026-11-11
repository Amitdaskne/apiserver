package com.example.apiserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import fi.iki.elonen.NanoHTTPD;

public class ApiService extends Service {
    public static final String ACTION_URL = "com.example.apiserver.URL";
    private ApiServer server;
    private CloudflaredTunnel tunnel;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        Notification notif = new NotificationCompat.Builder(this, "api")
                .setContentTitle("API Server")
                .setContentText("Starting tunnel...")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notif);
        }

        new Thread(() -> {
            try {
                server = new ApiServer(8080);
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);

                tunnel = new CloudflaredTunnel();
                tunnel.start(this, 8080, url -> {
                    Intent i = new Intent(ACTION_URL);
                    i.setPackage(getPackageName());
                    i.putExtra("url", url);
                    sendBroadcast(i);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return START_STICKY;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    "api", "API Server", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        if (server != null) server.stop();
        if (tunnel != null) tunnel.stop();
        super.onDestroy();
    }
}

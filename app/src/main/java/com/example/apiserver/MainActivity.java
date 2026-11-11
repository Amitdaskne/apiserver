package com.example.apiserver;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView urlText;
    private Button startBtn, copyBtn;
    private String currentUrl;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            currentUrl = i.getStringExtra("url");
            urlText.setText(currentUrl);
            copyBtn.setEnabled(true);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        urlText  = findViewById(R.id.urlText);
        startBtn = findViewById(R.id.startBtn);
        copyBtn  = findViewById(R.id.copyBtn);

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        IntentFilter filter = new IntentFilter(ApiService.ACTION_URL);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }

        startBtn.setOnClickListener(v -> {
            urlText.setText("Starting server + tunnel...\n(5-10 sec lagenge)");
            startBtn.setEnabled(false);
            copyBtn.setEnabled(false);
            Intent svc = new Intent(this, ApiService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
            else startService(svc);
        });

        copyBtn.setOnClickListener(v -> {
            if (currentUrl != null) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("API URL", currentUrl));
                Toast.makeText(this, "URL copied!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}

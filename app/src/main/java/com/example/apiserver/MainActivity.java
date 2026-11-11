package com.example.apiserver;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView urlText, statusText;
    private Button startBtn, copyBtn;
    private String currentUrl;

    private final BroadcastReceiver recv = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String type = i.getStringExtra("type");
            String msg = i.getStringExtra("message");
            if ("url".equals(type)) {
                currentUrl = msg;
                urlText.setText(msg);
                copyBtn.setEnabled(true);
                append("✅ URL READY");
            } else if ("error".equals(type)) {
                append("❌ " + msg);
            } else {
                append(msg);
            }
        }
    };

    private void append(String s) { statusText.append(s + "\n"); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        urlText = findViewById(R.id.urlText);
        statusText = findViewById(R.id.statusText);
        startBtn = findViewById(R.id.startBtn);
        copyBtn = findViewById(R.id.copyBtn);

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        IntentFilter f = new IntentFilter(ApiService.ACTION_EVENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(recv, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(recv, f);

        startBtn.setOnClickListener(v -> {
            urlText.setText("Waiting...");
            startBtn.setEnabled(false);
            copyBtn.setEnabled(false);
            statusText.setText("");
            append("Starting service...");
            Intent s = new Intent(this, ApiService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s);
            else startService(s);
        });

        copyBtn.setOnClickListener(v -> {
            if (currentUrl != null) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("URL", currentUrl));
                Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(recv); } catch (Exception ignored) {}
        super.onDestroy();
    }
}

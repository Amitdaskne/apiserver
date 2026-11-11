package com.example.apiserver;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileWriter;

public class MainActivity extends AppCompatActivity {
    private TextView urlText, statusText, brandingText;
    private Button startBtn, stopBtn, copyBtn;
    private String currentUrl;

    private final BroadcastReceiver recv = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String type = i.getStringExtra("type");
            String msg = i.getStringExtra("message");
            if ("url".equals(type)) {
                currentUrl = msg;
                urlText.setText("> " + msg);
                copyBtn.setEnabled(true);
                append("> URL READY: " + msg);
                // Automatically export file on URL ready
                exportLinkAmtFile(msg);
            } else if ("error".equals(type)) {
                append("> [ERR] " + msg);
            } else if ("stopped".equals(type)) {
                resetUI();
            } else {
                append("> " + msg);
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
        stopBtn = findViewById(R.id.stopBtn);
        copyBtn = findViewById(R.id.copyBtn);
        brandingText = findViewById(R.id.brandingText);

        // Apply Retro Digital Font
        try {
            Typeface tf = Typeface.createFromAsset(getAssets(), "VT323.ttf");
            brandingText.setTypeface(tf);
            urlText.setTypeface(tf);
            statusText.setTypeface(tf);
            startBtn.setTypeface(tf);
            stopBtn.setTypeface(tf);
            copyBtn.setTypeface(tf);
        } catch (Exception ignored) {}

        // Continuous Pulse Animation for Branding
        brandingText.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        IntentFilter f = new IntentFilter(ApiService.ACTION_EVENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(recv, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(recv, f);

        startBtn.setOnClickListener(v -> {
            // Animation: Shrink Start, Fade Out
            startBtn.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(300).withEndAction(() -> {
                startBtn.setVisibility(View.GONE);
                stopBtn.setVisibility(View.VISIBLE);
                stopBtn.setScaleX(0f); stopBtn.setScaleY(0f); stopBtn.setAlpha(0f);
                stopBtn.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).start();
            }).start();

            urlText.setText("> BOOTING...");
            copyBtn.setEnabled(false);
            statusText.setText("");
            append("Initializing API Server...");
            
            Intent s = new Intent(this, ApiService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(s);
            else startService(s);
        });

        stopBtn.setOnClickListener(v -> {
            // Animation: Shrink Stop, Fade Out
            stopBtn.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(300).withEndAction(() -> {
                stopBtn.setVisibility(View.GONE);
                startBtn.setVisibility(View.VISIBLE);
                startBtn.setScaleX(0f); startBtn.setScaleY(0f); startBtn.setAlpha(0f);
                startBtn.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).start();
            }).start();

            append("Shutting down...");
            Intent s = new Intent(this, ApiService.class);
            s.setAction("STOP");
            startService(s);
        });

        copyBtn.setOnClickListener(v -> {
            if (currentUrl != null) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("URL", currentUrl));
                exportLinkAmtFile(currentUrl);
            }
        });
    }

    private void exportLinkAmtFile(String url) {
        try {
            File file = new File(getExternalFilesDir(null), "link.amit");
            FileWriter fw = new FileWriter(file);
            fw.write(url);
            fw.close();
            Toast.makeText(this, "Saved: link.amit\nPath: Android/data/" + getPackageName() + "/files/", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "File save failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetUI() {
        urlText.setText("SYSTEM OFFLINE...");
        copyBtn.setEnabled(false);
        currentUrl = null;
        append("System offline.");
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(recv); } catch (Exception ignored) {}
        super.onDestroy();
    }
}

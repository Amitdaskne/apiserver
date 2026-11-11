package com.example.apiserver;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.util.function.Consumer;
import java.util.regex.*;

public class CloudflaredTunnel {
    private static final String TAG = "Cloudflared";
    private Process process;

    public void start(Context ctx, int port, Consumer<String> onStatus, Consumer<String> onUrl) {
        new Thread(() -> {
            try {
                File bin = new File(ctx.getApplicationInfo().nativeLibraryDir, "libcloudflared.so");
                onStatus.accept("Binary: " + bin.getAbsolutePath());
                onStatus.accept("Exists: " + bin.exists() + ", canExec: " + bin.canExecute());
                if (!bin.exists()) {
                    onStatus.accept("ERROR: libcloudflared.so not in nativeLibraryDir");
                    return;
                }

                File certBundle = new File(ctx.getFilesDir(), "cacerts.pem");
                if (!certBundle.exists()) {
                    File[] certs = new File("/system/etc/security/cacerts").listFiles();
                    if (certs != null && certs.length > 0) {
                        try (OutputStream out = new FileOutputStream(certBundle)) {
                            for (File c : certs) {
                                try (InputStream in = new FileInputStream(c)) {
                                    byte[] buf = new byte[4096]; int n;
                                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                                    out.write("\n".getBytes());
                                }
                            }
                        }
                        onStatus.accept("CA bundle: " + certs.length + " certs");
                    }
                }

                ProcessBuilder pb = new ProcessBuilder(
                        bin.getAbsolutePath(),
                        "tunnel", "--url", "http://127.0.0.1:" + port,
                        "--no-autoupdate");
                pb.redirectErrorStream(true);
                pb.environment().put("HOME", ctx.getFilesDir().getAbsolutePath());
                pb.environment().put("TMPDIR", ctx.getCacheDir().getAbsolutePath());
                if (certBundle.exists()) {
                    pb.environment().put("SSL_CERT_FILE", certBundle.getAbsolutePath());
                }

                onStatus.accept("Launching cloudflared...");
                process = pb.start();
                onStatus.accept("PID: " + process.pid());

                BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()));
                Pattern p = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
                String line; boolean found = false;
                while ((line = r.readLine()) != null) {
                    Log.d(TAG, "CF: " + line);
                    onStatus.accept("CF: " + line);
                    if (!found) {
                        Matcher m = p.matcher(line);
                        if (m.find()) { found = true; onUrl.accept(m.group()); }
                    }
                }
                onStatus.accept("cloudflared exited: " + process.exitValue());
            } catch (Exception e) {
                Log.e(TAG, "err", e);
                onStatus.accept("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }).start();
    }

    public void stop() { if (process != null) { process.destroy(); process = null; } }
}

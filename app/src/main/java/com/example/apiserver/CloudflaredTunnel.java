package com.example.apiserver;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CloudflaredTunnel {
    private Process process;
    public String publicUrl;

    public void start(Context ctx, int port, Consumer<String> onUrl) throws Exception {
        File bin = new File(ctx.getFilesDir(), "cloudflared");
        if (!bin.exists()) {
            try (InputStream in = ctx.getAssets().open("cloudflared");
                 OutputStream out = new FileOutputStream(bin)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            bin.setExecutable(true);
        }

        ProcessBuilder pb = new ProcessBuilder(
                bin.getAbsolutePath(),
                "tunnel", "--url", "http://127.0.0.1:" + port,
                "--no-autoupdate"
        );
        pb.redirectErrorStream(true);
        process = pb.start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        Pattern p = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
        String line;
        while ((line = reader.readLine()) != null) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                publicUrl = m.group();
                onUrl.accept(publicUrl);
                break;
            }
        }
    }

    public void stop() {
        if (process != null) { process.destroy(); process = null; }
    }
}

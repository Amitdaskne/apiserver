package com.example.apiserver;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;
import org.json.JSONObject;

public class CloudflaredTunnel {
    private static final String TAG = "Cloudflared";
    private Process process;

    public void start(Context ctx, int port, Consumer<String> onStatus, Consumer<String> onUrl) {
        new Thread(() -> {
            try {
                // ── Step 1: Java se tunnel register karo (DNS Java me chalega) ──
                onStatus.accept("Registering tunnel via Java DNS...");
                JSONObject result = new JSONObject();
                HttpURLConnection conn = (HttpURLConnection)
                        new URL("https://api.trycloudflare.com/tunnel").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write("{}".getBytes());
                }
                if (conn.getResponseCode() != 200) {
                    onStatus.accept("ERROR: register failed HTTP " + conn.getResponseCode());
                    return;
                }
                String body = readAll(conn.getInputStream());
                result = new JSONObject(body).getJSONObject("result");

                String tunnelId = result.getString("id");
                String hostname = result.getString("hostname"); // e.g. xxx.trycloudflare.com
                String accountTag = result.getString("account_tag");
                String secret = result.getString("secret");

                // ── Step 2: Credentials + config files likho ──
                File credsFile = new File(ctx.getCacheDir(), "tunnel_creds.json");
                try (FileWriter fw = new FileWriter(credsFile)) {
                    JSONObject creds = new JSONObject();
                    creds.put("AccountTag", accountTag);
                    creds.put("TunnelID", tunnelId);
                    creds.put("TunnelSecret", secret);
                    fw.write(creds.toString());
                }

                File configFile = new File(ctx.getCacheDir(), "tunnel_config.yml");
                try (FileWriter fw = new FileWriter(configFile)) {
                    fw.write("tunnel: " + tunnelId + "\n");
                    fw.write("credentials-file: " + credsFile.getAbsolutePath() + "\n");
                    fw.write("protocol: http2\n");
                    fw.write("ingress:\n");
                    fw.write("  - hostname: " + hostname + "\n");
                    fw.write("    service: http://127.0.0.1:" + port + "\n");
                    fw.write("  - service: http_status:404\n");
                }

                // ── Step 3: Edge IPs Java se resolve karo (IPv4 only) ──
                onStatus.accept("Resolving edge IPs via Java...");
                List<String> edgeIps = new ArrayList<>();
                for (String host : new String[]{
                        "region1.v2.argotunnel.com",
                        "region2.v2.argotunnel.com"}) {
                    try {
                        for (InetAddress ia : InetAddress.getAllByName(host)) {
                            if (ia instanceof Inet4Address) {
                                edgeIps.add(ia.getHostAddress() + ":7844");
                                if (edgeIps.size() >= 4) break;
                            }
                        }
                    } catch (Exception e) {
                        onStatus.accept("Warn resolving " + host + ": " + e.getMessage());
                    }
                    if (edgeIps.size() >= 4) break;
                }
                if (edgeIps.isEmpty()) {
                    onStatus.accept("ERROR: no edge IPs resolved");
                    return;
                }
                onStatus.accept("Edge IPs: " + edgeIps.size());

                // ── Step 4: cloudflared run (zero DNS) ──
                File bin = new File(ctx.getApplicationInfo().nativeLibraryDir, "libcloudflared.so");
                List<String> cmd = new ArrayList<>();
                cmd.add(bin.getAbsolutePath());
                cmd.add("tunnel");
                cmd.add("--config"); cmd.add(configFile.getAbsolutePath());
                cmd.add("--edge-ip-version"); cmd.add("4");
                cmd.add("--no-autoupdate");
                for (String ip : edgeIps) {
                    cmd.add("--edge"); cmd.add(ip);   // alag-alag --edge har IP ke liye
                }
                cmd.add("run"); cmd.add(tunnelId);

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                pb.directory(ctx.getCacheDir());
                pb.environment().put("HOME", ctx.getFilesDir().getAbsolutePath());
                pb.environment().put("TMPDIR", ctx.getCacheDir().getAbsolutePath());

                onStatus.accept("Launching cloudflared (zero DNS)...");
                process = pb.start();

                // URL pehle se hi known hai
                onUrl.accept("https://" + hostname);
                onStatus.accept("Tunnel URL ready");

                // Logs drain karo (non-fatal errors ignore)
                BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = r.readLine()) != null) {
                    Log.d(TAG, "CF: " + line);
                    // Sirf important lines dikhao, spam kam karo
                    if (line.contains("ERR") || line.contains("error") || line.contains("Registered tunnel")) {
                        onStatus.accept("CF: " + line);
                    }
                }
                onStatus.accept("cloudflared exited: " + process.exitValue());
            } catch (Exception e) {
                Log.e(TAG, "err", e);
                onStatus.accept("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }).start();
    }

    private static String readAll(InputStream in) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = br.readLine()) != null) sb.append(l);
        return sb.toString();
    }

    public void stop() { if (process != null) { process.destroy(); process = null; } }
}

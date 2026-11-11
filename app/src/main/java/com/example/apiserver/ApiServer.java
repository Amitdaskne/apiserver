package com.example.apiserver;

import android.content.Context;
import fi.iki.elonen.NanoHTTPD;
import java.io.*;
import java.util.*;

public class ApiServer extends NanoHTTPD {
    private final File videoDir;

    public ApiServer(int port, Context ctx) {
        super(port);
        this.videoDir = new File(ctx.getFilesDir(), "videos");
        if (!videoDir.exists()) videoDir.mkdirs();
    }

    @Override
    public Response serve(IHTTPSession session) {
        Response resp;
        String uri = session.getUri();
        Method method = session.getMethod();

        try {
            if (method == Method.OPTIONS) {
                resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
            } 
            else if (uri.equals("/api/ping")) {
                resp = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"alive\"}");
            } 
            else if (uri.equals("/api/upload") && method == Method.POST) {
                Map<String, String> files = new HashMap<>();
                session.parseBody(files);
                String tempFilePath = files.get("video");
                
                if (tempFilePath != null) {
                    File tempFile = new File(tempFilePath);
                    String fileName = "vid_" + System.currentTimeMillis() + ".mp4";
                    File destFile = new File(videoDir, fileName);
                    
                    InputStream in = new FileInputStream(tempFile);
                    OutputStream out = new FileOutputStream(destFile);
                    byte[] buf = new byte[8192]; int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                    in.close(); out.close(); tempFile.delete();

                    resp = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true,\"file\":\"" + fileName + "\"}");
                } else {
                    resp = newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"No file data\"}");
                }
            } 
            else if (uri.equals("/api/videos") && method == Method.GET) {
                File[] files = videoDir.listFiles();
                StringBuilder json = new StringBuilder("{\"videos\":[");
                if (files != null) {
                    for (int i = 0; i < files.length; i++) {
                        json.append("\"").append(files[i].getName()).append("\"");
                        if (i < files.length - 1) json.append(",");
                    }
                }
                json.append("]}");
                resp = newFixedLengthResponse(Response.Status.OK, "application/json", json.toString());
            } 
            else if (uri.equals("/api/play") && method == Method.GET) {
                Map<String, List<String>> params = session.getParameters();
                String file = params.containsKey("file") ? params.get("file").get(0) : null;
                if (file == null || file.contains("..")) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid file");
                }
                File video = new File(videoDir, file);
                if (!video.exists()) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
                }

                InputStream is = new FileInputStream(video);
                long len = video.length();
                String range = session.getHeaders().get("range");
                
                if (range != null && range.startsWith("bytes=")) {
                    String[] parts = range.substring(6).split("-");
                    long start = Long.parseLong(parts[0]);
                    long end = parts.length > 1 ? Long.parseLong(parts[1]) : len - 1;
                    is.skip(start);
                    long contentLen = end - start + 1;
                    resp = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, "video/mp4", is, contentLen);
                    resp.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + len);
                } else {
                    resp = newFixedLengthResponse(Response.Status.OK, "video/mp4", is, len);
                }
                resp.addHeader("Accept-Ranges", "bytes");
            } 
            else {
                resp = newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", "{\"error\":\"not found\"}");
            }
        } catch (Exception e) {
            resp = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }

        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type, Range");
        return resp;
    }
}

package com.example.apiserver;

import fi.iki.elonen.NanoHTTPD;
import java.util.HashMap;
import java.util.Map;

public class ApiServer extends NanoHTTPD {

    public ApiServer(int port) { super(port); }

    @Override
    public Response serve(IHTTPSession session) {
        Response resp;
        String uri = session.getUri();
        Method method = session.getMethod();

        if (method == Method.OPTIONS) {
            resp = newFixedLengthResponse(Response.Status.OK, "text/plain", "");
        } else if (uri.equals("/api/notes") && method == Method.GET) {
            String json = "{\"notes\":[{\"id\":1,\"text\":\"Hello from phone\"}]}";
            resp = newFixedLengthResponse(Response.Status.OK, "application/json", json);
        } else if (uri.equals("/api/notes") && method == Method.POST) {
            Map<String, String> body = new HashMap<>();
            try { session.parseBody(body); } catch (Exception ignored) {}
            String payload = body.get("postData");
            resp = newFixedLengthResponse(Response.Status.OK, "application/json",
                    "{\"ok\":true,\"received\":" +
                    (payload == null ? "null" : "\"" + payload.replace("\"","\\\"") + "\"") + "}");
        } else if (uri.equals("/api/ping")) {
            resp = newFixedLengthResponse(Response.Status.OK, "application/json",
                    "{\"status\":\"alive\",\"time\":" + System.currentTimeMillis() + "}");
        } else {
            resp = newFixedLengthResponse(Response.Status.NOT_FOUND,
                    "application/json", "{\"error\":\"not found\"}");
        }

        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        return resp;
    }
}

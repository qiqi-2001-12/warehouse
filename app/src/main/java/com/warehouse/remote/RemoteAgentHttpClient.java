package com.warehouse.remote;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class RemoteAgentHttpClient {
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 8_000;

    private final RemoteAgentConfig config;

    RemoteAgentHttpClient(RemoteAgentConfig config) {
        this.config = config;
    }

    void sendHeartbeat(JSONObject telemetry) throws IOException {
        JSONObject request = new JSONObject();
        try {
            request.put("serialNo", config.serialNo());
            request.put("name", config.deviceName());
            request.put("telemetry", telemetry);
        } catch (JSONException error) {
            throw new IOException("build heartbeat failed", error);
        }
        post("/api/gateway/heartbeat", request);
    }

    List<RemoteCommand> pullCommands() throws IOException {
        String body = request("GET", "/api/gateway/commands?serialNo="
                + Uri.encode(config.serialNo()), null);
        try {
            JSONArray array = new JSONArray(body);
            List<RemoteCommand> commands = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                JSONObject command = array.getJSONObject(index);
                commands.add(new RemoteCommand(
                        command.getLong("id"),
                        command.getString("type"),
                        command.optJSONObject("payload") == null
                                ? new JSONObject() : command.getJSONObject("payload")));
            }
            return commands;
        } catch (JSONException error) {
            throw new IOException("parse command response failed", error);
        }
    }

    void acknowledge(long commandId, boolean success, String errorMessage) throws IOException {
        JSONObject request = new JSONObject();
        try {
            request.put("success", success);
            if (!success && errorMessage != null && !errorMessage.isEmpty()) {
                request.put("errorMessage", errorMessage);
            }
        } catch (JSONException error) {
            throw new IOException("build command acknowledgement failed", error);
        }
        post("/api/gateway/commands/" + commandId + "/ack", request);
    }

    private void post(String path, JSONObject body) throws IOException {
        request("POST", path, body.toString());
    }

    private String request(String method, String path, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(config.baseUrl() + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-Device-Key", config.deviceKey());
        if (body != null) {
            byte[] content = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(content.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(content);
            }
        }
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String response = readFully(stream);
            if (status < 200 || status >= 300) {
                throw new IOException("server returned " + status + ": " + response);
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static String readFully(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    static final class RemoteCommand {
        final long id;
        final String type;
        final JSONObject payload;

        RemoteCommand(long id, String type, JSONObject payload) {
            this.id = id;
            this.type = type;
            this.payload = payload;
        }
    }
}

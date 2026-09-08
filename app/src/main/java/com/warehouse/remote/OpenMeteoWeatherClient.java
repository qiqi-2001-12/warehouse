package com.warehouse.remote;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Minimal client for Open-Meteo's current-weather endpoint. */
public final class OpenMeteoWeatherClient {
    private static final String ENDPOINT = "https://api.open-meteo.com/v1/forecast";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static volatile CurrentWeather cachedCurrentWeather;

    public interface Callback {
        void onSuccess(CurrentWeather weather);

        void onError(Exception error);
    }

    public void fetchCurrentWeather(double latitude, double longitude, Callback callback) {
        if (callback == null) {
            return;
        }
        new Thread(() -> {
            try {
                callback.onSuccess(requestCurrentWeather(latitude, longitude));
            } catch (Exception error) {
                callback.onError(error);
            }
        }, "open-meteo-request").start();
    }

    public CurrentWeather getCachedCurrentWeather() {
        return cachedCurrentWeather;
    }

    private CurrentWeather requestCurrentWeather(double latitude, double longitude)
            throws IOException, JSONException {
        String urlText = String.format(Locale.US,
                "%s?latitude=%.6f&longitude=%.6f&current=temperature_2m,weather_code",
                ENDPOINT, latitude, longitude);
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");

        try {
            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readBody(stream);
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Open-Meteo HTTP " + responseCode + ": " + body);
            }
            JSONObject current = new JSONObject(body).getJSONObject("current");
            double temperature = current.getDouble("temperature_2m");
            int weatherCode = current.getInt("weather_code");
            CurrentWeather weather = new CurrentWeather(
                    temperature, weatherCode, WeatherCondition.fromCode(weatherCode));
            cachedCurrentWeather = weather;
            return weather;
        } finally {
            connection.disconnect();
        }
    }

    private String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    public static final class CurrentWeather {
        private final double temperatureCelsius;
        private final int weatherCode;
        private final WeatherCondition condition;

        CurrentWeather(double temperatureCelsius, int weatherCode, WeatherCondition condition) {
            this.temperatureCelsius = temperatureCelsius;
            this.weatherCode = weatherCode;
            this.condition = condition;
        }

        public double getTemperatureCelsius() {
            return temperatureCelsius;
        }

        public int getWeatherCode() {
            return weatherCode;
        }

        public WeatherCondition getCondition() {
            return condition;
        }
    }

    public static final class WeatherCondition {
        private final String label;
        private final String headerLabel;
        private final String icon;

        private WeatherCondition(String label, String headerLabel, String icon) {
            this.label = label;
            this.headerLabel = headerLabel;
            this.icon = icon;
        }

        public String getLabel() {
            return label;
        }

        public String getHeaderLabel() {
            return headerLabel;
        }

        public String getIcon() {
            return icon;
        }

        public static WeatherCondition fromCode(int code) {
            switch (code) {
                case 0:
                    return condition("\u6674\u5929", "\u6674", "\u2600\ufe0f");
                case 1:
                    return condition("\u57fa\u672c\u6674\u6717", "\u6674", "\ud83c\udf24\ufe0f");
                case 2:
                    return condition("\u5c40\u90e8\u591a\u4e91", "\u591a\u4e91", "\u26c5");
                case 3:
                    return condition("\u9634\u5929", "\u9634", "\u2601\ufe0f");
                case 45:
                case 48:
                    return condition("\u96fe", "\u96fe", "\ud83c\udf2b\ufe0f");
                case 51:
                case 53:
                case 55:
                case 56:
                case 57:
                    return condition("\u6bdb\u6bdb\u96e8", "\u96e8", "\ud83c\udf26\ufe0f");
                case 61:
                case 63:
                case 65:
                case 66:
                case 67:
                    return condition("\u4e0b\u96e8", "\u96e8", "\ud83c\udf27\ufe0f");
                case 71:
                case 73:
                case 75:
                case 77:
                case 85:
                case 86:
                    return condition("\u4e0b\u96ea", "\u96ea", "\ud83c\udf28\ufe0f");
                case 80:
                case 81:
                case 82:
                    return condition("\u9635\u96e8", "\u96e8", "\ud83c\udf27\ufe0f");
                case 95:
                case 96:
                case 99:
                    return condition("\u96f7\u66b4", "\u96f7", "\u26c8\ufe0f");
                default:
                    return condition("\u672a\u77e5", "--", "--");
            }
        }

        private static WeatherCondition condition(String label, String headerLabel, String icon) {
            return new WeatherCondition(label, headerLabel, icon);
        }
    }
}

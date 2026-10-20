package com.warehouse.data.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Persists the local operation history shown on the administrator page. */
public final class OperationLogStore {
    public static final String OPERATOR_LOCAL = "本地";
    public static final String OPERATOR_CLOUD = "云端";
    private static final String PREFERENCES_NAME = "operation_log";
    private static final String KEY_ENTRIES = "entries";
    private static final int MAX_ENTRIES = 200;

    private static volatile OperationLogStore instance;
    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private OperationLogStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public static OperationLogStore get(Context context) {
        OperationLogStore result = instance;
        if (result == null) {
            synchronized (OperationLogStore.class) {
                result = instance;
                if (result == null) {
                    result = new OperationLogStore(context);
                    instance = result;
                }
            }
        }
        return result;
    }

    public void recordLocal(String message) {
        record(OPERATOR_LOCAL, message);
    }

    public void recordCloud(String message) {
        record(OPERATOR_CLOUD, message);
    }

    public synchronized void record(String operator, String message) {
        if (TextUtils.isEmpty(message)) {
            return;
        }
        JSONArray oldEntries = readJsonArray();
        JSONArray newEntries = new JSONArray();
        JSONObject entry = new JSONObject();
        try {
            entry.put("timestamp", System.currentTimeMillis());
            entry.put("operator", TextUtils.isEmpty(operator) ? OPERATOR_LOCAL : operator);
            entry.put("message", message);
            newEntries.put(entry);
            for (int i = 0; i < oldEntries.length() && i < MAX_ENTRIES - 1; i++) {
                JSONObject oldEntry = oldEntries.optJSONObject(i);
                if (oldEntry != null) {
                    newEntries.put(oldEntry);
                }
            }
            preferences.edit().putString(KEY_ENTRIES, newEntries.toString()).apply();
        } catch (JSONException ignored) {
            return;
        }
        notifyListeners();
    }

    public synchronized List<Entry> getEntries() {
        JSONArray entries = readJsonArray();
        List<Entry> result = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.optJSONObject(i);
            if (item == null) {
                continue;
            }
            result.add(new Entry(
                    item.optLong("timestamp", 0L),
                    item.optString("operator", OPERATOR_LOCAL),
                    item.optString("message", "")));
        }
        return result;
    }

    public synchronized void clear() {
        preferences.edit().remove(KEY_ENTRIES).apply();
        notifyListeners();
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private JSONArray readJsonArray() {
        String raw = preferences.getString(KEY_ENTRIES, "");
        if (TextUtils.isEmpty(raw)) {
            return new JSONArray();
        }
        try {
            return new JSONArray(raw);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private void notifyListeners() {
        mainHandler.post(() -> {
            for (Listener listener : listeners) {
                listener.onChanged();
            }
        });
    }

    public interface Listener {
        void onChanged();
    }

    public static final class Entry {
        public final long timestamp;
        public final String operator;
        public final String message;

        Entry(long timestamp, String operator, String message) {
            this.timestamp = timestamp;
            this.operator = operator;
            this.message = message;
        }
    }
}

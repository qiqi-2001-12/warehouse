package com.warehouse.device;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FaultMonitor {
    private static final long POLL_INTERVAL_MS = 2000L;
    private static final long FAULT_RESET_SUPPRESSION_MS = 5 * 60 * 1000L;
    private static final int MAX_LOG_ENTRIES = 200;
    private static final String PREFERENCES_NAME = "fault_monitor";
    private static final String EVENTS_KEY = "events";
    private static final String STATE_KEY_PREFIX = "active_";
    private static final FaultDefinition[] FAULTS = new FaultDefinition[] {
            new FaultDefinition(800, "\u65b0\u98ce\u6e29\u6e7f\u5ea6\u4f20\u611f\u5668\u901a\u8baf\u6545\u969c"),
            new FaultDefinition(801, "\u9001\u98ce\u6e29\u6e7f\u5ea6\u4f20\u611f\u5668\u901a\u8baf\u6545\u969c"),
            new FaultDefinition(807, "\u8865\u6c34\u8d85\u65f6\u6545\u969c"),
            new FaultDefinition(808, "\u6ee4\u7f51\u62a5\u8b66"),
            new FaultDefinition(809, "\u538b\u7f29\u673a\u9a71\u52a8\u677f\u901a\u8baf\u62a5\u8b66"),
            new FaultDefinition(810, "\u538b\u7f29\u673a\u964d\u9891"),
            new FaultDefinition(814, "\u673a\u7ec4\u6392\u6c34\u6545\u969c"),
            new FaultDefinition(818, "\u673a\u7ec4\u9891\u7e41\u964d\u9891"),
            new FaultDefinition(822, "\u8865\u6c34\u9632\u51bb\u6545\u969c"),
            new FaultDefinition(823, "\u9ad8\u538b\u4f20\u611f\u5668\u6545\u969c"),
            new FaultDefinition(824, "\u4f4e\u538b\u4f20\u611f\u5668\u6545\u969c"),
            new FaultDefinition(825, "\u538b\u673a\u672c\u8eab\u6545\u969c"),
            new FaultDefinition(826, "\u51b7\u51dd\u98ce\u673a\u672c\u8eab\u6545\u969c"),
            new FaultDefinition(829, "\u673a\u7ec4\u6f0f\u6c14\u62a5\u8b66"),
            new FaultDefinition(832, "\u4e94\u5408\u4e00\u4f20\u611f\u5668\u901a\u8baf\u6545\u969c"),
            new FaultDefinition(841, "\u5438\u6c14\u6e29\u5ea6\u8fc7\u4f4e\u4fdd\u62a4"),
            new FaultDefinition(842, "\u6392\u6c14\u8fc7\u70ed\u5ea6\u8fc7\u9ad8\u4fdd\u62a4"),
            new FaultDefinition(843, "\u6392\u6c14\u6e29\u5ea6\u4f20\u611f\u5668\u6545\u969c"),
            new FaultDefinition(844, "\u5438\u6c14\u6e29\u5ea6\u4f20\u611f\u5668\u6545\u969c")
    };
    private static final ReadGroup[] READ_GROUPS = new ReadGroup[] {
            new ReadGroup(800, 2),
            new ReadGroup(807, 4),
            new ReadGroup(814, 5),
            new ReadGroup(822, 5),
            new ReadGroup(829, 1),
            new ReadGroup(832, 1),
            new ReadGroup(841, 4)
    };

    private static FaultMonitor instance;

    public static synchronized FaultMonitor get(Context context) {
        if (instance == null) {
            instance = new FaultMonitor(context.getApplicationContext());
        }
        return instance;
    }

    public static void start(Context context) {
        get(context).startMonitoring();
    }

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SharedPreferences preferences;
    private final List<FaultLogEntry> entries = new ArrayList<>();
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private final Set<Integer> unsupportedGroupStarts = new LinkedHashSet<>();
    private boolean monitoring;
    private boolean pollInFlight;

    private FaultMonitor(Context context) {
        appContext = context;
        preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        loadEntries();
    }

    void startMonitoring() {
        if (monitoring) {
            return;
        }
        monitoring = true;
        scheduleNextPoll(0L);
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            return;
        }
        listeners.add(listener);
        listener.onFaultLogChanged();
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public List<FaultLogEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    /** Clears the current fault log and suppresses reappearing faults for five minutes. */
    public void clearFaultsForFiveMinutes() {
        long suppressionUntil = System.currentTimeMillis() + FAULT_RESET_SUPPRESSION_MS;
        SharedPreferences.Editor editor = preferences.edit()
                .putLong("reset_suppressed_until", suppressionUntil)
                .putString(EVENTS_KEY, "[]");
        entries.clear();
        for (FaultDefinition fault : FAULTS) {
            editor.putBoolean(STATE_KEY_PREFIX + fault.address, false);
        }
        editor.apply();
        notifyListeners();
    }

    private void scheduleNextPoll(long delayMs) {
        handler.removeCallbacksAndMessages(null);
        if (monitoring) {
            handler.postDelayed(this::pollFaults, delayMs);
        }
    }

    private void pollFaults() {
        if (!monitoring || pollInFlight) {
            return;
        }
        List<ReadGroup> readableGroups = new ArrayList<>();
        for (ReadGroup group : READ_GROUPS) {
            if (!unsupportedGroupStarts.contains(group.startAddress)) {
                readableGroups.add(group);
            }
        }
        if (readableGroups.isEmpty()) {
            scheduleNextPoll(POLL_INTERVAL_MS);
            return;
        }
        pollInFlight = true;
        final int[] remainingReads = new int[] {readableGroups.size()};
        for (ReadGroup group : readableGroups) {
            DeviceGateway.get(appContext).readRange(group.startAddress, group.count,
                    new DeviceGateway.RangeCallback() {
                        @Override
                        public void onSuccess(int startAddress, int[] values) {
                            updateFaultStates(startAddress, values);
                            onReadFinished(remainingReads);
                        }

                        @Override
                        public void onError(Exception error) {
                            if (isUnsupportedFaultRead(error)) {
                                unsupportedGroupStarts.add(group.startAddress);
                            }
                            onReadFinished(remainingReads);
                        }
                    });
        }
    }

    private void onReadFinished(int[] remainingReads) {
        remainingReads[0]--;
        if (remainingReads[0] > 0) {
            return;
        }
        pollInFlight = false;
        scheduleNextPoll(POLL_INTERVAL_MS);
    }

    private boolean isUnsupportedFaultRead(Exception error) {
        if (error == null || error.getMessage() == null) {
            return false;
        }
        return "modbus exception: 1".equals(error.getMessage())
                || "modbus exception: 2".equals(error.getMessage());
    }

    private void updateFaultStates(int startAddress, int[] values) {
        for (FaultDefinition fault : FAULTS) {
            int offset = fault.address - startAddress;
            if (offset >= 0 && offset < values.length) {
                updateFaultState(fault, values[offset] == 1);
            }
        }
    }

    private void updateFaultState(FaultDefinition fault, boolean active) {
        String stateKey = STATE_KEY_PREFIX + fault.address;
        if (System.currentTimeMillis() < preferences.getLong("reset_suppressed_until", 0L)) {
            preferences.edit().putBoolean(stateKey, false).apply();
            return;
        }
        boolean wasActive = preferences.getBoolean(stateKey, false);
        SharedPreferences.Editor editor = preferences.edit().putBoolean(stateKey, active);
        if (active && !wasActive) {
            entries.add(0, new FaultLogEntry(System.currentTimeMillis(), fault.address, fault.message));
            while (entries.size() > MAX_LOG_ENTRIES) {
                entries.remove(entries.size() - 1);
            }
            editor.putString(EVENTS_KEY, serializeEntries());
            editor.apply();
            notifyListeners();
            return;
        }
        editor.apply();
    }

    private void loadEntries() {
        String savedEvents = preferences.getString(EVENTS_KEY, "[]");
        if (savedEvents == null) {
            return;
        }
        try {
            JSONArray array = new JSONArray(savedEvents);
            for (int i = 0; i < array.length() && entries.size() < MAX_LOG_ENTRIES; i++) {
                JSONObject event = array.getJSONObject(i);
                entries.add(new FaultLogEntry(
                        event.getLong("timestamp"),
                        event.getInt("address"),
                        event.getString("message")));
            }
        } catch (JSONException ignored) {
            entries.clear();
        }
    }

    private String serializeEntries() {
        JSONArray array = new JSONArray();
        for (FaultLogEntry entry : entries) {
            JSONObject event = new JSONObject();
            try {
                event.put("timestamp", entry.timestamp);
                event.put("address", entry.address);
                event.put("message", entry.message);
                array.put(event);
            } catch (JSONException ignored) {
                // All values are serializable primitives; retain any previous valid entries.
            }
        }
        return array.toString();
    }

    private void notifyListeners() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onFaultLogChanged();
        }
    }

    public interface Listener {
        void onFaultLogChanged();
    }

    public static final class FaultLogEntry {
        public final long timestamp;
        public final int address;
        public final String message;

        FaultLogEntry(long timestamp, int address, String message) {
            this.timestamp = timestamp;
            this.address = address;
            this.message = message;
        }
    }

    private static final class FaultDefinition {
        final int address;
        final String message;

        FaultDefinition(int address, String message) {
            this.address = address;
            this.message = message;
        }
    }

    private static final class ReadGroup {
        final int startAddress;
        final int count;

        ReadGroup(int startAddress, int count) {
            this.startAddress = startAddress;
            this.count = count;
        }
    }
}

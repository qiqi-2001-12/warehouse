package com.warehouse.remote;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.warehouse.R;
import com.warehouse.device.DeviceGateway;
import com.warehouse.data.local.OperationLogStore;
import com.warehouse.data.local.OperationLogFormatter;
import com.warehouse.ota.AppUpdateManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps the Android device connected to the remote control server while the UI is not visible.
 * Controller operations stay behind DeviceGateway so the serial implementation remains isolated.
 */
public final class RemoteAgentService extends Service {
    private static final String TAG = "RemoteAgent";
    private static final String CHANNEL_ID = "remote_agent";
    private static final int NOTIFICATION_ID = 7_301;
    private static final long POLL_INTERVAL_MS = 5_000L;
    private static final long STALLED_CYCLE_TIMEOUT_MS = 20_000L;
    private static final long TELEMETRY_COLLECTION_TIMEOUT_MS = 10_000L;
    private static final String PREFERENCES_NAME = "remote_agent";
    private static final String ACK_IDS_KEY = "pending_ack_ids";
    private static final String ACK_KEY_PREFIX = "pending_ack_";
    private static final TelemetryRead[] TELEMETRY_READS = {
            TelemetryRead.holding(5, 2),
            TelemetryRead.holding(15, 3),
            TelemetryRead.holding(100, 1),
            TelemetryRead.holding(202, 1),
            TelemetryRead.holding(140, 20),
            TelemetryRead.holding(169, 3),
            TelemetryRead.holding(500, 8),
            TelemetryRead.holding(512, 4),
            TelemetryRead.holding(538, 1),
            TelemetryRead.holding(800, 2),
            TelemetryRead.holding(807, 4),
            TelemetryRead.holding(814, 5),
            TelemetryRead.holding(822, 5),
            TelemetryRead.holding(829, 1),
            TelemetryRead.holding(832, 1),
            TelemetryRead.holding(841, 4),
            TelemetryRead.holding(860, 7),
            TelemetryRead.holding(900, 24),
            TelemetryRead.holding(935, 3),
            TelemetryRead.holding(947, 8),
            TelemetryRead.holding(996, 1)
    };
    private static final Map<String, Integer> FACTORY_PARAMETER_ADDRESSES = createFactoryParameterAddresses();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private boolean cycleInFlight;
    private long cycleStartedAtMs;
    private RemoteAgentHttpClient httpClient;
    private SharedPreferences preferences;

    public static void start(Context context) {
        Intent intent = new Intent(context, RemoteAgentService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        RemoteAgentConfig config = new RemoteAgentConfig(getApplicationContext());
        httpClient = new RemoteAgentHttpClient(config);
        preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        long cycleAge = cycleInFlight ? SystemClock.elapsedRealtime() - cycleStartedAtMs : Long.MAX_VALUE;
        if (!cycleInFlight || cycleAge > STALLED_CYCLE_TIMEOUT_MS) {
            // The activity can bring the service back to the foreground after
            // a controller read was interrupted. Reset only a genuinely stale
            // cycle so normal starts do not create duplicate loops.
            cycleInFlight = false;
            cycleStartedAtMs = 0L;
            Log.i(TAG, "remote agent loop started");
            scheduleNextCycle(0L);
        } else {
            Log.i(TAG, "remote agent loop already running");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        networkExecutor.shutdownNow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void scheduleNextCycle(long delayMs) {
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.postDelayed(this::runCycle, delayMs);
    }

    private void runCycle() {
        if (cycleInFlight) {
            return;
        }
        cycleInFlight = true;
        cycleStartedAtMs = SystemClock.elapsedRealtime();
        sendPresenceHeartbeat(() -> flushPendingAcknowledgements(this::collectAndSendTelemetry));
    }

    private void sendPresenceHeartbeat(Runnable onComplete) {
        networkExecutor.execute(() -> {
            try {
                // Mark the agent online immediately. The full telemetry
                // heartbeat follows after controller reads complete.
                httpClient.sendHeartbeat(new JSONObject());
                Log.i(TAG, "presence heartbeat sent");
            } catch (IOException error) {
                Log.w(TAG, "presence heartbeat failed", error);
            }
            mainHandler.post(onComplete);
        });
    }

    private void collectAndSendTelemetry() {
        collectTelemetry(telemetry -> networkExecutor.execute(() -> {
            try {
                httpClient.sendHeartbeat(telemetry);
                List<RemoteAgentHttpClient.RemoteCommand> commands = httpClient.pullCommands();
                Log.i(TAG, "heartbeat sent; controllerReachable="
                        + telemetry.optBoolean("controllerReachable") + ", commands=" + commands.size());
                mainHandler.post(() -> executeCommands(commands, 0));
            } catch (IOException error) {
                Log.w(TAG, "remote heartbeat or command pull failed", error);
                mainHandler.post(this::finishCycle);
            }
        }));
    }

    private void collectTelemetry(TelemetryCallback callback) {
        DeviceGateway gateway = DeviceGateway.get(getApplicationContext());
        Map<Integer, Integer> values = new HashMap<>();
        List<String> failures = new ArrayList<>();
        boolean[] completed = {false};
        Runnable finish = () -> {
            if (completed[0]) {
                return;
            }
            completed[0] = true;
            callback.onCollected(buildTelemetry(values, failures));
        };
        Runnable timeout = () -> {
            failures.add("telemetry collection timed out");
            finish.run();
        };
        mainHandler.postDelayed(timeout, TELEMETRY_COLLECTION_TIMEOUT_MS);
        readTelemetryGroups(gateway, values, failures, completed, 0, () -> {
            mainHandler.removeCallbacks(timeout);
            finish.run();
        });
    }

    private void readTelemetryGroups(DeviceGateway gateway, Map<Integer, Integer> values,
                                     List<String> failures, boolean[] completed, int index,
                                     Runnable onComplete) {
        if (completed[0]) {
            return;
        }
        if (index >= TELEMETRY_READS.length) {
            onComplete.run();
            return;
        }
        TelemetryRead read = TELEMETRY_READS[index];
        DeviceGateway.RangeCallback callback = new DeviceGateway.RangeCallback() {
            @Override
            public void onSuccess(int startAddress, int[] rangeValues) {
                for (int offset = 0; offset < rangeValues.length; offset++) {
                    values.put(startAddress + offset, rangeValues[offset]);
                }
                readTelemetryGroups(gateway, values, failures, completed, index + 1, onComplete);
            }

            @Override
            public void onError(Exception error) {
                failures.add(read.startAddress + ": " + messageOf(error));
                readTelemetryGroups(gateway, values, failures, completed, index + 1, onComplete);
            }
        };
        if (read.coils) {
            gateway.readCoils(read.startAddress, read.count, callback);
        } else {
            gateway.readRange(read.startAddress, read.count, callback);
        }
    }

    private JSONObject buildTelemetry(Map<Integer, Integer> values, List<String> failures) {
        JSONObject telemetry = new JSONObject();
        try {
            Integer autoMode = values.get(202);
            boolean automatic = autoMode != null && autoMode == 0;
            telemetry.put("controllerReachable", !values.isEmpty());
            telemetry.put("collectedAt", System.currentTimeMillis());
            putScaledIfPresent(telemetry, "temperature", firstPresent(values, 863, 500), 863);
            putScaledIfPresent(telemetry, "humidity", firstPresent(values, 864, 501), 864);
            if (values.containsKey(515)) {
                telemetry.put("power", values.get(515) == 1);
            }
            if (autoMode != null) {
                telemetry.put("autoMode", automatic);
            }
            if (values.containsKey(100)) {
                telemetry.put("smartMode", values.get(100) == 1);
            }
            putIfPresent(telemetry, "operationMode", values.get(513));
            Integer supplyMode = automatic ? values.get(996) : values.get(141);
            if (supplyMode == null) {
                supplyMode = values.get(996);
            }
            putIfPresent(telemetry, "supplyMode", supplyMode);
            putIfPresent(telemetry, "manualSupplyMode", values.get(141));
            putIfPresent(telemetry, "automaticSupplyMode", values.get(996));
            putScaledIfPresent(telemetry, "temperatureSetting", values.get(5), 5);
            putScaledIfPresent(telemetry, "humiditySetting", values.get(6), 6);
            putIfPresent(telemetry, "supplyVolume", values.get(15));
            telemetry.put("indoor", buildIndoorMetrics(values));
            telemetry.put("airStreams", buildAirStreamMetrics(values));
            telemetry.put("equipment", buildEquipmentStatus(values));
            telemetry.put("factoryParameters", buildFactoryParameters(values));
            telemetry.put("diagnostics", buildDiagnostics(values));
            telemetry.put("faults", buildFaults(values));
            telemetry.put("app", AppUpdateManager.get(getApplicationContext()).telemetry());
            if (!failures.isEmpty()) {
                telemetry.put(values.isEmpty() ? "controllerReadError" : "telemetryWarning", failures.get(0));
            }
        } catch (JSONException error) {
            Log.w(TAG, "build telemetry failed", error);
        }
        return telemetry;
    }

    private static JSONObject buildIndoorMetrics(Map<Integer, Integer> values) throws JSONException {
        JSONObject result = new JSONObject();
        putIfPresent(result, "pm25", values.get(860));
        putIfPresent(result, "co2", values.get(861));
        putScaledIfPresent(result, "sensorStatus", values.get(862), 862);
        putScaledIfPresent(result, "temperature", values.get(863), 863);
        putScaledIfPresent(result, "humidity", values.get(864), 864);
        putScaledIfPresent(result, "absoluteHumidity", values.get(865), 865);
        putScaledIfPresent(result, "outdoorTemperature", values.get(866), 866);
        return result;
    }

    private static JSONObject buildAirStreamMetrics(Map<Integer, Integer> values) throws JSONException {
        JSONObject result = new JSONObject();
        putScaledIfPresent(result, "freshAirTemperature", values.get(500), 500);
        putScaledIfPresent(result, "freshAirHumidity", values.get(501), 501);
        putScaledIfPresent(result, "supplyAirTemperature", values.get(502), 502);
        putScaledIfPresent(result, "supplyAirHumidity", values.get(503), 503);
        putScaledIfPresent(result, "freshAirAbsoluteHumidity", values.get(506), 506);
        putScaledIfPresent(result, "supplyAirAbsoluteHumidity", values.get(507), 507);
        return result;
    }

    private static JSONObject buildEquipmentStatus(Map<Integer, Integer> values) throws JSONException {
        JSONObject result = new JSONObject();
        putIfPresent(result, "filterLife", values.get(538));
        putBooleanIfPresent(result, "lowWaterLevel", values.get(947));
        putBooleanIfPresent(result, "returnAirValve", values.get(951));
        putBooleanIfPresent(result, "humidifierPump", values.get(952));
        putBooleanIfPresent(result, "drainValve", values.get(953));
        putBooleanIfPresent(result, "makeupValve", values.get(954));
        putBooleanIfPresent(result, "exhaustFan", values.get(935));
        return result;
    }

    private static JSONObject buildFactoryParameters(Map<Integer, Integer> values) throws JSONException {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, Integer> entry : FACTORY_PARAMETER_ADDRESSES.entrySet()) {
            putScaledIfPresent(result, entry.getKey(), values.get(entry.getValue()), entry.getValue());
        }
        return result;
    }

    private static JSONObject buildDiagnostics(Map<Integer, Integer> values) throws JSONException {
        JSONObject result = new JSONObject();
        putScaledIfPresent(result, "exhaustTemperature", values.get(900), 900);
        putScaledIfPresent(result, "outdoorCoilTemperature", values.get(901), 901);
        putScaledIfPresent(result, "environmentTemperature", values.get(902), 902);
        putScaledIfPresent(result, "suctionTemperature", values.get(903), 903);
        putScaledIfPresent(result, "compressorCurrent", values.get(910), 910);
        putScaledIfPresent(result, "compressorSpeed", values.get(914), 914);
        putScaledIfPresent(result, "compressorRpm", values.get(915), 915);
        putScaledIfPresent(result, "lowPressure", values.get(918), 918);
        putScaledIfPresent(result, "highPressure", values.get(919), 919);
        putScaledIfPresent(result, "evaporationTemperature", values.get(920), 920);
        putScaledIfPresent(result, "condensingTemperature", values.get(921), 921);
        putScaledIfPresent(result, "suctionSuperheat", values.get(922), 922);
        putScaledIfPresent(result, "exhaustSuperheat", values.get(923), 923);
        return result;
    }

    private static JSONObject buildFaults(Map<Integer, Integer> values) throws JSONException {
        JSONObject result = new JSONObject();
        putBooleanIfPresent(result, "freshAirTemperatureHumiditySensor", values.get(800));
        putBooleanIfPresent(result, "supplyAirTemperatureHumiditySensor", values.get(801));
        putBooleanIfPresent(result, "makeupWaterTimeout", values.get(807));
        putBooleanIfPresent(result, "filterAlarm", values.get(808));
        putBooleanIfPresent(result, "compressorDriveCommunication", values.get(809));
        putBooleanIfPresent(result, "compressorCapacityReduction", values.get(810));
        putBooleanIfPresent(result, "unitDrainage", values.get(814));
        putBooleanIfPresent(result, "unitFrequentCapacityReduction", values.get(818));
        putBooleanIfPresent(result, "makeupWaterFreezeProtection", values.get(822));
        putBooleanIfPresent(result, "highPressureSensor", values.get(823));
        putBooleanIfPresent(result, "lowPressureSensor", values.get(824));
        putBooleanIfPresent(result, "compressor", values.get(825));
        putBooleanIfPresent(result, "condenserFan", values.get(826));
        putBooleanIfPresent(result, "unitLeakage", values.get(829));
        putBooleanIfPresent(result, "fiveInOneSensor", values.get(832));
        putBooleanIfPresent(result, "suctionTemperatureLowProtection", values.get(841));
        putBooleanIfPresent(result, "exhaustOvertemperatureProtection", values.get(842));
        putBooleanIfPresent(result, "exhaustTemperatureSensor", values.get(843));
        putBooleanIfPresent(result, "suctionTemperatureSensor", values.get(844));
        return result;
    }

    private void executeCommands(List<RemoteAgentHttpClient.RemoteCommand> commands, int index) {
        if (index >= commands.size()) {
            finishCycle();
            return;
        }
        RemoteAgentHttpClient.RemoteCommand command = commands.get(index);
        executeCommand(command, (success, errorMessage) -> {
            saveAcknowledgement(command.id, success, errorMessage);
            sendAcknowledgementThenRefresh(command.id, success, errorMessage,
                    () -> executeCommands(commands, index + 1));
        });
    }

    private void executeCommand(RemoteAgentHttpClient.RemoteCommand command, CommandCallback callback) {
        CommandCallback loggedCallback = (success, errorMessage) -> {
            if (success) {
                OperationLogStore.get(getApplicationContext()).recordCloud(
                        OperationLogFormatter.describeRemoteCommand(command.type, command.payload));
            }
            callback.onFinished(success, errorMessage);
        };
        try {
            switch (command.type) {
                case "SET_POWER":
                    writeAndVerify(515, requireBoolean(command.payload, "enabled") ? 1 : 0, loggedCallback);
                    return;
                case "SET_AUTO_MODE":
                    writeAndVerify(202, requireBoolean(command.payload, "enabled") ? 0 : 1, loggedCallback);
                    return;
                case "SET_SMART_MODE":
                    writeAndVerify(100, requireBoolean(command.payload, "enabled") ? 1 : 0, loggedCallback);
                    return;
                case "SET_OPERATION_MODE":
                    writeAndVerify(513, requireInt(command.payload, "mode"), loggedCallback);
                    return;
                case "SET_SUPPLY_MODE":
                    setManualSupplyMode(requireInt(command.payload, "mode"), loggedCallback);
                    return;
                case "SET_PARAMETERS":
                    setParameters(command.payload, loggedCallback);
                    return;
                case "SET_FACTORY_PARAMETERS":
                    setFactoryParameters(command.payload, loggedCallback);
                    return;
                case "REQUEST_APP_UPDATE":
                    startAppUpdate(command.payload, loggedCallback);
                    return;
                default:
                    callback.onFinished(false, "unsupported command type: " + command.type);
            }
        } catch (IllegalArgumentException error) {
            callback.onFinished(false, error.getMessage());
        }
    }

    private void startAppUpdate(JSONObject payload, CommandCallback callback) {
        AppUpdateManager.get(getApplicationContext()).startUpdate(
                payload,
                getString(R.string.remote_agent_device_key),
                callback::onFinished);
    }

    private void setManualSupplyMode(int mode, CommandCallback callback) {
        if (mode < 1 || mode > 3) {
            callback.onFinished(false, "supply mode must be 1, 2, or 3");
            return;
        }
        DeviceGateway.get(getApplicationContext()).readFresh(202, new DeviceGateway.IntCallback() {
            @Override
            public void onSuccess(int value) {
                if (value == 0) {
                    callback.onFinished(false, "supply mode can only be changed in manual mode");
                    return;
                }
                writeAndVerify(141, mode, callback);
            }

            @Override
            public void onError(Exception error) {
                callback.onFinished(false, "read auto mode failed: " + messageOf(error));
            }
        });
    }

    private void setParameters(JSONObject payload, CommandCallback callback) {
        List<RegisterWrite> writes = new ArrayList<>();
        if (payload.has("temperature")) {
            writes.add(new RegisterWrite(5, toRawCommandValue(5, requireInt(payload, "temperature"))));
        }
        if (payload.has("humidity")) {
            writes.add(new RegisterWrite(6, toRawCommandValue(6, requireInt(payload, "humidity"))));
        }
        if (payload.has("supplyVolume")) {
            writes.add(new RegisterWrite(15, requireInt(payload, "supplyVolume")));
        }
        if (writes.isEmpty()) {
            callback.onFinished(false, "no supported parameters supplied");
            return;
        }
        writeParameterAt(writes, 0, callback);
    }

    private void setFactoryParameters(JSONObject payload, CommandCallback callback) {
        JSONObject values = payload.optJSONObject("values");
        if (values == null) {
            callback.onFinished(false, "factory parameter values are required");
            return;
        }
        List<RegisterWrite> writes = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : FACTORY_PARAMETER_ADDRESSES.entrySet()) {
            if (values.has(entry.getKey())) {
                int address = entry.getValue();
                writes.add(new RegisterWrite(address, toRawCommandValue(address, requireInt(values, entry.getKey()))));
            }
        }
        if (writes.isEmpty()) {
            callback.onFinished(false, "no supported factory parameters supplied");
            return;
        }
        writeParameterAt(writes, 0, callback);
    }

    private void writeParameterAt(List<RegisterWrite> writes, int index, CommandCallback callback) {
        if (index >= writes.size()) {
            callback.onFinished(true, null);
            return;
        }
        RegisterWrite write = writes.get(index);
        writeAndVerify(write.address, write.value, new CommandCallback() {
            @Override
            public void onFinished(boolean success, String errorMessage) {
                if (!success) {
                    callback.onFinished(false, errorMessage);
                    return;
                }
                writeParameterAt(writes, index + 1, callback);
            }
        });
    }

    private void writeAndVerify(int address, int requestedValue, CommandCallback callback) {
        DeviceGateway gateway = DeviceGateway.get(getApplicationContext());
        if (!gateway.canWrite(address) || !gateway.canRead(address)) {
            callback.onFinished(false, "register is not readable and writable: " + address);
            return;
        }
        if (gateway.clamp(address, requestedValue) != requestedValue) {
            callback.onFinished(false, "value out of range for register " + address);
            return;
        }
        gateway.write(address, requestedValue, new DeviceGateway.WriteCallback() {
            @Override
            public void onSuccess() {
                gateway.readFresh(address, new DeviceGateway.IntCallback() {
                    @Override
                    public void onSuccess(int actualValue) {
                        if (actualValue == requestedValue) {
                            callback.onFinished(true, null);
                        } else {
                            callback.onFinished(false, "controller readback " + actualValue
                                    + " does not match " + requestedValue);
                        }
                    }

                    @Override
                    public void onError(Exception error) {
                        callback.onFinished(false, "controller readback failed: " + messageOf(error));
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                callback.onFinished(false, "controller write failed: " + messageOf(error));
            }
        });
    }

    private void sendAcknowledgementThenRefresh(long commandId, boolean success, String errorMessage,
                                                Runnable onComplete) {
        networkExecutor.execute(() -> {
            try {
                httpClient.acknowledge(commandId, success, errorMessage);
                removeAcknowledgement(commandId);
            } catch (IOException error) {
                Log.w(TAG, "command acknowledgement failed and will be retried", error);
            }
            mainHandler.post(() -> collectTelemetry(telemetry -> networkExecutor.execute(() -> {
                try {
                    httpClient.sendHeartbeat(telemetry);
                } catch (IOException error) {
                    Log.w(TAG, "post-command telemetry failed", error);
                }
                mainHandler.post(onComplete);
            })));
        });
    }

    private void flushPendingAcknowledgements(Runnable onComplete) {
        networkExecutor.execute(() -> {
            for (Long commandId : pendingAcknowledgementIds()) {
                PendingAcknowledgement acknowledgement = readAcknowledgement(commandId);
                if (acknowledgement == null) {
                    removeAcknowledgement(commandId);
                    continue;
                }
                try {
                    httpClient.acknowledge(commandId, acknowledgement.success, acknowledgement.errorMessage);
                    removeAcknowledgement(commandId);
                } catch (IOException error) {
                    Log.w(TAG, "retry command acknowledgement failed", error);
                }
            }
            mainHandler.post(onComplete);
        });
    }

    private void saveAcknowledgement(long commandId, boolean success, String errorMessage) {
        JSONObject value = new JSONObject();
        try {
            value.put("success", success);
            value.put("errorMessage", errorMessage == null ? "" : errorMessage);
        } catch (JSONException error) {
            Log.w(TAG, "save acknowledgement failed", error);
            return;
        }
        Set<String> ids = new HashSet<>(preferences.getStringSet(ACK_IDS_KEY, new HashSet<>()));
        ids.add(String.valueOf(commandId));
        preferences.edit()
                .putStringSet(ACK_IDS_KEY, ids)
                .putString(ACK_KEY_PREFIX + commandId, value.toString())
                .apply();
    }

    private List<Long> pendingAcknowledgementIds() {
        Set<String> ids = new HashSet<>(preferences.getStringSet(ACK_IDS_KEY, new HashSet<>()));
        List<Long> result = new ArrayList<>();
        for (String id : ids) {
            try {
                result.add(Long.parseLong(id));
            } catch (NumberFormatException ignored) {
                // Remove malformed saved values during the next cleanup.
            }
        }
        return result;
    }

    @Nullable
    private PendingAcknowledgement readAcknowledgement(long commandId) {
        String stored = preferences.getString(ACK_KEY_PREFIX + commandId, null);
        if (stored == null) {
            return null;
        }
        try {
            JSONObject value = new JSONObject(stored);
            return new PendingAcknowledgement(value.getBoolean("success"), value.optString("errorMessage"));
        } catch (JSONException error) {
            return null;
        }
    }

    private void removeAcknowledgement(long commandId) {
        Set<String> ids = new HashSet<>(preferences.getStringSet(ACK_IDS_KEY, new HashSet<>()));
        ids.remove(String.valueOf(commandId));
        preferences.edit()
                .putStringSet(ACK_IDS_KEY, ids)
                .remove(ACK_KEY_PREFIX + commandId)
                .apply();
    }

    private void finishCycle() {
        cycleInFlight = false;
        cycleStartedAtMs = 0L;
        scheduleNextCycle(POLL_INTERVAL_MS);
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(getString(R.string.remote_agent_notification_title))
                .setContentText(getString(R.string.remote_agent_notification_text))
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.remote_agent_channel_name), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.remote_agent_notification_text));
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private static boolean requireBoolean(JSONObject payload, String name) {
        if (!payload.has(name)) {
            throw new IllegalArgumentException("missing boolean field: " + name);
        }
        Object value = payload.opt(name);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException("invalid boolean field: " + name);
        }
        return (Boolean) value;
    }

    private static int requireInt(JSONObject payload, String name) {
        if (!payload.has(name)) {
            throw new IllegalArgumentException("missing number field: " + name);
        }
        Object value = payload.opt(name);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("invalid number field: " + name);
        }
        double number = ((Number) value).doubleValue();
        if (number != Math.rint(number)) {
            throw new IllegalArgumentException("field must be an integer: " + name);
        }
        return (int) number;
    }

    private static String messageOf(Exception error) {
        return error == null || error.getMessage() == null ? "unknown error" : error.getMessage();
    }

    private static void putIfPresent(JSONObject target, String name, Integer value) throws JSONException {
        if (value != null) {
            target.put(name, value);
        }
    }

    private static void putScaledIfPresent(JSONObject target, String name, Integer rawValue, int address)
            throws JSONException {
        if (rawValue == null) {
            return;
        }
        target.put(name, displayValue(address, rawValue));
    }

    private static Number displayValue(int address, int rawValue) {
        int scale = telemetryScale(address);
        if (scale == 1) {
            return rawValue;
        }
        double value = (double) rawValue / scale;
        return value == Math.rint(value) ? (long) value : value;
    }

    private static int telemetryScale(int address) {
        switch (address) {
            case 5:
            case 6:
            case 142:
            case 158:
            case 500:
            case 501:
            case 502:
            case 503:
            case 863:
            case 864:
            case 866:
            case 900:
            case 901:
            case 902:
            case 903:
            case 918:
            case 919:
            case 920:
            case 921:
            case 922:
            case 923:
                return 10;
            case 506:
            case 507:
            case 862:
            case 865:
            case 910:
            case 914:
            case 915:
                return 100;
            default:
                return 1;
        }
    }

    private int toRawCommandValue(int address, int displayValue) {
        DeviceGateway gateway = DeviceGateway.get(getApplicationContext());
        if (displayValue < gateway.minValue(address) || displayValue > gateway.maxValue(address)) {
            throw new IllegalArgumentException("value out of range for register " + address);
        }
        return gateway.toRawValue(address, displayValue);
    }

    private static void putBooleanIfPresent(JSONObject target, String name, Integer value) throws JSONException {
        if (value != null) {
            target.put(name, value == 1);
        }
    }

    @Nullable
    private static Integer firstPresent(Map<Integer, Integer> values, int primaryAddress, int fallbackAddress) {
        Integer primary = values.get(primaryAddress);
        return primary == null ? values.get(fallbackAddress) : primary;
    }

    private static Map<String, Integer> createFactoryParameterAddresses() {
        Map<String, Integer> addresses = new HashMap<>();
        addresses.put("supplyVolume", 15);
        addresses.put("compressorFrequencyMin", 16);
        addresses.put("compressorFrequencyMax", 17);
        addresses.put("freshAirHeaterMaxOutput", 140);
        addresses.put("preheatTemperatureSetpoint", 142);
        addresses.put("energyFanOutput", 143);
        addresses.put("energyFreshAirValve", 144);
        addresses.put("comfortFanOutput", 145);
        addresses.put("comfortFreshAirValve", 146);
        addresses.put("boostFanOutput", 147);
        addresses.put("boostFreshAirValve", 148);
        addresses.put("energyCo2Limit", 149);
        addresses.put("comfortCo2Limit", 150);
        addresses.put("energyPm25Limit", 151);
        addresses.put("comfortPm25Limit", 152);
        addresses.put("co2RiseLimit", 153);
        addresses.put("pm25RiseLimit", 154);
        addresses.put("boostFirstCheckTime", 155);
        addresses.put("boostForceToEnergyExtraTime", 156);
        addresses.put("boostExitEnergyHoldTime", 157);
        addresses.put("preheatRatioAdjust", 158);
        addresses.put("automaticSupplyStrategy", 159);
        addresses.put("exhaustValveEnergy", 169);
        addresses.put("exhaustValveComfort", 170);
        addresses.put("exhaustValveBoost", 171);
        addresses.put("compressorFrequency", 512);
        addresses.put("expansionValveStep", 524);
        addresses.put("condenserFanOutput", 525);
        return addresses;
    }

    private interface TelemetryCallback {
        void onCollected(JSONObject telemetry);
    }

    private interface CommandCallback {
        void onFinished(boolean success, String errorMessage);
    }

    private static final class RegisterWrite {
        final int address;
        final int value;

        RegisterWrite(int address, int value) {
            this.address = address;
            this.value = value;
        }
    }

    private static final class TelemetryRead {
        final int startAddress;
        final int count;
        final boolean coils;

        private TelemetryRead(int startAddress, int count, boolean coils) {
            this.startAddress = startAddress;
            this.count = count;
            this.coils = coils;
        }

        static TelemetryRead holding(int startAddress, int count) {
            return new TelemetryRead(startAddress, count, false);
        }

        static TelemetryRead coils(int startAddress, int count) {
            return new TelemetryRead(startAddress, count, true);
        }
    }

    private static final class PendingAcknowledgement {
        final boolean success;
        final String errorMessage;

        PendingAcknowledgement(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }
}

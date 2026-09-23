# Android Remote Agent

## What It Does

`com.warehouse.remote.RemoteAgentService` is the Android-side proxy for the desktop remote-control platform. It runs as a foreground service, so the connection continues while the home screen is not active.

Every 5 seconds it performs this sequence:

1. Reads live controller values through `DeviceGateway.readFresh`. Cached Modbus values are deliberately not used for remote telemetry or command verification.
2. Sends `POST /api/gateway/heartbeat` with the device serial number, name, and telemetry.
3. Pulls `GET /api/gateway/commands?serialNo=WH-001`.
4. Writes each supported command through `DeviceGateway`, reads the same register back from the real controller, then sends an acknowledgement.
5. Sends one more heartbeat so the browser sees the actual state after the command.

If a command acknowledgement cannot reach the server, it is persisted in Android `SharedPreferences` and retried on the next cycle. This prevents a command from remaining permanently `DELIVERED` after a temporary network failure.

## LAN Integration Configuration

The development values are in `app/src/main/res/values/strings.xml`:

- `remote_agent_base_url`: currently `http://192.168.60.111:8080`
- `remote_agent_device_key`: must match the server `WAREHOUSE_DEVICE_KEY`
- `remote_agent_serial_no`: currently `WH-001`
- `remote_agent_device_name`: label shown in the browser console

`network_security_config.xml` permits HTTP only for the local server IP during LAN integration. Before deployment, switch the URL to HTTPS and replace the shared device key with per-device credentials stored outside source code.

## Command Mapping

| Remote command | Controller register | Rule |
| --- | --- | --- |
| `SET_POWER` | `515` | `enabled=true` writes 1; false writes 0. |
| `SET_AUTO_MODE` | `514` | `enabled=true` writes 0 (automatic); false writes 1 (manual). |
| `SET_SUPPLY_MODE` | `141` | Only valid in manual mode. Register `996` is read-only automatic feedback and is never written. |
| `SET_PARAMETERS` | `5`, `6`, `15` | Temperature, humidity, and supply-volume settings. |

## First Joint Test

1. Start the platform at `D:\warehouse-control-platform` with `./gradlew.bat bootRun`.
2. Confirm the Android device and computer are on the same LAN, then install this APK.
3. Open `http://192.168.60.111:8080`, log in, and check that `WH-001` becomes `ONLINE`.
4. Queue a power or manual-mode command from the browser. The command should transition from `PENDING` to `DELIVERED`, then `SUCCESS` or `FAILED` only after controller readback.
5. Use `adb logcat -s RemoteAgent ModbusManager` to diagnose any connection, command, or controller-read failure.

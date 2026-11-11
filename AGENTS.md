# Project Structure Rules

Read this file before making code changes in this repository.

## Package Placement

Do not add new application classes directly to `com.warehouse` unless the class is a temporary compatibility bridge for existing package-private code.

Place new code by responsibility:

- `com.warehouse.app`: application startup and activity entry points.
- `com.warehouse.app.boot`: boot and system startup receivers.
- `com.warehouse.core.ui`: shared UI helpers with no feature-specific behavior.
- `com.warehouse.fragment.<feature>`: Fragment screens and UI code for one feature.
- `com.warehouse.device`: device-facing abstractions, repositories, and Modbus integration.
- `com.warehouse.remote`: server APIs, WebSocket clients, remote commands, and synchronization.
- `com.warehouse.ota`: remote upgrade download, verification, and installation workflows.
- `com.warehouse.data.local`: preferences, local persistence, and caches.
- `com.warehouse.domain`: feature-independent models and use cases.

Current Fragment packages:

- `com.warehouse.fragment.admin`
- `com.warehouse.fragment.fault`
- `com.warehouse.fragment.factory`
- `com.warehouse.fragment.home` is reserved for the home screen migration.

## Dependency Rules

- Fragment and UI code must not add new direct dependencies on `ModbusManager`, `ModbusTable`, `ModbusRegisterSpec`, `ModbusUi`, or RTU transport classes.
- Use `DeviceGateway` for device register access until the Modbus implementation is fully moved into `com.warehouse.device`.
- Server and remote-control code must not be placed in Fragment classes. Route it through an appropriate remote or domain layer.
- Keep Android component names in `AndroidManifest.xml` synchronized when moving activities, services, or receivers.

## Change Verification

- Use IDE refactoring or update package declarations and imports together when moving classes.
- Run `./gradlew.bat :app:assembleDebug` after package changes.
- Check `git diff --check` before handing off changes.

package com.warehouse.device;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.Nullable;

/** Public boundary between application features and the Modbus implementation. */
public final class DeviceGateway {
    private final Context appContext;

    private DeviceGateway(Context context) {
        appContext = context.getApplicationContext();
    }

    public static DeviceGateway get(Context context) {
        return new DeviceGateway(context);
    }

    public boolean canRead(int address) {
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        return spec != null && spec.canRead();
    }

    public boolean canWrite(int address) {
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        return spec != null && spec.canWrite();
    }

    public int minValue(int address) {
        return requireSpec(address).minValue;
    }

    public int maxValue(int address) {
        return requireSpec(address).maxValue;
    }

    public int clamp(int address, int value) {
        return requireSpec(address).clamp(value);
    }

    public String formatDefault(int address, int value) {
        return ModbusUi.formatDefault(requireSpec(address), value);
    }

    public int toDisplayValue(int address, int rawValue) {
        return ModbusUi.toDisplayValue(requireSpec(address), rawValue);
    }

    public int toRawValue(int address, int displayValue) {
        return ModbusUi.toRawValue(requireSpec(address), displayValue);
    }

    @Nullable
    public Integer getCachedValue(int address) {
        return ModbusManager.get(appContext).getCachedValue(address);
    }

    public void showEditor(TextView view, int address) {
        ModbusUi.showEditor(view.getContext(), view, requireSpec(address), ModbusUi::formatDefault);
    }

    public void read(int address, IntCallback callback) {
        ModbusRegisterSpec spec = findReadableSpec(address, callback);
        if (spec == null) {
            return;
        }
        ModbusManager.get(appContext).read(spec, new ModbusManager.IntCallback() {
            @Override
            public void onSuccess(int value) {
                callback.onSuccess(value);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Reads directly from the controller without falling back to a cached value.
     * Remote control uses this method so an offline controller is never reported as online.
     */
    public void readFresh(int address, IntCallback callback) {
        ModbusRegisterSpec spec = findReadableSpec(address, callback);
        if (spec == null) {
            return;
        }
        ModbusManager.get(appContext).readFresh(spec, new ModbusManager.IntCallback() {
            @Override
            public void onSuccess(int value) {
                callback.onSuccess(value);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    public void readRange(int startAddress, int count, RangeCallback callback) {
        ModbusManager.get(appContext).readRange(startAddress, count, new ModbusManager.RangeCallback() {
            @Override
            public void onSuccess(int returnedStartAddress, int[] values) {
                callback.onSuccess(returnedStartAddress, values);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    public void readCoils(int startAddress, int count, RangeCallback callback) {
        ModbusManager.get(appContext).readCoils(startAddress, count, new ModbusManager.RangeCallback() {
            @Override
            public void onSuccess(int returnedStartAddress, int[] values) {
                callback.onSuccess(returnedStartAddress, values);
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    public void write(int address, int value, WriteCallback callback) {
        ModbusRegisterSpec spec = findWritableSpec(address, callback);
        if (spec == null) {
            return;
        }
        ModbusManager.get(appContext).write(spec, value, new ModbusManager.VoidCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(Exception error) {
                callback.onError(error);
            }
        });
    }

    private ModbusRegisterSpec findReadableSpec(int address, IntCallback callback) {
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        if (spec == null || !spec.canRead()) {
            callback.onError(new IllegalArgumentException("register is not readable: " + address));
            return null;
        }
        return spec;
    }

    private ModbusRegisterSpec findWritableSpec(int address, WriteCallback callback) {
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        if (spec == null || !spec.canWrite()) {
            callback.onError(new IllegalArgumentException("register is not writable: " + address));
            return null;
        }
        return spec;
    }

    private ModbusRegisterSpec requireSpec(int address) {
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        if (spec == null) {
            throw new IllegalArgumentException("unknown register: " + address);
        }
        return spec;
    }

    public interface IntCallback {
        void onSuccess(int value);

        void onError(Exception error);
    }

    public interface WriteCallback {
        void onSuccess();

        void onError(Exception error);
    }

    public interface RangeCallback {
        void onSuccess(int startAddress, int[] values);

        void onError(Exception error);
    }
}

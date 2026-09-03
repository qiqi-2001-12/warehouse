package com.warehouse;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import java.io.Closeable;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class ModbusTable {
    private static final Map<Integer, ModbusRegisterSpec> BY_ADDRESS = new HashMap<Integer, ModbusRegisterSpec>();

    private ModbusTable() {
    }

    static ModbusRegisterSpec byAddress(int address) {
        return BY_ADDRESS.get(address);
    }

    private static void register(ModbusRegisterSpec spec) {
        BY_ADDRESS.put(spec.address, spec);
    }

    private static ModbusRegisterSpec rwInt(int address, int minValue, int maxValue, String unit) {
        return new ModbusRegisterSpec(address, ModbusRegisterSpec.Access.READ_WRITE, ModbusRegisterSpec.Kind.INTEGER, minValue, maxValue, unit, null);
    }

    private static ModbusRegisterSpec roInt(int address, int minValue, int maxValue, String unit) {
        return new ModbusRegisterSpec(address, ModbusRegisterSpec.Access.READ_ONLY, ModbusRegisterSpec.Kind.INTEGER, minValue, maxValue, unit, null);
    }

    private static ModbusRegisterSpec rwBool(int address, int minValue, int maxValue, ModbusRegisterSpec.EnumOption ... options) {
        return new ModbusRegisterSpec(address, ModbusRegisterSpec.Access.READ_WRITE, ModbusRegisterSpec.Kind.BOOLEAN, minValue, maxValue, "", options);
    }

    private static ModbusRegisterSpec roBool(int address, int minValue, int maxValue, ModbusRegisterSpec.EnumOption ... options) {
        return new ModbusRegisterSpec(address, ModbusRegisterSpec.Access.READ_ONLY, ModbusRegisterSpec.Kind.BOOLEAN, minValue, maxValue, "", options);
    }

    private static ModbusRegisterSpec faultBool(int address) {
        return ModbusTable.roBool(address, 0, 1, ModbusTable.option(0, "正常"), ModbusTable.option(1, "故障"));
    }

    private static ModbusRegisterSpec woBool(int address, ModbusRegisterSpec.EnumOption ... options) {
        return new ModbusRegisterSpec(address, ModbusRegisterSpec.Access.WRITE_ONLY, ModbusRegisterSpec.Kind.BOOLEAN, 0, 1, "", options);
    }

    private static ModbusRegisterSpec rwEnum(int address, int minValue, int maxValue, ModbusRegisterSpec.EnumOption ... options) {
        return new ModbusRegisterSpec(address, ModbusRegisterSpec.Access.READ_WRITE, ModbusRegisterSpec.Kind.ENUM, minValue, maxValue, "", options);
    }

    private static ModbusRegisterSpec roEnum(int address, int minValue, int maxValue, ModbusRegisterSpec.EnumOption ... options) {
        return new ModbusRegisterSpec(address, ModbusRegisterSpec.Access.READ_ONLY, ModbusRegisterSpec.Kind.ENUM, minValue, maxValue, "", options);
    }

    private static ModbusRegisterSpec.EnumOption option(int value, String label) {
        return new ModbusRegisterSpec.EnumOption(value, label);
    }

    static {
        ModbusTable.register(ModbusTable.rwBool(515, 0, 1, ModbusTable.option(0, "关机"), ModbusTable.option(1, "开机")));
        ModbusTable.register(ModbusTable.rwEnum(513, 0, 5, ModbusTable.option(0, "除湿"), ModbusTable.option(1, "加湿"), ModbusTable.option(2, "通风"), ModbusTable.option(3, "低温加湿"), ModbusTable.option(4, "制热"), ModbusTable.option(5, "制冷")));
        ModbusTable.register(ModbusTable.rwBool(514, 0, 1, ModbusTable.option(0, "自动"), ModbusTable.option(1, "手动")));
        ModbusTable.register(ModbusTable.rwInt(5, 10, 50, "℃"));
        ModbusTable.register(ModbusTable.rwInt(6, 30, 100, "%"));
        ModbusTable.register(ModbusTable.rwInt(15, 50, 100, "%"));
        ModbusTable.register(ModbusTable.woBool(539, ModbusTable.option(1, "恢复出厂设置")));
        ModbusTable.register(ModbusTable.woBool(566, ModbusTable.option(1, "确认")));
        ModbusTable.register(ModbusTable.woBool(799, ModbusTable.option(1, "确认")));
        ModbusTable.register(ModbusTable.faultBool(800));
        ModbusTable.register(ModbusTable.faultBool(801));
        ModbusTable.register(ModbusTable.faultBool(807));
        ModbusTable.register(ModbusTable.faultBool(808));
        ModbusTable.register(ModbusTable.faultBool(809));
        ModbusTable.register(ModbusTable.faultBool(814));
        ModbusTable.register(ModbusTable.faultBool(822));
        ModbusTable.register(ModbusTable.faultBool(823));
        ModbusTable.register(ModbusTable.faultBool(824));
        ModbusTable.register(ModbusTable.faultBool(825));
        ModbusTable.register(ModbusTable.faultBool(826));
        ModbusTable.register(ModbusTable.faultBool(829));
        ModbusTable.register(ModbusTable.faultBool(832));
        ModbusTable.register(ModbusTable.faultBool(841));
        ModbusTable.register(ModbusTable.faultBool(842));
        ModbusTable.register(ModbusTable.faultBool(843));
        ModbusTable.register(ModbusTable.faultBool(844));
        ModbusTable.register(ModbusTable.rwEnum(937, 0, 1, ModbusTable.option(0, "已确认"), ModbusTable.option(1, "确认中")));
        ModbusTable.register(ModbusTable.roInt(500, Short.MIN_VALUE, Short.MAX_VALUE, "℃"));
        ModbusTable.register(ModbusTable.roInt(501, Short.MIN_VALUE, Short.MAX_VALUE, "%"));
        ModbusTable.register(ModbusTable.roInt(502, Short.MIN_VALUE, Short.MAX_VALUE, "℃"));
        ModbusTable.register(ModbusTable.roInt(503, Short.MIN_VALUE, Short.MAX_VALUE, "%"));
        ModbusTable.register(ModbusTable.roInt(506, Short.MIN_VALUE, Short.MAX_VALUE, "g/kg"));
        ModbusTable.register(ModbusTable.roInt(507, Short.MIN_VALUE, Short.MAX_VALUE, "g/kg"));
        ModbusTable.register(ModbusTable.roInt(900, Short.MIN_VALUE, Short.MAX_VALUE, "℃"));
        ModbusTable.register(ModbusTable.roInt(901, Short.MIN_VALUE, Short.MAX_VALUE, "℃"));
        ModbusTable.register(ModbusTable.roInt(902, Short.MIN_VALUE, Short.MAX_VALUE, "℃"));
        ModbusTable.register(ModbusTable.roInt(903, Short.MIN_VALUE, Short.MAX_VALUE, "℃"));
        ModbusTable.register(ModbusTable.roInt(910, Short.MIN_VALUE, Short.MAX_VALUE, "A"));
        ModbusTable.register(ModbusTable.roInt(914, Short.MIN_VALUE, Short.MAX_VALUE, "RPS"));
        ModbusTable.register(ModbusTable.roInt(915, Short.MIN_VALUE, Short.MAX_VALUE, "RPM"));
        ModbusTable.register(ModbusTable.roInt(918, Short.MIN_VALUE, Short.MAX_VALUE, "bar"));
        ModbusTable.register(ModbusTable.roInt(919, Short.MIN_VALUE, Short.MAX_VALUE, "bar"));
        ModbusTable.register(ModbusTable.roInt(920, Short.MIN_VALUE, Short.MAX_VALUE, "℃"));
        ModbusTable.register(ModbusTable.roInt(921, Short.MIN_VALUE, Short.MAX_VALUE, "℃"));
        ModbusTable.register(ModbusTable.roInt(922, Short.MIN_VALUE, Short.MAX_VALUE, "℃"));
        ModbusTable.register(ModbusTable.roInt(923, Short.MIN_VALUE, Short.MAX_VALUE, "℃"));
        ModbusTable.register(ModbusTable.roBool(947, 0, 1, ModbusTable.option(0, "低于低液位"), ModbusTable.option(1, "高于低液位")));
        ModbusTable.register(ModbusTable.roBool(948, 0, 1, ModbusTable.option(0, "关闭"), ModbusTable.option(1, "开启")));
        ModbusTable.register(ModbusTable.roBool(950, 0, 1, ModbusTable.option(0, "关闭"), ModbusTable.option(1, "开启")));
        ModbusTable.register(ModbusTable.roBool(951, 0, 1, ModbusTable.option(0, "关闭"), ModbusTable.option(1, "开启")));
        ModbusTable.register(ModbusTable.roBool(952, 0, 1, ModbusTable.option(0, "关闭"), ModbusTable.option(1, "开启")));
        ModbusTable.register(ModbusTable.roBool(953, 0, 1, ModbusTable.option(0, "关闭"), ModbusTable.option(1, "开启")));
        ModbusTable.register(ModbusTable.roBool(954, 0, 1, ModbusTable.option(0, "关闭"), ModbusTable.option(1, "开启")));
        ModbusTable.register(ModbusTable.roBool(935, 0, 1, ModbusTable.option(0, "关闭"), ModbusTable.option(1, "开启")));
        ModbusTable.register(ModbusTable.roEnum(996, 0, 3, ModbusTable.option(0, "停机"), ModbusTable.option(1, "节能"), ModbusTable.option(2, "舒适"), ModbusTable.option(3, "强力")));
        ModbusTable.register(ModbusTable.rwBool(100, 0, 1, ModbusTable.option(0, "禁用"), ModbusTable.option(1, "启用")));
        ModbusTable.register(ModbusTable.rwInt(512, 0, 120, "RPS"));
        ModbusTable.register(ModbusTable.rwInt(524, 0, 500, "Step"));
        ModbusTable.register(ModbusTable.rwInt(525, 0, 150, "%"));
        ModbusTable.register(ModbusTable.rwInt(140, 0, 100, "%"));
        ModbusTable.register(ModbusTable.rwEnum(141, 1, 3, ModbusTable.option(1, "节能"), ModbusTable.option(2, "舒适"), ModbusTable.option(3, "强力")));
        ModbusTable.register(ModbusTable.rwInt(142, -50, 50, "℃"));
        ModbusTable.register(ModbusTable.rwInt(143, 0, 100, "%"));
        ModbusTable.register(ModbusTable.rwInt(144, 0, 100, "%"));
        ModbusTable.register(ModbusTable.rwInt(145, 0, 100, "%"));
        ModbusTable.register(ModbusTable.rwInt(146, 0, 100, "%"));
        ModbusTable.register(ModbusTable.rwInt(147, 0, 100, "%"));
        ModbusTable.register(ModbusTable.rwInt(148, 0, 100, "%"));
        ModbusTable.register(ModbusTable.rwInt(149, 0, 9999, "ppm"));
        ModbusTable.register(ModbusTable.rwInt(150, 0, 9999, "ppm"));
        ModbusTable.register(ModbusTable.rwInt(151, 0, 9999, "ug/m3"));
        ModbusTable.register(ModbusTable.rwInt(158, -50, 50, "℃"));
        ModbusTable.register(ModbusTable.rwInt(153, 0, 9999, "ppm"));
        ModbusTable.register(ModbusTable.rwInt(154, 0, 9999, "ug/m3"));
        ModbusTable.register(ModbusTable.rwInt(155, 0, 9999, "min"));
        ModbusTable.register(ModbusTable.rwInt(156, 0, 9999, "min"));
        ModbusTable.register(ModbusTable.rwInt(157, 0, 9999, "min"));
        ModbusTable.register(ModbusTable.rwInt(158, -50, 50, "℃"));
        ModbusTable.register(ModbusTable.rwEnum(159, 0, 3, ModbusTable.option(0, "自动"), ModbusTable.option(1, "节能"), ModbusTable.option(2, "舒适"), ModbusTable.option(3, "强力")));
        ModbusTable.register(ModbusTable.rwInt(169, 0, 100, "%"));
        ModbusTable.register(ModbusTable.rwInt(170, 0, 100, "%"));
        ModbusTable.register(ModbusTable.rwInt(171, 0, 100, "%"));
        ModbusTable.register(ModbusTable.roInt(860, Short.MIN_VALUE, Short.MAX_VALUE, "ug/m3"));
        ModbusTable.register(ModbusTable.roInt(861, Short.MIN_VALUE, Short.MAX_VALUE, "ppm"));
        ModbusTable.register(ModbusTable.roInt(862, Short.MIN_VALUE, Short.MAX_VALUE, ""));
        ModbusTable.register(ModbusTable.roInt(863, Short.MIN_VALUE, Short.MAX_VALUE, "℃"));
        ModbusTable.register(ModbusTable.roInt(864, Short.MIN_VALUE, Short.MAX_VALUE, "%"));
        ModbusTable.register(ModbusTable.roInt(865, Short.MIN_VALUE, Short.MAX_VALUE, "g/kg"));
        ModbusTable.register(ModbusTable.roInt(866, Short.MIN_VALUE, Short.MAX_VALUE, "℃"));
        ModbusTable.register(ModbusTable.roInt(897, Short.MIN_VALUE, Short.MAX_VALUE, ""));
        ModbusTable.register(ModbusTable.roInt(898, Short.MIN_VALUE, Short.MAX_VALUE, ""));
    }
}


final class ModbusRegisterSpec {
    final int address;
    final Access access;
    final Kind kind;
    final int minValue;
    final int maxValue;
    final String unit;
    final EnumOption[] options;
    final int readFunctionCode;
    final int writeFunctionCode;

    ModbusRegisterSpec(int address, Access access, Kind kind, int minValue, int maxValue, String unit, EnumOption[] options) {
        this(address, access, kind, minValue, maxValue, unit, options, 3, 6);
    }

    ModbusRegisterSpec(int address, Access access, Kind kind, int minValue, int maxValue, String unit, EnumOption[] options, int readFunctionCode, int writeFunctionCode) {
        this.address = address;
        this.access = access;
        this.kind = kind;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.unit = unit;
        this.options = options == null ? new EnumOption[]{} : options;
        this.readFunctionCode = readFunctionCode;
        this.writeFunctionCode = writeFunctionCode;
    }

    boolean canRead() {
        return this.access != Access.WRITE_ONLY;
    }

    boolean canWrite() {
        return this.access != Access.READ_ONLY;
    }

    boolean isEnumLike() {
        return this.kind == Kind.BOOLEAN || this.kind == Kind.ENUM;
    }

    int clamp(int value) {
        return Math.max(this.minValue, Math.min(this.maxValue, value));
    }

    int encode(int value) {
        return this.clamp(value) & 0xFFFF;
    }

    int decode(int raw) {
        if (this.minValue < 0) {
            return (short)raw;
        }
        return raw & 0xFFFF;
    }

    String labelForValue(int raw) {
        if (this.options.length > 0) {
            for (EnumOption option : this.options) {
                if (option.value != raw) continue;
                return option.label;
            }
        }
        return String.valueOf(raw);
    }

    static enum Access {
        READ_ONLY,
        WRITE_ONLY,
        READ_WRITE;

    }

    static enum Kind {
        BOOLEAN,
        ENUM,
        INTEGER;

    }

    static final class EnumOption {
        final int value;
        final String label;

        EnumOption(int value, String label) {
            this.value = value;
            this.label = label;
        }
    }
}


final class ModbusUi {
    private ModbusUi() {
    }

    static void bindValue(TextView view, ModbusRegisterSpec spec) {
        ModbusUi.bindValue(view, spec, ModbusUi::formatDefault);
    }

    static void bindValue(final TextView view, final ModbusRegisterSpec spec, final ValueFormatter formatter) {
        if (view == null || spec == null) {
            return;
        }
        Context context = view.getContext();
        view.setText((CharSequence)"--");
        view.setTag((Object)spec.minValue);
        ModbusManager.get(context).read(spec, new ModbusManager.IntCallback(){

            @Override
            public void onSuccess(int value) {
                view.setTag((Object)value);
                view.setText((CharSequence)formatter.format(spec, value));
            }

            @Override
            public void onError(Exception error) {
                if (spec.canRead()) {
                    view.setText((CharSequence)"--");
                }
            }
        });
        if (spec.canWrite()) {
            view.setClickable(true);
            view.setFocusable(true);
            view.setOnClickListener(v -> ModbusUi.showEditor(context, view, spec, formatter));
        }
    }

    static void showEditor(Context context, TextView targetView, ModbusRegisterSpec spec, ValueFormatter formatter) {
        if (spec == null || !spec.canWrite()) {
            return;
        }
        int currentValue = ModbusUi.readTagAsInt(targetView, spec.minValue);
        if (spec.isEnumLike()) {
            ModbusUi.showEnumEditor(context, targetView, spec, currentValue, formatter);
            return;
        }
        ModbusUi.showNumericEditor(context, targetView, spec, currentValue, formatter);
    }

    static String formatDefault(ModbusRegisterSpec spec, int rawValue) {
        if (spec != null && spec.isEnumLike()) {
            return spec.labelForValue(rawValue);
        }
        return String.valueOf(rawValue);
    }

    static String formatOneDecimal(ModbusRegisterSpec spec, int rawValue) {
        return String.format(Locale.CHINA, "%.1f", (double)rawValue * 1.0);
    }

    static String formatTwoDecimal(ModbusRegisterSpec spec, int rawValue) {
        return String.format(Locale.CHINA, "%.2f", (double)rawValue * 1.0);
    }

    static String formatValueWithUnit(String value, String unit) {
        if (unit == null || unit.isEmpty()) {
            return value;
        }
        return value + unit;
    }

    private static void showNumericEditor(final Context context, final TextView targetView, final ModbusRegisterSpec spec, int currentValue, final ValueFormatter formatter) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(1);
        root.setPadding(ModbusUi.dp(context, 18), ModbusUi.dp(context, 16), ModbusUi.dp(context, 18), ModbusUi.dp(context, 14));
        TextView title = new TextView(context);
        title.setText((CharSequence)("地址 " + spec.address));
        title.setTextColor(-1);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(18.0f);
        root.addView((View)title);
        TextView hint = new TextView(context);
        hint.setText((CharSequence)("范围: " + spec.minValue + " ~ " + spec.maxValue));
        hint.setTextColor(Color.rgb((int)180, (int)220, (int)230));
        hint.setTextSize(12.0f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = ModbusUi.dp(context, 6);
        root.addView((View)hint, (ViewGroup.LayoutParams)hintParams);
        EditText input = new EditText(context);
        input.setInputType(4098);
        input.setText((CharSequence)String.valueOf(currentValue));
        input.setTextColor(-1);
        input.setHintTextColor(Color.rgb((int)140, (int)180, (int)190));
        input.setPadding(ModbusUi.dp(context, 12), ModbusUi.dp(context, 10), ModbusUi.dp(context, 12), ModbusUi.dp(context, 10));
        input.setBackgroundColor(Color.rgb((int)10, (int)38, (int)54));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, -2);
        inputParams.topMargin = ModbusUi.dp(context, 10);
        root.addView((View)input, (ViewGroup.LayoutParams)inputParams);
        final AlertDialog dialog = new AlertDialog.Builder(context).setView((View)root).setNegativeButton((CharSequence)"取消", null).setPositiveButton((CharSequence)"确定", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            try {
                final int value = spec.clamp(Integer.parseInt(text));
                ModbusManager.get(context).write(spec, value, new ModbusManager.VoidCallback(){

                    @Override
                    public void onSuccess() {
                        targetView.setTag((Object)value);
                        targetView.setText((CharSequence)formatter.format(spec, value));
                        dialog.dismiss();
                    }

                    @Override
                    public void onError(Exception error) {
                        Toast.makeText((Context)context, (CharSequence)("写入失败: " + error.getMessage()), (int)0).show();
                    }
                });
            }
            catch (NumberFormatException e) {
                Toast.makeText((Context)context, (CharSequence)"请输入有效数值", (int)0).show();
            }
        }));
        dialog.show();
    }

    private static void showEnumEditor(final Context context, final TextView targetView, final ModbusRegisterSpec spec, int currentValue, final ValueFormatter formatter) {
        CharSequence[] labels = new CharSequence[spec.options.length];
        int checked = 0;
        for (int i = 0; i < spec.options.length; ++i) {
            labels[i] = spec.options[i].label;
            if (spec.options[i].value != currentValue) continue;
            checked = i;
        }
        int[] selected = new int[]{checked};
        final AlertDialog dialog = new AlertDialog.Builder(context).setTitle((CharSequence)("地址 " + spec.address)).setSingleChoiceItems(labels, checked, (d, which) -> {
            selected[0] = which;
        }).setNegativeButton((CharSequence)"取消", null).setPositiveButton((CharSequence)"确定", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(-1).setOnClickListener(v -> {
            final int value = spec.options[selected[0]].value;
            ModbusManager.get(context).write(spec, value, new ModbusManager.VoidCallback(){

                @Override
                public void onSuccess() {
                    targetView.setTag((Object)value);
                    targetView.setText((CharSequence)formatter.format(spec, value));
                    dialog.dismiss();
                }

                @Override
                public void onError(Exception error) {
                    Toast.makeText((Context)context, (CharSequence)("写入失败: " + error.getMessage()), (int)0).show();
                }
            });
        }));
        dialog.show();
    }

    private static int readTagAsInt(TextView view, int fallback) {
        Object tag = view.getTag();
        if (tag instanceof Integer) {
            return (Integer)tag;
        }
        return fallback;
    }

    private static int dp(Context context, int value) {
        return Math.round((float)value * context.getResources().getDisplayMetrics().density);
    }

    static interface ValueFormatter {
        public String format(ModbusRegisterSpec var1, int var2);
    }
}


final class ModbusManager {
    private static final String DEFAULT_DEVICE_PATH = "/dev/ttyAS2";
    private static final int DEFAULT_BAUD_RATE = 9600;
    private static final int DEFAULT_SLAVE_ID = 1;
    private static final ModbusManager INSTANCE = new ModbusManager();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private volatile Context appContext;
    private volatile String devicePath = DEFAULT_DEVICE_PATH;
    private volatile int baudRate = 9600;
    private volatile int slaveId = 1;
    @Nullable
    private ModbusRtuClient client;

    private ModbusManager() {
    }

    static ModbusManager get(Context context) {
        ModbusManager.INSTANCE.appContext = context.getApplicationContext();
        return INSTANCE;
    }

    void setDevicePath(String path) {
        if (path != null && !path.trim().isEmpty()) {
            this.devicePath = path.trim();
            this.resetClient();
        }
    }

    void setSlaveId(int slaveId) {
        if (slaveId > 0 && slaveId < 248) {
            this.slaveId = slaveId;
            this.resetClient();
        }
    }

    void read(ModbusRegisterSpec spec, IntCallback callback) {
        if (spec == null || callback == null) {
            return;
        }
        if (!spec.canRead()) {
            this.postError(callback, (Exception)new IOException("register is write only"));
            return;
        }
        this.executor.execute(() -> {
            try {
                int value = this.ensureClient().readRegister(spec.address, spec.readFunctionCode);
                this.postSuccess(callback, spec.decode(value));
            }
            catch (Exception e) {
                this.resetClient();
                this.postError(callback, e);
            }
        });
    }

    void write(ModbusRegisterSpec spec, int value, VoidCallback callback) {
        if (spec == null || callback == null) {
            return;
        }
        if (!spec.canWrite()) {
            this.postError(callback, (Exception)new IOException("register is read only"));
            return;
        }
        this.executor.execute(() -> {
            try {
                int encoded = spec.encode(value);
                this.ensureClient().writeRegister(spec.address, encoded, spec.writeFunctionCode);
                this.postSuccess(callback);
            }
            catch (Exception e) {
                this.resetClient();
                this.postError(callback, e);
            }
        });
    }

    void close() {
        this.executor.execute(() -> {
            Object object = this.lock;
            synchronized (object) {
                if (this.client != null) {
                    try {
                        this.client.close();
                    }
                    catch (IOException iOException) {
                        // empty catch block
                    }
                    this.client = null;
                }
            }
        });
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private ModbusRtuClient ensureClient() {
        Object object = this.lock;
        synchronized (object) {
            if (this.client == null) {
                this.client = new ModbusRtuClient(new ModbusSerialTransport(this.devicePath, this.baudRate), this.slaveId);
            }
            return this.client;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void resetClient() {
        Object object = this.lock;
        synchronized (object) {
            if (this.client != null) {
                try {
                    this.client.close();
                }
                catch (IOException iOException) {
                    // empty catch block
                }
                this.client = null;
            }
        }
    }

    private void postSuccess(IntCallback callback, int value) {
        this.mainHandler.post(() -> callback.onSuccess(value));
    }

    private void postError(IntCallback callback, Exception error) {
        this.mainHandler.post(() -> callback.onError(error));
    }

    private void postSuccess(VoidCallback callback) {
        this.mainHandler.post(callback::onSuccess);
    }

    private void postError(VoidCallback callback, Exception error) {
        this.mainHandler.post(() -> callback.onError(error));
    }

    static interface IntCallback {
        public void onSuccess(int var1);

        public void onError(Exception var1);
    }

    static interface VoidCallback {
        public void onSuccess();

        public void onError(Exception var1);
    }
}


final class ModbusRtuClient {
    private static final int MAX_FRAME_SIZE = 256;
    private static final int DEFAULT_TIMEOUT_MS = 1500;
    private final ModbusTransport transport;
    private final int slaveId;

    ModbusRtuClient(ModbusTransport transport, int slaveId) {
        this.transport = transport;
        this.slaveId = slaveId;
    }

    synchronized int readRegister(int address, int functionCode) throws IOException {
        int[] values = this.readRegisters(address, 1, functionCode);
        return values[0];
    }

    synchronized int[] readRegisters(int address, int count, int functionCode) throws IOException {
        if (count < 1 || count > 125) {
            throw new IOException("invalid read count: " + count);
        }
        this.transport.open();
        byte[] request = new byte[8];
        request[0] = (byte)this.slaveId;
        request[1] = (byte)functionCode;
        request[2] = (byte)(address >> 8 & 0xFF);
        request[3] = (byte)(address & 0xFF);
        request[4] = (byte)(count >> 8 & 0xFF);
        request[5] = (byte)(count & 0xFF);
        this.writeCrc(request, 6);
        this.transport.write(request, 0, request.length);
        this.sleepQuietly(25L);
        byte[] header = new byte[3];
        this.readFully(header, 0, header.length, 1500);
        this.validateHeader(header, functionCode);
        if ((header[1] & 0x80) != 0) {
            int exceptionCode = this.readExceptionCode(header);
            throw new IOException("modbus exception: " + exceptionCode);
        }
        int byteCount = header[2] & 0xFF;
        int frameLength = 3 + byteCount + 2;
        if (frameLength > 256) {
            throw new IOException("response too large");
        }
        byte[] frame = new byte[frameLength];
        System.arraycopy(header, 0, frame, 0, header.length);
        if (byteCount > 0) {
            this.readFully(frame, 3, byteCount + 2, 1500);
        } else {
            this.readFully(frame, 3, 2, 1500);
        }
        this.verifyCrc(frame, frameLength);
        int[] values = new int[count];
        for (int i = 0; i < count; ++i) {
            int index = 3 + i * 2;
            values[i] = (frame[index] & 0xFF) << 8 | frame[index + 1] & 0xFF;
        }
        return values;
    }

    synchronized void writeRegister(int address, int value, int functionCode) throws IOException {
        this.transport.open();
        byte[] request = new byte[8];
        request[0] = (byte)this.slaveId;
        request[1] = (byte)functionCode;
        request[2] = (byte)(address >> 8 & 0xFF);
        request[3] = (byte)(address & 0xFF);
        int rawValue = value & 0xFFFF;
        request[4] = (byte)(rawValue >> 8 & 0xFF);
        request[5] = (byte)(rawValue & 0xFF);
        this.writeCrc(request, 6);
        this.transport.write(request, 0, request.length);
        this.sleepQuietly(25L);
        byte[] response = new byte[8];
        this.readFully(response, 0, response.length, 1500);
        this.validateHeader(response, functionCode);
        this.verifyCrc(response, response.length);
        if (response[1] != request[1] || response[2] != request[2] || response[3] != request[3]) {
            throw new IOException("modbus write echo mismatch");
        }
    }

    synchronized void close() throws IOException {
        this.transport.close();
    }

    private void readFully(byte[] buffer, int offset, int length, int timeoutMs) throws IOException {
        int read;
        for (int total = 0; total < length; total += read) {
            read = this.transport.read(buffer, offset + total, length - total, timeoutMs);
            if (read > 0) continue;
            throw new IOException("modbus read returned no data");
        }
    }

    private void validateHeader(byte[] frame, int functionCode) throws IOException {
        if ((frame[0] & 0xFF) != this.slaveId) {
            throw new IOException("unexpected slave id: " + (frame[0] & 0xFF));
        }
        int responseFunction = frame[1] & 0xFF;
        if (responseFunction != (functionCode & 0xFF) && responseFunction != ((functionCode | 0x80) & 0xFF)) {
            throw new IOException("unexpected function code: " + responseFunction);
        }
    }

    private int readExceptionCode(byte[] header) throws IOException {
        byte[] frame = new byte[5];
        frame[0] = header[0];
        frame[1] = header[1];
        this.readFully(frame, 2, 3, 1500);
        this.verifyCrc(frame, frame.length);
        return frame[2] & 0xFF;
    }

    private void writeCrc(byte[] frame, int lengthWithoutCrc) {
        int crc = this.crc16(frame, 0, lengthWithoutCrc);
        frame[lengthWithoutCrc] = (byte)(crc & 0xFF);
        frame[lengthWithoutCrc + 1] = (byte)(crc >> 8 & 0xFF);
    }

    private void verifyCrc(byte[] frame, int length) throws IOException {
        int actual;
        if (length < 2) {
            throw new IOException("frame too short");
        }
        int expected = this.crc16(frame, 0, length - 2);
        if (expected != (actual = (frame[length - 1] & 0xFF) << 8 | frame[length - 2] & 0xFF)) {
            throw new IOException("crc mismatch");
        }
    }

    private int crc16(byte[] frame, int offset, int length) {
        int crc = 65535;
        for (int i = offset; i < offset + length; ++i) {
            crc ^= frame[i] & 0xFF;
            for (int j = 0; j < 8; ++j) {
                if ((crc & 1) != 0) {
                    crc = crc >> 1 ^ 0xA001;
                    continue;
                }
                crc >>= 1;
            }
        }
        return crc & 0xFFFF;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}


final class ModbusSerialTransport
implements ModbusTransport {
    private static final String TAG = "ModbusSerialTransport";
    private static final int READ_TIMEOUT_MS = 1500;
    private static final int WRITE_TIMEOUT_MS = 1000;
    private final String devicePath;
    private final int baudRate;
    private FileDescriptor fileDescriptor;

    ModbusSerialTransport(String devicePath, int baudRate) {
        this.devicePath = devicePath;
        this.baudRate = baudRate;
    }

    @Override
    public synchronized void open() throws IOException {
        if (this.isOpen()) {
            return;
        }
        this.applyBestEffortSerialConfig();
        try {
            this.fileDescriptor = Os.open((String)this.devicePath, (int)(OsConstants.O_RDWR | OsConstants.O_NOCTTY | OsConstants.O_NONBLOCK), (int)0);
        }
        catch (ErrnoException e) {
            throw new IOException("open serial port failed: " + this.devicePath, e);
        }
    }

    @Override
    public synchronized int read(byte[] buffer, int offset, int length, int timeoutMs) throws IOException {
        this.ensureOpen();
        long deadline = System.currentTimeMillis() + (long)Math.max(1, timeoutMs > 0 ? timeoutMs : 1500);
        int total = 0;
        while (total < length) {
            int waitMs = (int)Math.max(1L, deadline - System.currentTimeMillis());
            if (!this.waitReadable(waitMs)) {
                throw new IOException("serial read timeout");
            }
            try {
                int read = Os.read((FileDescriptor)this.fileDescriptor, (byte[])buffer, (int)(offset + total), (int)(length - total));
                if (read <= 0) continue;
                total += read;
            }
            catch (ErrnoException e) {
                if (e.errno == OsConstants.EAGAIN) continue;
                throw new IOException("serial read failed", e);
            }
        }
        return total;
    }

    @Override
    public synchronized void write(byte[] buffer, int offset, int length) throws IOException {
        this.ensureOpen();
        long deadline = System.currentTimeMillis() + 1000L;
        int written = 0;
        while (written < length) {
            try {
                int count = Os.write((FileDescriptor)this.fileDescriptor, (byte[])buffer, (int)(offset + written), (int)(length - written));
                if (count > 0) {
                    written += count;
                    continue;
                }
            }
            catch (ErrnoException e) {
                if (e.errno == OsConstants.EAGAIN) {
                    if (System.currentTimeMillis() > deadline) {
                        throw new IOException("serial write timeout");
                    }
                    this.sleepQuietly(10L);
                    continue;
                }
                throw new IOException("serial write failed", e);
            }
            if (System.currentTimeMillis() <= deadline) continue;
            throw new IOException("serial write timeout");
        }
    }

    @Override
    public synchronized boolean isOpen() {
        return this.fileDescriptor != null;
    }

    @Override
    public synchronized void close() throws IOException {
        if (this.fileDescriptor == null) {
            return;
        }
        try {
            Os.close((FileDescriptor)this.fileDescriptor);
        }
        catch (ErrnoException e) {
            throw new IOException("close serial port failed", e);
        }
        finally {
            this.fileDescriptor = null;
        }
    }

    private void ensureOpen() throws IOException {
        if (!this.isOpen()) {
            this.open();
        }
    }

    private boolean waitReadable(int timeoutMs) throws IOException {
        StructPollfd pollFd = new StructPollfd();
        pollFd.fd = this.fileDescriptor;
        pollFd.events = (short)OsConstants.POLLIN;
        try {
            int result = Os.poll((StructPollfd[])new StructPollfd[]{pollFd}, (int)timeoutMs);
            return result > 0 && (pollFd.revents & OsConstants.POLLIN) != 0;
        }
        catch (ErrnoException e) {
            throw new IOException("serial poll failed", e);
        }
    }

    private void applyBestEffortSerialConfig() {
        String[] commands = new String[]{"stty", "-F", this.devicePath, String.valueOf(this.baudRate), "cs8", "-parenb", "-cstopb", "-ixon", "-ixoff", "-crtscts", "raw"};
        String[] altCommands = new String[]{"toybox", "stty", "-F", this.devicePath, String.valueOf(this.baudRate), "cs8", "-parenb", "-cstopb", "-ixon", "-ixoff", "-crtscts", "raw"};
        if (!this.runCommand(commands) && !this.runCommand(altCommands)) {
            Log.w((String)TAG, (String)("best-effort serial config skipped for " + this.devicePath));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private boolean runCommand(String[] command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(2L, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                boolean bl = false;
                return bl;
            }
            int exit = process.exitValue();
            if (exit != 0) {
                byte[] output = this.readFully(process.getInputStream());
                Log.w((String)TAG, (String)new String(output, StandardCharsets.UTF_8));
                boolean bl = false;
                return bl;
            }
            boolean bl = true;
            return bl;
        }
        catch (Exception ignored) {
            boolean bl = false;
            return bl;
        }
        finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private byte[] readFully(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream;){
            byte[] byArray;
            try (ByteArrayOutputStream out = new ByteArrayOutputStream();){
                int read;
                byte[] buffer = new byte[256];
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                byArray = out.toByteArray();
            }
            return byArray;
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}


interface ModbusTransport
extends Closeable {
    public void open() throws IOException;

    public int read(byte[] var1, int var2, int var3, int var4) throws IOException;

    public void write(byte[] var1, int var2, int var3) throws IOException;

    public boolean isOpen();
}

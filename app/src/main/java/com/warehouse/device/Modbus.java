package com.warehouse.device;

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
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.warehouse.R;
import com.warehouse.core.ui.AdminUi;
import java.io.File;
import java.io.Closeable;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.f1reking.serialportlib.SerialPortHelper;

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
        return new ModbusRegisterSpec(address, ModbusRegisterSpec.Access.READ_ONLY,
                ModbusRegisterSpec.Kind.BOOLEAN, 0, 1, "",
                new ModbusRegisterSpec.EnumOption[] {
                        ModbusTable.option(0, "正常"), ModbusTable.option(1, "故障")
                });
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
        ModbusTable.register(ModbusTable.rwBool(202, 0, 1, ModbusTable.option(0, "自动"), ModbusTable.option(1, "手动")));
        ModbusTable.register(ModbusTable.rwInt(5, 10, 50, "℃"));
        ModbusTable.register(ModbusTable.rwInt(6, 30, 100, "%"));
        ModbusTable.register(ModbusTable.rwInt(15, 50, 100, "%"));
        ModbusTable.register(ModbusTable.rwInt(16, 0, 120, "RPS"));
        ModbusTable.register(ModbusTable.rwInt(17, 0, 120, "RPS"));
        ModbusTable.register(ModbusTable.roInt(538, 0, 100, "%"));
        ModbusTable.register(ModbusTable.woBool(539, ModbusTable.option(1, "恢复出厂设置")));
        ModbusTable.register(ModbusTable.woBool(566, ModbusTable.option(1, "确认")));
        ModbusTable.register(ModbusTable.woBool(799, ModbusTable.option(1, "确认")));
        ModbusTable.register(ModbusTable.faultBool(800));
        ModbusTable.register(ModbusTable.faultBool(801));
        ModbusTable.register(ModbusTable.faultBool(807));
        ModbusTable.register(ModbusTable.faultBool(808));
        ModbusTable.register(ModbusTable.faultBool(809));
        ModbusTable.register(ModbusTable.faultBool(810));
        ModbusTable.register(ModbusTable.faultBool(814));
        ModbusTable.register(ModbusTable.faultBool(818));
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
        ModbusTable.register(ModbusTable.roEnum(996, 0, 3, ModbusTable.option(0, "关闭"), ModbusTable.option(1, "节能"), ModbusTable.option(2, "舒适"), ModbusTable.option(3, "强力")));
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
        ModbusTable.register(ModbusTable.rwInt(152, 0, 9999, "ug/m3"));
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
    final int writeFunctionCode;

    ModbusRegisterSpec(int address, Access access, Kind kind, int minValue, int maxValue, String unit, EnumOption[] options) {
        this.address = address;
        this.access = access;
        this.kind = kind;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.unit = unit;
        this.options = options == null ? new EnumOption[]{} : options;
        this.writeFunctionCode = 6;
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
        // Writable scaled registers receive raw values after UI conversion.
        // Accept those raw values without clamping them to the display range.
        int scale = ModbusUi.displayScale(this.address);
        if (scale > 1 && (value < this.minValue || value > this.maxValue)) {
            long rawMin = (long) this.minValue * scale;
            long rawMax = (long) this.maxValue * scale;
            if (value >= rawMin && value <= rawMax) {
                return value;
            }
        }
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
        Integer cachedValue = ModbusManager.get(context).getCachedValue(spec.address);
        if (cachedValue != null) {
            view.setText((CharSequence)formatter.format(spec, cachedValue));
            view.setTag((Object)cachedValue);
        } else {
            view.setText((CharSequence)"--");
            view.setTag((Object)ModbusUi.toRawValue(spec, spec.minValue));
        }
        ModbusManager.get(context).read(spec, new ModbusManager.IntCallback(){

            @Override
            public void onSuccess(int value) {
                view.setTag((Object)value);
                view.setText((CharSequence)formatter.format(spec, value));
            }

            @Override
            public void onError(Exception error) {
                if (spec.canRead() && ModbusManager.get(context).getCachedValue(spec.address) == null) {
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
        int currentValue = ModbusUi.readTagAsInt(targetView, ModbusUi.toRawValue(spec, spec.minValue));
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
        if (spec != null && (spec.address == 918 || spec.address == 919)) {
            return String.format(Locale.CHINA, "%.1f", (double) rawValue / 10.0);
        }
        if (spec != null && spec.address == 158) {
            return formatScaledDecimal(rawValue, 10);
        }
        if (spec != null) {
            int scale = displayScale(spec.address);
            if (scale > 1) {
                return String.valueOf(Math.round((double) rawValue / scale));
            }
        }
        return String.valueOf(rawValue);
    }

    /** Keep raw fixed-point values in storage while displaying whole engineering units. */
    static int displayScale(int address) {
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
            case 920:
            case 921:
            case 922:
            case 923:
                return 10;
            case 910:
            case 914:
            case 915:
            case 506:
            case 507:
            case 862:
            case 865:
                return 100;
            default:
                return 1;
        }
    }

    private static String formatScaledDecimal(int rawValue, int scale) {
        double value = (double) rawValue / scale;
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.CHINA, "%.1f", value);
    }

    static int toDisplayValue(ModbusRegisterSpec spec, int rawValue) {
        if (spec == null) {
            return rawValue;
        }
        int scale = displayScale(spec.address);
        return scale > 1 ? Math.round((float) rawValue / scale) : rawValue;
    }

    static int toRawValue(ModbusRegisterSpec spec, int displayValue) {
        if (spec == null) {
            return displayValue;
        }
        int scale = displayScale(spec.address);
        return scale > 1 ? Math.round((float) displayValue * scale) : displayValue;
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
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(ModbusUi.dp(context, 16), ModbusUi.dp(context, 16), ModbusUi.dp(context, 16), ModbusUi.dp(context, 16));
        root.setBackgroundColor(Color.BLACK);

        TextView title = AdminUi.text(context, "\u53c2\u6570\u8bbe\u7f6e", 20, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        String rangeText = "\u5141\u8bb8\u8303\u56f4 " + spec.minValue + " - " + spec.maxValue;
        if (spec.unit != null && !spec.unit.isEmpty()) {
            rangeText += " " + spec.unit;
        }
        TextView range = AdminUi.text(context, rangeText, 14, AdminUi.TEXT_SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(-1, -2);
        rangeParams.topMargin = ModbusUi.dp(context, 4);
        root.addView(range, rangeParams);

        View divider = new View(context);
        divider.setBackgroundColor(AdminUi.PANEL_STROKE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, ModbusUi.dp(context, 1));
        dividerParams.topMargin = ModbusUi.dp(context, 12);
        root.addView(divider, dividerParams);

        EditText input = new EditText(context);
        input.setInputType(4098);
        input.setText((CharSequence)String.valueOf(ModbusUi.toDisplayValue(spec, currentValue)));
        AdminUi.styleCompactNumericInput(input);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                AdminUi.compactNumericInputWidth(context),
                AdminUi.compactNumericInputHeight(context));
        inputParams.topMargin = ModbusUi.dp(context, 14);
        root.addView((View)input, (ViewGroup.LayoutParams)inputParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = ModbusUi.dp(context, 16);
        root.addView(actions, actionsParams);

        Context dialogContext = new ContextThemeWrapper(context, R.style.BlackInputDialogTheme);
        final AlertDialog dialog = new AlertDialog.Builder(dialogContext).setView((View)root).create();

        TextView cancel = ModbusUi.createEditorActionButton(context, "\u53d6\u6d88", AdminUi.TEXT_SECONDARY, Color.rgb(12, 53, 73));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, ModbusUi.dp(context, 46), 1));
        View gap = new View(context);
        actions.addView(gap, new LinearLayout.LayoutParams(ModbusUi.dp(context, 10), 1));
        TextView confirm = ModbusUi.createEditorActionButton(context, "\u786e\u5b9a", AdminUi.PAGE_BG, AdminUi.ACCENT);
        actions.addView(confirm, new LinearLayout.LayoutParams(0, ModbusUi.dp(context, 46), 1));

        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            String text = input.getText() == null ? "" : input.getText().toString().trim();
            try {
                int displayValue = spec.clamp(Integer.parseInt(text));
                final int value = ModbusUi.toRawValue(spec, displayValue);
                ModbusUi.updateLocalValue(targetView, spec, formatter, value);
                dialog.dismiss();
                ModbusManager.get(context).write(spec, value, new ModbusManager.VoidCallback(){

                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(Exception error) {
                    }
                });
            }
            catch (NumberFormatException e) {
                Toast.makeText((Context)context, (CharSequence)"\u8bf7\u8f93\u5165\u6709\u6548\u6570\u503c", (int)0).show();
            }
        });
        dialog.setOnShowListener(d -> ModbusUi.styleEditorDialog(dialog, context));
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
        Context dialogContext = new ContextThemeWrapper(context, R.style.BlackInputDialogTheme);
        final AlertDialog dialog = new AlertDialog.Builder(dialogContext).setSingleChoiceItems(labels, checked, (d, which) -> {
            selected[0] = which;
        }).setNegativeButton((CharSequence)"取消", null).setPositiveButton((CharSequence)"确定", null).create();
        dialog.setOnShowListener(d -> {
            ModbusUi.styleEditorDialog(dialog, context);
            dialog.getButton(-1).setOnClickListener(v -> {
                final int value = spec.options[selected[0]].value;
                ModbusUi.updateLocalValue(targetView, spec, formatter, value);
                dialog.dismiss();
                ModbusManager.get(context).write(spec, value, new ModbusManager.VoidCallback(){

                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onError(Exception error) {
                    }
                });
            });
        });
        dialog.show();
    }

    private static void styleEditorDialog(AlertDialog dialog, Context context) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    context.getDrawable(R.drawable.input_dialog_black_bg));
            dialog.getWindow().setLayout(
                    AdminUi.compactDialogWidth(context),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        if (dialog.getButton(-1) != null) {
            dialog.getButton(-1).setTextColor(AdminUi.ACCENT);
        }
        if (dialog.getButton(-2) != null) {
            dialog.getButton(-2).setTextColor(AdminUi.TEXT_SECONDARY);
        }
        int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(AdminUi.TEXT_PRIMARY);
        }
        if (dialog.getListView() != null) {
            dialog.getListView().setBackgroundColor(Color.BLACK);
            dialog.getListView().post(() -> {
                for (int i = 0; i < dialog.getListView().getChildCount(); i++) {
                    View item = dialog.getListView().getChildAt(i);
                    if (item instanceof TextView) {
                        ((TextView) item).setTextColor(AdminUi.TEXT_PRIMARY);
                    }
                }
            });
        }
    }

    private static TextView createEditorActionButton(Context context, String text, int textColor, int backgroundColor) {
        TextView button = AdminUi.text(context, text, 16, textColor, Typeface.BOLD);
        button.setBackground(AdminUi.bg(backgroundColor, 4, AdminUi.PANEL_STROKE, 1, context));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private static void updateLocalValue(TextView targetView, ModbusRegisterSpec spec, ValueFormatter formatter, int value) {
        if (targetView == null) {
            return;
        }
        targetView.setTag((Object)value);
        targetView.setText((CharSequence)formatter.format(spec, value));
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
    private static final String TAG = "ModbusManager";
    private static final String DEFAULT_DEVICE_PATH = "/dev/ttyS1";
    private static final int DEFAULT_BAUD_RATE = 9600;
    private static final int DEFAULT_SLAVE_ID = 1;
    private static final ModbusManager INSTANCE = new ModbusManager();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L,
            TimeUnit.MILLISECONDS, new PriorityBlockingQueue<Runnable>());
    private final AtomicLong taskSequence = new AtomicLong();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final Map<Integer, Integer> localValues = new ConcurrentHashMap<Integer, Integer>();
    private volatile Context appContext;
    private volatile String devicePath = DEFAULT_DEVICE_PATH;
    private volatile int baudRate = DEFAULT_BAUD_RATE;
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

    @Nullable
    Integer getCachedValue(int address) {
        return this.localValues.get(address);
    }

    void read(ModbusRegisterSpec spec, IntCallback callback) {
        if (spec == null || callback == null) {
            return;
        }
        if (!spec.canRead()) {
            this.postError(callback, (Exception)new IOException("register is write only"));
            return;
        }
        this.executeRead(() -> {
            try {
                int value = spec.decode(this.ensureClient().readRegister(spec.address));
                this.localValues.put(spec.address, value);
                this.postSuccess(callback, value);
            }
            catch (Exception e) {
                Log.e(TAG, "read failed"
                        + " device=" + this.devicePath
                        + " baud=" + this.baudRate
                        + " slave=" + this.slaveId
                        + " address=" + spec.address
                        + " fc=3", e);
                this.resetClient();
                Integer localValue = this.localValues.get(spec.address);
                if (localValue != null) {
                    this.postSuccess(callback, localValue);
                } else {
                    this.postError(callback, e);
                }
            }
        });
    }

    void readFresh(ModbusRegisterSpec spec, IntCallback callback) {
        if (spec == null || callback == null) {
            return;
        }
        if (!spec.canRead()) {
            this.postError(callback, (Exception)new IOException("register is write only"));
            return;
        }
        this.executeRead(() -> {
            try {
                int value = spec.decode(this.ensureClient().readRegister(spec.address));
                this.localValues.put(spec.address, value);
                this.postSuccess(callback, value);
            }
            catch (Exception e) {
                Log.e(TAG, "fresh read failed"
                        + " device=" + this.devicePath
                        + " baud=" + this.baudRate
                        + " slave=" + this.slaveId
                        + " address=" + spec.address
                        + " fc=3", e);
                this.resetClient();
                this.postError(callback, e);
            }
        });
    }

    void readRange(int startAddress, int count, RangeCallback callback) {
        if (callback == null) {
            return;
        }
        if (startAddress < 0 || count < 1 || count > 125) {
            this.postError(callback, (Exception)new IOException("invalid read range"));
            return;
        }
        this.executeRead(() -> {
            try {
                int[] rawValues = this.ensureClient().readRegisters(startAddress, count, 3);
                int[] values = new int[rawValues.length];
                for (int i = 0; i < rawValues.length; i++) {
                    int address = startAddress + i;
                    values[i] = this.decodeAddressValue(address, rawValues[i]);
                    this.localValues.put(address, values[i]);
                }
                this.postSuccess(callback, startAddress, values);
            }
            catch (Exception e) {
                Log.e(TAG, "range read failed"
                        + " device=" + this.devicePath
                        + " baud=" + this.baudRate
                        + " slave=" + this.slaveId
                        + " start=" + startAddress
                        + " count=" + count, e);
                this.resetClient();
                int[] cachedValues = this.readCachedRange(startAddress, count);
                if (cachedValues != null) {
                    this.postSuccess(callback, startAddress, cachedValues);
                } else {
                    this.postError(callback, e);
                }
            }
        });
    }

    void readCoils(int startAddress, int count, RangeCallback callback) {
        if (callback == null) {
            return;
        }
        if (startAddress < 0 || count < 1 || count > 2000) {
            this.postError(callback, (Exception)new IOException("invalid coil count"));
            return;
        }
        this.executeRead(() -> {
            try {
                int[] rawValues = this.ensureClient().readCoils(startAddress, count);
                int[] values = new int[rawValues.length];
                for (int i = 0; i < rawValues.length; i++) {
                    int address = startAddress + i;
                    values[i] = this.decodeAddressValue(address, rawValues[i]);
                    this.localValues.put(address, values[i]);
                }
                this.postSuccess(callback, startAddress, values);
            }
            catch (Exception e) {
                Log.e(TAG, "coil read failed"
                        + " device=" + this.devicePath
                        + " baud=" + this.baudRate
                        + " slave=" + this.slaveId
                        + " start=" + startAddress
                        + " count=" + count, e);
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
        this.localValues.put(spec.address, spec.clamp(value));
        this.executeWrite(() -> {
            try {
                int encoded = spec.encode(value);
                this.ensureClient().writeRegister(spec.address, encoded, spec.writeFunctionCode);
                this.postSuccess(callback);
            }
            catch (Exception e) {
                Log.e(TAG, "write failed"
                        + " device=" + this.devicePath
                        + " baud=" + this.baudRate
                        + " slave=" + this.slaveId
                        + " address=" + spec.address
                        + " value=" + value
                        + " fc=" + spec.writeFunctionCode, e);
                this.resetClient();
                this.postError(callback, e);
            }
        });
    }

    void close() {
        this.executeRead(() -> {
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

    private void executeRead(Runnable task) {
        this.executor.execute(new SerialTask(0, this.taskSequence.getAndIncrement(), task));
    }

    private void executeWrite(Runnable task) {
        this.executor.execute(new SerialTask(1, this.taskSequence.getAndIncrement(), task));
    }

    private static final class SerialTask implements Runnable, Comparable<SerialTask> {
        private final int priority;
        private final long sequence;
        private final Runnable task;

        SerialTask(int priority, long sequence, Runnable task) {
            this.priority = priority;
            this.sequence = sequence;
            this.task = task;
        }

        @Override
        public void run() {
            task.run();
        }

        @Override
        public int compareTo(SerialTask other) {
            int priorityComparison = Integer.compare(other.priority, priority);
            return priorityComparison != 0
                    ? priorityComparison : Long.compare(sequence, other.sequence);
        }
    }

    private int decodeAddressValue(int address, int rawValue) {
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        if (spec == null) {
            return rawValue & 0xFFFF;
        }
        return spec.decode(rawValue);
    }

    @Nullable
    private int[] readCachedRange(int startAddress, int count) {
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            Integer value = this.localValues.get(startAddress + i);
            if (value == null) {
                return null;
            }
            values[i] = value;
        }
        return values;
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

    private void postSuccess(RangeCallback callback, int startAddress, int[] values) {
        this.mainHandler.post(() -> callback.onSuccess(startAddress, values));
    }

    private void postError(RangeCallback callback, Exception error) {
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

    static interface RangeCallback {
        public void onSuccess(int startAddress, int[] values);

        public void onError(Exception var1);
    }
}


final class ModbusRtuClient {
    private static final int MAX_FRAME_SIZE = 256;
    private static final int DEFAULT_TIMEOUT_MS = 500;
    private final ModbusTransport transport;
    private final int slaveId;

    ModbusRtuClient(ModbusTransport transport, int slaveId) {
        this.transport = transport;
        this.slaveId = slaveId;
    }

    synchronized int readRegister(int address) throws IOException {
        int[] values = this.readRegisters(address, 1, 3);
        return values[0];
    }

    synchronized int[] readCoils(int address, int count) throws IOException {
        if (count < 1 || count > 2000) {
            throw new IOException("invalid coil count: " + count);
        }
        this.transport.open();
        this.transport.discardInput();
        byte[] request = new byte[8];
        request[0] = (byte)this.slaveId;
        request[1] = 1;
        request[2] = (byte)(address >> 8 & 0xFF);
        request[3] = (byte)(address & 0xFF);
        request[4] = (byte)(count >> 8 & 0xFF);
        request[5] = (byte)(count & 0xFF);
        this.writeCrc(request, 6);
        this.transport.write(request, 0, request.length);
        this.transport.settleAfterWrite(request.length);
        byte[] header = new byte[3];
        this.readFully(header, 0, header.length, DEFAULT_TIMEOUT_MS);
        this.validateHeader(header, 1);
        if ((header[1] & 0x80) != 0) {
            throw new IOException("modbus exception: " + this.readExceptionCode(header));
        }
        int byteCount = header[2] & 0xFF;
        int expectedByteCount = (count + 7) / 8;
        if (byteCount != expectedByteCount) {
            throw new IOException("unexpected coil byte count: " + byteCount);
        }
        byte[] frame = new byte[3 + byteCount + 2];
        System.arraycopy(header, 0, frame, 0, header.length);
        this.readFully(frame, header.length, frame.length - header.length, DEFAULT_TIMEOUT_MS);
        this.verifyCrc(frame, frame.length);
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = (frame[3 + i / 8] >> (i % 8)) & 1;
        }
        return values;
    }

    synchronized int[] readRegisters(int address, int count, int functionCode) throws IOException {
        if (count < 1 || count > 125) {
            throw new IOException("invalid read count: " + count);
        }
        this.transport.open();
        this.transport.discardInput();
        byte[] request = new byte[8];
        request[0] = (byte)this.slaveId;
        request[1] = (byte)functionCode;
        request[2] = (byte)(address >> 8 & 0xFF);
        request[3] = (byte)(address & 0xFF);
        request[4] = (byte)(count >> 8 & 0xFF);
        request[5] = (byte)(count & 0xFF);
        this.writeCrc(request, 6);
        this.transport.write(request, 0, request.length);
        this.transport.settleAfterWrite(request.length);
        byte[] header = new byte[3];
        this.readFully(header, 0, header.length, DEFAULT_TIMEOUT_MS);
        this.validateHeader(header, functionCode);
        if ((header[1] & 0x80) != 0) {
            int exceptionCode = this.readExceptionCode(header);
            throw new IOException("modbus exception: " + exceptionCode);
        }
        int byteCount = header[2] & 0xFF;
        if (byteCount != count * 2) {
            throw new IOException("unexpected byte count: " + byteCount);
        }
        int frameLength = 3 + byteCount + 2;
        if (frameLength > 256) {
            throw new IOException("response too large");
        }
        byte[] frame = new byte[frameLength];
        System.arraycopy(header, 0, frame, 0, header.length);
        if (byteCount > 0) {
            this.readFully(frame, 3, byteCount + 2, DEFAULT_TIMEOUT_MS);
        } else {
            this.readFully(frame, 3, 2, DEFAULT_TIMEOUT_MS);
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
        this.transport.discardInput();
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
        this.transport.settleAfterWrite(request.length);
        byte[] header = new byte[3];
        this.readFully(header, 0, header.length, DEFAULT_TIMEOUT_MS);
        this.validateHeader(header, functionCode);
        if ((header[1] & 0x80) != 0) {
            throw new IOException("modbus exception: " + this.readExceptionCode(header));
        }
        byte[] response = new byte[8];
        System.arraycopy(header, 0, response, 0, header.length);
        this.readFully(response, header.length, response.length - header.length, DEFAULT_TIMEOUT_MS);
        this.verifyCrc(response, response.length);
        if (response[1] != request[1]
                || response[2] != request[2]
                || response[3] != request[3]
                || response[4] != request[4]
                || response[5] != request[5]) {
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
        System.arraycopy(header, 0, frame, 0, header.length);
        this.readFully(frame, header.length, frame.length - header.length, DEFAULT_TIMEOUT_MS);
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
    private static final boolean DEBUG_FRAMES = true;
    private static final int READ_TIMEOUT_MS = 1500;
    private static final int WRITE_TIMEOUT_MS = 1000;
    private static final int BITS_PER_CHAR = 10;
    private static final int PRE_WRITE_DISCARD_WINDOW_MS = 1;
    private static final int POST_WRITE_DISCARD_WINDOW_MS = 1;
    private static final int POST_WRITE_MARGIN_MS = 2;
    private static final int DATA_BITS = 8;
    private static final int STOP_BITS = 1;
    private static final int PARITY = 0;
    private static final int FLOW_CONTROL = 0;
    private static final int OPEN_FLAGS = 0;
    private static final String RS485_DIRECTION_PATH = "/sys/class/gpio/gpio239/value";
    private static final String RS485_DIRECTION_MODE_PATH = "/sys/class/gpio/gpio239/direction";
    private static final String RS485_TX_VALUE = "1";
    private static final String RS485_RX_VALUE = "0";
    @Nullable
    private static Method nativeOpenMethod;
    private final String devicePath;
    private final int baudRate;
    private final SerialPortHelper serialPortHelper = new SerialPortHelper();
    private FileDescriptor fileDescriptor;
    private boolean directionControlAvailable;

    ModbusSerialTransport(String devicePath, int baudRate) {
        this.devicePath = devicePath;
        this.baudRate = baudRate;
    }

    @Override
    public synchronized void open() throws IOException {
        if (this.isOpen()) {
            return;
        }
        try {
            this.applyBestEffortSerialConfig();
            this.fileDescriptor = this.openWithNativeDriver();
            this.prepareDirectionControl();
            this.setReceiveMode();
            Log.i(TAG, "opened serial port " + this.devicePath + " @" + baudRate);
        } catch (Exception e) {
            throw new IOException("open serial port failed: " + this.devicePath, e);
        }
    }

    @Override
    public synchronized int read(byte[] buffer, int offset, int length, int timeoutMs) throws IOException {
        this.ensureOpen();
        this.setReceiveMode();
        long deadline = System.currentTimeMillis() + (long)Math.max(1, timeoutMs > 0 ? timeoutMs : READ_TIMEOUT_MS);
        int total = 0;
        while (total < length) {
            int waitMs = (int)Math.max(1L, deadline - System.currentTimeMillis());
            if (!this.waitReadable(waitMs)) {
                if (DEBUG_FRAMES) {
                    Log.d(TAG, "read timeout waiting for " + length + " bytes, received=" + total);
                }
                throw new IOException("serial read timeout");
            }
            try {
                int read = Os.read(this.fileDescriptor, buffer, offset + total, length - total);
                if (read <= 0) {
                    continue;
                }
                if (DEBUG_FRAMES) {
                    Log.d(TAG, "received " + read + " bytes: " + toHex(buffer, offset + total, read));
                }
                total += read;
            } catch (ErrnoException e) {
                if (e.errno == OsConstants.EAGAIN) {
                    continue;
                }
                throw new IOException("serial read failed", e);
            }
        }
        return total;
    }

    @Override
    public synchronized void write(byte[] buffer, int offset, int length) throws IOException {
        this.ensureOpen();
        this.setTransmitMode();
        long deadline = System.currentTimeMillis() + WRITE_TIMEOUT_MS;
        int written = 0;
        while (written < length) {
            try {
                int count = Os.write(this.fileDescriptor, buffer, offset + written, length - written);
                if (count > 0) {
                    if (DEBUG_FRAMES) {
                        Log.d(TAG, "sent " + count + " bytes: " + toHex(buffer, offset + written, count));
                    }
                    written += count;
                    continue;
                }
            } catch (ErrnoException e) {
                if (e.errno == OsConstants.EAGAIN) {
                    if (System.currentTimeMillis() > deadline) {
                        throw new IOException("serial write timeout");
                    }
                    this.sleepQuietly(10L);
                    continue;
                }
                throw new IOException("serial write failed", e);
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IOException("serial write timeout");
            }
        }
    }

    @Override
    public synchronized void discardInput() throws IOException {
        this.ensureOpen();
        this.setReceiveMode();
        this.discardReadableBytes(PRE_WRITE_DISCARD_WINDOW_MS, "before tx");
    }

    @Override
    public synchronized void settleAfterWrite(int byteCount) throws IOException {
        this.ensureOpen();
        this.sleepQuietly(this.estimateTransmitMillis(byteCount) + POST_WRITE_MARGIN_MS);
        this.setReceiveMode();
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
            Os.close(this.fileDescriptor);
        } catch (ErrnoException e) {
            throw new IOException("close serial port failed", e);
        } finally {
            this.setReceiveMode();
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
        pollFd.events = (short) OsConstants.POLLIN;
        try {
            int result = Os.poll(new StructPollfd[]{pollFd}, timeoutMs);
            return result > 0 && (pollFd.revents & OsConstants.POLLIN) != 0;
        } catch (ErrnoException e) {
            throw new IOException("serial poll failed", e);
        }
    }

    private FileDescriptor openWithNativeDriver() throws Exception {
        Method method = nativeOpenMethod;
        if (method == null) {
            method = SerialPortHelper.class.getDeclaredMethod("nativeOpen",
                    String.class, int.class, int.class, int.class, int.class, int.class, int.class);
            method.setAccessible(true);
            nativeOpenMethod = method;
        }
        Object result = method.invoke(null, this.devicePath, this.baudRate, STOP_BITS, DATA_BITS,
                PARITY, FLOW_CONTROL, OPEN_FLAGS);
        if (!(result instanceof FileDescriptor)) {
            throw new IOException("nativeOpen returned invalid descriptor");
        }
        return (FileDescriptor) result;
    }

    private void discardReadableBytes(int windowMs, String reason) throws IOException {
        byte[] scratch = new byte[256];
        int total = 0;
        while (this.waitReadable(windowMs)) {
            try {
                int read = Os.read(this.fileDescriptor, scratch, 0, scratch.length);
                if (read <= 0) {
                    break;
                }
                total += read;
                if (DEBUG_FRAMES) {
                    Log.d(TAG, "discarded " + read + " bytes " + reason + ": " + toHex(scratch, 0, read));
                }
            } catch (ErrnoException e) {
                if (e.errno == OsConstants.EAGAIN) {
                    break;
                }
                throw new IOException("serial discard failed", e);
            }
        }
        if (DEBUG_FRAMES && total > 0) {
            Log.d(TAG, "discarded total " + total + " buffered bytes " + reason);
        }
    }

    private void applyBestEffortSerialConfig() {
        String[] commands = new String[]{"stty", "-F", this.devicePath, String.valueOf(this.baudRate),
                "cs8", "-parenb", "-cstopb", "-ixon", "-ixoff", "-crtscts", "raw"};
        String[] altCommands = new String[]{"toybox", "stty", "-F", this.devicePath, String.valueOf(this.baudRate),
                "cs8", "-parenb", "-cstopb", "-ixon", "-ixoff", "-crtscts", "raw"};
        if (!this.runCommand(commands) && !this.runCommand(altCommands)) {
            Log.w(TAG, "best-effort serial config skipped for " + this.devicePath);
        }
    }

    private boolean runCommand(String[] command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(2L, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                return false;
            }
            int exit = process.exitValue();
            if (exit != 0) {
                byte[] output = this.readFully(process.getInputStream());
                Log.w(TAG, new String(output, StandardCharsets.UTF_8));
                return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private byte[] readFully(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private void prepareDirectionControl() {
        File valueFile = new File(RS485_DIRECTION_PATH);
        this.directionControlAvailable = valueFile.exists() && valueFile.canWrite();
        if (!this.directionControlAvailable) {
            return;
        }
        File directionFile = new File(RS485_DIRECTION_MODE_PATH);
        if (directionFile.canWrite()) {
            this.writeSysfsQuietly(RS485_DIRECTION_MODE_PATH, "out");
        }
        Log.i(TAG, "RS485 direction control enabled: " + RS485_DIRECTION_PATH);
    }

    private void setTransmitMode() {
        if (this.directionControlAvailable) {
            this.writeSysfsQuietly(RS485_DIRECTION_PATH, RS485_TX_VALUE);
        }
    }

    private void setReceiveMode() {
        if (this.directionControlAvailable) {
            this.writeSysfsQuietly(RS485_DIRECTION_PATH, RS485_RX_VALUE);
        }
    }

    private void writeSysfsQuietly(String path, String value) {
        try (FileOutputStream output = new FileOutputStream(path, false)) {
            output.write(value.getBytes(StandardCharsets.US_ASCII));
            output.flush();
        } catch (IOException e) {
            Log.w(TAG, "write sysfs failed: " + path, e);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long estimateTransmitMillis(int byteCount) {
        long baud = Math.max(1, this.baudRate);
        long bits = (long)Math.max(1, byteCount) * BITS_PER_CHAR;
        return Math.max(1L, (bits * 1000L + baud - 1L) / baud);
    }

    private static String toHex(byte[] buffer, int offset, int length) {
        if (buffer == null || length <= 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder(length * 3);
        for (int i = 0; i < length; ++i) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(String.format(Locale.US, "%02X", buffer[offset + i] & 0xFF));
        }
        return builder.toString();
    }
}


interface ModbusTransport
extends Closeable {
    public void open() throws IOException;

    public int read(byte[] var1, int var2, int var3, int var4) throws IOException;

    public void write(byte[] var1, int var2, int var3) throws IOException;

    public void discardInput() throws IOException;

    public void settleAfterWrite(int var1) throws IOException;

    public boolean isOpen();
}

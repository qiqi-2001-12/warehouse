package com.warehouse.data.local;

import java.util.Locale;

/** Builds human-readable operation messages without exposing Modbus register addresses. */
public final class OperationLogFormatter {
    private OperationLogFormatter() {
    }

    public static String describeHomeControl(int address, int value) {
        return describe(address, value);
    }

    public static String describeSetting(int address, int value) {
        return describe(address, value);
    }

    public static String describeParameter(int address, String value) {
        String label = labelFor(address);
        return label + "：" + value;
    }

    public static String describeParameter(int address, int value) {
        return describe(address, value);
    }

    public static String describeRemoteCommand(String type, org.json.JSONObject payload) {
        if ("REQUEST_APP_UPDATE".equals(type)) {
            String version = payload == null ? "" : payload.optString("versionName", "");
            return version.isEmpty() ? "远程升级：开始升级" : "远程升级：升级到版本 " + version;
        }
        if ("SET_POWER".equals(type)) {
            return describe(515, payload != null && payload.optBoolean("enabled", false) ? 1 : 0);
        }
        if ("SET_AUTO_MODE".equals(type)) {
            return describe(202, payload != null && payload.optBoolean("enabled", false) ? 0 : 1);
        }
        if ("SET_SMART_MODE".equals(type)) {
            return describe(100, payload != null && payload.optBoolean("enabled", false) ? 1 : 0);
        }
        if ("SET_OPERATION_MODE".equals(type)) {
            return describe(513, payload == null ? 0 : payload.optInt("mode", 0));
        }
        if ("SET_SUPPLY_MODE".equals(type)) {
            return describe(141, payload == null ? 0 : payload.optInt("mode", 0));
        }
        if ("SET_PARAMETERS".equals(type)) {
            StringBuilder result = new StringBuilder();
            if (payload != null && payload.has("temperature")) {
                append(result, describe(5, payload.optInt("temperature")));
            }
            if (payload != null && payload.has("humidity")) {
                append(result, describe(6, payload.optInt("humidity")));
            }
            if (payload != null && payload.has("supplyVolume")) {
                append(result, describe(15, payload.optInt("supplyVolume")));
            }
            return result.length() == 0 ? "修改设备设定" : result.toString();
        }
        if ("SET_FACTORY_PARAMETERS".equals(type)) {
            StringBuilder result = new StringBuilder();
            org.json.JSONObject values = payload == null ? null : payload.optJSONObject("values");
            if (values != null) {
                int[] addresses = {15, 16, 17, 140, 142, 143, 144, 145, 146, 147, 148,
                        149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159,
                        169, 170, 171, 512, 524, 525};
                String[] keys = {"supplyVolume", "compressorFrequencyMin", "compressorFrequencyMax",
                        "freshAirHeaterMaxOutput", "preheatTemperatureSetpoint", "energyFanOutput",
                        "energyFreshAirValve", "comfortFanOutput", "comfortFreshAirValve", "boostFanOutput",
                        "boostFreshAirValve", "energyCo2Limit", "comfortCo2Limit", "energyPm25Limit",
                        "comfortPm25Limit", "co2RiseLimit", "pm25RiseLimit", "boostFirstCheckTime",
                        "boostForceToEnergyExtraTime", "boostExitEnergyHoldTime", "preheatRatioAdjust",
                        "automaticSupplyStrategy", "exhaustValveEnergy", "exhaustValveComfort",
                        "exhaustValveBoost", "compressorFrequency", "expansionValveStep", "condenserFanOutput"};
                for (int i = 0; i < addresses.length; i++) {
                    if (values.has(keys[i])) {
                        append(result, describe(addresses[i], values.optInt(keys[i])));
                    }
                }
            }
            return result.length() == 0 ? "修改厂家参数" : result.toString();
        }
        return "执行远程操作";
    }

    private static String describe(int address, int value) {
        switch (address) {
            case 515: return "设备开关：" + (value == 1 ? "开机" : "关机");
            case 202: return "控制模式：" + (value == 0 ? "自动" : "手动");
            case 100: return "智慧模式：" + (value == 1 ? "启用" : "禁用");
            case 513: return "运行模式：" + enumValue(value, new String[]{"除湿", "加湿", "通风", "低温加湿", "制热", "制冷"});
            case 141: return "送风模式：" + enumValue(value, new String[]{"关闭", "节能", "舒适", "强力"});
            case 159: return "自动送风策略：" + enumValue(value, new String[]{"自动", "节能", "舒适", "强力"});
            case 5: return "设定温度：" + value + "℃";
            case 6: return "设定湿度：" + value + "%";
            case 15: return "设定送风量：" + value + "%";
            default: return labelFor(address) + "：" + valueWithUnit(address, value);
        }
    }

    private static String labelFor(int address) {
        switch (address) {
            case 16: return "压缩机最低频率";
            case 17: return "压缩机最高频率";
            case 140: return "新风加热最大输出";
            case 142: return "预热温度设定";
            case 143: return "节能风机输出";
            case 144: return "节能新风阀开度";
            case 145: return "舒适风机输出";
            case 146: return "舒适新风阀开度";
            case 147: return "强力风机输出";
            case 148: return "强力新风阀开度";
            case 149: return "节能CO₂上限";
            case 150: return "舒适CO₂上限";
            case 151: return "节能PM2.5上限";
            case 152: return "舒适PM2.5上限";
            case 153: return "CO₂上升限值";
            case 154: return "PM2.5上升限值";
            case 155: return "强力模式首次检测时间";
            case 156: return "强力转节能额外时间";
            case 157: return "强力退出节能保持时间";
            case 158: return "预热比例调节";
            case 169: return "节能排风阀开度";
            case 170: return "舒适排风阀开度";
            case 171: return "强力排风阀开度";
            case 512: return "压缩机频率";
            case 524: return "膨胀阀步数";
            case 525: return "冷凝风机输出";
            default: return "修改设备参数";
        }
    }

    private static String valueWithUnit(int address, int value) {
        switch (address) {
            case 16: case 17: return value + "Hz";
            case 140: case 143: case 144: case 145: case 146: case 147: case 148:
            case 169: case 170: case 171: case 525: return value + "%";
            case 142: case 158: return value + "℃";
            case 149: case 150: case 153: return value + "ppm";
            case 151: case 152: case 154: return value + "μg/m³";
            case 155: case 156: case 157: return value + "分钟";
            case 524: return value + "步";
            case 512: return value + "Hz";
            default: return String.valueOf(value);
        }
    }

    private static String enumValue(int value, String[] options) {
        return value >= 0 && value < options.length ? options[value] : String.format(Locale.CHINA, "值%d", value);
    }

    private static void append(StringBuilder builder, String value) {
        if (builder.length() > 0) {
            builder.append("；");
        }
        builder.append(value);
    }
}

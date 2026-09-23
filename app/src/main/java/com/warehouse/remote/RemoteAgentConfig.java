package com.warehouse.remote;

import android.content.Context;

import com.warehouse.R;

final class RemoteAgentConfig {
    private final String baseUrl;
    private final String deviceKey;
    private final String serialNo;
    private final String deviceName;

    RemoteAgentConfig(Context context) {
        baseUrl = trimTrailingSlash(context.getString(R.string.remote_agent_base_url));
        deviceKey = context.getString(R.string.remote_agent_device_key);
        serialNo = context.getString(R.string.remote_agent_serial_no);
        deviceName = context.getString(R.string.remote_agent_device_name);
    }

    String baseUrl() {
        return baseUrl;
    }

    String deviceKey() {
        return deviceKey;
    }

    String serialNo() {
        return serialNo;
    }

    String deviceName() {
        return deviceName;
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}

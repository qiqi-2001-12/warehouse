package com.warehouse.fragment.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.warehouse.device.DeviceGateway;
import com.warehouse.R;
import com.warehouse.core.ui.AdminUi;
import com.warehouse.core.ui.ScreenFit;
import com.warehouse.data.local.OperationLogStore;
import com.warehouse.data.local.OperationLogFormatter;
import com.warehouse.fragment.admin.AdminContainerFragment;
import com.warehouse.remote.OpenMeteoWeatherClient;

public class HomeFragment extends Fragment {
    private static final double FALLBACK_WEATHER_LATITUDE = 39.32542;
    private static final double FALLBACK_WEATHER_LONGITUDE = 115.86885;
    private static final long WEATHER_REFRESH_INTERVAL_MS = 10 * 60 * 1000L;
    private static final long LOCATION_UPDATE_TIMEOUT_MS = 10_000L;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final String VALUE_PLACEHOLDER = "--";
    private static final String INPUT_HINT_PLACEHOLDER = "待绑定";

    private View rootView;
    private View panel;
    private ViewPager2 pager;
    private TextView titleView;
    private TextView outdoorTempView;
    private TextView weatherIconView;
    private TextView outdoorStatusView;
    private ImageView adminIconView;
    private ImageView wifiIconView;
    private View adminTouchTarget;
    private View wifiTouchTarget;
    private final Handler weatherHandler = new Handler(Looper.getMainLooper());
    private final OpenMeteoWeatherClient weatherClient = new OpenMeteoWeatherClient();
    private final Runnable weatherRefreshRunnable = this::loadOutdoorWeather;
    private int weatherRequestSequence;
    private boolean locationPermissionRequestInProgress;
    private boolean locationUpdateInProgress;
    private LocationManager weatherLocationManager;
    private LocationListener weatherLocationListener;
    private final Runnable locationUpdateTimeout = this::cancelPendingLocationUpdate;

    private final BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateWifiIconVisibility();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment, container, false);
        bindViews(view);
        setupPager();
        setupHeader();
        updateWifiIconVisibility();
        ScreenFit.bind(rootView, panel);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter wifiFilter = new IntentFilter();
        wifiFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        wifiFilter.addAction("android.net.wifi.STATE_CHANGE");
        requireContext().registerReceiver(wifiStateReceiver, wifiFilter);
        scheduleWeatherRefresh(0L);
    }

    @Override
    public void onStop() {
        weatherHandler.removeCallbacksAndMessages(null);
        cancelPendingLocationUpdate();
        try {
            requireContext().unregisterReceiver(wifiStateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onStop();
    }

    private void bindViews(View root) {
        rootView = root.findViewById(R.id.root);
        panel = root.findViewById(R.id.panel);
        pager = root.findViewById(R.id.pager);
        titleView = root.findViewById(R.id.tv_title);
        outdoorTempView = root.findViewById(R.id.tv_outdoor_temp);
        weatherIconView = root.findViewById(R.id.tv_weather_icon);
        outdoorStatusView = root.findViewById(R.id.tv_outdoor_status);
        adminIconView = root.findViewById(R.id.iv_admin);
        wifiIconView = root.findViewById(R.id.iv_wifi);
        adminTouchTarget = root.findViewById(R.id.admin_touch_target);
        wifiTouchTarget = root.findViewById(R.id.wifi_touch_target);
    }

    private void setupPager() {
        if (pager == null) {
            return;
        }
        pager.setAdapter(new HomePagerAdapter(this));
        pager.setOffscreenPageLimit(2);
        pager.setUserInputEnabled(true);
        RecyclerView pagerRecyclerView = (RecyclerView) pager.getChildAt(0);
        if (pagerRecyclerView != null) {
            pagerRecyclerView.setItemAnimator(null);
            pagerRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
        pager.setCurrentItem(0, false);
    }

    private void setupHeader() {
        if (titleView != null) {
            titleView.setText("全效舱环境一体机");
            titleView.setTextColor(Color.rgb(36, 200, 217));
        }
        OpenMeteoWeatherClient.CurrentWeather cachedWeather = weatherClient.getCachedCurrentWeather();
        if (cachedWeather != null) {
            showOutdoorWeather(cachedWeather);
        } else {
            if (weatherIconView != null) {
                weatherIconView.setText(VALUE_PLACEHOLDER);
            }
            if (outdoorTempView != null) {
                outdoorTempView.setText(VALUE_PLACEHOLDER);
            }
            if (outdoorStatusView != null) {
                outdoorStatusView.setText(VALUE_PLACEHOLDER);
            }
        }
        if (adminIconView != null) {
            adminIconView.setImageResource(R.drawable.administrator);
            ImageViewCompat.setImageTintList(adminIconView, ColorStateList.valueOf(AdminUi.ACCENT));
            if (adminTouchTarget != null) {
                adminTouchTarget.setContentDescription("Administrator");
                AdminUi.addPressFeedback(adminTouchTarget);
                adminTouchTarget.setOnClickListener(v -> openAdminPage());
            }
        }
        if (wifiIconView != null) {
            wifiIconView.setImageResource(R.drawable.wifi);
            ImageViewCompat.setImageTintList(wifiIconView, ColorStateList.valueOf(AdminUi.ACCENT));
            wifiIconView.setContentDescription("Wi-Fi setup");
            if (wifiTouchTarget != null) {
                wifiTouchTarget.setContentDescription("Wi-Fi setup");
                AdminUi.addPressFeedback(wifiTouchTarget);
                wifiTouchTarget.setOnClickListener(v -> openWifiPage());
            }
        }
    }

    private void openAdminPage() {
        if (!isAdded()) {
            return;
        }
        showAdminPasswordDialog();
    }

    private void showAdminPasswordDialog() {
        if (!isAdded()) {
            return;
        }
        Context context = requireContext();
        LinearLayout content = AdminUi.column(context);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(AdminUi.dp(context, 18), AdminUi.dp(context, 18),
                AdminUi.dp(context, 18), AdminUi.dp(context, 18));
        content.setBackground(AdminUi.bg(Color.BLACK, 8, AdminUi.PANEL_STROKE, 1, context));

        TextView title = AdminUi.text(context, "管理员验证", 22,
                AdminUi.TEXT_PRIMARY, Typeface.BOLD);
        content.addView(title, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                AdminUi.dp(context, 38)));

        EditText passwordInput = new EditText(context);
        passwordInput.setSoundEffectsEnabled(false);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setSingleLine(true);
        passwordInput.setHint("请输入密码");
        passwordInput.setTextColor(AdminUi.TEXT_PRIMARY);
        passwordInput.setHintTextColor(AdminUi.TEXT_SECONDARY);
        passwordInput.setTextSize(22);
        passwordInput.setGravity(Gravity.CENTER);
        passwordInput.setSelectAllOnFocus(true);
        passwordInput.setBackground(AdminUi.bg(Color.rgb(5, 35, 52), 4,
                AdminUi.ACCENT, 1, context));
        passwordInput.setPadding(AdminUi.dp(context, 12), 0,
                AdminUi.dp(context, 12), 0);
        LinearLayout.LayoutParams inputParams = AdminUi.lp(
                ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 52));
        inputParams.topMargin = AdminUi.dp(context, 14);
        content.addView(passwordInput, inputParams);

        LinearLayout actions = AdminUi.row(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = AdminUi.lp(
                ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 46));
        actionsParams.topMargin = AdminUi.dp(context, 16);
        content.addView(actions, actionsParams);

        TextView cancel = AdminUi.button(context, "取消");
        TextView confirm = AdminUi.button(context, "确定");
        actions.addView(cancel, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        View gap = new View(context);
        actions.addView(gap, AdminUi.lp(AdminUi.dp(context, 10), 1));
        actions.addView(confirm, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        AlertDialog dialog = new AlertDialog.Builder(
                new ContextThemeWrapper(context, R.style.BlackInputDialogTheme))
                .setView(content)
                .create();
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            String password = passwordInput.getText() == null
                    ? "" : passwordInput.getText().toString();
            if ("admin".equals(password)) {
                dialog.dismiss();
                enterAdminPage();
                return;
            }
            passwordInput.setError("密码错误");
            passwordInput.requestFocus();
            passwordInput.selectAll();
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    context.getDrawable(R.drawable.input_dialog_black_bg));
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int dialogWidth = Math.min(AdminUi.dp(context, 480),
                    Math.max(AdminUi.dp(context, 260), screenWidth - AdminUi.dp(context, 32)));
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        passwordInput.requestFocus();
    }

    private void enterAdminPage() {
        if (!isAdded()) {
            return;
        }
        getParentFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new AdminContainerFragment())
                .addToBackStack("admin_page")
                .commit();
    }

    private void openWifiPage() {
        if (!isAdded()) {
            return;
        }
        getParentFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new WifiSetupFragment())
                .addToBackStack("wifi_setup")
                .commit();
    }

    private void updateWifiIconVisibility() {
        if (wifiIconView == null) {
            return;
        }
        boolean wifiConnected = false;
        try {
            ConnectivityManager manager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
            wifiConnected = info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (Exception ignored) {
        }
        wifiIconView.setVisibility(View.VISIBLE);
        ImageViewCompat.setImageTintList(wifiIconView, ColorStateList.valueOf(
                wifiConnected ? AdminUi.ACCENT : AdminUi.TEXT_SECONDARY));
    }

    private void scheduleWeatherRefresh(long delayMs) {
        weatherHandler.removeCallbacks(weatherRefreshRunnable);
        if (!isAdded()) {
            return;
        }
        weatherHandler.postDelayed(weatherRefreshRunnable, Math.max(0L, delayMs));
    }

    private void loadOutdoorWeather() {
        if (!isAdded()) {
            return;
        }
        Location location = getLastKnownWeatherLocation();
        if (location != null) {
            fetchOutdoorWeather(location.getLatitude(), location.getLongitude());
        } else {
            fetchOutdoorWeather(FALLBACK_WEATHER_LATITUDE, FALLBACK_WEATHER_LONGITUDE);
        }
        requestLocationPermissionIfNeeded();
        requestSingleLocationUpdate();
    }

    private void fetchOutdoorWeather(double latitude, double longitude) {
        int requestSequence = ++weatherRequestSequence;
        weatherClient.fetchCurrentWeather(latitude, longitude,
                new OpenMeteoWeatherClient.Callback() {
                    @Override
                    public void onSuccess(OpenMeteoWeatherClient.CurrentWeather weather) {
                        weatherHandler.post(() -> {
                            if (!isAdded() || requestSequence != weatherRequestSequence) {
                                return;
                            }
                            showOutdoorWeather(weather);
                            scheduleWeatherRefresh(WEATHER_REFRESH_INTERVAL_MS);
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        weatherHandler.post(() -> {
                            if (isAdded() && requestSequence == weatherRequestSequence) {
                                scheduleWeatherRefresh(WEATHER_REFRESH_INTERVAL_MS);
                            }
                        });
                    }
                });
    }

    @Nullable
    private Location getLastKnownWeatherLocation() {
        if (!hasLocationPermission() || !isAdded()) {
            return null;
        }
        LocationManager locationManager = (LocationManager) requireContext()
                .getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return null;
        }
        Location newestLocation = null;
        String[] providers = {
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
        };
        for (String provider : providers) {
            try {
                Location location = locationManager.getLastKnownLocation(provider);
                if (location != null && (newestLocation == null
                        || location.getTime() > newestLocation.getTime())) {
                    newestLocation = location;
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }
        return newestLocation;
    }

    private void requestLocationPermissionIfNeeded() {
        if (hasLocationPermission() || locationPermissionRequestInProgress || !isAdded()) {
            return;
        }
        locationPermissionRequestInProgress = true;
        requestPermissions(new String[]{
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
        }, LOCATION_PERMISSION_REQUEST_CODE);
    }

    private boolean hasLocationPermission() {
        if (!isAdded()) {
            return false;
        }
        Context context = requireContext();
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestSingleLocationUpdate() {
        if (!hasLocationPermission() || locationUpdateInProgress || !isAdded()) {
            return;
        }
        LocationManager locationManager = (LocationManager) requireContext()
                .getSystemService(Context.LOCATION_SERVICE);
        String provider = chooseLocationProvider(locationManager);
        if (locationManager == null || provider == null) {
            return;
        }
        weatherLocationManager = locationManager;
        weatherLocationListener = location -> {
            cancelPendingLocationUpdate();
            if (isAdded()) {
                fetchOutdoorWeather(location.getLatitude(), location.getLongitude());
            }
        };
        locationUpdateInProgress = true;
        try {
            locationManager.requestSingleUpdate(provider, weatherLocationListener, Looper.getMainLooper());
            weatherHandler.postDelayed(locationUpdateTimeout, LOCATION_UPDATE_TIMEOUT_MS);
        } catch (SecurityException | IllegalArgumentException ignored) {
            cancelPendingLocationUpdate();
        }
    }

    @Nullable
    private String chooseLocationProvider(@Nullable LocationManager locationManager) {
        if (locationManager == null) {
            return null;
        }
        String[] providers = {
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
        };
        for (String provider : providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    return provider;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private void cancelPendingLocationUpdate() {
        weatherHandler.removeCallbacks(locationUpdateTimeout);
        if (weatherLocationManager != null && weatherLocationListener != null) {
            try {
                weatherLocationManager.removeUpdates(weatherLocationListener);
            } catch (SecurityException ignored) {
            }
        }
        weatherLocationManager = null;
        weatherLocationListener = null;
        locationUpdateInProgress = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }
        locationPermissionRequestInProgress = false;
        if (hasLocationPermission()) {
            scheduleWeatherRefresh(0L);
        }
    }

    private void showOutdoorWeather(OpenMeteoWeatherClient.CurrentWeather weather) {
        if (weatherIconView != null) {
            weatherIconView.setText(weather.getCondition().getIcon());
        }
        if (outdoorTempView != null) {
            outdoorTempView.setText(String.format(
                    java.util.Locale.CHINA, "%.0f\u00b0", weather.getTemperatureCelsius()));
        }
        if (outdoorStatusView != null) {
            outdoorStatusView.setText(weather.getCondition().getHeaderLabel());
        }
    }

    private static final class HomePagerAdapter extends FragmentStateAdapter {
        HomePagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return position == 0 ? new IndoorStatusPageFragment() : new SmartModePageFragment();
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    public static class IndoorStatusPageFragment extends Fragment {
        private static final long METRIC_POLL_INTERVAL_MS = 1500L;
        private static final long STATE_POLL_INTERVAL_MS = 3000L;
        private static final long STATE_POLL_START_DELAY_MS = 400L;
        private final Handler pollHandler = new Handler(Looper.getMainLooper());
        private final Runnable metricPollRunnable = this::pollMetricValues;
        private final Runnable statePollRunnable = this::pollStateValues;
        private boolean switchOn;
        private boolean humidityAutoMode = true;
        private int humidityModeIndex = 0;
        private boolean smartModeEnabled = true;
        private int manualSupplyModeIndex = -1;
        private int realtimeSupplyModeIndex = -1;
        private int lastAutomaticSupplyModeIndex = -1;
        private boolean pollingActive;
        private boolean metricPollInFlight;
        private boolean statePollInFlight;
        private int pendingMetricReads;
        private int pendingStateReads;
        private FrameLayout switchButton;
        private TextView switchButtonText;
        private TextView autoButton;
        private TextView leftHumidityModeButton;
        private TextView rightHumidityModeButton;
        private TextView humidityModeValue;
        private TextView smartModeButton;
        private TextView supplyModeButton;
        private TextView indoorTemperatureValueView;
        private TextView indoorHumidityValueView;
        private TextView indoorPm25ValueView;
        private TextView indoorCo2ValueView;
        private TextView indoorAbsoluteHumidityValueView;

        private static final String[] SUPPLY_MODES = {"关闭", "节能", "舒适", "强力"};
        private static final String[] HUMIDITY_MODES = {"除湿", "加湿", "通风", "低温加湿", "制热", "制冷"};

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            Context context = requireContext();
            View root = inflater.inflate(R.layout.fragment_indoor_status_page, container, false);

            switchButton = root.findViewById(R.id.btn_auto_mode);
            switchButtonText = root.findViewById(R.id.btn_auto_mode_text);
            autoButton = root.findViewById(R.id.btn_auto_manual);
            LinearLayout modeBar = root.findViewById(R.id.mode_bar);
            LinearLayout metricGrid = root.findViewById(R.id.metric_grid);
            LinearLayout humidityRow = root.findViewById(R.id.humidity_row);
            LinearLayout moduleRow = root.findViewById(R.id.module_row);

            if (modeBar != null) {
                modeBar.removeAllViews();
                modeBar.addView(buildHumidityModeModule(context), AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            if (switchButton != null) {
                AdminUi.addPressFeedback(switchButton);
                updateSwitchButton();
                switchButton.setOnClickListener(v -> writeIntValue(515, switchOn ? 0 : 1, value -> {
                    switchOn = value == 1;
                    updateSwitchButton();
                }));
            }

            if (autoButton != null) {
                AdminUi.addPressFeedback(autoButton);
                autoButton.setText("自动");
                autoButton.setOnClickListener(v -> {
                    boolean switchingToManual = humidityAutoMode;
                    int lastAutomaticMode = lastAutomaticSupplyModeIndex;
                    writeIntValue(202, humidityAutoMode ? 1 : 0, value -> {
                        humidityAutoMode = value == 0;
                        if (switchingToManual && !humidityAutoMode) {
                            applyLastAutomaticSupplyMode(lastAutomaticMode);
                        }
                        refreshHumidityModeButton();
                        refreshSupplyButtons();
                    });
                });
            }

            buildMetricGrid(context, metricGrid);
            buildHumidityRow(context, humidityRow);
            buildModuleRow(context, moduleRow);
            restoreCachedIndoorState();
            restoreCachedIndoorMetrics();
            return root;
        }

        @Override
        public void onResume() {
            super.onResume();
            startPolling();
        }

        @Override
        public void onPause() {
            stopPolling();
            super.onPause();
        }

        @Override
        public void onDestroyView() {
            stopPolling();
            switchButton = null;
            switchButtonText = null;
            autoButton = null;
            leftHumidityModeButton = null;
            rightHumidityModeButton = null;
            humidityModeValue = null;
            smartModeButton = null;
            supplyModeButton = null;
            indoorTemperatureValueView = null;
            indoorHumidityValueView = null;
            indoorPm25ValueView = null;
            indoorCo2ValueView = null;
            indoorAbsoluteHumidityValueView = null;
            super.onDestroyView();
        }

        private void startPolling() {
            pollingActive = true;
            requestImmediateMetricPoll();
            requestImmediateStatePoll();
        }

        private void stopPolling() {
            pollingActive = false;
            metricPollInFlight = false;
            statePollInFlight = false;
            pendingMetricReads = 0;
            pendingStateReads = 0;
            pollHandler.removeCallbacks(metricPollRunnable);
            pollHandler.removeCallbacks(statePollRunnable);
        }

        private void requestImmediateMetricPoll() {
            scheduleNextMetricPoll(0L);
        }

        private void requestImmediateStatePoll() {
            scheduleNextStatePoll(STATE_POLL_START_DELAY_MS);
        }

        private void scheduleNextMetricPoll(long delayMs) {
            pollHandler.removeCallbacks(metricPollRunnable);
            if (!pollingActive || !isAdded()) {
                return;
            }
            pollHandler.postDelayed(metricPollRunnable, Math.max(0L, delayMs));
        }

        private void scheduleNextStatePoll(long delayMs) {
            pollHandler.removeCallbacks(statePollRunnable);
            if (!pollingActive || !isAdded()) {
                return;
            }
            pollHandler.postDelayed(statePollRunnable, Math.max(0L, delayMs));
        }

        private void pollMetricValues() {
            if (!pollingActive || !isAdded()) {
                return;
            }
            if (metricPollInFlight) {
                scheduleNextMetricPoll(METRIC_POLL_INTERVAL_MS);
                return;
            }
            int reads = countMetricReads();
            if (reads <= 0) {
                scheduleNextMetricPoll(METRIC_POLL_INTERVAL_MS);
                return;
            }
            metricPollInFlight = true;
            pendingMetricReads = reads;
            refreshMetricRange(this::onMetricPollReadFinished);
        }

        private void pollStateValues() {
            if (!pollingActive || !isAdded()) {
                return;
            }
            if (statePollInFlight) {
                scheduleNextStatePoll(STATE_POLL_INTERVAL_MS);
                return;
            }
            int reads = countStateReads();
            if (reads <= 0) {
                scheduleNextStatePoll(STATE_POLL_INTERVAL_MS);
                return;
            }
            statePollInFlight = true;
            pendingStateReads = reads;
            refreshControlStateRange(this::onStatePollReadFinished);
            refreshState(202, value -> {
                humidityAutoMode = value == 0;
                refreshHumidityModeButton();
                refreshSupplyButtons();
            }, this::onStatePollReadFinished);
            refreshState(515, value -> {
                switchOn = value == 1;
                updateSwitchButton();
            }, this::onStatePollReadFinished);
            refreshState(100, value -> {
                smartModeEnabled = value == 1;
                refreshSmartButtons();
            }, this::onStatePollReadFinished);
            refreshState(141, value -> {
                manualSupplyModeIndex = clampManualSupplyModeIndex(value);
                refreshSupplyButtons();
            }, this::onStatePollReadFinished);
            refreshState(996, value -> {
                realtimeSupplyModeIndex = clampIndex(value, SUPPLY_MODES.length);
                if (humidityAutoMode) {
                    lastAutomaticSupplyModeIndex = realtimeSupplyModeIndex;
                }
                refreshSupplyButtons();
            }, this::onStatePollReadFinished);
        }

        private int countMetricReads() {
            return hasReadableMetricRange() ? 1 : 0;
        }

        private int countStateReads() {
            int reads = 0;
            reads += hasReadableControlStateRange() ? 1 : 0;
            reads += canReadAddress(202) ? 1 : 0;
            reads += canReadAddress(515) ? 1 : 0;
            reads += canReadAddress(100) ? 1 : 0;
            reads += canReadAddress(141) ? 1 : 0;
            reads += canReadAddress(996) ? 1 : 0;
            return reads;
        }

        private boolean hasReadableMetricRange() {
            return canReadValue(indoorTemperatureValueView, 863)
                    || canReadValue(indoorHumidityValueView, 864)
                    || canReadValue(indoorPm25ValueView, 860)
                    || canReadValue(indoorCo2ValueView, 861)
                    || canReadValue(indoorAbsoluteHumidityValueView, 865);
        }

        private boolean hasReadableControlStateRange() {
            return canReadAddress(513);
        }

        private boolean canReadValue(TextView view, int address) {
            return view != null && canReadAddress(address);
        }

        private boolean canReadAddress(int address) {
            return DeviceGateway.get(requireContext()).canRead(address);
        }

        private int refreshState(int address, IntValueConsumer consumer, Runnable onFinished) {
            DeviceGateway gateway = DeviceGateway.get(requireContext());
            if (!gateway.canRead(address)) {
                return 0;
            }
            gateway.read(address, new DeviceGateway.IntCallback() {
                @Override
                public void onSuccess(int value) {
                    consumer.accept(value);
                    onFinished.run();
                }

                @Override
                public void onError(Exception error) {
                    onFinished.run();
                }
            });
            return 1;
        }

        private int refreshMetricRange(Runnable onFinished) {
            if (!hasReadableMetricRange()) {
                return 0;
            }
            DeviceGateway.get(requireContext()).readRange(860, 6, new DeviceGateway.RangeCallback() {
                @Override
                public void onSuccess(int startAddress, int[] values) {
                    updateMetricValue(indoorPm25ValueView, 860, startAddress, values);
                    updateMetricValue(indoorCo2ValueView, 861, startAddress, values);
                    updateMetricValue(indoorTemperatureValueView, 863, startAddress, values);
                    updateMetricValue(indoorHumidityValueView, 864, startAddress, values);
                    updateMetricValue(indoorAbsoluteHumidityValueView, 865, startAddress, values);
                    onFinished.run();
                }

                @Override
                public void onError(Exception error) {
                    restoreCachedIndoorMetrics();
                    onFinished.run();
                }
            });
            return 1;
        }

        private void restoreCachedIndoorState() {
            if (!isAdded()) {
                return;
            }
            DeviceGateway gateway = DeviceGateway.get(requireContext());
            Integer humidityMode = gateway.getCachedValue(513);
            Integer autoMode = gateway.getCachedValue(202);
            Integer power = gateway.getCachedValue(515);
            Integer smartMode = gateway.getCachedValue(100);
            Integer manualSupplyMode = gateway.getCachedValue(141);
            Integer realtimeSupplyMode = gateway.getCachedValue(996);
            if (humidityMode != null) {
                humidityModeIndex = clampIndex(humidityMode, HUMIDITY_MODES.length);
            }
            if (autoMode != null) {
                humidityAutoMode = autoMode == 0;
            }
            if (power != null) {
                switchOn = power == 1;
            }
            if (smartMode != null) {
                smartModeEnabled = smartMode == 1;
            }
            if (manualSupplyMode != null) {
                manualSupplyModeIndex = clampManualSupplyModeIndex(manualSupplyMode);
            }
            if (realtimeSupplyMode != null) {
                realtimeSupplyModeIndex = clampIndex(realtimeSupplyMode, SUPPLY_MODES.length);
                if (humidityAutoMode) {
                    lastAutomaticSupplyModeIndex = realtimeSupplyModeIndex;
                }
            }
            refreshHumidityModeButton();
            refreshHumidityModeValue();
            refreshSmartButtons();
            refreshSupplyButtons();
            updateSwitchButton();
        }

        private void restoreCachedIndoorMetrics() {
            if (!isAdded()) {
                return;
            }
            restoreCachedMetric(indoorPm25ValueView, 860);
            restoreCachedMetric(indoorCo2ValueView, 861);
            restoreCachedMetric(indoorTemperatureValueView, 863);
            restoreCachedMetric(indoorHumidityValueView, 864);
            restoreCachedMetric(indoorAbsoluteHumidityValueView, 865);
        }

        private void restoreCachedMetric(TextView view, int address) {
            if (view == null || !isAdded()) {
                return;
            }
            DeviceGateway gateway = DeviceGateway.get(requireContext());
            Integer cachedValue = gateway.getCachedValue(address);
            if (cachedValue != null) {
                view.setTag(cachedValue);
                view.setText(gateway.formatDefault(address, cachedValue));
            }
        }

        private int refreshControlStateRange(Runnable onFinished) {
            if (!hasReadableControlStateRange()) {
                return 0;
            }
            DeviceGateway.get(requireContext()).readRange(513, 1, new DeviceGateway.RangeCallback() {
                @Override
                public void onSuccess(int startAddress, int[] values) {
                    humidityModeIndex = clampIndex(rangeValue(startAddress, values, 513), HUMIDITY_MODES.length);
                    refreshHumidityModeValue();
                    onFinished.run();
                }

                @Override
                public void onError(Exception error) {
                    onFinished.run();
                }
            });
            return 1;
        }

        private void updateMetricValue(TextView view, int address, int startAddress, int[] values) {
            if (view == null) {
                return;
            }
            DeviceGateway gateway = DeviceGateway.get(requireContext());
            if (!gateway.canRead(address)) {
                view.setText(VALUE_PLACEHOLDER);
                return;
            }
            int value = rangeValue(startAddress, values, address);
            view.setTag(value);
            view.setText(gateway.formatDefault(address, value));
        }

        private void showMetricPlaceholder(TextView view) {
            if (view != null) {
                view.setText(VALUE_PLACEHOLDER);
            }
        }

        private int rangeValue(int startAddress, int[] values, int address) {
            int index = address - startAddress;
            if (values == null || index < 0 || index >= values.length) {
                return 0;
            }
            return values[index];
        }

        private void onMetricPollReadFinished() {
            if (pendingMetricReads > 0) {
                pendingMetricReads--;
            }
            if (pendingMetricReads > 0) {
                return;
            }
            metricPollInFlight = false;
            if (pollingActive) {
                scheduleNextMetricPoll(METRIC_POLL_INTERVAL_MS);
            }
        }

        private void onStatePollReadFinished() {
            if (pendingStateReads > 0) {
                pendingStateReads--;
            }
            if (pendingStateReads > 0) {
                return;
            }
            statePollInFlight = false;
            if (pollingActive) {
                scheduleNextStatePoll(STATE_POLL_INTERVAL_MS);
            }
        }

        private void updateSwitchButton() {
            if (switchButton == null || switchButtonText == null) {
                return;
            }
            switchButton.setBackgroundResource(
                    switchOn ? R.drawable.humidity_switch_on_bg : R.drawable.humidity_switch_off_bg);
            switchButtonText.setText(switchOn ? "ON" : "OFF");
            switchButtonText.setTextColor(AdminUi.PAGE_BG);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) switchButtonText.getLayoutParams();
            if (params != null) {
                params.gravity = (switchOn ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL;
                params.leftMargin = 0;
                params.rightMargin = 0;
                switchButtonText.setLayoutParams(params);
            }
        }

        private void buildMetricGrid(Context context, LinearLayout metricGrid) {
            if (metricGrid == null) {
                return;
            }
            metricGrid.removeAllViews();

            LinearLayout firstRow = AdminUi.row(context);
            metricGrid.addView(firstRow, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0));
            LinearLayout.LayoutParams firstRowParams = (LinearLayout.LayoutParams) firstRow.getLayoutParams();
            firstRowParams.height = 0;
            firstRowParams.weight = 1;
            firstRow.setLayoutParams(firstRowParams);

            LinearLayout secondRow = AdminUi.row(context);
            LinearLayout.LayoutParams secondRowParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
            secondRowParams.topMargin = AdminUi.dp(context, 10);
            secondRowParams.weight = 1;
            metricGrid.addView(secondRow, secondRowParams);

            indoorTemperatureValueView = addMetricCell(firstRow, context, "温度", VALUE_PLACEHOLDER, "℃", AdminUi.TEXT_PRIMARY);
            indoorHumidityValueView = addMetricCell(firstRow, context, "湿度", VALUE_PLACEHOLDER, "%", AdminUi.TEXT_PRIMARY);
            indoorPm25ValueView = addMetricCell(secondRow, context, "PM2.5", VALUE_PLACEHOLDER, "μg/m³", Color.rgb(91, 154, 255));
            indoorCo2ValueView = addMetricCell(secondRow, context, "CO₂", VALUE_PLACEHOLDER, "ppm", Color.rgb(245, 166, 35));
        }

        private void buildHumidityRow(Context context, LinearLayout humidityRow) {
            if (humidityRow == null) {
                return;
            }
            humidityRow.removeAllViews();
            humidityRow.setGravity(Gravity.CENTER);

            LinearLayout humidityContent = AdminUi.row(context);
            humidityContent.setGravity(Gravity.CENTER_VERTICAL);
            humidityRow.addView(humidityContent, AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView humidityLabel = AdminUi.text(context, "含湿量", 14,
                    AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            humidityContent.addView(humidityLabel, AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            humidityLabel.setTextSize(20);

            TextView humidityValue = AdminUi.text(context, VALUE_PLACEHOLDER, 54,
                    Color.rgb(255, 200, 87), Typeface.BOLD);
            LinearLayout.LayoutParams humidityValueParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            humidityValueParams.leftMargin = AdminUi.dp(context, 10);
            humidityContent.addView(humidityValue, humidityValueParams);
            indoorAbsoluteHumidityValueView = humidityValue;

            TextView humidityUnit = AdminUi.text(context, "g/kg", 16,
                    AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            LinearLayout.LayoutParams humidityUnitParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            humidityUnitParams.leftMargin = AdminUi.dp(context, 8);
            humidityContent.addView(humidityUnit, humidityUnitParams);
            humidityUnit.setTextSize(22);
        }

        private void buildModuleRow(Context context, LinearLayout moduleRow) {
            if (moduleRow == null) {
                return;
            }
            moduleRow.removeAllViews();
            moduleRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams moduleRowParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            moduleRowParams.topMargin = AdminUi.dp(context, 12);
            moduleRow.setLayoutParams(moduleRowParams);

            addModeModule(context, moduleRow, "智慧模式", true);
            addModeModule(context, moduleRow, "送风模式", false);
        }

        private TextView addMetricCell(LinearLayout parent, Context context, String label, String value, String unit, int valueColor) {
            LinearLayout cell = AdminUi.column(context);
            cell.setGravity(Gravity.CENTER);
            parent.addView(cell, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

            TextView labelView = AdminUi.text(context, label, 16, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            labelView.setGravity(Gravity.CENTER);
            cell.addView(labelView, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 28)));

            LinearLayout valueRow = AdminUi.row(context);
            valueRow.setGravity(Gravity.CENTER);
            cell.addView(valueRow, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0));
            LinearLayout.LayoutParams valueRowParams = (LinearLayout.LayoutParams) valueRow.getLayoutParams();
            valueRowParams.weight = 1;
            valueRow.setLayoutParams(valueRowParams);

            TextView valueView = AdminUi.text(context, value, 54, valueColor, Typeface.NORMAL);
            valueRow.addView(valueView, AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView unitView = AdminUi.text(context, unit, 22, AdminUi.TEXT_SECONDARY, Typeface.NORMAL);
            LinearLayout.LayoutParams unitParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            unitParams.leftMargin = AdminUi.dp(context, 2);
            valueRow.addView(unitView, unitParams);
            return valueView;
        }

        private LinearLayout buildHumidityModeModule(Context context) {
            LinearLayout module = AdminUi.row(context);
            module.setGravity(Gravity.CENTER_VERTICAL);
            module.setBackgroundColor(Color.TRANSPARENT);

            LinearLayout modeCluster = AdminUi.row(context);
            modeCluster.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams modeClusterParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            module.addView(modeCluster, modeClusterParams);

            leftHumidityModeButton = createBracketButton(context, "<");
            leftHumidityModeButton.setOnClickListener(v -> cycleHumidityMode(-1));
            modeCluster.addView(leftHumidityModeButton, AdminUi.lp(AdminUi.dp(context, 44), AdminUi.dp(context, 48)));

            humidityModeValue = AdminUi.text(context, getHumidityModeText(), 28, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
            humidityModeValue.setGravity(Gravity.CENTER_VERTICAL);
            humidityModeValue.setClickable(true);
            humidityModeValue.setFocusable(true);
            humidityModeValue.setOnClickListener(v -> showHumidityModeDialog());
            AdminUi.addPressFeedback(humidityModeValue);
            LinearLayout.LayoutParams humidityModeValueParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            humidityModeValueParams.leftMargin = AdminUi.dp(context, 10);
            humidityModeValueParams.rightMargin = AdminUi.dp(context, 10);
            modeCluster.addView(humidityModeValue, humidityModeValueParams);

            rightHumidityModeButton = createBracketButton(context, ">");
            rightHumidityModeButton.setOnClickListener(v -> cycleHumidityMode(1));
            modeCluster.addView(rightHumidityModeButton, AdminUi.lp(AdminUi.dp(context, 44), AdminUi.dp(context, 48)));

            return module;
        }

        private void addModeModule(Context context, LinearLayout parent, String titleText, boolean smartMode) {
            LinearLayout module = AdminUi.column(context);
            module.setPadding(AdminUi.dp(context, 16), AdminUi.dp(context, 16), AdminUi.dp(context, 16), AdminUi.dp(context, 16));
            module.setBackground(AdminUi.bg(Color.argb(190, 6, 44, 81), 8,
                    Color.rgb(52, 139, 176), 1, context));
            LinearLayout.LayoutParams moduleParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (parent.getChildCount() > 0) {
                moduleParams.leftMargin = AdminUi.dp(context, 8);
            }
            parent.addView(module, moduleParams);

            LinearLayout row = AdminUi.row(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            module.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 50)));

            TextView button = smartMode
                    ? createModeToggleButton(context, smartModeEnabled ? "启用" : "禁用")
                    : createSupplyModeButton(context, getSupplyModeText());
            if (smartMode) {
                TextView title = AdminUi.text(context, titleText, 22, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
                title.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams titleParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                row.addView(title, titleParams);
                row.addView(button, AdminUi.lp(AdminUi.dp(context, 110), AdminUi.dp(context, 50)));
            } else {
                row.addView(button, AdminUi.lp(AdminUi.dp(context, 110), AdminUi.dp(context, 50)));
                TextView title = AdminUi.text(context, titleText, 22, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
                title.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                LinearLayout.LayoutParams titleParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                row.addView(title, titleParams);
            }

            if (smartMode) {
                smartModeButton = button;
                button.setOnClickListener(v -> writeIntValue(100, smartModeEnabled ? 0 : 1, value -> {
                    smartModeEnabled = value == 1;
                    refreshSmartButtons();
                }));
            } else {
                supplyModeButton = button;
                button.setOnClickListener(v -> cycleSupplyMode());
            }
        }

        private TextView createModeToggleButton(Context context, String text) {
            TextView button = AdminUi.text(context, text, 17, AdminUi.PAGE_BG, Typeface.BOLD);
            button.setBackgroundResource(R.drawable.humidity_switch_on_bg);
            AdminUi.addPressFeedback(button);
            button.setClickable(true);
            button.setFocusable(true);
            return button;
        }

        private TextView createSupplyModeButton(Context context, String text) {
            TextView button = AdminUi.text(context, text, 17, AdminUi.ACCENT, Typeface.BOLD);
            button.setBackgroundResource("关闭".equals(text)
                    ? R.drawable.humidity_switch_on_bg
                    : R.drawable.humidity_switch_off_bg);
            AdminUi.addPressFeedback(button);
            button.setTextColor("关闭".equals(text) ? AdminUi.PAGE_BG : AdminUi.ACCENT);
            button.setClickable(false);
            button.setFocusable(false);
            return button;
        }

        private TextView createHumidityModeButton(Context context, String text) {
            TextView button = AdminUi.text(context, text, 17, AdminUi.PAGE_BG, Typeface.BOLD);
            button.setTextColor(humidityAutoMode ? AdminUi.PAGE_BG : AdminUi.ACCENT);
            button.setBackgroundResource(humidityAutoMode ? R.drawable.humidity_switch_on_bg : R.drawable.humidity_switch_off_bg);
            AdminUi.addPressFeedback(button);
            button.setClickable(true);
            button.setFocusable(true);
            return button;
        }

        private TextView createBracketButton(Context context, String text) {
            TextView button = AdminUi.text(context, text, 34, AdminUi.ACCENT, Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            button.setPadding(0, 0, 0, 0);
            button.setClickable(true);
            button.setFocusable(true);
            button.setMinWidth(AdminUi.dp(context, 44));
            button.setMinHeight(AdminUi.dp(context, 48));
            AdminUi.addPressFeedback(button);
            return button;
        }

        private void cycleHumidityMode(int delta) {
            if (humidityAutoMode) {
                return;
            }
            int length = HUMIDITY_MODES.length;
            int next = (humidityModeIndex + delta + length) % length;
            writeIntValue(513, next, value -> {
                humidityModeIndex = clampIndex(value, HUMIDITY_MODES.length);
                refreshHumidityModeValue();
            });
        }

        private void showHumidityModeDialog() {
            if (humidityAutoMode || !isAdded()) {
                return;
            }
            Context context = requireContext();
            LinearLayout content = AdminUi.column(context);
            content.setPadding(AdminUi.dp(context, 20), AdminUi.dp(context, 18),
                    AdminUi.dp(context, 20), AdminUi.dp(context, 20));

            TextView title = AdminUi.text(context, "选择运行模式", 22,
                    AdminUi.TEXT_PRIMARY, Typeface.BOLD);
            title.setGravity(Gravity.CENTER_HORIZONTAL);
            content.addView(title, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    AdminUi.dp(context, 38)));

            AlertDialog dialog = new AlertDialog.Builder(
                    new ContextThemeWrapper(context, R.style.BlackInputDialogTheme))
                    .setView(content)
                    .create();
            for (int rowIndex = 0; rowIndex < HUMIDITY_MODES.length; rowIndex += 3) {
                LinearLayout row = AdminUi.row(context);
                row.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowParams = AdminUi.lp(
                        ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 58));
                if (rowIndex > 0) {
                    rowParams.topMargin = AdminUi.dp(context, 12);
                }
                content.addView(row, rowParams);
                for (int column = 0; column < 3 && rowIndex + column < HUMIDITY_MODES.length; column++) {
                    int position = rowIndex + column;
                    TextView option = AdminUi.text(context, HUMIDITY_MODES[position], 18,
                            position == humidityModeIndex ? AdminUi.PAGE_BG : AdminUi.ACCENT,
                            Typeface.BOLD);
                    option.setBackgroundResource(position == humidityModeIndex
                            ? R.drawable.humidity_switch_on_bg
                            : R.drawable.humidity_switch_off_bg);
                    option.setClickable(true);
                    option.setFocusable(true);
                    AdminUi.addPressFeedback(option);
                    LinearLayout.LayoutParams optionParams = AdminUi.weightedLp(0,
                            AdminUi.dp(context, 54), 1);
                    optionParams.leftMargin = column == 0 ? 0 : AdminUi.dp(context, 6);
                    optionParams.rightMargin = column == 2 ? 0 : AdminUi.dp(context, 6);
                    row.addView(option, optionParams);
                    option.setOnClickListener(v -> {
                        writeIntValue(513, position, value -> {
                            humidityModeIndex = clampIndex(value, HUMIDITY_MODES.length);
                            refreshHumidityModeValue();
                        });
                        dialog.dismiss();
                    });
                }
            }
            dialog.setOnShowListener(d -> {
                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawable(
                            context.getDrawable(R.drawable.input_dialog_black_bg));
                    int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
                    int dialogWidth = Math.min(screenWidth - AdminUi.dp(context, 32), AdminUi.dp(context, 520));
                    dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
                }
            });
            dialog.show();
        }

        private void refreshHumidityModeButton() {
            if (autoButton != null) {
                autoButton.setText(humidityAutoMode ? "自动" : "手动");
                autoButton.setTextColor(humidityAutoMode ? AdminUi.PAGE_BG : AdminUi.ACCENT);
                autoButton.setBackgroundResource(
                        humidityAutoMode ? R.drawable.humidity_switch_on_bg : R.drawable.humidity_switch_off_bg);
            }
            setHumidityModeButtonsEnabled(!humidityAutoMode);
        }

        private void setHumidityModeButtonsEnabled(boolean enabled) {
            setHumidityModeButtonEnabled(leftHumidityModeButton, enabled);
            setHumidityModeButtonEnabled(humidityModeValue, enabled);
            setHumidityModeButtonEnabled(rightHumidityModeButton, enabled);
        }

        private void setHumidityModeButtonEnabled(TextView button, boolean enabled) {
            if (button == null) {
                return;
            }
            button.setEnabled(enabled);
            button.setClickable(enabled);
            button.setFocusable(enabled);
            button.setAlpha(enabled ? 1f : 0.45f);
        }

        private void refreshHumidityModeValue() {
            if (humidityModeValue != null) {
                humidityModeValue.setText(getHumidityModeText());
            }
        }

        private void refreshSmartButtons() {
            if (smartModeButton != null) {
                smartModeButton.setText(smartModeEnabled ? "启用" : "禁用");
                smartModeButton.setTextColor(smartModeEnabled ? AdminUi.PAGE_BG : AdminUi.ACCENT);
                smartModeButton.setBackgroundResource(
                        smartModeEnabled ? R.drawable.humidity_switch_on_bg : R.drawable.humidity_switch_off_bg);
            }
        }

        private void refreshSupplyButtons() {
            if (supplyModeButton != null) {
                String text = getSupplyModeText();
                boolean manualMode = !humidityAutoMode;
                supplyModeButton.setText(text);
                supplyModeButton.setBackgroundResource("关闭".equals(text)
                        ? R.drawable.humidity_switch_on_bg
                        : R.drawable.humidity_switch_off_bg);
                supplyModeButton.setTextColor("关闭".equals(text) ? AdminUi.PAGE_BG : AdminUi.ACCENT);
                supplyModeButton.setEnabled(manualMode);
                supplyModeButton.setClickable(manualMode);
                supplyModeButton.setFocusable(manualMode);
                supplyModeButton.setAlpha(manualMode ? 1f : 0.7f);
            }
        }

        private String getSupplyModeText() {
            int modeIndex = humidityAutoMode ? realtimeSupplyModeIndex : manualSupplyModeIndex;
            if (modeIndex < 0 || modeIndex >= SUPPLY_MODES.length) {
                return VALUE_PLACEHOLDER;
            }
            return SUPPLY_MODES[modeIndex];
        }

        private void cycleSupplyMode() {
            if (humidityAutoMode) {
                return;
            }
            int nextMode = manualSupplyModeIndex < 1 || manualSupplyModeIndex >= 3
                    ? 1 : manualSupplyModeIndex + 1;
            writeIntValue(141, nextMode, value -> {
                manualSupplyModeIndex = clampManualSupplyModeIndex(value);
                refreshSupplyButtons();
            });
        }

        private int clampManualSupplyModeIndex(int value) {
            return Math.max(1, Math.min(3, value));
        }

        private void applyLastAutomaticSupplyMode(int mode) {
            if (mode < 1) {
                manualSupplyModeIndex = -1;
                refreshSupplyButtons();
                return;
            }
            manualSupplyModeIndex = clampIndex(mode, SUPPLY_MODES.length);
            if (manualSupplyModeIndex == 0) {
                refreshSupplyButtons();
                return;
            }
            writeIntValue(141, manualSupplyModeIndex, value -> {
                manualSupplyModeIndex = clampManualSupplyModeIndex(value);
                refreshSupplyButtons();
            });
        }

        private String getHumidityModeText() {
            return HUMIDITY_MODES[humidityModeIndex];
        }

        private void writeIntValue(int address, int value, IntValueConsumer consumer) {
            DeviceGateway gateway = DeviceGateway.get(requireContext());
            if (!gateway.canWrite(address) || !isAdded()) {
                return;
            }
            consumer.accept(value);
            gateway.write(address, value, new DeviceGateway.WriteCallback() {
                @Override
                public void onSuccess() {
                    Context context = getContext();
                    if (context != null) {
                        OperationLogStore.get(context).recordLocal(
                                OperationLogFormatter.describeHomeControl(address, value));
                    }
                    requestImmediateMetricPoll();
                    requestImmediateStatePoll();
                }

                @Override
                public void onError(Exception error) {
                }
            });
        }

        private int clampIndex(int value, int length) {
            if (length <= 0) {
                return 0;
            }
            return Math.max(0, Math.min(length - 1, value));
        }

        private interface IntValueConsumer {
            void accept(int value);
        }
    }

    public static class SmartModePageFragment extends Fragment {
        private static final long POLL_INTERVAL_MS = 1500L;
        private static final String STATE_TEMPERATURE_SETTING = "temperature_setting";
        private static final String STATE_HUMIDITY_SETTING = "humidity_setting";
        private static final String STATE_SUPPLY_SETTING = "supply_setting";
        private final Handler pollHandler = new Handler(Looper.getMainLooper());
        private final Runnable pollRunnable = this::pollSmartAirValues;

        private int temperatureSetting = -1;
        private int humiditySetting = -1;
        private int supplySetting = -1;
        private boolean pollingActive;
        private boolean pollInFlight;
        private int pendingPollReads;
        private TextView temperatureValueView;
        private TextView humidityValueView;
        private TextView supplyValueView;
        private TextView freshAirTemperatureValueView;
        private TextView supplyAirTemperatureValueView;
        private TextView freshAirHumidityValueView;
        private TextView supplyAirHumidityValueView;
        private TextView freshAirAbsoluteHumidityValueView;
        private TextView supplyAirAbsoluteHumidityValueView;
        private ProgressBar filterLifeProgressBar;
        private TextView filterLifeValueView;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (savedInstanceState == null) {
                return;
            }
            temperatureSetting = savedInstanceState.getInt(STATE_TEMPERATURE_SETTING, temperatureSetting);
            humiditySetting = savedInstanceState.getInt(STATE_HUMIDITY_SETTING, humiditySetting);
            supplySetting = savedInstanceState.getInt(STATE_SUPPLY_SETTING, supplySetting);
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            Context context = requireContext();
            View root = inflater.inflate(R.layout.fragment_smart_mode_page, container, false);
            LinearLayout summaryHost = root.findViewById(R.id.smart_summary_host);
            LinearLayout tableHost = root.findViewById(R.id.smart_table_host);
            LinearLayout filterHost = root.findViewById(R.id.smart_filter_host);

            if (summaryHost != null) {
                summaryHost.removeAllViews();
                summaryHost.addView(buildSettingSummaryModule(context));
            }
            if (tableHost != null) {
                tableHost.removeAllViews();
                tableHost.addView(buildSmartAirTable(context),
                        AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
            if (filterHost != null) {
                filterHost.removeAllViews();
                filterHost.addView(buildFilterRow(context));
            }
            restoreCachedSmartAirValues();
            return root;
        }

        @Override
        public void onResume() {
            super.onResume();
            startPolling();
        }

        @Override
        public void onPause() {
            stopPolling();
            super.onPause();
        }

        @Override
        public void onDestroyView() {
            stopPolling();
            temperatureValueView = null;
            humidityValueView = null;
            supplyValueView = null;
            freshAirTemperatureValueView = null;
            supplyAirTemperatureValueView = null;
            freshAirHumidityValueView = null;
            supplyAirHumidityValueView = null;
            freshAirAbsoluteHumidityValueView = null;
            supplyAirAbsoluteHumidityValueView = null;
            filterLifeProgressBar = null;
            filterLifeValueView = null;
            super.onDestroyView();
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt(STATE_TEMPERATURE_SETTING, temperatureSetting);
            outState.putInt(STATE_HUMIDITY_SETTING, humiditySetting);
            outState.putInt(STATE_SUPPLY_SETTING, supplySetting);
        }

        private void startPolling() {
            pollingActive = true;
            requestImmediatePoll();
        }

        private void stopPolling() {
            pollingActive = false;
            pollInFlight = false;
            pendingPollReads = 0;
            pollHandler.removeCallbacks(pollRunnable);
        }

        private void requestImmediatePoll() {
            scheduleNextPoll(0L);
        }

        private void scheduleNextPoll(long delayMs) {
            pollHandler.removeCallbacks(pollRunnable);
            if (!pollingActive || !isAdded()) {
                return;
            }
            pollHandler.postDelayed(pollRunnable, Math.max(0L, delayMs));
        }

        private void pollSmartAirValues() {
            if (!pollingActive || !isAdded()) {
                return;
            }
            if (pollInFlight) {
                scheduleNextPoll(POLL_INTERVAL_MS);
                return;
            }
            int reads = countSmartAirReads();
            if (reads <= 0) {
                scheduleNextPoll(POLL_INTERVAL_MS);
                return;
            }
            pollInFlight = true;
            pendingPollReads = reads;
            refreshSettingRange();
            refreshSettingValue(supplyValueView, 15, value -> supplySetting = value);
            refreshSmartAirRange();
            refreshFilterLife();
        }

        private int countSmartAirReads() {
            int reads = 0;
            reads += hasReadableSettingRange() ? 1 : 0;
            reads += canReadValue(supplyValueView, 15) ? 1 : 0;
            reads += hasReadableSmartAirRange() ? 1 : 0;
            reads += hasReadableFilterLife() ? 1 : 0;
            return reads;
        }

        private boolean hasReadableSettingRange() {
            return canReadValue(temperatureValueView, 5)
                    || canReadValue(humidityValueView, 6);
        }

        private boolean hasReadableSmartAirRange() {
            return canReadValue(freshAirTemperatureValueView, 500)
                    || canReadValue(supplyAirTemperatureValueView, 502)
                    || canReadValue(freshAirHumidityValueView, 501)
                    || canReadValue(supplyAirHumidityValueView, 503)
                    || canReadValue(freshAirAbsoluteHumidityValueView, 506)
                    || canReadValue(supplyAirAbsoluteHumidityValueView, 507);
        }

        private boolean hasReadableFilterLife() {
            return filterLifeProgressBar != null && filterLifeValueView != null
                    && DeviceGateway.get(requireContext()).canRead(538);
        }

        private boolean canReadValue(TextView view, int address) {
            if (view == null) {
                return false;
            }
            return DeviceGateway.get(requireContext()).canRead(address);
        }

        private int refreshSettingRange() {
            if (!hasReadableSettingRange()) {
                return 0;
            }
            DeviceGateway.get(requireContext()).readRange(5, 2, new DeviceGateway.RangeCallback() {
                @Override
                public void onSuccess(int startAddress, int[] values) {
                    temperatureSetting = rangeValue(startAddress, values, 5);
                    humiditySetting = rangeValue(startAddress, values, 6);
                    refreshSummaryValues();
                    onPollReadFinished();
                }

                @Override
                public void onError(Exception error) {
                    refreshSummaryValues();
                    onPollReadFinished();
                }
            });
            return 1;
        }

        private int refreshSettingValue(TextView view, int address, IntValueConsumer consumer) {
            if (view == null) {
                return 0;
            }
            DeviceGateway gateway = DeviceGateway.get(view.getContext());
            if (!gateway.canRead(address)) {
                view.setText(VALUE_PLACEHOLDER);
                return 0;
            }
            gateway.read(address, new DeviceGateway.IntCallback() {
                @Override
                public void onSuccess(int value) {
                    consumer.accept(value);
                    view.setTag(value);
                    view.setText(formatSettingValue(address, value));
                    onPollReadFinished();
                }

                @Override
                public void onError(Exception error) {
                    refreshSummaryValues();
                    onPollReadFinished();
                }
            });
            return 1;
        }

        private int refreshSmartAirRange() {
            if (!hasReadableSmartAirRange()) {
                return 0;
            }
            DeviceGateway.get(requireContext()).readRange(500, 8, new DeviceGateway.RangeCallback() {
                @Override
                public void onSuccess(int startAddress, int[] values) {
                    updateSmartAirValue(freshAirTemperatureValueView, 500, startAddress, values);
                    updateSmartAirValue(freshAirHumidityValueView, 501, startAddress, values);
                    updateSmartAirValue(supplyAirTemperatureValueView, 502, startAddress, values);
                    updateSmartAirValue(supplyAirHumidityValueView, 503, startAddress, values);
                    updateSmartAirValue(freshAirAbsoluteHumidityValueView, 506, startAddress, values);
                    updateSmartAirValue(supplyAirAbsoluteHumidityValueView, 507, startAddress, values);
                    onPollReadFinished();
                }

                @Override
                public void onError(Exception error) {
                    restoreCachedSmartAirValues();
                    onPollReadFinished();
                }
            });
            return 1;
        }

        private int refreshFilterLife() {
            if (!hasReadableFilterLife()) {
                return 0;
            }
            DeviceGateway.get(requireContext()).read(538, new DeviceGateway.IntCallback() {
                @Override
                public void onSuccess(int value) {
                    updateFilterLife(value);
                    onPollReadFinished();
                }

                @Override
                public void onError(Exception error) {
                    restoreCachedFilterLife();
                    onPollReadFinished();
                }
            });
            return 1;
        }

        private void restoreCachedSmartAirValues() {
            if (!isAdded()) {
                return;
            }
            DeviceGateway gateway = DeviceGateway.get(requireContext());
            Integer cachedTemperatureSetting = gateway.getCachedValue(5);
            Integer cachedHumiditySetting = gateway.getCachedValue(6);
            Integer cachedSupplySetting = gateway.getCachedValue(15);
            if (cachedTemperatureSetting != null) {
                temperatureSetting = cachedTemperatureSetting;
            }
            if (cachedHumiditySetting != null) {
                humiditySetting = cachedHumiditySetting;
            }
            if (cachedSupplySetting != null) {
                supplySetting = cachedSupplySetting;
            }
            refreshSummaryValues();
            restoreCachedSmartAirValue(freshAirTemperatureValueView, 500);
            restoreCachedSmartAirValue(freshAirHumidityValueView, 501);
            restoreCachedSmartAirValue(supplyAirTemperatureValueView, 502);
            restoreCachedSmartAirValue(supplyAirHumidityValueView, 503);
            restoreCachedSmartAirValue(freshAirAbsoluteHumidityValueView, 506);
            restoreCachedSmartAirValue(supplyAirAbsoluteHumidityValueView, 507);
            restoreCachedFilterLife();
        }

        private void restoreCachedSmartAirValue(TextView view, int address) {
            if (view == null || !isAdded()) {
                return;
            }
            DeviceGateway gateway = DeviceGateway.get(requireContext());
            Integer cachedValue = gateway.getCachedValue(address);
            if (cachedValue != null) {
                view.setTag(cachedValue);
                view.setText(gateway.formatDefault(address, cachedValue));
            }
        }

        private void restoreCachedFilterLife() {
            if (!isAdded()) {
                return;
            }
            Integer cachedValue = DeviceGateway.get(requireContext()).getCachedValue(538);
            if (cachedValue != null) {
                updateFilterLife(cachedValue);
            }
        }

        private void updateFilterLife(int value) {
            int percentage = Math.max(0, Math.min(100, value));
            if (filterLifeProgressBar != null) {
                filterLifeProgressBar.setProgress(percentage);
            }
            if (filterLifeValueView != null) {
                filterLifeValueView.setText(percentage + "%");
            }
        }

        private void updateSmartAirValue(TextView view, int address, int startAddress, int[] values) {
            if (view == null) {
                return;
            }
            DeviceGateway gateway = DeviceGateway.get(requireContext());
            if (!gateway.canRead(address)) {
                view.setText(VALUE_PLACEHOLDER);
                return;
            }
            int value = rangeValue(startAddress, values, address);
            view.setTag(value);
            view.setText(gateway.formatDefault(address, value));
        }

        private void showValuePlaceholder(TextView view) {
            if (view != null) {
                view.setText(VALUE_PLACEHOLDER);
            }
        }

        private int rangeValue(int startAddress, int[] values, int address) {
            int index = address - startAddress;
            if (values == null || index < 0 || index >= values.length) {
                return 0;
            }
            return values[index];
        }

        private void onPollReadFinished() {
            if (pendingPollReads > 0) {
                pendingPollReads--;
            }
            if (pendingPollReads > 0) {
                return;
            }
            pollInFlight = false;
            if (pollingActive) {
                scheduleNextPoll(POLL_INTERVAL_MS);
            }
        }

        private LinearLayout buildSmartAirTable(Context context) {
            LinearLayout table = AdminUi.column(context);
            table.setBackground(context.getDrawable(R.drawable.smart_air_table_bg));
            table.setClipToPadding(false);
            table.setClipChildren(false);

            table.addView(buildTableRow(context, true, "新风", "", "", "送风", "", ""),
                    AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 70)));
            table.addView(dividerLine(context), AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 1)));
            ValueTableRow temperatureRow = buildValueTableRow(context, "温度", VALUE_PLACEHOLDER, "℃", "温度", VALUE_PLACEHOLDER, "℃");
            freshAirTemperatureValueView = temperatureRow.leftValueView;
            supplyAirTemperatureValueView = temperatureRow.rightValueView;
            table.addView(temperatureRow.container,
                    AdminUi.weightedLp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            table.addView(dividerLine(context), AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 1)));
            ValueTableRow humidityRow = buildValueTableRow(context, "湿度", VALUE_PLACEHOLDER, "%", "湿度", VALUE_PLACEHOLDER, "%");
            freshAirHumidityValueView = humidityRow.leftValueView;
            supplyAirHumidityValueView = humidityRow.rightValueView;
            table.addView(humidityRow.container,
                    AdminUi.weightedLp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            table.addView(dividerLine(context), AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 1)));
            ValueTableRow absoluteHumidityRow = buildValueTableRow(context, "含湿量", VALUE_PLACEHOLDER, "g/kg", "含湿量", VALUE_PLACEHOLDER, "g/kg");
            freshAirAbsoluteHumidityValueView = absoluteHumidityRow.leftValueView;
            supplyAirAbsoluteHumidityValueView = absoluteHumidityRow.rightValueView;
            table.addView(absoluteHumidityRow.container,
                    AdminUi.weightedLp(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            return table;
        }

        private LinearLayout buildTableRow(Context context, boolean headerRow,
                                           String leftLabel, String leftValue, String leftUnit,
                                           String rightLabel, String rightValue, String rightUnit) {
            LinearLayout row = AdminUi.row(context);
            row.setGravity(Gravity.CENTER_VERTICAL);

            row.addView(buildTableCell(context, headerRow, leftLabel, leftValue, leftUnit),
                    AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

            View divider = new View(context);
            divider.setBackgroundColor(Color.rgb(32, 214, 226));
            row.addView(divider, AdminUi.lp(AdminUi.dp(context, 1), ViewGroup.LayoutParams.MATCH_PARENT));

            row.addView(buildTableCell(context, headerRow, rightLabel, rightValue, rightUnit),
                    AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            return row;
        }

        private LinearLayout buildTableCell(Context context, boolean headerRow, String labelText, String valueText, String unitText) {
            LinearLayout cell = AdminUi.column(context);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(AdminUi.dp(context, 12), 0, AdminUi.dp(context, 12), 0);

            if (headerRow) {
                TextView header = AdminUi.text(context, labelText, 24, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
                header.setGravity(Gravity.CENTER);
                cell.addView(header, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return cell;
            }

            LinearLayout content = AdminUi.row(context);
            content.setGravity(Gravity.CENTER_VERTICAL);
            cell.addView(content, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView label = AdminUi.text(context, labelText, 15, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            label.setGravity(Gravity.CENTER_VERTICAL);
            content.addView(label, AdminUi.weightedLp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView value = AdminUi.text(context, valueText, 28, AdminUi.ACCENT, Typeface.BOLD);
            content.addView(value, AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView unit = AdminUi.text(context, unitText, 13, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            LinearLayout.LayoutParams unitParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            unitParams.leftMargin = AdminUi.dp(context, 8);
            content.addView(unit, unitParams);
            return cell;
        }

        private ValueTableRow buildValueTableRow(Context context,
                                                 String leftLabel, String leftValue, String leftUnit,
                                                 String rightLabel, String rightValue, String rightUnit) {
            LinearLayout row = AdminUi.row(context);
            row.setGravity(Gravity.CENTER_VERTICAL);

            ValueTableCell leftCell = buildValueTableCell(context, leftLabel, leftValue, leftUnit);
            row.addView(leftCell.container, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

            View divider = new View(context);
            divider.setBackgroundColor(Color.rgb(32, 214, 226));
            row.addView(divider, AdminUi.lp(AdminUi.dp(context, 1), ViewGroup.LayoutParams.MATCH_PARENT));

            ValueTableCell rightCell = buildValueTableCell(context, rightLabel, rightValue, rightUnit);
            row.addView(rightCell.container, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            return new ValueTableRow(row, leftCell.valueView, rightCell.valueView);
        }

        private ValueTableCell buildValueTableCell(Context context, String labelText, String valueText, String unitText) {
            LinearLayout cell = AdminUi.column(context);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(AdminUi.dp(context, 12), 0, AdminUi.dp(context, 12), 0);

            LinearLayout content = AdminUi.row(context);
            content.setGravity(Gravity.CENTER_VERTICAL);
            cell.addView(content, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView label = AdminUi.text(context, labelText, 19, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            label.setGravity(Gravity.CENTER_VERTICAL);
            content.addView(label, AdminUi.weightedLp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView value = AdminUi.text(context, valueText, 34, AdminUi.ACCENT, Typeface.BOLD);
            content.addView(value, AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView unit = AdminUi.text(context, unitText, 16, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            LinearLayout.LayoutParams unitParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            unitParams.leftMargin = AdminUi.dp(context, 8);
            content.addView(unit, unitParams);
            return new ValueTableCell(cell, value);
        }

        private View dividerLine(Context context) {
            View divider = new View(context);
            divider.setBackgroundColor(Color.rgb(42, 138, 155));
            return divider;
        }

        private LinearLayout buildSettingSummaryModule(Context context) {
            LinearLayout module = AdminUi.column(context);
            module.setPadding(AdminUi.dp(context, 14), AdminUi.dp(context, 10), AdminUi.dp(context, 14), AdminUi.dp(context, 10));
            module.setBackground(context.getDrawable(R.drawable.smart_mode_disabled_bg));
            module.setMinimumHeight(AdminUi.dp(context, 128));
            module.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout row = AdminUi.row(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            module.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 76)));

            SummaryItem temperatureItem = buildSummaryItem(context, "温度设定", "℃");
            temperatureValueView = temperatureItem.valueView;
            temperatureItem.container.setOnClickListener(v -> showIntegerInputDialog(
                    "\u6e29\u5ea6\u8bbe\u5b9a",
                    displaySettingValue(5, temperatureSetting),
                    10,
                    50,
                    value -> {
                        writeSettingValue(5, rawSettingValue(5, value), () -> {
                            temperatureSetting = rawSettingValue(5, value);
                            refreshSummaryValues();
                        });
                    }));
            AdminUi.addPressFeedback(temperatureItem.container);
            row.addView(temperatureItem.container, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

            View divider1 = new View(context);
            divider1.setBackgroundColor(Color.rgb(42, 138, 155));
            row.addView(divider1, AdminUi.lp(AdminUi.dp(context, 1), ViewGroup.LayoutParams.MATCH_PARENT));

            SummaryItem humidityItem = buildSummaryItem(context, "湿度设定", "%");
            humidityValueView = humidityItem.valueView;
            humidityItem.container.setOnClickListener(v -> showIntegerInputDialog(
                    "\u6e7f\u5ea6\u8bbe\u5b9a",
                    displaySettingValue(6, humiditySetting),
                    30,
                    100,
                    value -> {
                        writeSettingValue(6, rawSettingValue(6, value), () -> {
                            humiditySetting = rawSettingValue(6, value);
                            refreshSummaryValues();
                        });
                    }));
            AdminUi.addPressFeedback(humidityItem.container);
            row.addView(humidityItem.container, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

            View divider2 = new View(context);
            divider2.setBackgroundColor(Color.rgb(42, 138, 155));
            row.addView(divider2, AdminUi.lp(AdminUi.dp(context, 1), ViewGroup.LayoutParams.MATCH_PARENT));

            SummaryItem supplyItem = buildSummaryItem(context, "送风量", "%");
            supplyValueView = supplyItem.valueView;
            supplyItem.container.setOnClickListener(v -> showIntegerInputDialog(
                    "\u9001\u98ce\u91cf",
                    displaySettingValue(15, supplySetting),
                    50,
                    100,
                    value -> {
                        writeSettingValue(15, rawSettingValue(15, value), () -> {
                            supplySetting = rawSettingValue(15, value);
                            refreshSummaryValues();
                        });
                    }));
            AdminUi.addPressFeedback(supplyItem.container);
            row.addView(supplyItem.container, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            return module;
        }

        private SummaryItem buildSummaryItem(Context context, String labelText, String unitText) {
            LinearLayout item = AdminUi.column(context);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setFocusable(true);

            TextView label = AdminUi.text(context, labelText, 24, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            item.addView(label, AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout valueRow = AdminUi.row(context);
            valueRow.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams valueRowParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            valueRowParams.topMargin = AdminUi.dp(context, 4);
            item.addView(valueRow, valueRowParams);

            TextView value = AdminUi.text(context, "", 36, AdminUi.ACCENT, Typeface.BOLD);
            valueRow.addView(value, AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView unit = AdminUi.text(context, unitText, 18, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            LinearLayout.LayoutParams unitParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            unitParams.leftMargin = AdminUi.dp(context, 4);
            valueRow.addView(unit, unitParams);
            return new SummaryItem(item, value);
        }

        private void refreshSummaryValues() {
            if (temperatureValueView != null) {
                temperatureValueView.setText(temperatureSetting < 0
                        ? VALUE_PLACEHOLDER : formatSettingValue(5, temperatureSetting));
            }
            if (humidityValueView != null) {
                humidityValueView.setText(humiditySetting < 0
                        ? VALUE_PLACEHOLDER : formatSettingValue(6, humiditySetting));
            }
            if (supplyValueView != null) {
                supplyValueView.setText(supplySetting < 0
                        ? VALUE_PLACEHOLDER : formatSettingValue(15, supplySetting));
            }
        }

        private String formatSettingValue(int address, int rawValue) {
            return DeviceGateway.get(requireContext()).formatDefault(address, rawValue);
        }

        private int displaySettingValue(int address, int rawValue) {
            if (rawValue < 0) {
                return rawValue;
            }
            return DeviceGateway.get(requireContext()).toDisplayValue(address, rawValue);
        }

        private int rawSettingValue(int address, int displayValue) {
            return DeviceGateway.get(requireContext()).toRawValue(address, displayValue);
        }

        private void writeSettingValue(int address, int value, Runnable onSuccess) {
            DeviceGateway gateway = DeviceGateway.get(requireContext());
            if (!gateway.canWrite(address) || !isAdded()) {
                return;
            }
            onSuccess.run();
            gateway.write(address, value, new DeviceGateway.WriteCallback() {
                @Override
                public void onSuccess() {
                    Context context = getContext();
                    if (context != null) {
                        OperationLogStore.get(context).recordLocal(
                                OperationLogFormatter.describeSetting(address,
                                        DeviceGateway.get(context).toDisplayValue(address, value)));
                    }
                    requestImmediatePoll();
                }

                @Override
                public void onError(Exception error) {
                    requestImmediatePoll();
                }
            });
        }

        private void showDecimalInputDialog(String title, double currentValue, boolean percentRange, DoubleValueConsumer onValueConfirmed) {
            if (!isAdded()) {
                return;
            }
            Context context = requireContext();
            EditText input = createInputField(
                    context,
                    Double.isNaN(currentValue) ? "" : formatOneDecimal(currentValue),
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            InputDialogParts dialogParts = buildInputDialog(
                    context,
                    title,
                    percentRange ? "\u5141\u8bb8\u8303\u56f4 0 - 100" : "\u8bf7\u8f93\u5165\u6570\u503c",
                    input);
            dialogParts.cancelButton.setOnClickListener(v -> dialogParts.dialog.dismiss());
            dialogParts.confirmButton.setOnClickListener(v -> {
                String text = input.getText() == null ? "" : input.getText().toString().trim();
                try {
                    double value = Double.parseDouble(text);
                    if (!Double.isFinite(value)) {
                        throw new NumberFormatException("not finite");
                    }
                    if (percentRange && (value < 0.0 || value > 100.0)) {
                        Toast.makeText(context, "\u8bf7\u8f93\u5165 0-100 \u4e4b\u95f4\u7684\u6570\u503c", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    onValueConfirmed.accept(value);
                    dialogParts.dialog.dismiss();
                } catch (NumberFormatException error) {
                    Toast.makeText(context, "\u8bf7\u8f93\u5165\u6709\u6548\u6570\u503c", Toast.LENGTH_SHORT).show();
                }
            });
            dialogParts.dialog.show();
            styleCompactDialog(dialogParts.dialog);
            input.requestFocus();
            if (dialogParts.dialog.getWindow() != null) {
                dialogParts.dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        }

        private void showIntegerInputDialog(String title, int currentValue, int minValue, int maxValue, IntValueConsumer onValueConfirmed) {
            if (!isAdded()) {
                return;
            }
            Context context = requireContext();
            EditText input = createInputField(
                    context,
                    currentValue < 0 ? "" : String.valueOf(currentValue),
                    InputType.TYPE_CLASS_NUMBER);
            InputDialogParts dialogParts = buildInputDialog(
                    context,
                    title,
                    "\u5141\u8bb8\u8303\u56f4 " + minValue + " - " + maxValue,
                    input);
            dialogParts.cancelButton.setOnClickListener(v -> dialogParts.dialog.dismiss());
            dialogParts.confirmButton.setOnClickListener(v -> {
                String text = input.getText() == null ? "" : input.getText().toString().trim();
                try {
                    int value = Integer.parseInt(text);
                    if (value < minValue || value > maxValue) {
                        Toast.makeText(context, "\u8bf7\u8f93\u5165 " + minValue + "-" + maxValue + " \u4e4b\u95f4\u7684\u6574\u6570", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    onValueConfirmed.accept(value);
                    dialogParts.dialog.dismiss();
                } catch (NumberFormatException error) {
                    Toast.makeText(context, "\u8bf7\u8f93\u5165\u6709\u6548\u6574\u6570", Toast.LENGTH_SHORT).show();
                }
            });
            dialogParts.dialog.show();
            styleCompactDialog(dialogParts.dialog);
            input.requestFocus();
            if (dialogParts.dialog.getWindow() != null) {
                dialogParts.dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        }

        private InputDialogParts buildInputDialog(Context context, String title, String range, EditText input) {
            LinearLayout root = AdminUi.column(context);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(AdminUi.dp(context, 16), AdminUi.dp(context, 16), AdminUi.dp(context, 16), AdminUi.dp(context, 16));
            root.setBackground(AdminUi.bg(Color.BLACK, 8, AdminUi.PANEL_STROKE, 1, context));

            TextView titleView = AdminUi.text(context, title, 20, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
            root.addView(titleView, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView rangeView = AdminUi.text(context, range, 14, AdminUi.TEXT_SECONDARY, Typeface.NORMAL);
            LinearLayout.LayoutParams rangeParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rangeParams.topMargin = AdminUi.dp(context, 4);
            root.addView(rangeView, rangeParams);

            View divider = new View(context);
            divider.setBackgroundColor(AdminUi.PANEL_STROKE);
            LinearLayout.LayoutParams dividerParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 1));
            dividerParams.topMargin = AdminUi.dp(context, 12);
            root.addView(divider, dividerParams);

            input.setHint(INPUT_HINT_PLACEHOLDER);

            LinearLayout.LayoutParams inputParams = AdminUi.lp(
                    AdminUi.compactNumericInputWidth(context),
                    AdminUi.compactNumericInputHeight(context));
            inputParams.topMargin = AdminUi.dp(context, 14);
            root.addView(input, inputParams);

            LinearLayout buttonRow = AdminUi.row(context);
            buttonRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams buttonRowParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            buttonRowParams.topMargin = AdminUi.dp(context, 16);
            root.addView(buttonRow, buttonRowParams);

            TextView cancelButton = createDialogActionButton(context, "\u53d6\u6d88", AdminUi.TEXT_SECONDARY, Color.rgb(12, 53, 73));
            buttonRow.addView(cancelButton, AdminUi.weightedLp(0, AdminUi.dp(context, 46), 1));

            View gap = new View(context);
            buttonRow.addView(gap, AdminUi.lp(AdminUi.dp(context, 10), 1));

            TextView confirmButton = createDialogActionButton(context, "\u786e\u5b9a", AdminUi.PAGE_BG, AdminUi.ACCENT);
            buttonRow.addView(confirmButton, AdminUi.weightedLp(0, AdminUi.dp(context, 46), 1));

            AlertDialog dialog = new AlertDialog.Builder(
                    new ContextThemeWrapper(context, R.style.BlackInputDialogTheme))
                    .setView(root)
                    .create();
            return new InputDialogParts(dialog, cancelButton, confirmButton);
        }

        private EditText createInputField(Context context, String value, int inputType) {
            EditText input = new EditText(context);
            input.setInputType(inputType);
            input.setText(value);
            input.setSelectAllOnFocus(true);
            AdminUi.styleCompactNumericInput(input);
            return input;
        }

        private void styleCompactDialog(AlertDialog dialog) {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        requireContext().getDrawable(R.drawable.input_dialog_black_bg));
                dialog.getWindow().setLayout(
                        AdminUi.compactDialogWidth(requireContext()),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }

        private TextView createDialogActionButton(Context context, String text, int textColor, int backgroundColor) {
            TextView button = AdminUi.text(context, text, 16, textColor, Typeface.BOLD);
            button.setBackground(AdminUi.bg(backgroundColor, 4, AdminUi.PANEL_STROKE, 1, context));
            button.setClickable(true);
            button.setFocusable(true);
            AdminUi.addPressFeedback(button);
            return button;
        }

        private LinearLayout buildFilterRow(Context context) {
            LinearLayout filterRow = AdminUi.row(context);
            filterRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams filterParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 50));
            filterParams.topMargin = AdminUi.dp(context, 12);
            filterRow.setLayoutParams(filterParams);

            TextView filterIcon = AdminUi.text(context, "◼", 13, AdminUi.ACCENT, Typeface.BOLD);
            filterRow.addView(filterIcon, AdminUi.lp(AdminUi.dp(context, 26), ViewGroup.LayoutParams.MATCH_PARENT));
            filterIcon.setTextSize(18);

            TextView filterLabel = AdminUi.text(context, "滤芯寿命", 13, AdminUi.TEXT_PRIMARY, Typeface.NORMAL);
            LinearLayout.LayoutParams filterLabelParams = AdminUi.lp(AdminUi.dp(context, 82), ViewGroup.LayoutParams.MATCH_PARENT);
            filterLabelParams.leftMargin = AdminUi.dp(context, 4);
            filterRow.addView(filterLabel, filterLabelParams);
            filterLabel.setTextSize(18);

            ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(0);
            progressBar.setProgressDrawable(context.getDrawable(R.drawable.progress_filter));
            filterLifeProgressBar = progressBar;
            LinearLayout.LayoutParams progressParams = AdminUi.lp(AdminUi.dp(context, 400), AdminUi.dp(context, 16));
            progressParams.leftMargin = AdminUi.dp(context, 8);
            progressParams.rightMargin = AdminUi.dp(context, 8);
            filterRow.addView(progressBar, progressParams);

            TextView progressText = AdminUi.text(context, VALUE_PLACEHOLDER, 13, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
            progressText.setSingleLine(true);
            progressText.setEllipsize(null);
            filterRow.addView(progressText, AdminUi.lp(AdminUi.dp(context, 60), ViewGroup.LayoutParams.MATCH_PARENT));
            progressText.setTextSize(18);
            filterLifeValueView = progressText;
            return filterRow;
        }

        private String formatOneDecimal(double value) {
            return String.format(java.util.Locale.CHINA, "%.1f", value);
        }

        private interface DoubleValueConsumer {
            void accept(double value);
        }

        private interface IntValueConsumer {
            void accept(int value);
        }

        private static final class ValueTableRow {
            final LinearLayout container;
            final TextView leftValueView;
            final TextView rightValueView;

            ValueTableRow(LinearLayout container, TextView leftValueView, TextView rightValueView) {
                this.container = container;
                this.leftValueView = leftValueView;
                this.rightValueView = rightValueView;
            }
        }

        private static final class ValueTableCell {
            final LinearLayout container;
            final TextView valueView;

            ValueTableCell(LinearLayout container, TextView valueView) {
                this.container = container;
                this.valueView = valueView;
            }
        }

        private static final class InputDialogParts {
            final AlertDialog dialog;
            final TextView cancelButton;
            final TextView confirmButton;

            InputDialogParts(AlertDialog dialog, TextView cancelButton, TextView confirmButton) {
                this.dialog = dialog;
                this.cancelButton = cancelButton;
                this.confirmButton = confirmButton;
            }
        }

        private static final class SummaryItem {
            final LinearLayout container;
            final TextView valueView;

            SummaryItem(LinearLayout container, TextView valueView) {
                this.container = container;
                this.valueView = valueView;
            }
        }
    }
}

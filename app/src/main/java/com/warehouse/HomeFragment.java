package com.warehouse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
import androidx.fragment.app.Fragment;
import androidx.core.widget.ImageViewCompat;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

public class HomeFragment extends Fragment {
    private static final float PANEL_SCALE = 1.25f;
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
        view.post(this::applyPanelScale);
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter wifiFilter = new IntentFilter();
        wifiFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        wifiFilter.addAction("android.net.wifi.STATE_CHANGE");
        requireContext().registerReceiver(wifiStateReceiver, wifiFilter);
    }

    @Override
    public void onStop() {
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
    }

    private void setupPager() {
        if (pager == null) {
            return;
        }
        pager.setAdapter(new HomePagerAdapter(this));
        pager.setOffscreenPageLimit(2);
        pager.setCurrentItem(0, false);
    }

    private void setupHeader() {
        if (titleView != null) {
            titleView.setText("全效舱环境一体机");
            titleView.setTextColor(Color.rgb(36, 200, 217));
        }
        if (weatherIconView != null) {
            weatherIconView.setText(VALUE_PLACEHOLDER);
        }
        if (outdoorTempView != null) {
            outdoorTempView.setText(VALUE_PLACEHOLDER);
        }
        if (outdoorStatusView != null) {
            outdoorStatusView.setText(VALUE_PLACEHOLDER);
        }
        if (adminIconView != null) {
            adminIconView.setImageResource(R.drawable.administrator);
            ImageViewCompat.setImageTintList(adminIconView, ColorStateList.valueOf(AdminUi.ACCENT));
            adminIconView.setOnClickListener(v -> openAdminPage());
        }
        if (wifiIconView != null) {
            wifiIconView.setImageResource(R.drawable.wifi);
            ImageViewCompat.setImageTintList(wifiIconView, ColorStateList.valueOf(AdminUi.ACCENT));
        }
    }

    private void openAdminPage() {
        if (!isAdded()) {
            return;
        }
        getParentFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new AdminContainerFragment())
                .addToBackStack("admin_page")
                .commit();
    }

    private void applyPanelScale() {
        if (panel == null) {
            return;
        }
        panel.setPivotX(panel.getWidth() / 2f);
        panel.setPivotY(panel.getHeight() / 2f);
        panel.setScaleX(PANEL_SCALE);
        panel.setScaleY(PANEL_SCALE);
        ViewParent parent = panel.getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            group.setClipChildren(false);
            group.setClipToPadding(false);
        }
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
        wifiIconView.setVisibility(wifiConnected ? View.VISIBLE : View.GONE);
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
        private boolean switchOn;
        private boolean humidityAutoMode = true;
        private int humidityModeIndex = 0;
        private boolean smartModeEnabled = true;
        private int supplyModeIndex = 0;
        private FrameLayout switchButton;
        private TextView switchButtonText;
        private TextView autoButton;
        private TextView humidityModeValue;
        private TextView smartModeButton;
        private TextView supplyModeButton;

        private static final String[] SUPPLY_MODES = {"自动", "节能", "舒适", "强力"};
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
                updateSwitchButton();
                switchButton.setOnClickListener(v -> {
                    switchOn = !switchOn;
                    updateSwitchButton();
                });
            }

            if (autoButton != null) {
                autoButton.setText("自动");
                autoButton.setOnClickListener(v -> {
                    humidityAutoMode = !humidityAutoMode;
                    refreshHumidityModeButton();
                });
            }

            buildMetricGrid(context, metricGrid);
            buildHumidityRow(context, humidityRow);
            buildModuleRow(context, moduleRow);
            refreshHumidityModeButton();
            refreshHumidityModeValue();
            refreshSmartButtons();
            refreshSupplyButtons();
            return root;
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

            addMetricCell(firstRow, context, "温度", VALUE_PLACEHOLDER, "℃", AdminUi.TEXT_PRIMARY);
            addMetricCell(firstRow, context, "湿度", VALUE_PLACEHOLDER, "%", AdminUi.TEXT_PRIMARY);
            addMetricCell(secondRow, context, "PM2.5", VALUE_PLACEHOLDER, "μg/m³", Color.rgb(91, 154, 255));
            addMetricCell(secondRow, context, "CO₂", VALUE_PLACEHOLDER, "ppm", Color.rgb(245, 166, 35));
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

            TextView humidityLabel = AdminUi.text(context, "含湿量", 14, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            humidityContent.addView(humidityLabel, AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView humidityValue = AdminUi.text(context, VALUE_PLACEHOLDER, 32, AdminUi.ACCENT, Typeface.BOLD);
            LinearLayout.LayoutParams humidityValueParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            humidityValueParams.leftMargin = AdminUi.dp(context, 10);
            humidityContent.addView(humidityValue, humidityValueParams);

            TextView humidityUnit = AdminUi.text(context, "g/kg", 14, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            LinearLayout.LayoutParams humidityUnitParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            humidityUnitParams.leftMargin = AdminUi.dp(context, 8);
            humidityContent.addView(humidityUnit, humidityUnitParams);
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

        private void addMetricCell(LinearLayout parent, Context context, String label, String value, String unit, int valueColor) {
            LinearLayout cell = AdminUi.column(context);
            cell.setGravity(Gravity.CENTER);
            parent.addView(cell, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

            TextView labelView = AdminUi.text(context, label, 14, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            labelView.setGravity(Gravity.CENTER);
            cell.addView(labelView, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 24)));

            LinearLayout valueRow = AdminUi.row(context);
            valueRow.setGravity(Gravity.CENTER);
            cell.addView(valueRow, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0));
            LinearLayout.LayoutParams valueRowParams = (LinearLayout.LayoutParams) valueRow.getLayoutParams();
            valueRowParams.weight = 1;
            valueRow.setLayoutParams(valueRowParams);

            TextView valueView = AdminUi.text(context, value, 32, valueColor, Typeface.NORMAL);
            valueRow.addView(valueView, AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView unitView = AdminUi.text(context, unit, 14, AdminUi.TEXT_SECONDARY, Typeface.NORMAL);
            LinearLayout.LayoutParams unitParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            unitParams.leftMargin = AdminUi.dp(context, 2);
            valueRow.addView(unitView, unitParams);
        }

        private LinearLayout buildHumidityModeModule(Context context) {
            LinearLayout module = AdminUi.row(context);
            module.setGravity(Gravity.CENTER_VERTICAL);
            module.setBackgroundColor(Color.TRANSPARENT);

            LinearLayout modeCluster = AdminUi.row(context);
            modeCluster.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams modeClusterParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            module.addView(modeCluster, modeClusterParams);

            TextView leftBracket = createBracketButton(context, "<");
            leftBracket.setOnClickListener(v -> cycleHumidityMode(-1));
            modeCluster.addView(leftBracket, AdminUi.lp(AdminUi.dp(context, 18), AdminUi.dp(context, 24)));

            humidityModeValue = AdminUi.text(context, getHumidityModeText(), 16, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
            humidityModeValue.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams humidityModeValueParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            humidityModeValueParams.leftMargin = AdminUi.dp(context, 6);
            humidityModeValueParams.rightMargin = AdminUi.dp(context, 6);
            modeCluster.addView(humidityModeValue, humidityModeValueParams);

            TextView rightBracket = createBracketButton(context, ">");
            rightBracket.setOnClickListener(v -> cycleHumidityMode(1));
            modeCluster.addView(rightBracket, AdminUi.lp(AdminUi.dp(context, 28), AdminUi.dp(context, 24)));

            return module;
        }

        private void addModeModule(Context context, LinearLayout parent, String titleText, boolean smartMode) {
            LinearLayout module = AdminUi.column(context);
            module.setPadding(AdminUi.dp(context, 12), AdminUi.dp(context, 10), AdminUi.dp(context, 12), AdminUi.dp(context, 10));
            module.setBackground(AdminUi.bg(Color.rgb(9, 57, 80), 8, Color.rgb(18, 96, 118), 1, context));
            LinearLayout.LayoutParams moduleParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (parent.getChildCount() > 0) {
                moduleParams.leftMargin = AdminUi.dp(context, 8);
            }
            parent.addView(module, moduleParams);

            LinearLayout row = AdminUi.row(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            module.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 28)));

            TextView button = smartMode
                    ? createModeToggleButton(context, smartModeEnabled ? "启用" : "禁用")
                    : createSupplyModeButton(context, getSupplyModeText());
            if (smartMode) {
                TextView title = AdminUi.text(context, titleText, 14, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
                title.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams titleParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                row.addView(title, titleParams);
                row.addView(button, AdminUi.lp(AdminUi.dp(context, 72), AdminUi.dp(context, 28)));
            } else {
                row.addView(button, AdminUi.lp(AdminUi.dp(context, 72), AdminUi.dp(context, 28)));
                TextView title = AdminUi.text(context, titleText, 14, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
                title.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                LinearLayout.LayoutParams titleParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                row.addView(title, titleParams);
            }

            if (smartMode) {
                smartModeButton = button;
                button.setOnClickListener(v -> {
                    smartModeEnabled = !smartModeEnabled;
                    refreshSmartButtons();
                });
            } else {
                supplyModeButton = button;
                button.setOnClickListener(v -> {
                    supplyModeIndex = (supplyModeIndex + 1) % SUPPLY_MODES.length;
                    refreshSupplyButtons();
                });
            }
        }

        private TextView createModeToggleButton(Context context, String text) {
            TextView button = AdminUi.text(context, text, 12, AdminUi.PAGE_BG, Typeface.BOLD);
            button.setBackgroundResource(R.drawable.humidity_switch_on_bg);
            button.setClickable(true);
            button.setFocusable(true);
            return button;
        }

        private TextView createSupplyModeButton(Context context, String text) {
            TextView button = AdminUi.text(context, text, 12, AdminUi.ACCENT, Typeface.BOLD);
            button.setBackgroundResource("自动".equals(text)
                    ? R.drawable.humidity_switch_on_bg
                    : R.drawable.humidity_switch_off_bg);
            button.setTextColor("自动".equals(text) ? AdminUi.PAGE_BG : AdminUi.ACCENT);
            button.setClickable(true);
            button.setFocusable(true);
            return button;
        }

        private TextView createHumidityModeButton(Context context, String text) {
            TextView button = AdminUi.text(context, text, 12, AdminUi.PAGE_BG, Typeface.BOLD);
            button.setTextColor(humidityAutoMode ? AdminUi.PAGE_BG : AdminUi.ACCENT);
            button.setBackgroundResource(humidityAutoMode ? R.drawable.humidity_switch_on_bg : R.drawable.humidity_switch_off_bg);
            button.setClickable(true);
            button.setFocusable(true);
            return button;
        }

        private TextView createBracketButton(Context context, String text) {
            TextView button = AdminUi.text(context, text, 22, AdminUi.ACCENT, Typeface.BOLD);
            button.setGravity(Gravity.CENTER);
            button.setPadding(0, 0, 0, 0);
            button.setClickable(true);
            button.setFocusable(true);
            button.setMinWidth(AdminUi.dp(context, 18));
            button.setMinHeight(AdminUi.dp(context, 24));
            return button;
        }

        private void cycleHumidityMode(int delta) {
            int length = HUMIDITY_MODES.length;
            humidityModeIndex = (humidityModeIndex + delta + length) % length;
            refreshHumidityModeValue();
        }

        private void refreshHumidityModeButton() {
            if (autoButton != null) {
                autoButton.setText(humidityAutoMode ? "自动" : "手动");
                autoButton.setTextColor(humidityAutoMode ? AdminUi.PAGE_BG : AdminUi.ACCENT);
                autoButton.setBackgroundResource(
                        humidityAutoMode ? R.drawable.humidity_switch_on_bg : R.drawable.humidity_switch_off_bg);
            }
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
                supplyModeButton.setText(text);
                supplyModeButton.setBackgroundResource("自动".equals(text)
                        ? R.drawable.humidity_switch_on_bg
                        : R.drawable.humidity_switch_off_bg);
                supplyModeButton.setTextColor("自动".equals(text) ? AdminUi.PAGE_BG : AdminUi.ACCENT);
            }
        }

        private String getSupplyModeText() {
            return SUPPLY_MODES[supplyModeIndex];
        }

        private String getHumidityModeText() {
            return HUMIDITY_MODES[humidityModeIndex];
        }
    }

    public static class SmartModePageFragment extends Fragment {
        private static final String STATE_TEMPERATURE_SETTING = "temperature_setting";
        private static final String STATE_HUMIDITY_SETTING = "humidity_setting";
        private static final String STATE_SUPPLY_SETTING = "supply_setting";

        private double temperatureSetting = Double.NaN;
        private double humiditySetting = Double.NaN;
        private int supplySetting = -1;
        private TextView temperatureValueView;
        private TextView humidityValueView;
        private TextView supplyValueView;

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (savedInstanceState == null) {
                return;
            }
            temperatureSetting = savedInstanceState.getDouble(STATE_TEMPERATURE_SETTING, temperatureSetting);
            humiditySetting = savedInstanceState.getDouble(STATE_HUMIDITY_SETTING, humiditySetting);
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
                tableHost.addView(buildSmartAirTable(context));
            }
            if (filterHost != null) {
                filterHost.removeAllViews();
                filterHost.addView(buildFilterRow(context));
            }
            refreshSummaryValues();
            return root;
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putDouble(STATE_TEMPERATURE_SETTING, temperatureSetting);
            outState.putDouble(STATE_HUMIDITY_SETTING, humiditySetting);
            outState.putInt(STATE_SUPPLY_SETTING, supplySetting);
        }

        private LinearLayout buildSmartAirTable(Context context) {
            LinearLayout table = AdminUi.column(context);
            table.setBackground(context.getDrawable(R.drawable.smart_air_table_bg));
            table.setClipToPadding(false);
            table.setClipChildren(false);

            table.addView(buildTableRow(context, true, "新风", "", "", "送风", "", ""),
                    AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 58)));
            table.addView(dividerLine(context), AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 1)));
            table.addView(buildTableRow(context, false, "温度", VALUE_PLACEHOLDER, "℃", "温度", VALUE_PLACEHOLDER, "℃"),
                    AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 44)));
            table.addView(dividerLine(context), AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 1)));
            table.addView(buildTableRow(context, false, "湿度", VALUE_PLACEHOLDER, "%", "湿度", VALUE_PLACEHOLDER, "%"),
                    AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 44)));
            table.addView(dividerLine(context), AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 1)));
            table.addView(buildTableRow(context, false, "含湿量", VALUE_PLACEHOLDER, "g/kg", "含湿量", VALUE_PLACEHOLDER, "g/kg"),
                    AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 44)));
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
                TextView header = AdminUi.text(context, labelText, 18, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
                header.setGravity(Gravity.CENTER);
                cell.addView(header, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return cell;
            }

            LinearLayout content = AdminUi.row(context);
            content.setGravity(Gravity.CENTER_VERTICAL);
            cell.addView(content, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView label = AdminUi.text(context, labelText, 13, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            label.setGravity(Gravity.CENTER_VERTICAL);
            content.addView(label, AdminUi.weightedLp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView value = AdminUi.text(context, valueText, 22, AdminUi.ACCENT, Typeface.BOLD);
            content.addView(value, AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView unit = AdminUi.text(context, unitText, 11, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            LinearLayout.LayoutParams unitParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            unitParams.leftMargin = AdminUi.dp(context, 8);
            content.addView(unit, unitParams);
            return cell;
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
            module.setMinimumHeight(AdminUi.dp(context, 108));
            module.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout row = AdminUi.row(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            module.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 60)));

            SummaryItem temperatureItem = buildSummaryItem(context, "温度设定", "℃");
            temperatureValueView = temperatureItem.valueView;
            temperatureItem.container.setOnClickListener(v -> showDecimalInputDialog(
                    "\u6e29\u5ea6\u8bbe\u5b9a",
                    temperatureSetting,
                    false,
                    value -> {
                        temperatureSetting = value;
                        refreshSummaryValues();
                    }));
            row.addView(temperatureItem.container, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

            View divider1 = new View(context);
            divider1.setBackgroundColor(Color.rgb(42, 138, 155));
            row.addView(divider1, AdminUi.lp(AdminUi.dp(context, 1), ViewGroup.LayoutParams.MATCH_PARENT));

            SummaryItem humidityItem = buildSummaryItem(context, "湿度设定", "%");
            humidityValueView = humidityItem.valueView;
            humidityItem.container.setOnClickListener(v -> showDecimalInputDialog(
                    "\u6e7f\u5ea6\u8bbe\u5b9a",
                    humiditySetting,
                    true,
                    value -> {
                        humiditySetting = value;
                        refreshSummaryValues();
                    }));
            row.addView(humidityItem.container, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

            View divider2 = new View(context);
            divider2.setBackgroundColor(Color.rgb(42, 138, 155));
            row.addView(divider2, AdminUi.lp(AdminUi.dp(context, 1), ViewGroup.LayoutParams.MATCH_PARENT));

            SummaryItem supplyItem = buildSummaryItem(context, "送风量", "%");
            supplyValueView = supplyItem.valueView;
            supplyItem.container.setOnClickListener(v -> showIntegerInputDialog(
                    "\u9001\u98ce\u91cf",
                    supplySetting,
                    0,
                    100,
                    value -> {
                        supplySetting = value;
                        refreshSummaryValues();
                    }));
            row.addView(supplyItem.container, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            return module;
        }

        private SummaryItem buildSummaryItem(Context context, String labelText, String unitText) {
            LinearLayout item = AdminUi.column(context);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setFocusable(true);

            TextView label = AdminUi.text(context, labelText, 16, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            item.addView(label, AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout valueRow = AdminUi.row(context);
            valueRow.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams valueRowParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            valueRowParams.topMargin = AdminUi.dp(context, 4);
            item.addView(valueRow, valueRowParams);

            TextView value = AdminUi.text(context, "", 22, AdminUi.ACCENT, Typeface.BOLD);
            valueRow.addView(value, AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView unit = AdminUi.text(context, unitText, 13, AdminUi.TEXT_SECONDARY, Typeface.BOLD);
            LinearLayout.LayoutParams unitParams = AdminUi.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            unitParams.leftMargin = AdminUi.dp(context, 4);
            valueRow.addView(unit, unitParams);
            return new SummaryItem(item, value);
        }

        private void refreshSummaryValues() {
            if (temperatureValueView != null) {
                temperatureValueView.setText(Double.isNaN(temperatureSetting) ? VALUE_PLACEHOLDER : formatOneDecimal(temperatureSetting));
            }
            if (humidityValueView != null) {
                humidityValueView.setText(Double.isNaN(humiditySetting) ? VALUE_PLACEHOLDER : formatOneDecimal(humiditySetting));
            }
            if (supplyValueView != null) {
                supplyValueView.setText(supplySetting < 0 ? VALUE_PLACEHOLDER : String.valueOf(supplySetting));
            }
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
                    percentRange ? title + " 0-100" : title,
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
                    title + " " + minValue + "-" + maxValue,
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
        }

        private InputDialogParts buildInputDialog(Context context, String hint, EditText input) {
            LinearLayout root = AdminUi.column(context);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(AdminUi.dp(context, 12), AdminUi.dp(context, 12), AdminUi.dp(context, 12), AdminUi.dp(context, 12));
            root.setBackground(AdminUi.bg(AdminUi.PANEL_BG, 8, AdminUi.PANEL_STROKE, 1, context));
            input.setHint(INPUT_HINT_PLACEHOLDER);

            LinearLayout.LayoutParams inputParams = AdminUi.lp(AdminUi.mm(context, 44), ViewGroup.LayoutParams.WRAP_CONTENT);
            root.addView(input, inputParams);

            LinearLayout buttonRow = AdminUi.row(context);
            buttonRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams buttonRowParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            buttonRowParams.topMargin = AdminUi.dp(context, 12);
            root.addView(buttonRow, buttonRowParams);

            TextView cancelButton = createDialogActionButton(context, "\u53d6\u6d88", AdminUi.TEXT_SECONDARY, Color.rgb(12, 53, 73));
            buttonRow.addView(cancelButton, AdminUi.weightedLp(0, AdminUi.dp(context, 54), 1));

            View gap = new View(context);
            buttonRow.addView(gap, AdminUi.lp(AdminUi.dp(context, 10), 1));

            TextView confirmButton = createDialogActionButton(context, "\u786e\u5b9a", AdminUi.PAGE_BG, AdminUi.ACCENT);
            buttonRow.addView(confirmButton, AdminUi.weightedLp(0, AdminUi.dp(context, 54), 1));

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setView(root)
                    .create();
            return new InputDialogParts(dialog, cancelButton, confirmButton);
        }

        private EditText createInputField(Context context, String value, int inputType) {
            EditText input = new EditText(context);
            input.setInputType(inputType);
            input.setText(value);
            input.setSelectAllOnFocus(true);
            input.setSingleLine(true);
            input.setTextColor(AdminUi.TEXT_PRIMARY);
            input.setHintTextColor(AdminUi.TEXT_SECONDARY);
            input.setTextSize(20);
            input.setBackground(AdminUi.bg(Color.rgb(10, 38, 54), 6, Color.rgb(42, 138, 155), 1, context));
            input.setPadding(AdminUi.dp(context, 14), AdminUi.dp(context, 12), AdminUi.dp(context, 14), AdminUi.dp(context, 12));
            return input;
        }

        private void styleCompactDialog(AlertDialog dialog) {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(
                        AdminUi.bg(AdminUi.PANEL_BG, 8, AdminUi.PANEL_STROKE, 1, requireContext()));
                dialog.getWindow().setLayout(AdminUi.mm(requireContext(), 58), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }

        private TextView createDialogActionButton(Context context, String text, int textColor, int backgroundColor) {
            TextView button = AdminUi.text(context, text, 18, textColor, Typeface.BOLD);
            button.setBackground(AdminUi.bg(backgroundColor, 6, AdminUi.PANEL_STROKE, 1, context));
            button.setClickable(true);
            button.setFocusable(true);
            return button;
        }

        private LinearLayout buildFilterRow(Context context) {
            LinearLayout filterRow = AdminUi.row(context);
            filterRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams filterParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(context, 42));
            filterParams.topMargin = AdminUi.dp(context, 12);
            filterRow.setLayoutParams(filterParams);

            TextView filterIcon = AdminUi.text(context, "◼", 13, AdminUi.ACCENT, Typeface.BOLD);
            filterRow.addView(filterIcon, AdminUi.lp(AdminUi.dp(context, 18), ViewGroup.LayoutParams.MATCH_PARENT));

            TextView filterLabel = AdminUi.text(context, "滤芯寿命", 13, AdminUi.TEXT_PRIMARY, Typeface.NORMAL);
            LinearLayout.LayoutParams filterLabelParams = AdminUi.lp(AdminUi.dp(context, 70), ViewGroup.LayoutParams.MATCH_PARENT);
            filterLabelParams.leftMargin = AdminUi.dp(context, 4);
            filterRow.addView(filterLabel, filterLabelParams);

            ProgressBar progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(0);
            progressBar.setProgressDrawable(context.getDrawable(R.drawable.progress_filter));
            LinearLayout.LayoutParams progressParams = AdminUi.weightedLp(0, AdminUi.dp(context, 9), 1);
            progressParams.leftMargin = AdminUi.dp(context, 8);
            progressParams.rightMargin = AdminUi.dp(context, 8);
            filterRow.addView(progressBar, progressParams);

            TextView progressText = AdminUi.text(context, VALUE_PLACEHOLDER, 13, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
            filterRow.addView(progressText, AdminUi.lp(AdminUi.dp(context, 34), ViewGroup.LayoutParams.WRAP_CONTENT));
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

package com.warehouse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NewHomeTestFragment extends Fragment {
    private View rootView;
    private View panel;
    private View leftCard;
    private View middleCard;
    private View rightCard;
    private View filterCard;
    private View energyCard;
    private View targetPanel;
    private View fanPanel;
    private View fanOffButton;
    private View fanLowButton;
    private View fanMidButton;
    private View fanHighButton;
    private View modePanel;
    private View seasonPanel;
    private View tempMinusButton;
    private View tempPlusButton;
    private View humidityMinusButton;
    private View humidityPlusButton;
    private TextView realtimePill;
    private TextView sceneSwitchPill;
    private TextView energyDropPill;
    private View outdoorPill;
    private TextView outdoorStatusView;
    private TextView indoorQualityView;
    private TextView outdoorTempView;
    private TextView weatherIconView;
    private TextView timeView;
    private ImageView adminIconView;
    private ImageView wifiIconView;
    private TextView titleView;
    private TextView indoorTempUnitView;
    private TextView indoorTempLabelView;
    private TextView indoorHumidityUnitView;
    private TextView indoorHumidityLabelView;
    private TextView pm25LabelView;
    private TextView pm25UnitView;
    private TextView co2LabelView;
    private TextView co2UnitView;
    private TextView footerStatusView;
    private TextView tempSettingTitle;
    private TextView humiditySettingTitle;
    private TextView targetTempView;
    private TextView targetTempUnitView;
    private TextView targetHumidityView;
    private TextView targetHumidityUnitView;
    private ProgressBar filterProgressBar;
    private TextView fanTitleView;
    private View fanOffLabel;
    private View fanLowLabel;
    private View fanMidLabel;
    private View fanHighLabel;
    private TextView classicModeButton;
    private TextView careModeButton;
    private TextView summerButton;
    private TextView winterButton;
    private View sceneEcoButton;
    private View sceneComfortButton;
    private View sceneVacationButton;
    private View sceneCustomButton;
    private View careSceneContent;
    private View careSceneEcoButton;
    private View careSceneComfortButton;
    private View careSceneVacationButton;
    private View careSceneCustomButton;
    private View careTempCard;
    private View careHumidityCard;
    private View carePm25Card;
    private View careCo2Card;
    private TextView careTempLabelView;
    private TextView careTempUnitView;
    private TextView careHumidityLabelView;
    private TextView careHumidityUnitView;
    private TextView carePm25LabelView;
    private TextView carePm25UnitView;
    private TextView careCo2LabelView;
    private TextView careCo2UnitView;
    private TextView sceneEcoTitle;
    private TextView sceneComfortTitle;
    private TextView sceneVacationTitle;
    private TextView sceneCustomTitle;
    private TextView sceneEcoPreset;
    private TextView sceneComfortPreset;
    private TextView sceneVacationPreset;
    private TextView sceneCustomPreset;
    private TextView sceneEcoCheck;
    private TextView sceneComfortCheck;
    private TextView sceneVacationCheck;
    private TextView sceneCustomCheck;

    private int targetTemp = 26;
    private int targetHumidity = 60;
    private String selectedScene = "comfort";
    private boolean classicModeSelected = true;
    private boolean winterThemeSelected = true;
    private View selectedFanButton;
    private boolean warmTheme;
    private final List<TextView> primaryTextViews = new ArrayList<>();
    private final List<TextView> secondaryTextViews = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private final Runnable timeTicker = new Runnable() {
        @Override
        public void run() {
            if (timeView != null) {
                timeView.setText(timeFormat.format(new Date()));
            }
            handler.postDelayed(this, 1000);
        }
    };
    private final BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateWifiIconVisibility();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_test, container, false);
        bindViews(view);
        collectThemeTextViews(view);
        initPlaceholders(view);
        initClicks(view);
        setTitleText();
        view.post(() -> scalePanelToScreen(view));
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter wifiFilter = new IntentFilter();
        wifiFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        wifiFilter.addAction("android.net.wifi.STATE_CHANGE");
        requireContext().registerReceiver(wifiStateReceiver, wifiFilter);
        updateWifiIconVisibility();
        handler.post(timeTicker);
    }

    @Override
    public void onStop() {
        handler.removeCallbacks(timeTicker);
        try {
            requireContext().unregisterReceiver(wifiStateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        super.onStop();
    }

    public void resetToDefaultView() {
        classicModeSelected = true;
        selectedScene = "comfort";
        targetTemp = getSceneTemp(selectedScene);
        targetHumidity = getSceneHumidity(selectedScene);
        updateTargetViews();
        selectFan(fanLowButton);
        selectScene(selectedScene);
        applyLayoutMode();
    }

    private void bindViews(View root) {
        rootView = root.findViewById(R.id.root);
        panel = root.findViewById(R.id.panel);
        leftCard = root.findViewById(R.id.left_card);
        middleCard = root.findViewById(R.id.middle_card);
        rightCard = root.findViewById(R.id.right_card);
        filterCard = root.findViewById(R.id.card_filter);
        energyCard = root.findViewById(R.id.card_energy);
        targetPanel = root.findViewById(R.id.target_panel);
        fanPanel = root.findViewById(R.id.fan_panel);
        modePanel = root.findViewById(R.id.mode_panel);
        seasonPanel = root.findViewById(R.id.season_panel);
        tempMinusButton = root.findViewById(R.id.btn_temp_minus);
        tempPlusButton = root.findViewById(R.id.btn_temp_plus);
        humidityMinusButton = root.findViewById(R.id.btn_humidity_minus);
        humidityPlusButton = root.findViewById(R.id.btn_humidity_plus);
        realtimePill = root.findViewById(R.id.pill_realtime);
        sceneSwitchPill = root.findViewById(R.id.pill_scene_switch);
        energyDropPill = root.findViewById(R.id.pill_energy_drop);
        outdoorPill = root.findViewById(R.id.pill_outdoor);
        outdoorStatusView = root.findViewById(R.id.tv_outdoor_status);
        indoorQualityView = root.findViewById(R.id.tv_indoor_quality);
        outdoorTempView = root.findViewById(R.id.tv_outdoor_temp);
        weatherIconView = root.findViewById(R.id.tv_weather_icon);
        timeView = root.findViewById(R.id.tv_time);
        adminIconView = root.findViewById(R.id.iv_admin);
        wifiIconView = root.findViewById(R.id.iv_wifi);
        titleView = root.findViewById(R.id.tv_title);
        indoorTempUnitView = root.findViewById(R.id.tv_temp_big_unit);
        indoorTempLabelView = root.findViewById(R.id.tv_temp_big_label);
        indoorHumidityUnitView = root.findViewById(R.id.tv_humidity_big_unit);
        indoorHumidityLabelView = root.findViewById(R.id.tv_humidity_big_label);
        pm25LabelView = root.findViewById(R.id.tv_pm25_label);
        pm25UnitView = root.findViewById(R.id.tv_pm25_unit);
        co2LabelView = root.findViewById(R.id.tv_co2_label);
        co2UnitView = root.findViewById(R.id.tv_co2_unit);
        footerStatusView = root.findViewById(R.id.tv_footer_status);
        tempSettingTitle = root.findViewById(R.id.tv_temp_setting_title);
        humiditySettingTitle = root.findViewById(R.id.tv_humidity_setting_title);
        targetTempView = root.findViewById(R.id.tv_target_temp);
        targetTempUnitView = root.findViewById(R.id.tv_target_temp_unit);
        targetHumidityView = root.findViewById(R.id.tv_target_humidity);
        targetHumidityUnitView = root.findViewById(R.id.tv_target_humidity_unit);
        filterProgressBar = root.findViewById(R.id.progress_filter);
        fanOffButton = root.findViewById(R.id.btn_fan_off);
        fanLowButton = root.findViewById(R.id.btn_fan_low);
        fanMidButton = root.findViewById(R.id.btn_fan_mid);
        fanHighButton = root.findViewById(R.id.btn_fan_high);
        fanTitleView = root.findViewById(R.id.tv_fan_title);
        fanOffLabel = root.findViewById(R.id.tv_fan_off_label);
        fanLowLabel = root.findViewById(R.id.tv_fan_low_label);
        fanMidLabel = root.findViewById(R.id.tv_fan_mid_label);
        fanHighLabel = root.findViewById(R.id.tv_fan_high_label);
        classicModeButton = root.findViewById(R.id.btn_classic_mode);
        careModeButton = root.findViewById(R.id.btn_care_mode);
        summerButton = root.findViewById(R.id.btn_summer);
        winterButton = root.findViewById(R.id.btn_winter);
        sceneEcoButton = root.findViewById(R.id.btn_scene_eco);
        sceneComfortButton = root.findViewById(R.id.btn_scene_comfort);
        sceneVacationButton = root.findViewById(R.id.btn_scene_vacation);
        sceneCustomButton = root.findViewById(R.id.btn_scene_custom);
        careSceneContent = root.findViewById(R.id.care_scene_content);
        careSceneEcoButton = root.findViewById(R.id.btn_care_scene_eco);
        careSceneComfortButton = root.findViewById(R.id.btn_care_scene_comfort);
        careSceneVacationButton = root.findViewById(R.id.btn_care_scene_vacation);
        careSceneCustomButton = root.findViewById(R.id.btn_care_scene_custom);
        careTempCard = root.findViewById(R.id.card_care_temp);
        careHumidityCard = root.findViewById(R.id.card_care_humidity);
        carePm25Card = root.findViewById(R.id.card_care_pm25);
        careCo2Card = root.findViewById(R.id.card_care_co2);
        careTempLabelView = root.findViewById(R.id.tv_care_temp_label);
        careTempUnitView = root.findViewById(R.id.tv_care_temp_unit);
        careHumidityLabelView = root.findViewById(R.id.tv_care_humidity_label);
        careHumidityUnitView = root.findViewById(R.id.tv_care_humidity_unit);
        carePm25LabelView = root.findViewById(R.id.tv_care_pm25_label);
        carePm25UnitView = root.findViewById(R.id.tv_care_pm25_unit);
        careCo2LabelView = root.findViewById(R.id.tv_care_co2_label);
        careCo2UnitView = root.findViewById(R.id.tv_care_co2_unit);
        sceneEcoTitle = root.findViewById(R.id.tv_scene_eco_title);
        sceneComfortTitle = root.findViewById(R.id.tv_scene_comfort_title);
        sceneVacationTitle = root.findViewById(R.id.tv_scene_vacation_title);
        sceneCustomTitle = root.findViewById(R.id.tv_scene_custom_title);
        sceneEcoPreset = root.findViewById(R.id.tv_scene_eco_preset);
        sceneComfortPreset = root.findViewById(R.id.tv_scene_comfort_preset);
        sceneVacationPreset = root.findViewById(R.id.tv_scene_vacation_preset);
        sceneCustomPreset = root.findViewById(R.id.tv_scene_custom_preset);
        sceneEcoCheck = root.findViewById(R.id.tv_scene_eco_check);
        sceneComfortCheck = root.findViewById(R.id.tv_scene_comfort_check);
        sceneVacationCheck = root.findViewById(R.id.tv_scene_vacation_check);
        sceneCustomCheck = root.findViewById(R.id.tv_scene_custom_check);
    }

    private void collectThemeTextViews(View root) {
        primaryTextViews.clear();
        secondaryTextViews.clear();
        collectTextViews(root, primaryTextViews);
        secondaryTextViews.add(timeView);
        secondaryTextViews.add(sceneEcoPreset);
        secondaryTextViews.add(sceneComfortPreset);
        secondaryTextViews.add(sceneVacationPreset);
        secondaryTextViews.add(sceneCustomPreset);
    }

    private void collectTextViews(View view, List<TextView> out) {
        if (view instanceof TextView) {
            out.add((TextView) view);
            return;
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectTextViews(group.getChildAt(i), out);
        }
    }

    private void initPlaceholders(View root) {
        ((TextView) root.findViewById(R.id.tv_outdoor_temp)).setText("24℃");
        ((TextView) root.findViewById(R.id.tv_outdoor_status)).setText("优");
        ((TextView) root.findViewById(R.id.tv_weather_icon)).setText("☀");
        ((TextView) root.findViewById(R.id.tv_indoor_quality)).setText("优");
        ((TextView) root.findViewById(R.id.tv_temp_big)).setText("24");
        ((TextView) root.findViewById(R.id.tv_humidity_big)).setText("50");
        ((TextView) root.findViewById(R.id.tv_pm25)).setText("9");
        ((TextView) root.findViewById(R.id.tv_co2)).setText("483");
        ((TextView) root.findViewById(R.id.tv_filter)).setText("55%");
        ((TextView) root.findViewById(R.id.tv_energy)).setText("12.5 度");
        outdoorStatusView.setText("室外");
        indoorQualityView.setTextColor(Color.WHITE);
        updateWifiIconVisibility();
        resetToDefaultView();
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

    private TextView requireViewById(int id) {
        View root = getView();
        if (root == null) {
            throw new IllegalStateException("View is not created");
        }
        return root.findViewById(id);
    }

    private void initClicks(View root) {
        tempMinusButton.setOnClickListener(v -> adjustTemp(-1));
        tempPlusButton.setOnClickListener(v -> adjustTemp(1));
        humidityMinusButton.setOnClickListener(v -> adjustHumidity(-1));
        humidityPlusButton.setOnClickListener(v -> adjustHumidity(1));
        fanOffButton.setOnClickListener(v -> onFanClicked(fanOffButton));
        fanLowButton.setOnClickListener(v -> onFanClicked(fanLowButton));
        fanMidButton.setOnClickListener(v -> onFanClicked(fanMidButton));
        fanHighButton.setOnClickListener(v -> onFanClicked(fanHighButton));
        if (adminIconView != null) {
            adminIconView.setOnClickListener(v -> getParentFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new AdminContainerFragment())
                    .addToBackStack(null)
                    .commit());
        }
        sceneEcoButton.setOnClickListener(v -> applyScene("eco"));
        sceneComfortButton.setOnClickListener(v -> applyScene("comfort"));
        sceneVacationButton.setOnClickListener(v -> applyScene("vacation"));
        sceneCustomButton.setOnClickListener(v -> selectScene("custom"));
        careSceneEcoButton.setOnClickListener(v -> applyScene("eco"));
        careSceneComfortButton.setOnClickListener(v -> applyScene("comfort"));
        careSceneVacationButton.setOnClickListener(v -> applyScene("vacation"));
        careSceneCustomButton.setOnClickListener(v -> selectScene("custom"));
        classicModeButton.setOnClickListener(v -> selectMode(true));
        careModeButton.setOnClickListener(v -> selectMode(false));
        root.findViewById(R.id.hit_classic_mode).setOnClickListener(v -> selectMode(true));
        root.findViewById(R.id.hit_care_mode).setOnClickListener(v -> selectMode(false));
        middleCard.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP || event.getY() > dp(92)) {
                return false;
            }
            selectMode(event.getX() < v.getWidth() / 2f);
            return true;
        });
        summerButton.setOnClickListener(v -> selectSeason(false));
        winterButton.setOnClickListener(v -> selectSeason(true));
    }

    private void scalePanelToScreen(View root) {
        if (panel == null || root.getWidth() == 0 || root.getHeight() == 0) {
            return;
        }
        ViewGroup.LayoutParams params = panel.getLayoutParams();
        if (params.width <= 0 || params.height <= 0) {
            return;
        }
        float scale = Math.min(root.getWidth() / (float) params.width, root.getHeight() / (float) params.height);
        panel.setPivotX(params.width / 2f);
        panel.setPivotY(params.height / 2f);
        panel.setScaleX(scale);
        panel.setScaleY(scale);
    }

    private void adjustTemp(int delta) {
        targetTemp = clamp(targetTemp + delta, 16, 32);
        updateTargetViews();
        selectScene("custom");
    }

    private void adjustHumidity(int delta) {
        targetHumidity = clamp(targetHumidity + delta, 30, 80);
        updateTargetViews();
        selectScene("custom");
    }

    private void applyScene(String scene) {
        targetTemp = getSceneTemp(scene);
        targetHumidity = getSceneHumidity(scene);
        updateTargetViews();
        selectScene(scene);
    }

    private void updateTargetViews() {
        targetTempView.setText(String.valueOf(targetTemp));
        targetHumidityView.setText(String.valueOf(targetHumidity));
        updateScenePresetTexts();
        sceneCustomPreset.setText(targetTemp + "℃" + targetHumidity + "%");
        updateThemeForTargetTemp();
    }

    private void updateScenePresetTexts() {
        sceneEcoPreset.setText(formatScenePreset("eco"));
        sceneComfortPreset.setText(formatScenePreset("comfort"));
        sceneVacationPreset.setText(formatScenePreset("vacation"));
    }

    private String formatScenePreset(String scene) {
        return getSceneTemp(scene) + "℃" + getSceneHumidity(scene) + "%";
    }

    private int getSceneTemp(String scene) {
        if (winterThemeSelected) {
            if ("eco".equals(scene)) {
                return 20;
            }
            if ("comfort".equals(scene)) {
                return 23;
            }
            if ("vacation".equals(scene)) {
                return 18;
            }
        } else {
            if ("eco".equals(scene)) {
                return 26;
            }
            if ("comfort".equals(scene)) {
                return 25;
            }
            if ("vacation".equals(scene)) {
                return 28;
            }
        }
        return targetTemp;
    }

    private int getSceneHumidity(String scene) {
        if (winterThemeSelected) {
            if ("eco".equals(scene)) {
                return 30;
            }
            if ("comfort".equals(scene)) {
                return 40;
            }
            if ("vacation".equals(scene)) {
                return 30;
            }
        } else {
            if ("eco".equals(scene)) {
                return 60;
            }
            if ("comfort".equals(scene)) {
                return 50;
            }
            if ("vacation".equals(scene)) {
                return 70;
            }
        }
        return targetHumidity;
    }

    private void selectFan(View selected) {
        updateFanSelection(selected);
    }

    private void onFanClicked(View selected) {
        updateFanSelection(selected);
        selectScene("custom");
    }

    private void updateFanSelection(View selected) {
        selectedFanButton = selected;
        setFanSelected(fanOffButton, selected == fanOffButton);
        setFanSelected(fanLowButton, selected == fanLowButton);
        setFanSelected(fanMidButton, selected == fanMidButton);
        setFanSelected(fanHighButton, selected == fanHighButton);
        setFanLabelSelected(fanOffLabel, selected == fanOffButton);
        setFanLabelSelected(fanLowLabel, selected == fanLowButton);
        setFanLabelSelected(fanMidLabel, selected == fanMidButton);
        setFanLabelSelected(fanHighLabel, selected == fanHighButton);
    }

    private void selectScene(String scene) {
        selectedScene = scene;
        boolean eco = "eco".equals(scene);
        boolean comfort = "comfort".equals(scene);
        boolean vacation = "vacation".equals(scene);
        boolean custom = "custom".equals(scene);
        sceneEcoButton.setBackgroundResource(eco ? R.drawable.scene_active_bg : R.drawable.scene_normal_bg);
        sceneComfortButton.setBackgroundResource(comfort ? R.drawable.scene_active_bg : R.drawable.scene_normal_bg);
        sceneVacationButton.setBackgroundResource(vacation ? R.drawable.scene_active_bg : R.drawable.scene_normal_bg);
        sceneCustomButton.setBackgroundResource(custom ? R.drawable.scene_active_bg : R.drawable.scene_normal_bg);
        sceneEcoTitle.setText("节能");
        sceneComfortTitle.setText("舒适");
        sceneVacationTitle.setText("度假");
        sceneCustomTitle.setText("自定义");
        updateScenePresetTexts();
        sceneCustomPreset.setText(targetTemp + "℃" + targetHumidity + "%");
        sceneEcoCheck.setText(eco ? "✓" : "");
        sceneComfortCheck.setText(comfort ? "✓" : "");
        sceneVacationCheck.setText(vacation ? "✓" : "");
        sceneCustomCheck.setText(custom ? "✓" : "");
        sceneEcoPreset.setTextColor(Color.parseColor(eco ? "#5B9AFF" : "#B8C5D8"));
        sceneComfortPreset.setTextColor(Color.parseColor(comfort ? "#5B9AFF" : "#B8C5D8"));
        sceneVacationPreset.setTextColor(Color.parseColor(vacation ? "#5B9AFF" : "#B8C5D8"));
        sceneCustomPreset.setTextColor(Color.parseColor(custom ? "#5B9AFF" : "#B8C5D8"));
        updateThemeForTargetTemp();
    }

    private void setChipSelected(TextView view, boolean selected, int selectedBackground) {
        view.setBackgroundResource(selected ? selectedBackground : R.drawable.chip_normal_bg);
        view.setTextColor(selected ? Color.WHITE : Color.parseColor("#99FFFFFF"));
    }

    private void setFanSelected(View view, boolean selected) {
        view.setBackgroundColor(Color.TRANSPARENT);
    }

    private void setCardBackground(View view, boolean warm, String edgeColor) {
        if (warm) {
            view.setBackgroundResource(R.drawable.card_warm_bg);
            return;
        }
        view.setBackground(new TopEdgeCardDrawable(
                dp(18),
                dp(3),
                Color.parseColor("#223044"),
                Color.parseColor("#26FFFFFF"),
                Color.parseColor(edgeColor)
        ));
    }

    private void updateThemeForTargetTemp() {
        boolean shouldWarm = winterThemeSelected;
        warmTheme = shouldWarm;
        if (rootView == null || leftCard == null || middleCard == null || rightCard == null) {
            return;
        }
        rootView.setBackgroundResource(shouldWarm ? R.drawable.page_warm_bg : R.drawable.page_bg);
        panel.setBackgroundResource(shouldWarm ? R.drawable.page_warm_bg : R.drawable.panel_summer_bg);
        setCardBackground(leftCard, shouldWarm, "#34C759");
        setCardBackground(middleCard, shouldWarm, "#5B9AFF");
        setCardBackground(rightCard, shouldWarm, "#FF9F0A");
        fanPanel.setBackgroundResource(shouldWarm ? R.drawable.fan_warm_normal_bg : R.drawable.fan_normal_bg);
        fanTitleView.setTextColor(Color.parseColor(shouldWarm ? "#6E5E4E" : "#99FFFFFF"));
        int metricBackground = shouldWarm ? R.drawable.metric_warm_bg : R.drawable.metric_bg;
        filterCard.setBackgroundResource(metricBackground);
        energyCard.setBackgroundResource(metricBackground);
        if (careTempCard != null) {
            int careMetricBackground = shouldWarm ? R.drawable.care_metric_warm_bg : R.drawable.care_metric_bg;
            careTempCard.setBackgroundResource(careMetricBackground);
            careHumidityCard.setBackgroundResource(careMetricBackground);
            carePm25Card.setBackgroundResource(careMetricBackground);
            careCo2Card.setBackgroundResource(careMetricBackground);
        }
        filterProgressBar.setProgressDrawable(getResources().getDrawable(shouldWarm ? R.drawable.progress_filter_warm : R.drawable.progress_filter));
        int stepBackground = shouldWarm ? R.drawable.step_circle_warm_press_bg : R.drawable.step_circle_press_bg;
        tempMinusButton.setBackgroundResource(stepBackground);
        tempPlusButton.setBackgroundResource(stepBackground);
        humidityMinusButton.setBackgroundResource(stepBackground);
        humidityPlusButton.setBackgroundResource(stepBackground);
        int commonPill = shouldWarm ? R.drawable.pill_warm_bg : R.drawable.pill_dark_bg;
        outdoorPill.setBackgroundResource(shouldWarm ? R.drawable.outdoor_pill_warm_bg : R.drawable.pill_dark_bg);
        realtimePill.setBackgroundResource(commonPill);
        sceneSwitchPill.setBackgroundResource(commonPill);
        energyDropPill.setBackgroundResource(shouldWarm ? R.drawable.pill_drop_warm_bg : R.drawable.pill_drop_dark_bg);
        realtimePill.setTextColor(Color.parseColor(shouldWarm ? "#7F705F" : "#66FFFFFF"));
        sceneSwitchPill.setTextColor(Color.parseColor(shouldWarm ? "#7F705F" : "#66FFFFFF"));
        energyDropPill.setTextColor(Color.parseColor(shouldWarm ? "#4C8D68" : "#34C759"));
        outdoorStatusView.setTextColor(Color.parseColor(shouldWarm ? "#2C2723" : "#FFFFFF"));
        int primary = Color.parseColor(shouldWarm ? "#2C2723" : "#E6FFFFFF");
        int secondary = Color.parseColor(shouldWarm ? "#8B8177" : "#73FFFFFF");
        for (TextView textView : primaryTextViews) {
            textView.setTextColor(primary);
        }
        for (TextView textView : secondaryTextViews) {
            textView.setTextColor(secondary);
        }
        outdoorStatusView.setText("室外");
        outdoorStatusView.setTextColor(Color.parseColor(shouldWarm ? "#2C2723" : "#FFFFFF"));
        indoorQualityView.setTextColor(Color.WHITE);
        outdoorTempView.setTextColor(Color.parseColor(shouldWarm ? "#2C2723" : "#FFFFFF"));
        weatherIconView.setTextColor(Color.parseColor("#F6C453"));
        if (adminIconView != null) {
            adminIconView.setImageResource(shouldWarm ? R.drawable.fit_home_guanli : R.drawable.fit_home_guanli_summer);
        }
        if (wifiIconView != null) {
            wifiIconView.setImageResource(shouldWarm ? R.drawable.fit_home_wifi : R.drawable.fit_home_wifi_summer);
        }
        int settingTitleColor = Color.parseColor(shouldWarm ? "#7F705F" : "#55FFFFFF");
        int targetUnitColor = Color.parseColor(shouldWarm ? "#7F705F" : "#73FFFFFF");
        indoorTempUnitView.setTextColor(targetUnitColor);
        indoorTempLabelView.setTextColor(settingTitleColor);
        indoorHumidityUnitView.setTextColor(targetUnitColor);
        indoorHumidityLabelView.setTextColor(settingTitleColor);
        pm25LabelView.setTextColor(targetUnitColor);
        pm25UnitView.setTextColor(targetUnitColor);
        co2LabelView.setTextColor(targetUnitColor);
        co2UnitView.setTextColor(targetUnitColor);
        footerStatusView.setTextColor(targetUnitColor);
        tempSettingTitle.setTextColor(settingTitleColor);
        humiditySettingTitle.setTextColor(settingTitleColor);
        targetTempUnitView.setTextColor(targetUnitColor);
        targetHumidityUnitView.setTextColor(targetUnitColor);
        if (careTempLabelView != null) {
            careTempLabelView.setTextColor(settingTitleColor);
            careHumidityLabelView.setTextColor(settingTitleColor);
            careTempUnitView.setTextColor(settingTitleColor);
            careHumidityUnitView.setTextColor(settingTitleColor);
            carePm25LabelView.setTextColor(settingTitleColor);
            careCo2LabelView.setTextColor(settingTitleColor);
            carePm25UnitView.setTextColor(settingTitleColor);
            careCo2UnitView.setTextColor(settingTitleColor);
        }
        applyModeButtons();
        applySeasonButtons();
        setTitleText();
        sceneEcoCheck.setTextColor(Color.parseColor(shouldWarm ? "#A97842" : "#5B9AFF"));
        sceneComfortCheck.setTextColor(Color.parseColor(shouldWarm ? "#A97842" : "#5B9AFF"));
        sceneVacationCheck.setTextColor(Color.parseColor(shouldWarm ? "#A97842" : "#5B9AFF"));
        sceneCustomCheck.setTextColor(Color.parseColor(shouldWarm ? "#A97842" : "#5B9AFF"));
        applySceneBackgrounds();
        if (selectedFanButton != null) {
            selectFan(selectedFanButton);
        }
        applyLayoutMode();
    }

    private void applySceneBackgrounds() {
        boolean eco = "eco".equals(selectedScene);
        boolean comfort = "comfort".equals(selectedScene);
        boolean vacation = "vacation".equals(selectedScene);
        boolean custom = "custom".equals(selectedScene);
        int active = warmTheme ? R.drawable.scene_warm_active_bg : R.drawable.scene_active_bg;
        int normal = warmTheme ? R.drawable.scene_warm_normal_bg : R.drawable.scene_normal_bg;
        sceneEcoButton.setBackgroundResource(eco ? active : normal);
        sceneComfortButton.setBackgroundResource(comfort ? active : normal);
        sceneVacationButton.setBackgroundResource(vacation ? active : normal);
        sceneCustomButton.setBackgroundResource(custom ? active : normal);
        setCareSceneButtonState(careSceneEcoButton, eco);
        setCareSceneButtonState(careSceneComfortButton, comfort);
        setCareSceneButtonState(careSceneVacationButton, vacation);
        setCareSceneButtonState(careSceneCustomButton, custom);
        int selectedColor = Color.parseColor(warmTheme ? "#A97842" : "#5B9AFF");
        int normalColor = Color.parseColor(warmTheme ? "#7F705F" : "#B8C5D8");
        sceneEcoPreset.setTextColor(eco ? selectedColor : normalColor);
        sceneComfortPreset.setTextColor(comfort ? selectedColor : normalColor);
        sceneVacationPreset.setTextColor(vacation ? selectedColor : normalColor);
        sceneCustomPreset.setTextColor(custom ? selectedColor : normalColor);
    }

    private void setCareSceneButtonState(View view, boolean selected) {
        if (view == null) {
            return;
        }
        int targetTextColor = Color.parseColor(warmTheme ? "#2C2723" : "#E6FFFFFF");
        if (selected) {
            view.setBackgroundResource(warmTheme ? R.drawable.fan_label_warm_active_bg : R.drawable.fan_label_dark_active_bg);
        } else {
            view.setBackgroundResource(warmTheme ? R.drawable.fan_label_warm_normal_bg : R.drawable.fan_label_normal_bg);
        }
        setTextColorRecursive(view, targetTextColor);
    }

    private void setFanLabelSelected(View view, boolean selected) {
        if (selected) {
            view.setBackgroundResource(warmTheme ? R.drawable.mode_button_warm_bg : R.drawable.mode_button_dark_selected_bg);
            setTextColorRecursive(view, Color.parseColor(warmTheme ? "#2C2723" : "#FFFFFF"));
        } else {
            view.setBackgroundResource(warmTheme ? R.drawable.fan_label_warm_normal_bg : R.drawable.fan_label_normal_bg);
            setTextColorRecursive(view, Color.parseColor(warmTheme ? "#7F705F" : "#99FFFFFF"));
        }
    }

    private void selectMode(boolean classic) {
        classicModeSelected = classic;
        applyLayoutMode();
        applyModeButtons();
    }

    private void selectSeason(boolean winter) {
        winterThemeSelected = winter;
        if ("eco".equals(selectedScene) || "comfort".equals(selectedScene) || "vacation".equals(selectedScene)) {
            applyScene(selectedScene);
        } else {
            updateScenePresetTexts();
            updateThemeForTargetTemp();
        }
    }

    private void applyModeButtons() {
        if (classicModeButton == null || careModeButton == null) {
            return;
        }
        int selectedBg = warmTheme ? R.drawable.mode_button_warm_bg : R.drawable.mode_button_dark_selected_bg;
        int normalBg = warmTheme ? R.drawable.mode_button_warm_normal_bg : R.drawable.mode_button_dark_bg;
        int selectedColor = Color.parseColor(warmTheme ? "#2C2723" : "#FFFFFF");
        int normalColor = Color.parseColor(warmTheme ? "#7F705F" : "#80FFFFFF");
        classicModeButton.setBackgroundResource(classicModeSelected ? selectedBg : normalBg);
        careModeButton.setBackgroundResource(classicModeSelected ? normalBg : selectedBg);
        classicModeButton.setTextColor(classicModeSelected ? selectedColor : normalColor);
        careModeButton.setTextColor(classicModeSelected ? normalColor : selectedColor);
    }

    private void applySeasonButtons() {
        if (summerButton == null || winterButton == null) {
            return;
        }
        boolean summerSelected = !winterThemeSelected;
        int summerSelectedBg = R.drawable.mode_button_dark_selected_bg;
        int winterSelectedBg = R.drawable.mode_button_warm_bg;
        int normalBg = warmTheme ? R.drawable.mode_button_warm_normal_bg : R.drawable.mode_button_dark_bg;
        summerButton.setBackgroundResource(summerSelected ? summerSelectedBg : normalBg);
        winterButton.setBackgroundResource(winterThemeSelected ? winterSelectedBg : normalBg);
        summerButton.setTextColor(Color.parseColor(summerSelected ? "#FFFFFF" : (warmTheme ? "#7F705F" : "#80FFFFFF")));
        winterButton.setTextColor(Color.parseColor(winterThemeSelected ? "#2C2723" : (warmTheme ? "#7F705F" : "#80FFFFFF")));
    }

    private void applyLayoutMode() {
        if (leftCard == null || middleCard == null || rightCard == null) {
            return;
        }
        boolean careMode = !classicModeSelected;
        leftCard.setVisibility(careMode ? View.GONE : View.VISIBLE);
        if (careSceneContent != null) {
            careSceneContent.setVisibility(careMode ? View.VISIBLE : View.GONE);
        }
        int classicSceneVisibility = careMode ? View.GONE : View.VISIBLE;
        sceneEcoButton.setVisibility(classicSceneVisibility);
        sceneComfortButton.setVisibility(classicSceneVisibility);
        sceneVacationButton.setVisibility(classicSceneVisibility);
        sceneCustomButton.setVisibility(classicSceneVisibility);

        LinearLayout.LayoutParams middleParams = (LinearLayout.LayoutParams) middleCard.getLayoutParams();
        LinearLayout.LayoutParams rightParams = (LinearLayout.LayoutParams) rightCard.getLayoutParams();
        middleParams.weight = careMode ? 1f : 1.6f;
        rightParams.weight = careMode ? 1f : 1.2f;
        middleParams.leftMargin = careMode ? 0 : dp(14);
        rightParams.leftMargin = dp(14);
        middleCard.setLayoutParams(middleParams);
        rightCard.setLayoutParams(rightParams);

        setSize(seasonPanel, ViewGroup.LayoutParams.MATCH_PARENT, dp(careMode ? 42 : 38));
        setTopMargin(seasonPanel, dp(careMode ? 8 : 6));
        setSize(targetPanel, ViewGroup.LayoutParams.MATCH_PARENT, dp(careMode ? 240 : 230));
        setTopMargin(targetPanel, dp(careMode ? 8 : 6));
        setSize(fanPanel, careMode ? dp(400) : ViewGroup.LayoutParams.MATCH_PARENT, dp(careMode ? 100 : 104));
        setTopMargin(fanPanel, dp(careMode ? -10 : 18));
        setSize(modePanel, dp(520), ViewGroup.LayoutParams.MATCH_PARENT);

        targetTempView.setTextSize(careMode ? 68 : 66);
        targetHumidityView.setTextSize(careMode ? 68 : 66);
        targetTempUnitView.setTextSize(careMode ? 24 : 22);
        targetHumidityUnitView.setTextSize(careMode ? 24 : 22);
        setSquareSize(tempMinusButton, dp(careMode ? 58 : 60));
        setSquareSize(tempPlusButton, dp(careMode ? 58 : 60));
        setSquareSize(humidityMinusButton, dp(careMode ? 58 : 60));
        setSquareSize(humidityPlusButton, dp(careMode ? 58 : 60));

        setSize(fanOffLabel, careMode ? dp(54) : ViewGroup.LayoutParams.MATCH_PARENT, dp(careMode ? 50 : 48));
        setSize(fanLowLabel, careMode ? dp(54) : ViewGroup.LayoutParams.MATCH_PARENT, dp(careMode ? 50 : 48));
        setSize(fanMidLabel, careMode ? dp(54) : ViewGroup.LayoutParams.MATCH_PARENT, dp(careMode ? 50 : 48));
        setSize(fanHighLabel, careMode ? dp(54) : ViewGroup.LayoutParams.MATCH_PARENT, dp(careMode ? 50 : 48));
        setFanButtonContainerLayout(fanOffButton, careMode);
        setFanButtonContainerLayout(fanLowButton, careMode);
        setFanButtonContainerLayout(fanMidButton, careMode);
        setFanButtonContainerLayout(fanHighButton, careMode);
        setFanButtonGroupOffset(careMode);
        setSize(summerButton, dp(careMode ? 132 : 112), dp(careMode ? 36 : 32));
        setSize(winterButton, dp(careMode ? 132 : 112), dp(careMode ? 36 : 32));
        summerButton.setTextSize(careMode ? 15 : 14);
        winterButton.setTextSize(careMode ? 15 : 14);
        setSize(classicModeButton, dp(112), dp(34));
        setSize(careModeButton, dp(112), dp(34));
        classicModeButton.setTextSize(14);
        careModeButton.setTextSize(14);

        int sceneTitleSize = careMode ? 20 : 18;
        int scenePresetSize = careMode ? 20 : 18;
        sceneEcoTitle.setTextSize(sceneTitleSize);
        sceneComfortTitle.setTextSize(sceneTitleSize);
        sceneVacationTitle.setTextSize(sceneTitleSize);
        sceneCustomTitle.setTextSize(sceneTitleSize);
        sceneEcoPreset.setTextSize(scenePresetSize);
        sceneComfortPreset.setTextSize(scenePresetSize);
        sceneVacationPreset.setTextSize(scenePresetSize);
        sceneCustomPreset.setTextSize(scenePresetSize);
        setScenePresetWidth(careMode ? 118 : 104);
        setSceneCheckWidth(careMode ? 18 : 16);
    }

    private void setScenePresetWidth(int widthDp) {
        int width = dp(widthDp);
        setSize(sceneEcoPreset, width, ViewGroup.LayoutParams.MATCH_PARENT);
        setSize(sceneComfortPreset, width, ViewGroup.LayoutParams.MATCH_PARENT);
        setSize(sceneVacationPreset, width, ViewGroup.LayoutParams.MATCH_PARENT);
        setSize(sceneCustomPreset, width, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void setSceneCheckWidth(int widthDp) {
        int width = dp(widthDp);
        setSize(sceneEcoCheck, width, ViewGroup.LayoutParams.MATCH_PARENT);
        setSize(sceneComfortCheck, width, ViewGroup.LayoutParams.MATCH_PARENT);
        setSize(sceneVacationCheck, width, ViewGroup.LayoutParams.MATCH_PARENT);
        setSize(sceneCustomCheck, width, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void setFanButtonContainerLayout(View view, boolean careMode) {
        ViewGroup.LayoutParams rawParams = view.getLayoutParams();
        if (rawParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rawParams;
            params.width = 0;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.weight = 1f;
            view.setLayoutParams(params);
        }
    }

    private void setFanButtonGroupOffset(boolean careMode) {
        ViewGroup parent = (ViewGroup) fanOffButton.getParent();
        ViewGroup.LayoutParams rawParams = parent.getLayoutParams();
        if (rawParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rawParams;
            params.leftMargin = dp(careMode ? 10 : 2);
            params.rightMargin = dp(careMode ? 0 : 8);
            parent.setLayoutParams(params);
        }
    }

    private void setSize(View view, int width, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = width;
        params.height = height;
        view.setLayoutParams(params);
    }

    private void setSquareSize(View view, int size) {
        setSize(view, size, size);
    }

    private void setTopMargin(View view, int topMargin) {
        ViewGroup.LayoutParams rawParams = view.getLayoutParams();
        if (rawParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) rawParams;
            params.topMargin = topMargin;
            view.setLayoutParams(params);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void setTextColorRecursive(View view, int color) {
        if (view instanceof TextView) {
            ((TextView) view).setTextColor(color);
            return;
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            setTextColorRecursive(group.getChildAt(i), color);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void setTitleText() {
        if (titleView == null) {
            return;
        }
        SpannableString title = new SpannableString("健康房 · 智慧家 ●");
        title.setSpan(new ForegroundColorSpan(Color.parseColor("#34C759")), title.length() - 1, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        titleView.setText(title);
    }

    private static final class TopEdgeCardDrawable extends Drawable {
        private final float radius;
        private final float edgeWidth;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clipPath = new Path();

        TopEdgeCardDrawable(float radius, float edgeWidth, int fillColor, int borderColor, int edgeColor) {
            this.radius = radius;
            this.edgeWidth = edgeWidth;
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(fillColor);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(1f);
            borderPaint.setColor(borderColor);
            edgePaint.setStyle(Paint.Style.STROKE);
            edgePaint.setStrokeWidth(edgeWidth);
            edgePaint.setStrokeCap(Paint.Cap.ROUND);
            edgePaint.setStrokeJoin(Paint.Join.ROUND);
            edgePaint.setColor(edgeColor);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            float halfBorder = 0.5f;
            rect.set(halfBorder, halfBorder, getBounds().width() - halfBorder, getBounds().height() - halfBorder);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
            canvas.drawRoundRect(rect, radius, radius, borderPaint);

            int save = canvas.save();
            clipPath.reset();
            clipPath.addRect(0, 0, getBounds().width(), radius + edgeWidth, Path.Direction.CW);
            canvas.clipPath(clipPath);

            float halfEdge = edgeWidth / 2f;
            rect.set(halfEdge, halfEdge, getBounds().width() - halfEdge, getBounds().height() - halfEdge);
            canvas.drawRoundRect(rect, radius, radius, edgePaint);
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
            fillPaint.setAlpha(alpha);
            borderPaint.setAlpha(alpha);
            edgePaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable android.graphics.ColorFilter colorFilter) {
            fillPaint.setColorFilter(colorFilter);
            borderPaint.setColorFilter(colorFilter);
            edgePaint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}

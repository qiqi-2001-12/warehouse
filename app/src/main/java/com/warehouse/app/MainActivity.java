package com.warehouse.app;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.view.Gravity;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import com.warehouse.device.FaultMonitor;
import com.warehouse.fragment.home.HomeFragment;
import com.warehouse.remote.RemoteAgentService;
import com.warehouse.ota.AppUpdateManager;

public class MainActivity extends AppCompatActivity {
    private final Handler otaUiHandler = new Handler(Looper.getMainLooper());
    private LinearLayout otaPanel;
    private TextView otaStatus;
    private TextView otaMessage;
    private ProgressBar otaProgress;
    private final Runnable otaUiRefresh = new Runnable() {
        @Override
        public void run() {
            updateOtaPanel();
            otaUiHandler.postDelayed(this, 500L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
        FaultMonitor.start(getApplicationContext());
        AppUpdateManager.get(getApplicationContext()).markStartupHealthy();
        RemoteAgentService.start(getApplicationContext());
        FrameLayout root = new FrameLayout(this);
        FrameLayout content = new FrameLayout(this);
        content.setId(View.generateViewId());
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        createOtaPanel(root);
        setContentView(root);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(content.getId(), new HomeFragment())
                .commit();
        otaUiHandler.post(otaUiRefresh);
    }

    @Override
    protected void onDestroy() {
        otaUiHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void createOtaPanel(FrameLayout root) {
        otaPanel = new LinearLayout(this);
        otaPanel.setOrientation(LinearLayout.VERTICAL);
        otaPanel.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(28, 48, 61));
        background.setCornerRadius(dp(8));
        otaPanel.setBackground(background);
        otaPanel.setElevation(dp(8));

        TextView title = new TextView(this);
        title.setText("设备远程升级");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        otaPanel.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        otaStatus = new TextView(this);
        otaStatus.setTextColor(Color.WHITE);
        otaStatus.setTextSize(15);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(5);
        otaPanel.addView(otaStatus, statusParams);

        otaProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        otaProgress.setMax(100);
        otaProgress.setIndeterminate(false);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        progressParams.topMargin = dp(8);
        otaPanel.addView(otaProgress, progressParams);

        otaMessage = new TextView(this);
        otaMessage.setTextColor(Color.rgb(205, 222, 230));
        otaMessage.setTextSize(13);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(5);
        otaPanel.addView(otaMessage, messageParams);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        panelParams.leftMargin = dp(16);
        panelParams.rightMargin = dp(16);
        panelParams.topMargin = dp(16);
        root.addView(otaPanel, panelParams);
    }

    private void updateOtaPanel() {
        if (otaPanel == null) {
            return;
        }
        JSONObject app = AppUpdateManager.get(getApplicationContext()).telemetry();
        String status = app.optString("updateStatus", AppUpdateManager.STATUS_IDLE);
        boolean terminalState = AppUpdateManager.STATUS_SUCCESS.equals(status)
                || AppUpdateManager.STATUS_FAILED.equals(status);
        if (AppUpdateManager.STATUS_IDLE.equals(status)
                || terminalState) {
            otaPanel.setVisibility(View.GONE);
            return;
        }
        otaPanel.setVisibility(View.VISIBLE);
        int progress = Math.max(0, Math.min(100, app.optInt("updateProgress", 0)));
        otaProgress.setProgress(progress);
        String label;
        int color;
        switch (status) {
            case AppUpdateManager.STATUS_DOWNLOADING:
                label = "正在下载升级包";
                color = Color.rgb(120, 220, 255);
                break;
            case AppUpdateManager.STATUS_VERIFYING:
                label = "正在校验升级包";
                color = Color.rgb(255, 210, 100);
                break;
            case AppUpdateManager.STATUS_INSTALLING:
                label = "正在安装升级包";
                color = Color.rgb(255, 210, 100);
                break;
            case AppUpdateManager.STATUS_SUCCESS:
                label = "升级成功";
                color = Color.rgb(120, 235, 170);
                break;
            case AppUpdateManager.STATUS_FAILED:
                label = "升级失败";
                color = Color.rgb(255, 130, 130);
                break;
            default:
                label = "已收到升级任务";
                color = Color.WHITE;
                break;
        }
        otaStatus.setTextColor(color);
        otaStatus.setText(label + "  " + progress + "%");
        String target = app.optString("updateTargetVersion", "");
        String error = app.optString("updateError", "");
        if (!error.isEmpty()) {
            otaMessage.setText("失败原因：" + error);
        } else if (!target.isEmpty()) {
            otaMessage.setText("目标版本：" + target);
        } else {
            otaMessage.setText("");
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

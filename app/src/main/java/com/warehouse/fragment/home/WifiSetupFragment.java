package com.warehouse.fragment.home;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warehouse.R;
import com.warehouse.core.ui.AdminUi;
import com.warehouse.core.ui.ScreenFit;
import com.warehouse.data.local.OperationLogStore;
import com.warehouse.device.DeviceGateway;

import java.util.EnumMap;
import java.util.Map;

public class WifiSetupFragment extends Fragment {
    private static final int QR_SIZE_PX = 180;

    private EditText deviceCodeInput;
    private ImageView qrCodeView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundResource(R.drawable.beijing_top);

        LinearLayout panel = AdminUi.column(context);
        panel.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(480, 480, Gravity.CENTER);
        root.addView(panel, panelParams);

        panel.addView(buildHeader(context), AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 62));
        panel.addView(AdminUi.divider(context), AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        panel.addView(buildContent(context), AdminUi.weightedLp(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        ScreenFit.bind(root, panel);
        return root;
    }

    private View buildHeader(Context context) {
        LinearLayout header = AdminUi.row(context);
        header.setPadding(AdminUi.dp(context, 20), 0, AdminUi.dp(context, 20), 0);

        TextView back = AdminUi.text(context, "\u2b05\ufe0e", 40, AdminUi.ACCENT,
                android.graphics.Typeface.BOLD);
        back.setClickable(false);
        back.setFocusable(false);
        FrameLayout backTouchTarget = new FrameLayout(context);
        backTouchTarget.setContentDescription("Back");
        backTouchTarget.setClickable(true);
        backTouchTarget.setFocusable(true);
        AdminUi.addPressFeedback(backTouchTarget);
        backTouchTarget.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        backTouchTarget.addView(back, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        header.addView(backTouchTarget, AdminUi.lp(AdminUi.dp(context, 92),
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = AdminUi.text(context, "Wi-Fi \u914d\u7f51", 22, AdminUi.TEXT_PRIMARY,
                android.graphics.Typeface.BOLD);
        header.addView(title, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        ImageView wifiSettings = new ImageView(context);
        wifiSettings.setImageResource(R.drawable.wifi);
        wifiSettings.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        wifiSettings.setPadding(AdminUi.dp(context, 8), AdminUi.dp(context, 8),
                AdminUi.dp(context, 8), AdminUi.dp(context, 8));
        ImageViewCompat.setImageTintList(wifiSettings,
                ColorStateList.valueOf(AdminUi.ACCENT));
        wifiSettings.setContentDescription("Wi-Fi settings");
        wifiSettings.setClickable(true);
        wifiSettings.setFocusable(true);
        AdminUi.addPressFeedback(wifiSettings);
        wifiSettings.setOnClickListener(v -> openWifiSettings());
        header.addView(wifiSettings, AdminUi.lp(AdminUi.dp(context, 64),
                ViewGroup.LayoutParams.MATCH_PARENT));
        return header;
    }

    private View buildContent(Context context) {
        LinearLayout content = AdminUi.column(context);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(AdminUi.dp(context, 44), AdminUi.dp(context, 22),
                AdminUi.dp(context, 44), AdminUi.dp(context, 28));

        deviceCodeInput = new EditText(context);
        deviceCodeInput.setSoundEffectsEnabled(false);
        deviceCodeInput.setHint("\u8bf7\u8f93\u5165\u8bbe\u5907\u7801");
        deviceCodeInput.setTextColor(AdminUi.TEXT_PRIMARY);
        deviceCodeInput.setHintTextColor(AdminUi.TEXT_SECONDARY);
        deviceCodeInput.setTextSize(18);
        deviceCodeInput.setGravity(Gravity.CENTER);
        deviceCodeInput.setSingleLine(true);
        deviceCodeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        deviceCodeInput.setBackground(AdminUi.bg(AdminUi.CONTROL_BG, 4, AdminUi.ACCENT, 1, context));
        deviceCodeInput.setPadding(AdminUi.dp(context, 12), 0, AdminUi.dp(context, 12), 0);
        LinearLayout.LayoutParams inputParams = AdminUi.lp(
                AdminUi.dp(context, 280), AdminUi.dp(context, 40));
        inputParams.topMargin = AdminUi.dp(context, 10);
        content.addView(deviceCodeInput, inputParams);

        TextView generateButton = AdminUi.button(context, "\u751f\u6210\u4e8c\u7ef4\u7801");
        generateButton.setTextSize(17);
        generateButton.setOnClickListener(v -> generateQrCode());
        LinearLayout.LayoutParams generateParams = AdminUi.lp(
                AdminUi.dp(context, 180), AdminUi.dp(context, 56));
        generateParams.topMargin = AdminUi.dp(context, 8);
        content.addView(generateButton, generateParams);

        qrCodeView = new ImageView(context);
        qrCodeView.setBackgroundColor(Color.WHITE);
        qrCodeView.setContentDescription("Device QR code");
        qrCodeView.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams qrParams = AdminUi.lp(QR_SIZE_PX, QR_SIZE_PX);
        qrParams.topMargin = AdminUi.dp(context, 14);
        content.addView(qrCodeView, qrParams);

        View bottomSpacer = new View(context);
        content.addView(bottomSpacer, AdminUi.weightedLp(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView resetFactory = AdminUi.text(context, "\u6062\u590d\u51fa\u5382\u8bbe\u7f6e",
                17, AdminUi.TEXT_PRIMARY, android.graphics.Typeface.BOLD);
        resetFactory.setClickable(true);
        resetFactory.setFocusable(true);
        StateListDrawable resetBackground = new StateListDrawable();
        resetBackground.addState(new int[]{android.R.attr.state_pressed},
                AdminUi.bg(Color.rgb(15, 196, 225), 999, Color.rgb(143, 238, 250), 1, context));
        resetBackground.addState(new int[]{},
                AdminUi.bg(AdminUi.CONTROL_BG, 6, AdminUi.CONTROL_STROKE, 1, context));
        resetFactory.setBackground(resetBackground);
        AdminUi.addPressFeedback(resetFactory);
        resetFactory.setOnClickListener(v -> writeFactoryReset(resetFactory));
        content.addView(resetFactory, AdminUi.lp(AdminUi.dp(context, 180), AdminUi.dp(context, 56)));

        return content;
    }

    private void writeFactoryReset(TextView button) {
        Context context = getContext();
        if (context == null || !isAdded()) {
            return;
        }
        DeviceGateway gateway = DeviceGateway.get(context);
        if (!gateway.canWrite(539)) {
            Toast.makeText(context, "\u672a\u627e\u5230\u5bc4\u5b58\u5668", Toast.LENGTH_SHORT).show();
            return;
        }
        button.setEnabled(false);
        gateway.write(539, 1, new DeviceGateway.WriteCallback() {
            @Override
            public void onSuccess() {
                Context callbackContext = getContext();
                if (callbackContext != null && isAdded()) {
                    OperationLogStore.get(callbackContext).recordLocal("恢复出厂设置");
                    Toast.makeText(callbackContext, "\u5df2\u53d1\u9001", Toast.LENGTH_SHORT).show();
                    button.setEnabled(true);
                }
            }

            @Override
            public void onError(Exception error) {
                Context callbackContext = getContext();
                if (callbackContext != null && isAdded()) {
                    Toast.makeText(callbackContext, error.getMessage(), Toast.LENGTH_SHORT).show();
                    button.setEnabled(true);
                }
            }
        });
    }

    private void generateQrCode() {
        String deviceCode = deviceCodeInput.getText().toString().trim();
        if (deviceCode.isEmpty()) {
            qrCodeView.setImageBitmap(null);
            qrCodeView.setVisibility(View.INVISIBLE);
            Toast.makeText(requireContext(), "\u8bf7\u8f93\u5165\u8bbe\u5907\u7801", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap bitmap = createQrBitmap(deviceCode);
        if (bitmap == null) {
            Toast.makeText(requireContext(), "\u4e8c\u7ef4\u7801\u751f\u6210\u5931\u8d25", Toast.LENGTH_SHORT).show();
            return;
        }
        qrCodeView.setImageBitmap(bitmap);
        qrCodeView.setVisibility(View.VISIBLE);
    }

    private void openWifiSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
        } catch (ActivityNotFoundException error) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "\u65e0\u6cd5\u6253\u5f00 Wi-Fi \u8bbe\u7f6e", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap createQrBitmap(String content) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(
                    content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX, hints);
            Bitmap bitmap = Bitmap.createBitmap(QR_SIZE_PX, QR_SIZE_PX, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < QR_SIZE_PX; y++) {
                for (int x = 0; x < QR_SIZE_PX; x++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bitmap;
        } catch (WriterException error) {
            return null;
        }
    }
}

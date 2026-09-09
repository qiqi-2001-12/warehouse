package com.warehouse.fragment.home;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warehouse.R;
import com.warehouse.core.ui.AdminUi;
import com.warehouse.core.ui.ScreenFit;

import java.util.EnumMap;
import java.util.Map;

public class WifiSetupFragment extends Fragment {
    private static final String DEVICE_MAC = "54aeba1b8d55";
    private static final int QR_SIZE_PX = 224;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundResource(R.drawable.beijing);

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

        TextView back = AdminUi.text(context, "\u2190", 36, AdminUi.ACCENT, android.graphics.Typeface.BOLD);
        back.setContentDescription("Back");
        back.setClickable(true);
        back.setFocusable(true);
        back.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        header.addView(back, AdminUi.lp(AdminUi.dp(context, 60), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = AdminUi.text(context, "Wi-Fi \u914d\u7f51", 22, AdminUi.TEXT_PRIMARY,
                android.graphics.Typeface.BOLD);
        header.addView(title, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        View spacer = new View(context);
        header.addView(spacer, AdminUi.lp(AdminUi.dp(context, 60), ViewGroup.LayoutParams.MATCH_PARENT));
        return header;
    }

    private View buildContent(Context context) {
        LinearLayout content = AdminUi.column(context);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(AdminUi.dp(context, 44), AdminUi.dp(context, 22),
                AdminUi.dp(context, 44), AdminUi.dp(context, 28));

        TextView settingsButton = AdminUi.button(context, "\u524d\u5f80 Wi-Fi \u8bbe\u7f6e");
        settingsButton.setTextSize(20);
        settingsButton.setOnClickListener(v -> openWifiSettings());
        content.addView(settingsButton, AdminUi.lp(
                AdminUi.dp(context, 280), AdminUi.dp(context, 46)));

        ImageView qrCode = new ImageView(context);
        qrCode.setBackgroundColor(Color.WHITE);
        qrCode.setImageBitmap(createQrBitmap());
        qrCode.setContentDescription("Device QR code");
        LinearLayout.LayoutParams qrParams = AdminUi.lp(QR_SIZE_PX, QR_SIZE_PX);
        qrParams.topMargin = AdminUi.dp(context, 28);
        content.addView(qrCode, qrParams);

        TextView macValue = AdminUi.text(context, "MAC: " + DEVICE_MAC, 21,
                AdminUi.TEXT_PRIMARY, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams macParams = AdminUi.lp(
                ViewGroup.LayoutParams.WRAP_CONTENT, AdminUi.dp(context, 40));
        macParams.topMargin = AdminUi.dp(context, 14);
        content.addView(macValue, macParams);
        return content;
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

    private Bitmap createQrBitmap() {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(
                    DEVICE_MAC, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX, hints);
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

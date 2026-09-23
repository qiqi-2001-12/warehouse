package com.warehouse.fragment.fault;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.warehouse.device.DeviceGateway;
import com.warehouse.device.FaultMonitor;
import com.warehouse.R;
import com.warehouse.core.ui.AdminUi;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FaultFragment extends Fragment {
    private static final int FAULT_ROW_HEIGHT_DP = 54;
    private static final int FAULT_TIME_COLUMN_WIDTH_DP = 120;
    private static final int FAULT_DATE_COLUMN_WIDTH_DP = 150;
    private static final int FAULT_CONTENT_TEXT_SIZE_SP = 20;

    @Nullable
    private LinearLayout faultTable;
    private final FaultMonitor.Listener faultLogListener = this::renderFaultLog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = AdminUi.column(requireContext());
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        scrollParams.weight = 1;
        scrollParams.topMargin = AdminUi.dp(requireContext(), 18);
        root.addView(scroll, scrollParams);

        faultTable = AdminUi.column(requireContext());
        faultTable.setBackgroundColor(Color.rgb(226, 234, 232));
        scroll.addView(faultTable, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout footer = AdminUi.row(requireContext());
        footer.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams footerParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 56));
        footerParams.topMargin = AdminUi.dp(requireContext(), 12);
        root.addView(footer, footerParams);

        TextView resetFault = AdminUi.button(requireContext(), "\u6545\u969c\u590d\u4f4d");
        resetFault.setGravity(Gravity.CENTER);
        resetFault.setClickable(true);
        resetFault.setFocusable(true);
        resetFault.setOnClickListener(v -> writeConfirm(799));
        footer.addView(resetFault, AdminUi.lp(AdminUi.dp(requireContext(), 142), AdminUi.dp(requireContext(), 56)));

        View spacer = new View(requireContext());
        footer.addView(spacer, AdminUi.weightedLp(0, 0, 1));

        TextView wetReset = AdminUi.button(requireContext(), "\u6ee4\u7f51\u590d\u4f4d");
        wetReset.setGravity(Gravity.CENTER);
        wetReset.setOnClickListener(v -> writeConfirm(566));
        footer.addView(wetReset, AdminUi.lp(AdminUi.dp(requireContext(), 142), AdminUi.dp(requireContext(), 56)));

        renderFaultLog();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        FaultMonitor.get(requireContext()).addListener(faultLogListener);
    }

    @Override
    public void onPause() {
        FaultMonitor.get(requireContext()).removeListener(faultLogListener);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        faultTable = null;
        super.onDestroyView();
    }

    private void renderFaultLog() {
        if (faultTable == null || !isAdded()) {
            return;
        }
        faultTable.removeAllViews();
        addHeaderRow(faultTable);
        List<FaultMonitor.FaultLogEntry> entries = FaultMonitor.get(requireContext()).getEntries();
        if (entries.isEmpty()) {
            return;
        }
        for (FaultMonitor.FaultLogEntry entry : entries) {
            addLogRow(faultTable, entry);
        }
    }

    private void addHeaderRow(LinearLayout parent) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.rgb(235, 242, 16));
        row.setPadding(AdminUi.dp(requireContext(), 12), AdminUi.dp(requireContext(), 4), AdminUi.dp(requireContext(), 12), AdminUi.dp(requireContext(), 4));
        parent.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), FAULT_ROW_HEIGHT_DP)));

        row.addView(AdminUi.text(requireContext(), "\u65f6\u95f4", 18, Color.rgb(30, 44, 48), Typeface.BOLD), AdminUi.lp(AdminUi.dp(requireContext(), FAULT_TIME_COLUMN_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.text(requireContext(), "\u65e5\u671f", 18, Color.rgb(30, 44, 48), Typeface.BOLD), AdminUi.lp(AdminUi.dp(requireContext(), FAULT_DATE_COLUMN_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView messageHeader = AdminUi.text(requireContext(), "\u6d88\u606f", 18, Color.rgb(30, 44, 48), Typeface.BOLD);
        LinearLayout.LayoutParams messageHeaderParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        messageHeaderParams.leftMargin = AdminUi.dp(requireContext(), 8);
        row.addView(messageHeader, messageHeaderParams);
    }

    private void addLogRow(LinearLayout parent, FaultMonitor.FaultLogEntry entry) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AdminUi.dp(requireContext(), 12), AdminUi.dp(requireContext(), 4), AdminUi.dp(requireContext(), 12), AdminUi.dp(requireContext(), 4));
        parent.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 64)));

        Date eventDate = new Date(entry.timestamp);
        String timeText = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(eventDate);
        TextView timeView = AdminUi.text(requireContext(), timeText, FAULT_CONTENT_TEXT_SIZE_SP, Color.rgb(30, 44, 48), Typeface.BOLD);
        timeView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(timeView, AdminUi.lp(AdminUi.dp(requireContext(), FAULT_TIME_COLUMN_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT));

        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));

        String dateText = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(eventDate);
        TextView dateView = AdminUi.text(requireContext(), dateText, FAULT_CONTENT_TEXT_SIZE_SP, Color.rgb(30, 44, 48), Typeface.BOLD);
        dateView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(dateView, AdminUi.lp(AdminUi.dp(requireContext(), FAULT_DATE_COLUMN_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT));

        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView messageView = AdminUi.text(requireContext(), entry.message, FAULT_CONTENT_TEXT_SIZE_SP, Color.rgb(30, 44, 48), Typeface.NORMAL);
        messageView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        messageView.setMaxLines(2);
        LinearLayout.LayoutParams messageParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        messageParams.leftMargin = AdminUi.dp(requireContext(), 8);
        row.addView(messageView, messageParams);

        parent.addView(AdminUi.divider(requireContext()),
                AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private void writeConfirm(int address) {
        DeviceGateway gateway = DeviceGateway.get(requireContext());
        if (!gateway.canWrite(address)) {
            Toast.makeText(requireContext(), "\u672a\u627e\u5230\u5bc4\u5b58\u5668", Toast.LENGTH_SHORT).show();
            return;
        }
        Context appContext = requireContext().getApplicationContext();
        gateway.write(address, 1, new DeviceGateway.WriteCallback() {
            @Override
            public void onSuccess() {
                if (address == 799) {
                    FaultMonitor.get(requireContext()).clearFaultsForFiveMinutes();
                }
                if (isAdded()) {
                    Toast.makeText(appContext, "\u5df2\u53d1\u9001", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(Exception error) {
                if (isAdded()) {
                    Toast.makeText(appContext, error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}

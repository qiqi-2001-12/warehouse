package com.warehouse.fragment.operation;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.warehouse.core.ui.AdminUi;
import com.warehouse.data.local.OperationLogStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OperationLogFragment extends Fragment {
    private static final int ROW_HEIGHT_DP = 54;
    private static final int TIME_COLUMN_WIDTH_DP = 120;
    private static final int DATE_COLUMN_WIDTH_DP = 150;
    private static final int CONTENT_TEXT_SIZE_SP = 20;

    @Nullable
    private LinearLayout operationTable;
    private final OperationLogStore.Listener logListener = this::renderLogs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        LinearLayout root = AdminUi.column(requireContext());
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollParams = AdminUi.lp(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        scrollParams.weight = 1;
        scrollParams.topMargin = AdminUi.dp(requireContext(), 18);
        root.addView(scroll, scrollParams);

        operationTable = AdminUi.column(requireContext());
        operationTable.setBackgroundColor(Color.rgb(226, 234, 232));
        scroll.addView(operationTable, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView clearButton = AdminUi.button(requireContext(), "清除日志");
        clearButton.setGravity(Gravity.CENTER);
        clearButton.setOnClickListener(v -> OperationLogStore.get(requireContext()).clear());
        LinearLayout.LayoutParams clearParams = AdminUi.lp(
                ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 56));
        clearParams.topMargin = AdminUi.dp(requireContext(), 12);
        root.addView(clearButton, clearParams);

        renderLogs();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        OperationLogStore.get(requireContext()).addListener(logListener);
        renderLogs();
    }

    @Override
    public void onPause() {
        OperationLogStore.get(requireContext()).removeListener(logListener);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        operationTable = null;
        super.onDestroyView();
    }

    private void renderLogs() {
        if (operationTable == null || !isAdded()) {
            return;
        }
        operationTable.removeAllViews();
        addHeaderRow(operationTable);
        List<OperationLogStore.Entry> entries =
                OperationLogStore.get(requireContext()).getEntries();
        for (OperationLogStore.Entry entry : entries) {
            addLogRow(operationTable, entry);
        }
    }

    private void addHeaderRow(LinearLayout parent) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.rgb(235, 242, 16));
        row.setPadding(AdminUi.dp(requireContext(), 12), AdminUi.dp(requireContext(), 4),
                AdminUi.dp(requireContext(), 12), AdminUi.dp(requireContext(), 4));
        parent.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                AdminUi.dp(requireContext(), ROW_HEIGHT_DP)));

        row.addView(AdminUi.text(requireContext(), "时间", 18,
                        Color.rgb(30, 44, 48), Typeface.BOLD),
                AdminUi.lp(AdminUi.dp(requireContext(), TIME_COLUMN_WIDTH_DP),
                        ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.divider(requireContext()),
                AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.text(requireContext(), "日期", 18,
                        Color.rgb(30, 44, 48), Typeface.BOLD),
                AdminUi.lp(AdminUi.dp(requireContext(), DATE_COLUMN_WIDTH_DP),
                        ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.divider(requireContext()),
                AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView messageHeader = AdminUi.text(requireContext(), "消息", 18,
                Color.rgb(30, 44, 48), Typeface.BOLD);
        LinearLayout.LayoutParams messageParams = AdminUi.weightedLp(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        messageParams.leftMargin = AdminUi.dp(requireContext(), 8);
        row.addView(messageHeader, messageParams);
    }

    private void addLogRow(LinearLayout parent, OperationLogStore.Entry entry) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AdminUi.dp(requireContext(), 12), AdminUi.dp(requireContext(), 4),
                AdminUi.dp(requireContext(), 12), AdminUi.dp(requireContext(), 4));
        parent.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                AdminUi.dp(requireContext(), 64)));

        Date eventDate = new Date(entry.timestamp);
        TextView timeView = AdminUi.text(requireContext(),
                new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(eventDate),
                CONTENT_TEXT_SIZE_SP, Color.rgb(30, 44, 48), Typeface.BOLD);
        timeView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(timeView, AdminUi.lp(AdminUi.dp(requireContext(), TIME_COLUMN_WIDTH_DP),
                ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.divider(requireContext()),
                AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView dateView = AdminUi.text(requireContext(),
                new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(eventDate),
                CONTENT_TEXT_SIZE_SP, Color.rgb(30, 44, 48), Typeface.BOLD);
        dateView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(dateView, AdminUi.lp(AdminUi.dp(requireContext(), DATE_COLUMN_WIDTH_DP),
                ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.divider(requireContext()),
                AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView messageView = AdminUi.text(requireContext(),
                entry.operator + "：" + entry.message, CONTENT_TEXT_SIZE_SP,
                Color.rgb(30, 44, 48), Typeface.NORMAL);
        messageView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        messageView.setMaxLines(2);
        LinearLayout.LayoutParams messageParams = AdminUi.weightedLp(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        messageParams.leftMargin = AdminUi.dp(requireContext(), 8);
        row.addView(messageView, messageParams);
        parent.addView(AdminUi.divider(requireContext()),
                AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }
}

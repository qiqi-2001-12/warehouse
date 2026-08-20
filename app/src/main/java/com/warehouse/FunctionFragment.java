package com.warehouse;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class FunctionFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = AdminUi.column(requireContext());
        root.setPadding(AdminUi.dp(requireContext(), 24), AdminUi.dp(requireContext(), 18), AdminUi.dp(requireContext(), 24), AdminUi.dp(requireContext(), 18));

        TextView title = AdminUi.text(requireContext(), "功能", 22, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
        root.addView(title, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 38)));

        LinearLayout content = AdminUi.row(requireContext());
        LinearLayout.LayoutParams contentParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        contentParams.topMargin = AdminUi.dp(requireContext(), 16);
        contentParams.weight = 1;
        root.addView(content, contentParams);

        LinearLayout left = card();
        LinearLayout right = card();
        content.addView(left, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout.LayoutParams rightParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        rightParams.leftMargin = AdminUi.dp(requireContext(), 18);
        content.addView(right, rightParams);

        addSectionTitle(left, "运行状态");
        addStatusRow(left, "除湿", "关闭");
        addStatusRow(left, "新风当前运行档位", "关闭");
        addStatusRow(left, "电辅热", "关闭");
        addStatusRow(left, "调湿模式选择", "自动");
        addStatusRow(left, "送风量", "0%");

        addSectionTitle(right, "实时参数");
        addMetricRow(right, "制热温度", "0.0", "℃");
        addMetricRow(right, "含湿量", "0.00", "g/kg");
        addMetricRow(right, "压缩机频率", "0", "RPS");
        addMetricRow(right, "膨胀阀步数", "0", "Step");
        addMetricRow(right, "回风阀", "0", "%");
        return root;
    }

    private LinearLayout card() {
        LinearLayout card = AdminUi.column(requireContext());
        card.setPadding(AdminUi.dp(requireContext(), 18), AdminUi.dp(requireContext(), 16), AdminUi.dp(requireContext(), 18), AdminUi.dp(requireContext(), 16));
        card.setBackground(AdminUi.bg(AdminUi.CARD_BG, 8, AdminUi.CARD_STROKE, 1, requireContext()));
        return card;
    }

    private void addSectionTitle(LinearLayout parent, String text) {
        TextView title = AdminUi.text(requireContext(), text, 18, AdminUi.ACCENT, Typeface.BOLD);
        title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        parent.addView(title, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 34)));
    }

    private void addStatusRow(LinearLayout parent, String label, String value) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 46));
        rowParams.topMargin = AdminUi.dp(requireContext(), 8);
        parent.addView(row, rowParams);

        TextView dot = AdminUi.text(requireContext(), "●", 18, Color.rgb(236, 65, 65), Typeface.BOLD);
        row.addView(dot, AdminUi.lp(AdminUi.dp(requireContext(), 34), ViewGroup.LayoutParams.MATCH_PARENT));

        TextView labelView = AdminUi.label(requireContext(), label);
        row.addView(labelView, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        TextView valueView = AdminUi.valueBox(requireContext(), value);
        row.addView(valueView, AdminUi.lp(AdminUi.dp(requireContext(), 96), AdminUi.dp(requireContext(), 32)));
    }

    private void addMetricRow(LinearLayout parent, String label, String value, String unit) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 46));
        rowParams.topMargin = AdminUi.dp(requireContext(), 8);
        parent.addView(row, rowParams);

        TextView labelView = AdminUi.label(requireContext(), label);
        row.addView(labelView, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        TextView valueView = AdminUi.valueBox(requireContext(), value);
        row.addView(valueView, AdminUi.lp(AdminUi.dp(requireContext(), 92), AdminUi.dp(requireContext(), 32)));

        TextView unitView = AdminUi.text(requireContext(), unit, 15, AdminUi.TEXT_SECONDARY, Typeface.NORMAL);
        unitView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(unitView, AdminUi.lp(AdminUi.dp(requireContext(), 64), ViewGroup.LayoutParams.MATCH_PARENT));
    }
}

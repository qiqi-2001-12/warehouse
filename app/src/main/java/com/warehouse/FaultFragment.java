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

public class FaultFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = AdminUi.column(requireContext());
        root.setPadding(AdminUi.dp(requireContext(), 34), AdminUi.dp(requireContext(), 22), AdminUi.dp(requireContext(), 34), AdminUi.dp(requireContext(), 22));

        LinearLayout header = AdminUi.row(requireContext());
        root.addView(header, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 48)));
        TextView title = AdminUi.text(requireContext(), "报警显示", 24, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
        header.addView(title, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        TextView resetFault = AdminUi.button(requireContext(), "故障复位");
        header.addView(resetFault, AdminUi.lp(AdminUi.dp(requireContext(), 120), AdminUi.dp(requireContext(), 36)));
        TextView wetReset = AdminUi.button(requireContext(), "滤网复位");
        LinearLayout.LayoutParams wetParams = AdminUi.lp(AdminUi.dp(requireContext(), 120), AdminUi.dp(requireContext(), 36));
        wetParams.leftMargin = AdminUi.dp(requireContext(), 14);
        header.addView(wetReset, wetParams);

        LinearLayout table = AdminUi.column(requireContext());
        table.setBackground(AdminUi.bg(Color.rgb(226, 234, 232), 2, Color.rgb(120, 135, 135), 1, requireContext()));
        LinearLayout.LayoutParams tableParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        tableParams.topMargin = AdminUi.dp(requireContext(), 24);
        tableParams.weight = 1;
        root.addView(table, tableParams);

        addRow(table, new String[]{"时间", "日期", "消息"}, true);
        for (int i = 0; i < 7; i++) {
            addRow(table, new String[]{"", "", ""}, false);
        }

        LinearLayout footer = AdminUi.row(requireContext());
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 54));
        footerParams.topMargin = AdminUi.dp(requireContext(), 16);
        root.addView(footer, footerParams);

        TextView prev = AdminUi.button(requireContext(), "上一页");
        footer.addView(prev, AdminUi.lp(AdminUi.dp(requireContext(), 120), AdminUi.dp(requireContext(), 38)));
        TextView next = AdminUi.button(requireContext(), "下一页");
        LinearLayout.LayoutParams nextParams = AdminUi.lp(AdminUi.dp(requireContext(), 120), AdminUi.dp(requireContext(), 38));
        nextParams.leftMargin = AdminUi.dp(requireContext(), 80);
        footer.addView(next, nextParams);
        return root;
    }

    private void addRow(LinearLayout table, String[] cells, boolean header) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER);
        row.setBackgroundColor(header ? Color.rgb(235, 242, 16) : Color.rgb(232, 238, 236));
        table.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, header ? AdminUi.dp(requireContext(), 42) : 0));
        if (!header) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) row.getLayoutParams();
            params.weight = 1;
            row.setLayoutParams(params);
        }
        for (int i = 0; i < cells.length; i++) {
            String cell = cells[i];
            TextView text = AdminUi.text(requireContext(), cell, 16, Color.rgb(30, 44, 48), header ? Typeface.BOLD : Typeface.NORMAL);
            row.addView(text, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            if (i < cells.length - 1) {
                row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }
    }
}

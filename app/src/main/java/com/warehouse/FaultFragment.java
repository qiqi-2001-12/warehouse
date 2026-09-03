package com.warehouse;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

public class FaultFragment extends Fragment {
    private static final FaultItem[] ITEMS = new FaultItem[]{
            new FaultItem(800, "新风温湿度传感器通讯故障"),
            new FaultItem(801, "送风温湿度传感器通讯故障"),
            new FaultItem(807, "补水超时故障"),
            new FaultItem(808, "滤网报警"),
            new FaultItem(809, "压机驱动板通讯报警"),
            new FaultItem(814, "机组排水故障"),
            new FaultItem(822, "补水防冻报警"),
            new FaultItem(823, "高压传感器故障"),
            new FaultItem(824, "低压传感器故障"),
            new FaultItem(825, "压机本身故障位"),
            new FaultItem(826, "冷凝风机本身故障位"),
            new FaultItem(829, "机组漏氟报警"),
            new FaultItem(832, "五合一传感器通讯故障"),
            new FaultItem(841, "吸气温度过低保护"),
            new FaultItem(842, "排气过热度过高保护"),
            new FaultItem(843, "排气温度传感器故障"),
            new FaultItem(844, "吸气温度传感器故障")
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = AdminUi.column(requireContext());
        root.setPadding(AdminUi.dp(requireContext(), 34), AdminUi.dp(requireContext(), 22), AdminUi.dp(requireContext(), 34), AdminUi.dp(requireContext(), 22));

        LinearLayout header = AdminUi.row(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 48)));

        TextView resetFault = AdminUi.button(requireContext(), "故障复位");
        resetFault.setOnClickListener(v -> writeConfirm(799));
        header.addView(resetFault, AdminUi.lp(AdminUi.dp(requireContext(), 120), AdminUi.dp(requireContext(), 36)));

        TextView wetReset = AdminUi.button(requireContext(), "滤网复位");
        LinearLayout.LayoutParams wetParams = AdminUi.lp(AdminUi.dp(requireContext(), 120), AdminUi.dp(requireContext(), 36));
        wetParams.leftMargin = AdminUi.dp(requireContext(), 14);
        wetReset.setOnClickListener(v -> writeConfirm(566));
        header.addView(wetReset, wetParams);

        View spacer = new View(requireContext());
        header.addView(spacer, AdminUi.weightedLp(0, 0, 1));

        TextView factoryReset = AdminUi.button(requireContext(), "恢复出厂设置");
        factoryReset.setOnClickListener(v -> writeConfirm(539));
        header.addView(factoryReset, AdminUi.lp(AdminUi.dp(requireContext(), 120), AdminUi.dp(requireContext(), 36)));

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        scrollParams.topMargin = AdminUi.dp(requireContext(), 24);
        scrollParams.weight = 1;
        root.addView(scroll, scrollParams);

        LinearLayout table = AdminUi.column(requireContext());
        table.setBackground(AdminUi.bg(Color.rgb(226, 234, 232), 2, Color.rgb(120, 135, 135), 1, requireContext()));
        scroll.addView(table, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addHeaderRow(table);
        for (FaultItem item : ITEMS) {
            addFaultRow(table, item);
        }
        return root;
    }

    private void addHeaderRow(LinearLayout parent) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.rgb(235, 242, 16));
        parent.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 42)));

        row.addView(AdminUi.text(requireContext(), "地址", 16, Color.rgb(30, 44, 48), Typeface.BOLD), AdminUi.lp(AdminUi.dp(requireContext(), 72), ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.text(requireContext(), "故障内容", 16, Color.rgb(30, 44, 48), Typeface.BOLD), AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.text(requireContext(), "状态", 16, Color.rgb(30, 44, 48), Typeface.BOLD), AdminUi.lp(AdminUi.dp(requireContext(), 74), ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.text(requireContext(), "灯", 16, Color.rgb(30, 44, 48), Typeface.BOLD), AdminUi.lp(AdminUi.dp(requireContext(), 44), ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void addFaultRow(LinearLayout parent, FaultItem item) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, AdminUi.dp(requireContext(), 4), 0, AdminUi.dp(requireContext(), 4));
        parent.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 42)));

        TextView address = AdminUi.text(requireContext(), String.valueOf(item.address), 14, Color.rgb(30, 44, 48), Typeface.BOLD);
        row.addView(address, AdminUi.lp(AdminUi.dp(requireContext(), 72), ViewGroup.LayoutParams.MATCH_PARENT));

        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView label = AdminUi.text(requireContext(), item.label, 14, Color.rgb(30, 44, 48), Typeface.NORMAL);
        label.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams labelParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        labelParams.leftMargin = AdminUi.dp(requireContext(), 8);
        row.addView(label, labelParams);

        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));

        FaultStatusView statusView = new FaultStatusView(requireContext());
        row.addView(statusView.root, AdminUi.lp(AdminUi.dp(requireContext(), 74), AdminUi.dp(requireContext(), 28)));

        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));

        FaultStatusLight light = new FaultStatusLight(requireContext());
        row.addView(light.root, AdminUi.lp(AdminUi.dp(requireContext(), 44), AdminUi.dp(requireContext(), 28)));

        bindFaultStatus(item.address, statusView, light);
    }

    private void bindFaultStatus(int address, FaultStatusView statusView, FaultStatusLight light) {
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        if (spec == null) {
            statusView.setUnknown();
            light.setUnknown();
            return;
        }
        ModbusManager.get(requireContext()).read(spec, new ModbusManager.IntCallback() {
            @Override
            public void onSuccess(int value) {
                statusView.setValue(value);
                light.setValue(value);
            }

            @Override
            public void onError(Exception error) {
                statusView.setUnknown();
                light.setUnknown();
            }
        });
    }

    private void writeConfirm(int address) {
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        if (spec == null) {
            Toast.makeText(requireContext(), "未找到寄存器", Toast.LENGTH_SHORT).show();
            return;
        }
        ModbusManager.get(requireContext()).write(spec, 1, new ModbusManager.VoidCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(requireContext(), "已发送", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static final class FaultItem {
        final int address;
        final String label;

        FaultItem(int address, String label) {
            this.address = address;
            this.label = label;
        }
    }

    private static final class FaultStatusView {
        final FrameLayout root;
        private final TextView textView;

        FaultStatusView(@NonNull android.content.Context context) {
            root = new FrameLayout(context);
            root.setBackground(AdminUi.bg(Color.rgb(8, 25, 36), 10, Color.rgb(20, 113, 136), 1, context));

            textView = AdminUi.text(context, "--", 14, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
            textView.setGravity(Gravity.CENTER);
            root.addView(textView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        void setValue(int value) {
            if (value == 0) {
                textView.setText("正常");
                textView.setTextColor(Color.rgb(120, 218, 51));
            } else if (value == 1) {
                textView.setText("故障");
                textView.setTextColor(Color.rgb(229, 51, 43));
            } else {
                textView.setText(String.valueOf(value));
                textView.setTextColor(AdminUi.TEXT_PRIMARY);
            }
        }

        void setUnknown() {
            textView.setText("--");
            textView.setTextColor(AdminUi.TEXT_SECONDARY);
        }
    }

    private static final class FaultStatusLight {
        final FrameLayout root;
        private final GradientDrawable shellBg = new GradientDrawable();
        private final GradientDrawable coreBg = new GradientDrawable();
        private final View core;

        FaultStatusLight(@NonNull android.content.Context context) {
            root = new FrameLayout(context);
            shellBg.setShape(GradientDrawable.RECTANGLE);
            shellBg.setCornerRadius(AdminUi.dp(context, 14));
            root.setBackground(shellBg);
            root.setPadding(AdminUi.dp(context, 2), AdminUi.dp(context, 2), AdminUi.dp(context, 2), AdminUi.dp(context, 2));
            root.setElevation(AdminUi.dp(context, 1));

            core = new View(context);
            coreBg.setShape(GradientDrawable.OVAL);
            core.setBackground(coreBg);
            root.addView(core, new FrameLayout.LayoutParams(AdminUi.dp(context, 14), AdminUi.dp(context, 14), Gravity.CENTER));
            setUnknown();
        }

        void setValue(int value) {
            if (value == 0) {
                setColor(Color.rgb(120, 218, 51), Color.argb(200, 120, 218, 51));
            } else if (value == 1) {
                setColor(Color.rgb(229, 51, 43), Color.argb(200, 229, 51, 43));
            } else {
                setUnknown();
            }
        }

        void setUnknown() {
            setColor(Color.rgb(120, 126, 132), Color.argb(160, 120, 126, 132));
        }

        private void setColor(int coreColor, int strokeColor) {
            shellBg.setColor(Color.rgb(8, 25, 36));
            shellBg.setStroke(1, strokeColor);
            coreBg.setColor(coreColor);
            coreBg.setStroke(1, Color.argb(110, 255, 255, 255));
            core.invalidate();
        }
    }
}

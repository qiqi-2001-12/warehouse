package com.warehouse;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class FactoryParamsFragment extends Fragment {
    private final TextView[] pageTabs = new TextView[5];
    private FrameLayout content;
    private int selectedPage;

    private final String[][][] pages = new String[][][]{
            {
                    {"环境温度", "0.0", "℃"}, {"排气温度", "0.0", "℃"}, {"吸气温度", "0.0", "℃"}, {"蒸发温度", "0.0", "℃"}, {"冷凝温度", "0.0", "℃"},
                    {"高压压力", "0.0", "Bar"}, {"低压压力", "0.0", "Bar"}, {"吸气过热度", "0.0", "℃"}, {"排气过热度", "0.0", "℃"}, {"室外盘管温度", "0.0", "℃"}
            },
            {
                    {"加湿泵", "关", ""}, {"排水阀", "关", ""}, {"补水阀", "关", ""}, {"新风阀", "关", ""}, {"回风阀", "开", ""},
                    {"送风风速", "0", "%"}, {"压机频率", "0", "RPS"}, {"压机MAX", "0", ""}, {"膨胀阀步数", "0", "Step"}, {"压机MIN", "0", ""}, {"冷凝风扇", "0", "%"}
            },
            {
                    {"舒适档风机输出", "0", "%"}, {"强力档风机输出", "0", "%"}, {"极速档风机输出", "0", "%"},
                    {"舒适档新风阀输出", "0", "%"}, {"强力档新风阀输出", "0", "%"}, {"极速档新风阀输出", "0", "%"},
                    {"舒适档排风阀输出", "0", "%"}, {"强力档排风阀输出", "0", "%"}, {"极速档排风阀输出", "0", "%"},
                    {"排风机", "关闭", ""}, {"新风电热最大输出", "0", "%"}, {"回风阀", "自动", ""}
            },
            {
                    {"舒适档PM2.5上限", "0", "ug/m3"}, {"强力档PM2.5上限", "0", "ug/m3"}, {"1分钟PM2.5突增判定值", "0", "ug/m3"},
                    {"舒适档CO₂上限", "0", "ppm"}, {"强力档CO₂上限", "0", "ppm"}, {"1分钟CO₂突增判定值", "0", "ppm"}
            },
            {
                    {"新风预热温度设定值", "0.0", "℃"}, {"新风预热比例调节", "0.0", "℃"}, {"极速档首次检查时间", "0", "分钟"},
                    {"极速档强制切舒适附加时间", "0", "分钟"}, {"极速退出后舒适保持时间", "0", "分钟"}
            }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = AdminUi.column(requireContext());
        root.setPadding(AdminUi.dp(requireContext(), 14), AdminUi.dp(requireContext(), 18), AdminUi.dp(requireContext(), 14), AdminUi.dp(requireContext(), 18));

        LinearLayout header = AdminUi.row(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 42)));

        TextView resetFactory = AdminUi.button(requireContext(), "\u6062\u590d\u51fa\u5382\u8bbe\u7f6e");
        resetFactory.setOnClickListener(v -> writeConfirm(539));
        header.addView(resetFactory, AdminUi.lp(AdminUi.dp(requireContext(), 120), AdminUi.dp(requireContext(), 32)));

        View spacer = new View(requireContext());
        LinearLayout.LayoutParams spacerParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        spacerParams.leftMargin = AdminUi.dp(requireContext(), 10);
        header.addView(spacer, spacerParams);

        for (int i = 0; i < pageTabs.length; i++) {
            final int page = i;
            TextView tab = AdminUi.button(requireContext(), String.valueOf(i + 1));
            tab.setTextSize(15);
            tab.setOnClickListener(v -> renderPage(page));
            LinearLayout.LayoutParams params = AdminUi.lp(AdminUi.dp(requireContext(), 44), AdminUi.dp(requireContext(), 32));
            params.leftMargin = AdminUi.dp(requireContext(), 8);
            header.addView(tab, params);
            pageTabs[i] = tab;
        }

        content = new FrameLayout(requireContext());
        content.setBackground(AdminUi.bg(AdminUi.CARD_BG, 8, AdminUi.CARD_STROKE, 1, requireContext()));
        LinearLayout.LayoutParams contentParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        contentParams.topMargin = AdminUi.dp(requireContext(), 10);
        contentParams.weight = 1;
        root.addView(content, contentParams);

        renderPage(0);
        return root;
    }

    private void renderPage(int page) {
        selectedPage = page;
        for (int i = 0; i < pageTabs.length; i++) {
            AdminUi.setSelected(pageTabs[i], selectedPage == i);
        }
        if (content == null) {
            return;
        }
        content.removeAllViews();
        if (page >= 0 && page <= 4) {
            int layoutRes;
            if (page == 0) {
                layoutRes = R.layout.page_factory_params_1;
            } else if (page == 1) {
                layoutRes = R.layout.page_factory_params_2;
            } else if (page == 2) {
                layoutRes = R.layout.page_factory_params_3;
            } else if (page == 3) {
                layoutRes = R.layout.page_factory_params_4;
            } else {
                layoutRes = R.layout.page_factory_params_5;
            }
            View fixedPage = LayoutInflater.from(requireContext()).inflate(layoutRes, content, false);
            content.addView(fixedPage, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }
        LinearLayout pageRoot = AdminUi.column(requireContext());
        pageRoot.setPadding(AdminUi.dp(requireContext(), 28), AdminUi.dp(requireContext(), 18), AdminUi.dp(requireContext(), 28), AdminUi.dp(requireContext(), 18));
        content.addView(pageRoot, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout grid = AdminUi.row(requireContext());
        grid.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams gridParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        gridParams.topMargin = AdminUi.dp(requireContext(), 4);
        gridParams.weight = 1;
        pageRoot.addView(grid, gridParams);

        LinearLayout left = AdminUi.column(requireContext());
        LinearLayout right = AdminUi.column(requireContext());
        grid.addView(left, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout.LayoutParams rightParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        rightParams.leftMargin = AdminUi.dp(requireContext(), 28);
        grid.addView(right, rightParams);

        String[][] rows = pages[page];
        int midpoint = (rows.length + 1) / 2;
        for (int i = 0; i < rows.length; i++) {
            addParamRow(i < midpoint ? left : right, rows[i]);
        }
    }

    private void addParamRow(LinearLayout parent, String[] data) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 42));
        params.topMargin = AdminUi.dp(requireContext(), 8);
        parent.addView(row, params);

        TextView label = AdminUi.label(requireContext(), data[0]);
        label.setTextSize(16);
        row.addView(label, AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        TextView value = AdminUi.valueBox(requireContext(), data[1]);
        row.addView(value, AdminUi.lp(AdminUi.dp(requireContext(), 78), AdminUi.dp(requireContext(), 28)));

        TextView unit = AdminUi.text(requireContext(), data[2], 14, AdminUi.TEXT_SECONDARY, Typeface.NORMAL);
        unit.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(unit, AdminUi.lp(AdminUi.dp(requireContext(), 70), ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void writeConfirm(int address) {
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        if (spec == null) {
            Toast.makeText(requireContext(), "\u672a\u627e\u5230\u5bc4\u5b58\u5668", Toast.LENGTH_SHORT).show();
            return;
        }
        ModbusManager.get(requireContext()).write(spec, 1, new ModbusManager.VoidCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(requireContext(), "\u5df2\u53d1\u9001", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Exception error) {
                Toast.makeText(requireContext(), error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

}

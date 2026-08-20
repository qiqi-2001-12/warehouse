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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AdminContainerFragment extends Fragment {
    private View panel;
    private int contentId;
    private TextView functionTab;
    private TextView faultTab;
    private TextView factoryTab;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout root = new FrameLayout(requireContext());
        root.setBackgroundColor(AdminUi.PAGE_BG);

        LinearLayout shell = AdminUi.column(requireContext());
        shell.setBackground(AdminUi.bg(AdminUi.PANEL_BG, 18, AdminUi.PANEL_STROKE, 1, requireContext()));
        int pad = AdminUi.dp(requireContext(), 18);
        shell.setPadding(pad, AdminUi.dp(requireContext(), 14), pad, AdminUi.dp(requireContext(), 14));
        FrameLayout.LayoutParams shellParams = new FrameLayout.LayoutParams(AdminUi.dp(requireContext(), 960), AdminUi.dp(requireContext(), 600));
        shellParams.gravity = Gravity.CENTER;
        root.addView(shell, shellParams);
        panel = shell;

        shell.addView(createHeader(), AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 54)));
        shell.addView(createTabs(), AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 44)));

        FrameLayout content = new FrameLayout(requireContext());
        contentId = View.generateViewId();
        content.setId(contentId);
        content.setBackground(AdminUi.bg(Color.rgb(5, 36, 52), 10, Color.rgb(18, 96, 118), 1, requireContext()));
        LinearLayout.LayoutParams contentParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        contentParams.topMargin = AdminUi.dp(requireContext(), 12);
        contentParams.weight = 1;
        shell.addView(content, contentParams);

        root.post(() -> scalePanelToScreen(root));
        showPage(new FunctionFragment(), functionTab);
        return root;
    }

    private View createHeader() {
        FrameLayout header = new FrameLayout(requireContext());

        TextView back = AdminUi.text(requireContext(), "返回", 16, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
        back.setGravity(Gravity.CENTER);
        back.setBackground(AdminUi.bg(Color.rgb(14, 63, 85), 6, AdminUi.PANEL_STROKE, 1, requireContext()));
        back.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(AdminUi.dp(requireContext(), 88), AdminUi.dp(requireContext(), 36));
        backParams.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        header.addView(back, backParams);

        TextView title = AdminUi.text(requireContext(), "管理中心", 24, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(AdminUi.dp(requireContext(), 260), ViewGroup.LayoutParams.MATCH_PARENT);
        titleParams.gravity = Gravity.CENTER;
        header.addView(title, titleParams);

        return header;
    }

    private View createTabs() {
        LinearLayout tabs = AdminUi.row(requireContext());
        tabs.setGravity(Gravity.CENTER);
        functionTab = addTab(tabs, "功能");
        faultTab = addTab(tabs, "故障");
        factoryTab = addTab(tabs, "厂家参数");
        functionTab.setOnClickListener(v -> showPage(new FunctionFragment(), functionTab));
        faultTab.setOnClickListener(v -> showPage(new FaultFragment(), faultTab));
        factoryTab.setOnClickListener(v -> showPage(new FactoryParamsFragment(), factoryTab));
        return tabs;
    }

    private TextView addTab(LinearLayout tabs, String title) {
        TextView tab = AdminUi.button(requireContext(), title);
        LinearLayout.LayoutParams params = AdminUi.lp(AdminUi.dp(requireContext(), 130), AdminUi.dp(requireContext(), 36));
        if (tabs.getChildCount() > 0) {
            params.leftMargin = AdminUi.dp(requireContext(), 14);
        }
        tabs.addView(tab, params);
        return tab;
    }

    private void showPage(Fragment fragment, TextView selectedTab) {
        if (contentId == 0) {
            return;
        }
        AdminUi.setSelected(functionTab, selectedTab == functionTab);
        AdminUi.setSelected(faultTab, selectedTab == faultTab);
        AdminUi.setSelected(factoryTab, selectedTab == factoryTab);
        getChildFragmentManager()
                .beginTransaction()
                .replace(contentId, fragment)
                .commit();
    }

    private void scalePanelToScreen(View root) {
        if (panel == null || root.getWidth() == 0 || root.getHeight() == 0) {
            return;
        }
        ViewGroup.LayoutParams params = panel.getLayoutParams();
        float scale = Math.min(root.getWidth() / (float) params.width, root.getHeight() / (float) params.height);
        panel.setPivotX(params.width / 2f);
        panel.setPivotY(params.height / 2f);
        panel.setScaleX(scale);
        panel.setScaleY(scale);
    }
}

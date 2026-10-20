package com.warehouse.fragment.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.warehouse.R;
import com.warehouse.core.ui.AdminUi;
import com.warehouse.core.ui.ScreenFit;
import com.warehouse.fragment.factory.FactoryParamsFragment;
import com.warehouse.fragment.fault.FaultFragment;
import com.warehouse.fragment.operation.OperationLogFragment;

public class AdminContainerFragment extends Fragment {
    private int contentId;
    private TextView faultTab;
    private TextView factoryTab;
    private TextView operationTab;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_admin_container, container, false);
        View backTouchTarget = root.findViewById(R.id.btn_back_touch_target);
        faultTab = root.findViewById(R.id.tab_fault);
        factoryTab = root.findViewById(R.id.tab_factory);
        operationTab = root.findViewById(R.id.tab_operation);
        contentId = R.id.admin_content;

        ScreenFit.bind(root, root.findViewById(R.id.admin_shell));

        AdminUi.addPressFeedback(backTouchTarget);
        backTouchTarget.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        AdminUi.addPressFeedback(faultTab);
        AdminUi.addPressFeedback(factoryTab);
        AdminUi.addPressFeedback(operationTab);
        faultTab.setOnClickListener(v -> showPage(new FaultFragment(), faultTab));
        factoryTab.setOnClickListener(v -> showPage(new FactoryParamsFragment(), factoryTab));
        operationTab.setOnClickListener(v -> showPage(new OperationLogFragment(), operationTab));
        showPage(new FactoryParamsFragment(), factoryTab);
        return root;
    }

    private void showPage(Fragment fragment, TextView selectedTab) {
        if (contentId == 0) {
            return;
        }
        AdminUi.setSelected(faultTab, selectedTab == faultTab);
        AdminUi.setSelected(factoryTab, selectedTab == factoryTab);
        AdminUi.setSelected(operationTab, selectedTab == operationTab);
        getChildFragmentManager()
                .beginTransaction()
                .replace(contentId, fragment)
                .commit();
    }
}

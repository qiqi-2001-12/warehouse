package com.warehouse;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class AdminContainerFragment extends Fragment {
    private int contentId;
    private TextView faultTab;
    private TextView factoryTab;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_admin_container, container, false);
        TextView back = root.findViewById(R.id.btn_back);
        faultTab = root.findViewById(R.id.tab_fault);
        factoryTab = root.findViewById(R.id.tab_factory);
        contentId = R.id.admin_content;

        back.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        faultTab.setOnClickListener(v -> showPage(new FaultFragment(), faultTab));
        factoryTab.setOnClickListener(v -> showPage(new FactoryParamsFragment(), factoryTab));
        showPage(new FaultFragment(), faultTab);
        return root;
    }

    private void showPage(Fragment fragment, TextView selectedTab) {
        if (contentId == 0) {
            return;
        }
        AdminUi.setSelected(faultTab, selectedTab == faultTab);
        AdminUi.setSelected(factoryTab, selectedTab == factoryTab);
        getChildFragmentManager()
                .beginTransaction()
                .replace(contentId, fragment)
                .commit();
    }
}

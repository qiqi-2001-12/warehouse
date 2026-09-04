package com.warehouse;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

public class FactoryParamsFragment extends Fragment {
    private static final int[] PAGE_LAYOUTS = new int[]{
            R.layout.params_1,
            R.layout.params_2,
            R.layout.params_3,
            R.layout.params_4,
            R.layout.params_5
    };

    private final TextView[] pageTabs = new TextView[PAGE_LAYOUTS.length];
    private ViewPager2 pager;
    private int selectedPage;

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
            tab.setOnClickListener(v -> selectPage(page, true));
            LinearLayout.LayoutParams params = AdminUi.lp(AdminUi.dp(requireContext(), 44), AdminUi.dp(requireContext(), 32));
            params.leftMargin = AdminUi.dp(requireContext(), 8);
            header.addView(tab, params);
            pageTabs[i] = tab;
        }

        pager = new ViewPager2(requireContext());
        pager.setBackground(AdminUi.bg(AdminUi.CARD_BG, 8, AdminUi.CARD_STROKE, 1, requireContext()));
        pager.setOffscreenPageLimit(PAGE_LAYOUTS.length);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pager.setAdapter(new FactoryParamsPagerAdapter());
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateSelectedPage(position);
            }
        });

        LinearLayout.LayoutParams pagerParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        pagerParams.topMargin = AdminUi.dp(requireContext(), 10);
        pagerParams.weight = 1;
        root.addView(pager, pagerParams);

        updateSelectedPage(selectedPage);
        pager.setCurrentItem(selectedPage, false);
        return root;
    }

    private void selectPage(int page, boolean smoothScroll) {
        if (pager == null) {
            updateSelectedPage(page);
            return;
        }
        if (pager.getCurrentItem() == page) {
            updateSelectedPage(page);
            return;
        }
        pager.setCurrentItem(page, smoothScroll);
    }

    private void updateSelectedPage(int page) {
        selectedPage = page;
        for (int i = 0; i < pageTabs.length; i++) {
            AdminUi.setSelected(pageTabs[i], selectedPage == i);
        }
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

    private static final class FactoryParamsPagerAdapter extends RecyclerView.Adapter<FactoryParamsPagerAdapter.PageViewHolder> {
        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
            return new PageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        }

        @Override
        public int getItemCount() {
            return PAGE_LAYOUTS.length;
        }

        @Override
        public int getItemViewType(int position) {
            return PAGE_LAYOUTS[position];
        }

        private static final class PageViewHolder extends RecyclerView.ViewHolder {
            PageViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}

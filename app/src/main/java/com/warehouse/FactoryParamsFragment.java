package com.warehouse;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final int[] PAGE_LAYOUTS = new int[]{
            R.layout.params_1,
            R.layout.params_2,
            R.layout.params_3,
            R.layout.params_4,
            R.layout.params_5
    };

    private final TextView[] pageTabs = new TextView[PAGE_LAYOUTS.length];
    private final View[] pageRoots = new View[PAGE_LAYOUTS.length];
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = this::pollSelectedPage;
    private ViewPager2 pager;
    private int selectedPage;
    private boolean pollingActive;
    private boolean pollInFlight;
    private int pendingPollReads;

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

    @Override
    public void onStart() {
        super.onStart();
        startPolling();
    }

    @Override
    public void onStop() {
        stopPolling();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        stopPolling();
        for (int i = 0; i < pageRoots.length; i++) {
            pageRoots[i] = null;
        }
        pager = null;
        super.onDestroyView();
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
        requestImmediatePoll();
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

    private void startPolling() {
        pollingActive = true;
        requestImmediatePoll();
    }

    private void stopPolling() {
        pollingActive = false;
        pollInFlight = false;
        pendingPollReads = 0;
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void requestImmediatePoll() {
        scheduleNextPoll(0L);
    }

    private void scheduleNextPoll(long delayMs) {
        pollHandler.removeCallbacks(pollRunnable);
        if (!pollingActive || !isAdded()) {
            return;
        }
        pollHandler.postDelayed(pollRunnable, Math.max(0L, delayMs));
    }

    private void pollSelectedPage() {
        if (!pollingActive || !isAdded()) {
            return;
        }
        if (pollInFlight) {
            scheduleNextPoll(POLL_INTERVAL_MS);
            return;
        }
        if (selectedPage < 0 || selectedPage >= PAGE_LAYOUTS.length) {
            scheduleNextPoll(POLL_INTERVAL_MS);
            return;
        }
        View pageRoot = pageRoots[selectedPage];
        if (pageRoot == null) {
            scheduleNextPoll(POLL_INTERVAL_MS);
            return;
        }
        int reads = refreshPage(pageRoot, PAGE_LAYOUTS[selectedPage]);
        if (reads <= 0) {
            scheduleNextPoll(POLL_INTERVAL_MS);
            return;
        }
        pollInFlight = true;
        pendingPollReads = reads;
    }

    private int refreshPage(View root, int layoutRes) {
        if (layoutRes == R.layout.params_1) {
            return refreshPage1(root);
        }
        if (layoutRes == R.layout.params_2) {
            return refreshPage2(root);
        }
        if (layoutRes == R.layout.params_3) {
            return refreshPage3(root);
        }
        if (layoutRes == R.layout.params_4) {
            return refreshPage4(root);
        }
        if (layoutRes == R.layout.params_5) {
            return refreshPage5(root);
        }
        return 0;
    }

    private int refreshPage1(View root) {
        int reads = 0;
        reads += refreshValue(root, R.id.param1_env_temp_value, 902);
        reads += refreshValue(root, R.id.param1_high_pressure_value, 919);
        reads += refreshValue(root, R.id.param1_exhaust_temp_value, 900);
        reads += refreshValue(root, R.id.param1_low_pressure_value, 918);
        reads += refreshValue(root, R.id.param1_suction_temp_value, 903);
        reads += refreshValue(root, R.id.param1_suction_superheat_value, 922);
        reads += refreshValue(root, R.id.param1_evaporation_temp_value, 920);
        reads += refreshValue(root, R.id.param1_exhaust_superheat_value, 923);
        reads += refreshValue(root, R.id.param1_condensing_temp_value, 921);
        reads += refreshValue(root, R.id.param1_outdoor_coil_temp_value, 901);
        return reads;
    }

    private int refreshPage2(View root) {
        int reads = 0;
        reads += refreshStatus(root, R.id.param2_low_level_status, 947);
        reads += refreshStatus(root, R.id.param2_humidifier_pump_status, 952);
        reads += refreshStatus(root, R.id.param2_drain_valve_status, 953);
        reads += refreshStatus(root, R.id.param2_makeup_valve_status, 954);
        reads += refreshStatus(root, R.id.param2_return_air_valve_status, 951);
        reads += refreshStatus(root, R.id.param2_exhaust_fan_status, 935);
        reads += refreshValue(root, R.id.param2_compressor_freq_value, 512);
        reads += refreshValue(root, R.id.param2_expansion_valve_step_value, 524);
        reads += refreshValue(root, R.id.param2_condenser_fan_value, 525);
        return reads;
    }

    private int refreshPage3(View root) {
        int reads = 0;
        reads += refreshValue(root, R.id.param3_energy_fan_output_value, 143);
        reads += refreshValue(root, R.id.param3_comfort_fan_output_value, 145);
        reads += refreshValue(root, R.id.param3_boost_fan_output_value, 147);
        reads += refreshValue(root, R.id.param3_energy_fresh_air_valve_value, 144);
        reads += refreshValue(root, R.id.param3_comfort_fresh_air_valve_value, 146);
        reads += refreshValue(root, R.id.param3_boost_fresh_air_valve_value, 148);
        reads += refreshValue(root, R.id.param3_energy_exhaust_valve_value, 169);
        reads += refreshValue(root, R.id.param3_comfort_exhaust_valve_value, 170);
        reads += refreshValue(root, R.id.param3_boost_exhaust_valve_value, 171);
        reads += refreshValue(root, R.id.param3_fresh_air_heater_max_output_value, 140);
        return reads;
    }

    private int refreshPage4(View root) {
        int reads = 0;
        reads += refreshValue(root, R.id.param4_energy_pm25_limit_value, 151);
        reads += refreshValue(root, R.id.param4_comfort_pm25_limit_value, 152);
        reads += refreshValue(root, R.id.param4_pm25_rise_limit_value, 154);
        reads += refreshValue(root, R.id.param4_energy_co2_limit_value, 149);
        reads += refreshValue(root, R.id.param4_comfort_co2_limit_value, 150);
        reads += refreshValue(root, R.id.param4_co2_rise_limit_value, 153);
        return reads;
    }

    private int refreshPage5(View root) {
        int reads = 0;
        reads += refreshValue(root, R.id.param5_preheat_temp_setpoint_value, 142);
        reads += refreshValue(root, R.id.param5_preheat_ratio_adjust_value, 158);
        reads += refreshValue(root, R.id.param5_boost_first_check_time_value, 155);
        reads += refreshValue(root, R.id.param5_boost_force_to_energy_extra_time_value, 156);
        reads += refreshValue(root, R.id.param5_boost_exit_energy_hold_time_value, 157);
        return reads;
    }

    private int refreshValue(View root, int viewId, int address) {
        TextView view = root.findViewById(viewId);
        if (view == null) {
            return 0;
        }
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        if (spec == null || !spec.canRead()) {
            view.setText("--");
            return 0;
        }
        ModbusManager.get(view.getContext()).read(spec, new ModbusManager.IntCallback() {
            @Override
            public void onSuccess(int value) {
                view.setTag(value);
                view.setText(ModbusUi.formatDefault(spec, value));
                onPollReadFinished();
            }

            @Override
            public void onError(Exception error) {
                view.setText("--");
                onPollReadFinished();
            }
        });
        return 1;
    }

    private int refreshStatus(View root, int viewId, int address) {
        View indicator = root.findViewById(viewId);
        if (indicator == null) {
            return 0;
        }
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        if (spec == null || !spec.canRead()) {
            indicator.setBackgroundResource(R.drawable.factory_status_light_placeholder);
            return 0;
        }
        ModbusManager.get(indicator.getContext()).read(spec, new ModbusManager.IntCallback() {
            @Override
            public void onSuccess(int value) {
                indicator.setBackgroundResource(value == 1
                        ? R.drawable.factory_status_light_green
                        : R.drawable.factory_status_light_red);
                onPollReadFinished();
            }

            @Override
            public void onError(Exception error) {
                indicator.setBackgroundResource(R.drawable.factory_status_light_placeholder);
                onPollReadFinished();
            }
        });
        return 1;
    }

    private void onPollReadFinished() {
        if (pendingPollReads > 0) {
            pendingPollReads--;
        }
        if (pendingPollReads > 0) {
            return;
        }
        pollInFlight = false;
        if (pollingActive) {
            scheduleNextPoll(POLL_INTERVAL_MS);
        }
    }

    private static void bindPage(View root, int layoutRes) {
        if (root == null) {
            return;
        }
        if (layoutRes == R.layout.params_1) {
            bindPage1(root);
            return;
        }
        if (layoutRes == R.layout.params_2) {
            bindPage2(root);
            return;
        }
        if (layoutRes == R.layout.params_3) {
            bindPage3(root);
            return;
        }
        if (layoutRes == R.layout.params_4) {
            bindPage4(root);
            return;
        }
        if (layoutRes == R.layout.params_5) {
            bindPage5(root);
        }
    }

    private static void bindPage1(View root) {
        bindValue(root, R.id.param1_env_temp_value, 902);
        bindValue(root, R.id.param1_high_pressure_value, 919);
        bindValue(root, R.id.param1_exhaust_temp_value, 900);
        bindValue(root, R.id.param1_low_pressure_value, 918);
        bindValue(root, R.id.param1_suction_temp_value, 903);
        bindValue(root, R.id.param1_suction_superheat_value, 922);
        bindValue(root, R.id.param1_evaporation_temp_value, 920);
        bindValue(root, R.id.param1_exhaust_superheat_value, 923);
        bindValue(root, R.id.param1_condensing_temp_value, 921);
        bindValue(root, R.id.param1_outdoor_coil_temp_value, 901);
    }

    private static void bindPage2(View root) {
        bindStatus(root, R.id.param2_low_level_status, 947);
        bindStatus(root, R.id.param2_humidifier_pump_status, 952);
        bindStatus(root, R.id.param2_drain_valve_status, 953);
        bindStatus(root, R.id.param2_makeup_valve_status, 954);
        bindStatus(root, R.id.param2_return_air_valve_status, 951);
        bindStatus(root, R.id.param2_exhaust_fan_status, 935);
        bindValue(root, R.id.param2_compressor_freq_value, 512);
        bindValue(root, R.id.param2_expansion_valve_step_value, 524);
        bindValue(root, R.id.param2_condenser_fan_value, 525);
    }

    private static void bindPage3(View root) {
        bindValue(root, R.id.param3_energy_fan_output_value, 143);
        bindValue(root, R.id.param3_comfort_fan_output_value, 145);
        bindValue(root, R.id.param3_boost_fan_output_value, 147);
        bindValue(root, R.id.param3_energy_fresh_air_valve_value, 144);
        bindValue(root, R.id.param3_comfort_fresh_air_valve_value, 146);
        bindValue(root, R.id.param3_boost_fresh_air_valve_value, 148);
        bindValue(root, R.id.param3_energy_exhaust_valve_value, 169);
        bindValue(root, R.id.param3_comfort_exhaust_valve_value, 170);
        bindValue(root, R.id.param3_boost_exhaust_valve_value, 171);
        bindValue(root, R.id.param3_fresh_air_heater_max_output_value, 140);
    }

    private static void bindPage4(View root) {
        bindValue(root, R.id.param4_energy_pm25_limit_value, 151);
        bindValue(root, R.id.param4_comfort_pm25_limit_value, 152);
        bindValue(root, R.id.param4_pm25_rise_limit_value, 154);
        bindValue(root, R.id.param4_energy_co2_limit_value, 149);
        bindValue(root, R.id.param4_comfort_co2_limit_value, 150);
        bindValue(root, R.id.param4_co2_rise_limit_value, 153);
    }

    private static void bindPage5(View root) {
        bindValue(root, R.id.param5_preheat_temp_setpoint_value, 142);
        bindValue(root, R.id.param5_preheat_ratio_adjust_value, 158);
        bindValue(root, R.id.param5_boost_first_check_time_value, 155);
        bindValue(root, R.id.param5_boost_force_to_energy_extra_time_value, 156);
        bindValue(root, R.id.param5_boost_exit_energy_hold_time_value, 157);
    }

    private static void bindValue(View root, int viewId, int address) {
        TextView view = root.findViewById(viewId);
        if (view == null) {
            return;
        }
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        if (spec == null) {
            view.setText("--");
            return;
        }
        ModbusUi.bindValue(view, spec);
    }

    private static void bindStatus(View root, int viewId, int address) {
        View indicator = root.findViewById(viewId);
        if (indicator == null) {
            return;
        }
        indicator.setBackgroundResource(R.drawable.factory_status_light_placeholder);
        ModbusRegisterSpec spec = ModbusTable.byAddress(address);
        if (spec == null) {
            return;
        }
        ModbusManager.get(indicator.getContext()).read(spec, new ModbusManager.IntCallback() {
            @Override
            public void onSuccess(int value) {
                indicator.setBackgroundResource(value == 1
                        ? R.drawable.factory_status_light_green
                        : R.drawable.factory_status_light_red);
            }

            @Override
            public void onError(Exception error) {
                indicator.setBackgroundResource(R.drawable.factory_status_light_placeholder);
            }
        });
    }

    private final class FactoryParamsPagerAdapter extends RecyclerView.Adapter<FactoryParamsPagerAdapter.PageViewHolder> {
        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
            return new PageViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            pageRoots[position] = holder.itemView;
            bindPage(holder.itemView, holder.layoutRes);
            if (position == selectedPage) {
                requestImmediatePoll();
            }
        }

        @Override
        public int getItemCount() {
            return PAGE_LAYOUTS.length;
        }

        @Override
        public int getItemViewType(int position) {
            return PAGE_LAYOUTS[position];
        }

        private final class PageViewHolder extends RecyclerView.ViewHolder {
            private final int layoutRes;

            PageViewHolder(@NonNull View itemView, int layoutRes) {
                super(itemView);
                this.layoutRes = layoutRes;
            }
        }
    }
}

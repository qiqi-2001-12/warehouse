package com.warehouse.fragment.factory;

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

import com.warehouse.device.DeviceGateway;
import com.warehouse.R;
import com.warehouse.core.ui.AdminUi;
import com.warehouse.data.local.OperationLogStore;
import com.warehouse.data.local.OperationLogFormatter;

public class FactoryParamsFragment extends Fragment {
    private static final long POLL_INTERVAL_MS = 1500L;
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
        LinearLayout header = AdminUi.row(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 56));
        headerParams.topMargin = AdminUi.dp(requireContext(), 18);
        root.addView(header, headerParams);

        View spacer = new View(requireContext());
        LinearLayout.LayoutParams spacerParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        spacerParams.leftMargin = AdminUi.dp(requireContext(), 10);
        header.addView(spacer, spacerParams);

        for (int i = 0; i < pageTabs.length; i++) {
            final int page = i;
            TextView tab = AdminUi.button(requireContext(), String.valueOf(i + 1));
            tab.setTextSize(15);
            tab.setOnClickListener(v -> selectPage(page, true));
            LinearLayout.LayoutParams params = AdminUi.lp(AdminUi.dp(requireContext(), 44), AdminUi.dp(requireContext(), 56));
            params.leftMargin = AdminUi.dp(requireContext(), 8);
            header.addView(tab, params);
            pageTabs[i] = tab;
        }

        pager = new ViewPager2(requireContext());
        pager.setBackgroundResource(R.drawable.factory_params_panel_bg);
        pager.setOffscreenPageLimit(1);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pager.setUserInputEnabled(true);
        pager.setAdapter(new FactoryParamsPagerAdapter());
        RecyclerView pagerRecyclerView = (RecyclerView) pager.getChildAt(0);
        if (pagerRecyclerView != null) {
            pagerRecyclerView.setItemAnimator(null);
            pagerRecyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateSelectedPage(position);
            }
        });

        LinearLayout.LayoutParams pagerParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
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
        DeviceGateway gateway = DeviceGateway.get(requireContext());
        if (!gateway.canWrite(address)) {
            Toast.makeText(requireContext(), "\u672a\u627e\u5230\u5bc4\u5b58\u5668", Toast.LENGTH_SHORT).show();
            return;
        }
        gateway.write(address, 1, new DeviceGateway.WriteCallback() {
            @Override
            public void onSuccess() {
                OperationLogStore.get(requireContext()).recordLocal(
                        address == 566 ? "确认厂家参数" : "执行厂家参数操作");
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
        return refreshRange(root, 900, 24,
                valueBinding(R.id.param1_env_temp_value, 902),
                valueBinding(R.id.param1_high_pressure_value, 919),
                valueBinding(R.id.param1_exhaust_temp_value, 900),
                valueBinding(R.id.param1_low_pressure_value, 918),
                valueBinding(R.id.param1_suction_temp_value, 903),
                valueBinding(R.id.param1_suction_superheat_value, 922),
                valueBinding(R.id.param1_evaporation_temp_value, 920),
                valueBinding(R.id.param1_exhaust_superheat_value, 923),
                valueBinding(R.id.param1_condensing_temp_value, 921),
                valueBinding(R.id.param1_outdoor_coil_temp_value, 901));
    }

    private int refreshPage2(View root) {
        int reads = 0;
        reads += refreshRange(root, 15, 3,
                valueBinding(R.id.param2_supply_fan_speed_value, 15),
                valueBinding(R.id.param2_compressor_freq_max_value, 17),
                valueBinding(R.id.param2_compressor_freq_min_value, 16));
        reads += refreshRange(root, 512, 4,
                valueBinding(R.id.param2_compressor_freq_value, 512));
        reads += refreshRange(root, 524, 2,
                valueBinding(R.id.param2_expansion_valve_step_value, 524),
                valueBinding(R.id.param2_condenser_fan_value, 525));
        reads += refreshRange(root, 935, 3,
                statusBinding(R.id.param2_exhaust_fan_status, 935));
        reads += refreshRange(root, 947, 8,
                statusBinding(R.id.param2_low_level_status, 947),
                statusBinding(R.id.param2_humidifier_pump_status, 952),
                statusBinding(R.id.param2_drain_valve_status, 953),
                statusBinding(R.id.param2_makeup_valve_status, 954),
                statusBinding(R.id.param2_return_air_valve_status, 951));
        return reads;
    }

    private int refreshPage3(View root) {
        int reads = 0;
        reads += refreshRange(root, 140, 20,
                valueBinding(R.id.param3_energy_fan_output_value, 143),
                valueBinding(R.id.param3_comfort_fan_output_value, 145),
                valueBinding(R.id.param3_boost_fan_output_value, 147),
                valueBinding(R.id.param3_energy_fresh_air_valve_value, 144),
                valueBinding(R.id.param3_comfort_fresh_air_valve_value, 146),
                valueBinding(R.id.param3_boost_fresh_air_valve_value, 148),
                valueBinding(R.id.param3_fresh_air_heater_max_output_value, 140),
                valueBinding(R.id.param3_supply_mode_button, 159));
        reads += refreshRange(root, 169, 3,
                valueBinding(R.id.param3_energy_exhaust_valve_value, 169),
                valueBinding(R.id.param3_comfort_exhaust_valve_value, 170),
                valueBinding(R.id.param3_boost_exhaust_valve_value, 171));
        return reads;
    }

    private void updateRangeValue(View root, int viewId, int address, int startAddress, int[] values) {
        TextView view = root.findViewById(viewId);
        int index = address - startAddress;
        if (view == null || index < 0 || values == null || index >= values.length) {
            return;
        }
        // Preserve the value selected by the operator until the pending write completes.
        if (viewId == R.id.param3_supply_mode_button && !view.isEnabled()) {
            return;
        }
        int value = values[index];
        DeviceGateway gateway = DeviceGateway.get(view.getContext());
        view.setTag(value);
        view.setText(gateway.formatDefault(address, value));
    }

    private int refreshPage4(View root) {
        return refreshRange(root, 149, 6,
                valueBinding(R.id.param4_energy_pm25_limit_value, 151),
                valueBinding(R.id.param4_comfort_pm25_limit_value, 152),
                valueBinding(R.id.param4_pm25_rise_limit_value, 154),
                valueBinding(R.id.param4_energy_co2_limit_value, 149),
                valueBinding(R.id.param4_comfort_co2_limit_value, 150),
                valueBinding(R.id.param4_co2_rise_limit_value, 153));
    }

    private int refreshPage5(View root) {
        return refreshRange(root, 142, 17,
                valueBinding(R.id.param5_preheat_temp_setpoint_value, 142),
                valueBinding(R.id.param5_preheat_ratio_adjust_value, 158),
                valueBinding(R.id.param5_boost_first_check_time_value, 155),
                valueBinding(R.id.param5_boost_force_to_energy_extra_time_value, 156),
                valueBinding(R.id.param5_boost_exit_energy_hold_time_value, 157));
    }

    private int refreshRange(View root, int startAddress, int count, RangeBinding... bindings) {
        DeviceGateway gateway = DeviceGateway.get(root.getContext());
        if (!gateway.canRead(startAddress)) {
            return 0;
        }
        gateway.readRange(startAddress, count, new DeviceGateway.RangeCallback() {
            @Override
            public void onSuccess(int returnedStartAddress, int[] values) {
                for (RangeBinding binding : bindings) {
                    if (binding.status) {
                        updateRangeStatus(root, binding.viewId, binding.address,
                                returnedStartAddress, values);
                    } else {
                        updateRangeValue(root, binding.viewId, binding.address,
                                returnedStartAddress, values);
                    }
                }
                onPollReadFinished();
            }

            @Override
            public void onError(Exception error) {
                onPollReadFinished();
            }
        });
        return 1;
    }

    private void updateRangeStatus(View root, int viewId, int address, int startAddress, int[] values) {
        View indicator = root.findViewById(viewId);
        int index = address - startAddress;
        if (indicator == null || index < 0 || values == null || index >= values.length) {
            return;
        }
        indicator.setBackgroundResource(values[index] == 1
                ? R.drawable.factory_status_light_green : R.drawable.factory_status_light_red);
    }

    private static RangeBinding valueBinding(int viewId, int address) {
        return new RangeBinding(viewId, address, false);
    }

    private static RangeBinding statusBinding(int viewId, int address) {
        return new RangeBinding(viewId, address, true);
    }

    private static final class RangeBinding {
        private final int viewId;
        private final int address;
        private final boolean status;

        RangeBinding(int viewId, int address, boolean status) {
            this.viewId = viewId;
            this.address = address;
            this.status = status;
        }
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
        bindValue(root, R.id.param2_supply_fan_speed_value, 15);
        bindValue(root, R.id.param2_compressor_freq_value, 512);
        bindValue(root, R.id.param2_compressor_freq_max_value, 17);
        bindValue(root, R.id.param2_compressor_freq_min_value, 16);
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
        bindSupplyModeButton(root);
    }

    private static void bindSupplyModeButton(View root) {
        TextView button = root.findViewById(R.id.param3_supply_mode_button);
        if (button == null) {
            return;
        }
        DeviceGateway gateway = DeviceGateway.get(button.getContext());
        if (!gateway.canWrite(159)) {
            return;
        }
        Integer cachedValue = gateway.getCachedValue(159);
        int initialValue = cachedValue == null ? gateway.minValue(159) : cachedValue;
        button.setTag(initialValue);
        button.setText(gateway.formatDefault(159, initialValue));
        AdminUi.addPressFeedback(button);
        button.setOnClickListener(v -> {
            Object tag = button.getTag();
            int currentValue = tag instanceof Integer ? (Integer) tag : gateway.maxValue(159);
            int nextValue = currentValue >= gateway.maxValue(159) ? gateway.minValue(159) : currentValue + 1;
            button.setEnabled(false);
            button.setTag(nextValue);
            button.setText(gateway.formatDefault(159, nextValue));
            gateway.write(159, nextValue, new DeviceGateway.WriteCallback() {
                @Override
                public void onSuccess() {
                    button.setTag(nextValue);
                    button.setText(gateway.formatDefault(159, nextValue));
                    button.setEnabled(true);
                    OperationLogStore.get(button.getContext()).recordLocal(
                            OperationLogFormatter.describeParameter(159,
                                    gateway.formatDefault(159, nextValue)));
                }

                @Override
                public void onError(Exception error) {
                    button.setEnabled(true);
                }
            });
        });
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
        DeviceGateway gateway = DeviceGateway.get(view.getContext());
        if (!gateway.canRead(address) && !gateway.canWrite(address)) {
            view.setText("--");
            return;
        }
        Integer cachedValue = gateway.getCachedValue(address);
        if (cachedValue != null) {
            view.setTag(cachedValue);
            view.setText(gateway.formatDefault(address, cachedValue));
        } else {
            view.setText("--");
            view.setTag(gateway.minValue(address));
        }
        if (gateway.canWrite(address)) {
            view.setClickable(true);
            view.setFocusable(true);
            AdminUi.addPressFeedback(view);
            view.setOnClickListener(v -> gateway.showEditor(view, address));
        }
    }

    private static void bindStatus(View root, int viewId, int address) {
        View indicator = root.findViewById(viewId);
        if (indicator == null) {
            return;
        }
        DeviceGateway gateway = DeviceGateway.get(indicator.getContext());
        Integer cachedValue = gateway.getCachedValue(address);
        if (cachedValue != null) {
            indicator.setBackgroundResource(cachedValue == 1
                    ? R.drawable.factory_status_light_green
                    : R.drawable.factory_status_light_red);
        } else {
            indicator.setBackgroundResource(R.drawable.factory_status_light_placeholder);
        }
        if (!gateway.canRead(address)) {
            return;
        }
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

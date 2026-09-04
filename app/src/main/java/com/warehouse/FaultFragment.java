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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class FaultFragment extends Fragment {
    private static final FaultItem[] ITEMS = new FaultItem[] {
            new FaultItem(800, "\u65b0\u98ce\u6e29\u6e7f\u5ea6\u4f20\u611f\u5668\u901a\u8baf\u6545\u969c"),
            new FaultItem(801, "\u9001\u98ce\u6e29\u6e7f\u5ea6\u4f20\u611f\u5668\u901a\u8baf\u6545\u969c"),
            new FaultItem(807, "\u8865\u6c34\u8d85\u65f6\u6545\u969c"),
            new FaultItem(808, "\u6ee4\u7f51\u62a5\u8b66"),
            new FaultItem(809, "\u538b\u7f29\u673a\u9a71\u52a8\u677f\u901a\u8baf\u62a5\u8b66"),
            new FaultItem(814, "\u673a\u7ec4\u6392\u6c34\u6545\u969c"),
            new FaultItem(822, "\u8865\u6c34\u9632\u51bb\u62a5\u8b66"),
            new FaultItem(823, "\u9ad8\u538b\u4f20\u611f\u5668\u6545\u969c"),
            new FaultItem(824, "\u4f4e\u538b\u4f20\u611f\u5668\u6545\u969c"),
            new FaultItem(825, "\u538b\u673a\u672c\u8eab\u6545\u969c"),
            new FaultItem(826, "\u51b7\u51dd\u98ce\u673a\u672c\u8eab\u6545\u969c"),
            new FaultItem(829, "\u673a\u7ec4\u6f0f\u6c14\u62a5\u8b66"),
            new FaultItem(832, "\u4e94\u5408\u4e00\u4f20\u611f\u5668\u901a\u8baf\u6545\u969c"),
            new FaultItem(841, "\u5438\u6c14\u6e29\u5ea6\u8fc7\u4f4e\u4fdd\u62a4"),
            new FaultItem(842, "\u6392\u6c14\u8fc7\u70ed\u5ea6\u8fc7\u9ad8\u4fdd\u62a4"),
            new FaultItem(843, "\u6392\u6c14\u6e29\u5ea6\u4f20\u611f\u5668\u6545\u969c"),
            new FaultItem(844, "\u5438\u6c14\u6e29\u5ea6\u4f20\u611f\u5668\u6545\u969c")
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = AdminUi.column(requireContext());
        root.setPadding(AdminUi.dp(requireContext(), 24), AdminUi.dp(requireContext(), 18), AdminUi.dp(requireContext(), 24), AdminUi.dp(requireContext(), 18));

        LinearLayout header = AdminUi.row(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, AdminUi.dp(requireContext(), 6), 0, 0);
        root.addView(header, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 40)));

        TextView resetFault = AdminUi.text(requireContext(), "\u6545\u969c\u590d\u4f4d", 16, AdminUi.TEXT_PRIMARY, Typeface.BOLD);
        resetFault.setGravity(Gravity.CENTER);
        resetFault.setBackground(AdminUi.bg(Color.rgb(19, 72, 94), 6, Color.TRANSPARENT, 0, requireContext()));
        resetFault.setClickable(true);
        resetFault.setFocusable(true);
        resetFault.setOnClickListener(v -> writeConfirm(799));
        header.addView(resetFault, AdminUi.lp(AdminUi.dp(requireContext(), 120), AdminUi.dp(requireContext(), 36)));

        TextView wetReset = AdminUi.button(requireContext(), "\u6ee4\u7f51\u590d\u4f4d");
        LinearLayout.LayoutParams wetParams = AdminUi.lp(AdminUi.dp(requireContext(), 120), AdminUi.dp(requireContext(), 36));
        wetParams.leftMargin = AdminUi.dp(requireContext(), 14);
        wetReset.setOnClickListener(v -> writeConfirm(566));
        header.addView(wetReset, wetParams);

        View spacer = new View(requireContext());
        header.addView(spacer, AdminUi.weightedLp(0, 0, 1));

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        LinearLayout.LayoutParams scrollParams = AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        scrollParams.topMargin = AdminUi.dp(requireContext(), 12);
        scrollParams.weight = 1;
        root.addView(scroll, scrollParams);

        LinearLayout table = AdminUi.column(requireContext());
        table.setBackground(AdminUi.bg(Color.rgb(226, 234, 232), 2, Color.rgb(120, 135, 135), 1, requireContext()));
        scroll.addView(table, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addHeaderRow(table);
        loadActiveFaults(table);
        return root;
    }

    private void addHeaderRow(LinearLayout parent) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(Color.rgb(235, 242, 16));
        parent.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 42)));

        row.addView(AdminUi.text(requireContext(), "\u65f6\u95f4", 16, Color.rgb(30, 44, 48), Typeface.BOLD), AdminUi.lp(AdminUi.dp(requireContext(), 86), ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.text(requireContext(), "\u65e5\u671f", 16, Color.rgb(30, 44, 48), Typeface.BOLD), AdminUi.lp(AdminUi.dp(requireContext(), 110), ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));
        row.addView(AdminUi.text(requireContext(), "\u6d88\u606f", 16, Color.rgb(30, 44, 48), Typeface.BOLD), AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
    }

    private void loadActiveFaults(LinearLayout parent) {
        List<FaultLogEntry> entries = new ArrayList<>();
        AtomicInteger remaining = new AtomicInteger(ITEMS.length);
        String timeText = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
        String dateText = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());

        for (FaultItem item : ITEMS) {
            ModbusRegisterSpec spec = ModbusTable.byAddress(item.address);
            if (spec == null) {
                if (remaining.decrementAndGet() == 0) {
                    renderEntries(parent, entries);
                }
                continue;
            }
            ModbusManager.get(requireContext()).read(spec, new ModbusManager.IntCallback() {
                @Override
                public void onSuccess(int value) {
                    if (value == 1) {
                        synchronized (entries) {
                            entries.add(new FaultLogEntry(timeText, dateText, item.label));
                        }
                    }
                    if (remaining.decrementAndGet() == 0) {
                        renderEntries(parent, entries);
                    }
                }

                @Override
                public void onError(Exception error) {
                    if (remaining.decrementAndGet() == 0) {
                        renderEntries(parent, entries);
                    }
                }
            });
        }
    }

    private void renderEntries(LinearLayout parent, List<FaultLogEntry> entries) {
        parent.post(() -> {
            if (entries.isEmpty()) {
                addEmptyRow(parent);
                return;
            }
            for (FaultLogEntry entry : entries) {
                addLogRow(parent, entry);
            }
        });
    }

    private void addEmptyRow(LinearLayout parent) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AdminUi.dp(requireContext(), 12), AdminUi.dp(requireContext(), 10), AdminUi.dp(requireContext(), 12), AdminUi.dp(requireContext(), 10));
        parent.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 42)));

        TextView empty = AdminUi.text(requireContext(), "\u6682\u65e0\u6545\u969c", 14, AdminUi.TEXT_SECONDARY, Typeface.NORMAL);
        empty.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(empty, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void addLogRow(LinearLayout parent, FaultLogEntry entry) {
        LinearLayout row = AdminUi.row(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AdminUi.dp(requireContext(), 12), AdminUi.dp(requireContext(), 4), AdminUi.dp(requireContext(), 12), AdminUi.dp(requireContext(), 4));
        parent.addView(row, AdminUi.lp(ViewGroup.LayoutParams.MATCH_PARENT, AdminUi.dp(requireContext(), 42)));

        TextView timeView = AdminUi.text(requireContext(), entry.time, 14, Color.rgb(30, 44, 48), Typeface.BOLD);
        timeView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(timeView, AdminUi.lp(AdminUi.dp(requireContext(), 86), ViewGroup.LayoutParams.MATCH_PARENT));

        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView dateView = AdminUi.text(requireContext(), entry.date, 14, Color.rgb(30, 44, 48), Typeface.BOLD);
        dateView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        row.addView(dateView, AdminUi.lp(AdminUi.dp(requireContext(), 110), ViewGroup.LayoutParams.MATCH_PARENT));

        row.addView(AdminUi.divider(requireContext()), AdminUi.lp(1, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView messageView = AdminUi.text(requireContext(), entry.message, 14, Color.rgb(30, 44, 48), Typeface.NORMAL);
        messageView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams messageParams = AdminUi.weightedLp(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        messageParams.leftMargin = AdminUi.dp(requireContext(), 8);
        row.addView(messageView, messageParams);
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

    private static final class FaultItem {
        final int address;
        final String label;

        FaultItem(int address, String label) {
            this.address = address;
            this.label = label;
        }
    }

    private static final class FaultLogEntry {
        final String time;
        final String date;
        final String message;

        FaultLogEntry(String time, String date, String message) {
            this.time = time;
            this.date = date;
            this.message = message;
        }
    }
}

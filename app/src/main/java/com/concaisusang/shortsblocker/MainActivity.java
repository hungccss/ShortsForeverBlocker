package com.concaisusang.shortsblocker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private TextView counter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private View buildUi() {
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Chặn YouTube Shorts Vĩnh Viễn");
        title.setTextSize(25f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Dùng trực tiếp với app YouTube chính chủ • điện thoại + tablet • không root • không Internet");
        subtitle.setTextSize(15f);
        subtitle.setPadding(0, dp(8), 0, dp(18));
        box.addView(subtitle);

        status = new TextView(this);
        status.setTextSize(18f);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(status);

        counter = new TextView(this);
        counter.setTextSize(15f);
        counter.setPadding(0, dp(6), 0, dp(18));
        box.addView(counter);

        Button enable = button("1. BẬT CHẶN SHORTS (TRỢ NĂNG)");
        enable.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(i);
                Toast.makeText(this, "Chọn “Chặn YouTube Shorts” rồi bật Cho phép.", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Không mở được cài đặt Trợ năng.", Toast.LENGTH_LONG).show();
            }
        });
        box.addView(enable);

        Button battery = button("2. CHO PHÉP CHẠY NỀN / TẮT TỐI ƯU PIN");
        battery.setOnClickListener(v -> requestBatteryExemption());
        box.addView(battery);

        Button batteryList = button("MỞ DANH SÁCH TỐI ƯU PIN");
        batteryList.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        box.addView(batteryList);

        Button test = button("MỞ YOUTUBE ĐỂ THỬ");
        test.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
            if (launch != null) startActivity(launch);
            else Toast.makeText(this, "Không tìm thấy app YouTube.", Toast.LENGTH_SHORT).show();
        });
        box.addView(test);

        TextView note = new TextView(this);
        note.setPadding(0, dp(20), 0, 0);
        note.setTextSize(14f);
        note.setText(
                "Cách hoạt động:\n" +
                "• Chỉ đọc giao diện của com.google.android.youtube.\n" +
                "• Bấm tab Shorts → tự Back ngay.\n" +
                "• Mở video Shorts từ Home/Search/link → phát hiện player Shorts và tự Back.\n" +
                "• Sau khi bạn bật Trợ năng một lần, Android sẽ giữ trạng thái đó qua khởi động lại và tự bind lại service.\n\n" +
                "Lưu ý: Android không cho app tự ý bật quyền Trợ năng. Một số ROM (Xiaomi/OPPO/realme/OnePlus/vivo/Huawei…) còn có mục “Tự khởi động/Auto launch”; nếu máy có mục đó hãy cho phép app này."
        );
        box.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        return scroll;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(7), 0, dp(7));
        b.setLayoutParams(lp);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private void requestBatteryExemption() {
        if (android.os.Build.VERSION.SDK_INT < 23) return;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "App đã được bỏ tối ưu pin.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        }
    }

    private void refresh() {
        boolean on = isAccessibilityEnabled();
        status.setText(on ? "✅ ĐANG CHẶN SHORTS" : "⚠️ CHƯA BẬT QUYỀN TRỢ NĂNG");
        long count = getSharedPreferences("state", MODE_PRIVATE).getLong("blocked_count", 0L);
        counter.setText("Đã chặn: " + count + " lần");
    }

    private boolean isAccessibilityEnabled() {
        ComponentName cn = new ComponentName(this, ShortsBlockerService.class);
        String expected = cn.flattenToString();
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            String s = splitter.next();
            if (expected.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}

package cn.vitamin.mocklocation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(24, 39, 62);
    private static final int MUTED = Color.rgb(89, 106, 130);
    private static final int BLUE = Color.rgb(35, 104, 222);
    private EditText latInput, lonInput;
    private Spinner format;
    private TextView setup, status, detail;
    private Button start;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        outer.setPadding(dp(20), dp(20), dp(20), dp(28));
        scroll.addView(outer);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(660));
        outer.addView(body, new LinearLayout.LayoutParams(width, -2));
        TextView title = text("定点定位", 28, INK);
        title.setTypeface(null, Typeface.BOLD);
        body.addView(title);
        body.addView(text("安卓 10 及以上 · 本机持续模拟 · 无需 Root", 14, MUTED));

        LinearLayout prepare = card(body);
        prepare.addView(label("01  首次设置"));
        setup = text("", 14, MUTED);
        prepare.addView(setup);
        LinearLayout actions = row(prepare);
        Button dev = button("选择模拟定位应用", false);
        dev.setOnClickListener(v -> openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        actions.addView(dev, weighted());
        Button location = button("打开系统定位", false);
        location.setOnClickListener(v -> openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        actions.addView(location, weighted());
        TextView locationHelp = text("请先打开系统定位，并在开发者选项中选择本应用。", 12, MUTED);
        prepare.addView(locationHelp);

        LinearLayout input = card(body);
        input.addView(label("02  输入目标位置"));
        input.addView(text("使用十进制度数；注意纬度和经度的顺序。", 13, MUTED));
        LinearLayout fields = row(input);
        LinearLayout latBox = column(fields);
        latBox.addView(text("纬度  Latitude", 14, INK));
        latInput = coordinateInput("-90 ～ 90", prefs.getString("input_lat", ""));
        latBox.addView(latInput);
        LinearLayout lonBox = column(fields);
        lonBox.addView(text("经度  Longitude", 14, INK));
        lonInput = coordinateInput("-180 ～ 180", prefs.getString("input_lon", ""));
        lonBox.addView(lonInput);
        Button map = button("在地图上选择位置", false);
        map.setOnClickListener(v -> {
            double lat = 22.281214, lon = 114.128642;
            try {
                double[] point = Coordinates.toWgs84(Coordinates.parse(latInput.getText().toString(), true),
                        Coordinates.parse(lonInput.getText().toString(), false), format.getSelectedItemPosition());
                lat = point[0]; lon = point[1];
            } catch (RuntimeException ignored) { }
            startActivityForResult(new Intent(this, MapActivity.class).putExtra("lat", lat).putExtra("lon", lon), 101);
        });
        input.addView(map, new LinearLayout.LayoutParams(-1, dp(50)));
        input.addView(text("地图需要联网；地图选点使用 WGS-84 坐标。", 12, MUTED));
        input.addView(text("坐标来源", 14, INK));
        format = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"GPS / WGS-84（原始经纬度）", "高德、腾讯地图 / GCJ-02", "百度地图 / BD-09"});
        format.setAdapter(adapter);
        format.setSelection(Math.max(0, Math.min(2, prefs.getInt("format", 0))));
        input.addView(format, new LinearLayout.LayoutParams(-1, dp(52)));
        input.addView(text("地图坐标会在本机近似换算为 GPS 坐标，不需要联网。", 12, MUTED));
        start = button("开始模拟定位", true);
        start.setOnClickListener(v -> startMock());
        input.addView(start, new LinearLayout.LayoutParams(-1, dp(54)));

        LinearLayout active = card(body);
        active.addView(label("03  运行状态"));
        status = text("未开始", 18, INK);
        status.setTypeface(null, Typeface.BOLD);
        active.addView(status);
        detail = text("", 13, MUTED);
        active.addView(detail);
        Button stop = button("停止模拟定位，恢复系统定位", false);
        stop.setOnClickListener(v -> {
            try {
                startService(new Intent(this, MockLocationService.class).setAction(MockLocationService.STOP));
                refresh();
            } catch (RuntimeException e) { showError("停止失败：" + e.getMessage()); }
        });
        active.addView(stop, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView help = text("使用说明与注意事项", 14, BLUE);
        help.setPadding(0, dp(12), 0, dp(8));
        help.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("使用说明")
                .setMessage("1. 在开发者选项 → 选择模拟位置信息应用中选「定点定位」。\n\n"
                        + "2. 打开系统定位，输入经纬度并选对坐标来源，再点击开始。\n\n"
                        + "3. 可以切换到其他应用，持续定位会显示通知。请勿强行停止本应用。若系统限制后台运行，可在应用设置中允许后台运行。\n\n"
                        + "4. 用完点击停止，等待系统或目标应用重新获取位置。系统或目标应用可能保留短期缓存。若位置没有恢复，取消模拟应用选择并重启设备。\n\n"
                        + "这是系统标准模拟定位，目标应用可识别模拟标记，是否采用位置需要实测。\n\n"
                        + "用于遥控器时，请在未连接飞行器的情况下测试；飞行前停止模拟并取消模拟应用选择。\n\n"
                        + "地图需要联网，会向地图服务请求当前浏览区域。强行停止、重启后不会自动开始模拟。")
                .setPositiveButton("知道了", null).show());
        body.addView(help);
        setContentView(scroll);
    }

    private void startMock() {
        try {
            double lat = Coordinates.parse(latInput.getText().toString(), true);
            double lon = Coordinates.parse(lonInput.getText().toString(), false);
            if (!MockLocationService.mockAllowed(this)) {
                new AlertDialog.Builder(this).setTitle("先选择模拟定位应用")
                        .setMessage("请在开发者选项 → 选择模拟位置信息应用中，选择「定点定位」。")
                        .setPositiveButton("前往设置", (d, w) -> openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        .setNegativeButton("稍后", null).show();
                return;
            }
            LocationManager locations = getSystemService(LocationManager.class);
            if (locations == null || !locations.isLocationEnabled()) {
                new AlertDialog.Builder(this).setTitle("需要开启系统定位")
                        .setMessage("请在系统设置中打开定位。若遥控器隐藏了定位设置，需要设备允许的设置方式。")
                        .setPositiveButton("打开系统设置", (d, w) -> openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        .setNegativeButton("知道了", null).show(); return;
            }
            double[] point = Coordinates.toWgs84(lat, lon, format.getSelectedItemPosition());
            saveInputs();
            View focus = getCurrentFocus();
            if (focus != null) getSystemService(InputMethodManager.class).hideSoftInputFromWindow(focus.getWindowToken(), 0);
            startForegroundService(new Intent(this, MockLocationService.class).setAction(MockLocationService.START)
                    .putExtra("latitude", point[0]).putExtra("longitude", point[1]));
            handler.postDelayed(this::refresh, 200);
        } catch (RuntimeException e) { showError(e.getMessage()); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 101 && result == RESULT_OK && data != null) {
            latInput.setText(String.format(Locale.US, "%.7f", data.getDoubleExtra("lat", 0)));
            lonInput.setText(String.format(Locale.US, "%.7f", data.getDoubleExtra("lon", 0)));
            format.setSelection(0);
            saveInputs();
        }
    }

    private void refresh() {
        boolean allowed = MockLocationService.mockAllowed(this);
        LocationManager locations = getSystemService(LocationManager.class);
        boolean enabled = locations != null && locations.isLocationEnabled();
        setup.setText((allowed ? "✓ 已选择本应用" : "○ 请选择「定点定位」")
                + "     " + (enabled ? "✓ 系统定位已开启" : "○ 系统定位未开启"));
        if (MockLocationService.running) {
            long age = Math.max(0, (SystemClock.elapsedRealtime() - MockLocationService.lastUpdate) / 1000);
            status.setText(age <= 3 ? "● 正在模拟定位" : "● 更新延迟，请检查后台限制");
            status.setTextColor(age <= 3 ? Color.rgb(19, 128, 88) : Color.rgb(174, 98, 14));
            detail.setText(String.format(Locale.US, "GPS 纬度 %.7f\nGPS 经度 %.7f\n%s · %d 秒前更新\n这是模拟坐标，目标应用是否采用需实测。",
                    MockLocationService.latitude, MockLocationService.longitude,
                    MockLocationService.providerSummary, age));
            start.setText("更新目标位置");
        } else {
            boolean error = prefs.getBoolean("error", false);
            status.setText(error ? "需要处理" : "未运行模拟定位");
            status.setTextColor(error ? Color.rgb(176, 55, 55) : INK);
            detail.setText(prefs.getString("status", "完成设置后，输入经纬度并点击开始。"));
            start.setText("开始模拟定位");
        }
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };
    @Override protected void onResume() { super.onResume(); handler.post(poll); }
    @Override protected void onPause() { saveInputs(); handler.removeCallbacksAndMessages(null); super.onPause(); }

    private void saveInputs() {
        prefs.edit().putString("input_lat", latInput.getText().toString())
                .putString("input_lon", lonInput.getText().toString())
                .putInt("format", format.getSelectedItemPosition()).apply();
    }
    private void openSettings(String action) {
        try { startActivity(new Intent(action)); }
        catch (RuntimeException e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (RuntimeException ignored) { showError("请手动打开系统设置。"); }
        }
    }
    private void showError(String message) {
        Toast.makeText(this, message == null ? "操作未完成，请检查设置。" : message, Toast.LENGTH_LONG).show();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setPadding(0, dp(3), 0, dp(5));
        return view;
    }
    private TextView label(String value) {
        TextView view = text(value, 16, INK); view.setTypeface(null, Typeface.BOLD); return view;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE);
        background.setCornerRadius(dp(16)); view.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16); parent.addView(view, params); return view;
    }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    private LinearLayout column(LinearLayout parent) {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(0, dp(10), dp(8), dp(8)); parent.addView(view, weighted()); return view;
    }
    private LinearLayout.LayoutParams weighted() { return new LinearLayout.LayoutParams(0, -2, 1); }
    private EditText coordinateInput(String hint, String value) {
        EditText view = new EditText(this); view.setTextSize(20); view.setSingleLine(true);
        view.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        view.setHint(hint); view.setText(value); view.setTextColor(INK);
        view.setPadding(dp(8), dp(8), dp(8), dp(8));
        view.setMinimumHeight(dp(52)); return view;
    }
    private Button button(String value, boolean primary) {
        Button view = new Button(this); view.setText(value); view.setTextSize(14); view.setAllCaps(false);
        view.setTextColor(primary ? Color.WHITE : BLUE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? BLUE : Color.rgb(235, 242, 254)); background.setCornerRadius(dp(10));
        view.setBackground(background); view.setMinimumHeight(dp(48));
        view.setPadding(dp(10), dp(6), dp(10), dp(6)); return view;
    }
}

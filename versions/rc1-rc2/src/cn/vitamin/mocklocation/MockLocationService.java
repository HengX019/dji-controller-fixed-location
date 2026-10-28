package cn.vitamin.mocklocation;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MockLocationService extends Service {
    public static final String START = "cn.vitamin.mocklocation.START";
    public static final String STOP = "cn.vitamin.mocklocation.STOP";
    private static final String CHANNEL = "active_mock_location";
    private static final int NOTIFICATION = 41;
    public static volatile boolean running;
    public static volatile long lastUpdate;
    public static volatile double latitude, longitude;
    public static volatile String providerSummary = "";
    private final ArrayList<String> owned = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locations;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences prefs;
    private long lastWakeRenewal;

    public static boolean mockAllowed(Context context) {
        AppOpsManager ops = context.getSystemService(AppOpsManager.class);
        return ops != null && ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    @Override public void onCreate() {
        super.onCreate();
        locations = getSystemService(LocationManager.class);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "持续模拟定位",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("显示模拟定位状态，并提供停止按钮");
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !START.equals(intent.getAction())) {
            handler.removeCallbacks(tick);
            running = false;
            boolean clean = restoreProviders();
            saveStatus(clean ? "已停止模拟定位，等待系统重新获取真实位置。"
                    : "模拟定位已停止，但撤销失败。请取消模拟应用选择并重启设备。", !clean);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            // Enter foreground immediately, including the error path, after startForegroundService.
            startForeground(NOTIFICATION, notification("正在准备模拟定位…"));
            if (!mockAllowed(this)) throw new SecurityException("请在开发者选项中选择「定点定位」作为模拟位置信息应用。");
            if (locations == null || !locations.isLocationEnabled()) {
                throw new IllegalStateException("请先打开设备的系统定位开关。");
            }
            double lat = intent.getDoubleExtra("latitude", Double.NaN);
            double lon = intent.getDoubleExtra("longitude", Double.NaN);
            Coordinates.parse(Double.toString(lat), true);
            Coordinates.parse(Double.toString(lon), false);
            handler.removeCallbacks(tick);
            running = false;
            if (!restoreProviders()) throw new IllegalStateException("旧模拟位置撤销失败，请取消模拟应用选择后重新选择。");
            latitude = lat;
            longitude = lon;
            addProvider(LocationManager.GPS_PROVIDER);
            addProvider(LocationManager.NETWORK_PROVIDER);
            addProvider("fused");
            providerSummary = android.text.TextUtils.join(" / ", owned);
            PowerManager power = getSystemService(PowerManager.class);
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FixedLocation:Updates");
            wakeLock.setReferenceCounted(false);
            renewWakeLock();
            inject();
            running = true;
            saveStatus("正在持续提供模拟位置", false);
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(
                    String.format(Locale.US, "纬度 %.6f · 经度 %.6f", latitude, longitude)));
            handler.postDelayed(tick, 1000);
        } catch (RuntimeException error) {
            fail(error);
        }
        // A killed process must not silently restart location simulation.
        return START_NOT_STICKY;
    }

    private void addProvider(String name) {
        locations.addTestProvider(name, false, false, false, false,
                true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
        owned.add(name);
        prefs.edit().putStringSet("owned_providers", new HashSet<>(owned)).commit();
        locations.setTestProviderEnabled(name, true);
    }

    private void renewWakeLock() {
        if (wakeLock != null) {
            wakeLock.acquire(10 * 60 * 1000L);
            lastWakeRenewal = SystemClock.elapsedRealtime();
        }
    }

    private void inject() {
        if (!mockAllowed(this)) throw new SecurityException("模拟定位授权已取消，请重新选择「定点定位」。");
        if (!locations.isLocationEnabled()) throw new IllegalStateException("系统定位已关闭，模拟已停止。");
        for (String provider : owned) {
            Location point = new Location(provider);
            point.setLatitude(latitude);
            point.setLongitude(longitude);
            point.setAccuracy(5.0f);
            point.setAltitude(0.0);
            point.setVerticalAccuracyMeters(10.0f);
            point.setSpeed(0.0f);
            point.setSpeedAccuracyMetersPerSecond(0.1f);
            point.setTime(System.currentTimeMillis());
            point.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            locations.setTestProviderLocation(provider, point);
        }
        lastUpdate = SystemClock.elapsedRealtime();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            try {
                inject();
                if (SystemClock.elapsedRealtime() - lastWakeRenewal > 60 * 1000L) renewWakeLock();
                handler.postDelayed(this, 1000);
            } catch (RuntimeException e) { fail(e); }
        }
    };

    private void fail(RuntimeException error) {
        running = false;
        handler.removeCallbacks(tick);
        boolean clean = restoreProviders();
        String message = error.getMessage();
        if (message == null || message.isEmpty()) message = error.getClass().getSimpleName();
        saveStatus("无法继续：" + message + (clean ? "" : "；请取消模拟应用选择并重启设备。"), true);
        stopForeground(true);
        stopSelf();
    }

    private void saveStatus(String message, boolean error) {
        prefs.edit().putString("status", message).putBoolean("error", error).apply();
    }

    private boolean restoreProviders() {
        boolean success = true;
        Set<String> pending = new HashSet<>(prefs.getStringSet("owned_providers", new HashSet<String>()));
        pending.addAll(owned);
        Set<String> failed = new HashSet<>();
        for (String provider : pending) {
            try { locations.removeTestProvider(provider); }
            catch (RuntimeException e) { success = false; failed.add(provider); }
        }
        prefs.edit().putStringSet("owned_providers", failed).commit();
        owned.clear();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
        return success;
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, MockLocationService.class).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_location).setContentTitle("定点定位 · 模拟中")
                .setContentText(text).setContentIntent(open).setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "停止模拟定位", stop).build())
                .build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(tick);
        running = false;
        restoreProviders();
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

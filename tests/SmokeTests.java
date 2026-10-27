package cn.vitamin.mocklocation.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import cn.vitamin.mocklocation.MainActivity;
import cn.vitamin.mocklocation.MockLocationService;
import java.io.FileInputStream;
import java.lang.reflect.Field;

/** Runs only in a disposable Android 11 emulator, against the exact signed release APK. */
public final class SmokeTests extends Instrumentation {
    private int assertions;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
    private void shell(String command) throws Exception {
        try (ParcelFileDescriptor fd = getUiAutomation().executeShellCommand(command);
             FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
            byte[] buffer = new byte[1024]; while (stream.read(buffer) >= 0) { }
        }
    }
    private <T> T field(Activity activity, String name, Class<T> type) {
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true); return type.cast(field.get(activity));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private Activity open() {
        return startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
    private void inputAndStart(Activity activity, String lat, String lon) {
        runOnMainSync(() -> {
            field(activity, "latInput", EditText.class).setText(lat);
            field(activity, "lonInput", EditText.class).setText(lon);
            field(activity, "format", Spinner.class).setSelection(0);
            field(activity, "start", Button.class).performClick();
        });
        waitForIdleSync();
    }
    private void waitRunning(boolean value) {
        long until = SystemClock.elapsedRealtime() + 5000;
        while (MockLocationService.running != value && SystemClock.elapsedRealtime() < until) SystemClock.sleep(50);
        check(MockLocationService.running == value, "running state expected " + value);
    }
    private void checkPoint(double lat, double lon) {
        getUiAutomation().adoptShellPermissionIdentity("android.permission.ACCESS_FINE_LOCATION");
        try {
            LocationManager manager = getTargetContext().getSystemService(LocationManager.class);
            for (String provider : new String[]{"gps", "network", "fused"}) {
                Location point = manager.getLastKnownLocation(provider);
                check(point != null, provider + " has a system location");
                check(Math.abs(point.getLatitude() - lat) < 1e-7, provider + " latitude");
                check(Math.abs(point.getLongitude() - lon) < 1e-7, provider + " longitude");
                check(point.isFromMockProvider(), provider + " mock flag");
                check(point.getElapsedRealtimeNanos() > 0, provider + " timestamp");
            }
        } finally { getUiAutomation().dropShellPermissionIdentity(); }
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Context app = getTargetContext();
        try {
            shell("cmd location set-location-enabled true");
            shell("appops set cn.vitamin.mocklocation android:mock_location deny");
            java.util.Set<String> baselineProviders = new java.util.HashSet<>(
                    app.getSystemService(LocationManager.class).getAllProviders());
            Activity activity = open();
            inputAndStart(activity, "22.543096", "114.057865");
            check(!MockLocationService.running, "permission denial cannot start simulation");
            shell("input keyevent 4");
            shell("appops set cn.vitamin.mocklocation android:mock_location allow");
            inputAndStart(activity, "91", "114");
            check(!MockLocationService.running, "invalid latitude rejected");
            inputAndStart(activity, "22.543096", "114.057865");
            waitRunning(true);
            checkPoint(22.543096, 114.057865);
            NotificationManager notices = app.getSystemService(NotificationManager.class);
            check(notices.getActiveNotifications().length == 1, "foreground notification exists");
            long before = MockLocationService.lastUpdate;
            shell("input keyevent 3");
            SystemClock.sleep(2400);
            check(MockLocationService.running && MockLocationService.lastUpdate > before, "continues after Home");
            checkPoint(22.543096, 114.057865);
            activity = open();
            inputAndStart(activity, "39.908823", "116.397470");
            waitRunning(true);
            checkPoint(39.908823, 116.397470);
            StatusBarNotification[] current = notices.getActiveNotifications();
            check(current.length == 1 && current[0].getNotification().actions.length == 1, "stop notification action exists");
            current[0].getNotification().actions[0].actionIntent.send();
            waitRunning(false);
            SystemClock.sleep(200);
            check(notices.getActiveNotifications().length == 0, "notification removed on stop");
            check(app.getSharedPreferences("settings", 0).getStringSet("owned_providers", java.util.Collections.emptySet()).isEmpty(), "providers restored");
            check(!app.getSharedPreferences("settings", 0).getBoolean("error", false), "normal stop has no error");
            LocationManager manager = app.getSystemService(LocationManager.class);
            check(baselineProviders.equals(new java.util.HashSet<>(manager.getAllProviders())), "system provider inventory restored");
            getUiAutomation().adoptShellPermissionIdentity("android.permission.ACCESS_FINE_LOCATION");
            try {
                Location afterStop = manager.getLastKnownLocation("gps");
                check(afterStop == null || !afterStop.isFromMockProvider(), "GPS no longer returns the simulated fix after stop");
            } finally { getUiAutomation().dropShellPermissionIdentity(); }
            // Revoke authorization during a running session: fail safely and stop updating.
            activity = open();
            inputAndStart(activity, "30.5728", "104.0668");
            waitRunning(true);
            shell("appops set cn.vitamin.mocklocation android:mock_location deny");
            waitRunning(false);
            check(app.getSharedPreferences("settings", 0).getBoolean("error", false), "revocation reports an error");
            // Re-selecting the app must recover from the previous interrupted session.
            shell("appops set cn.vitamin.mocklocation android:mock_location allow");
            activity = open();
            inputAndStart(activity, "31.2304", "121.4737");
            waitRunning(true);
            checkPoint(31.2304, 121.4737);
            app.startService(new Intent(app, MockLocationService.class).setAction(MockLocationService.STOP));
            waitRunning(false);
            result.putString("stream", "PASS: " + assertions + " Android 11 lifecycle, permissions and system-provider assertions\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            try { app.startService(new Intent(app, MockLocationService.class).setAction(MockLocationService.STOP)); }
            catch (Exception ignored) { }
            result.putString("stream", "FAIL: " + failure + "\n" + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}

package com.guardianshield.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class GuardService extends Service {

    private static final String TAG = "GuardService";
    private static final String CHANNEL_ID = "gs_service";
    private static final int CHECK_INTERVAL_MS = 3000;

    private Handler handler;
    private Runnable checkRunnable;
    private FirebaseFirestore db;
    private ListenerRegistration rulesListener;
    private ListenerRegistration appsListener;

    // rules: appId -> schedule map
    private Map<String, Object> currentRules = new HashMap<>();
    // packageName -> appId  (built from installed_apps list)
    private Map<String, String> packageToAppId = new HashMap<>();

    private static final String[] DAY_NAMES = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(1, buildNotification());
        listenToFirebaseRules();
        listenToInstalledApps();
        startMonitoring();
        Log.d(TAG, "GuardService started");
    }

    // ── Listen to rules ──────────────────────────────────────────
    private void listenToFirebaseRules() {
        rulesListener = db.collection("guardianshield").document("rules")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { Log.e(TAG, "Rules error", e); return; }
                    if (snap != null && snap.exists()) {
                        currentRules = snap.getData() != null ? snap.getData() : new HashMap<>();
                        Log.d(TAG, "Rules updated: " + currentRules.size() + " entries");
                    }
                });
    }

    // ── Listen to installed apps to build package->appId map ─────
    @SuppressWarnings("unchecked")
    private void listenToInstalledApps() {
        appsListener = db.collection("guardianshield").document("installed_apps")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { Log.e(TAG, "Apps error", e); return; }
                    if (snap != null && snap.exists()) {
                        Map<String, Object> data = snap.getData();
                        if (data == null) return;
                        List<Map<String, Object>> list =
                                (List<Map<String, Object>>) data.get("list");
                        if (list == null) return;
                        Map<String, String> newMap = new HashMap<>();
                        for (Map<String, Object> app : list) {
                            String pkg = (String) app.get("packageName");
                            String id  = (String) app.get("id");
                            if (pkg != null && id != null) {
                                newMap.put(pkg, id);
                            }
                        }
                        packageToAppId = newMap;
                        Log.d(TAG, "Package map updated: " + packageToAppId.size() + " apps");
                    }
                });
    }

    // ── Monitoring loop ──────────────────────────────────────────
    private void startMonitoring() {
        checkRunnable = new Runnable() {
            @Override
            public void run() {
                checkForegroundApp();
                handler.postDelayed(this, CHECK_INTERVAL_MS);
            }
        };
        handler.post(checkRunnable);
    }

    // ── Core check ───────────────────────────────────────────────
    private void checkForegroundApp() {
        String foregroundPkg = getForegroundApp();
        if (foregroundPkg == null || foregroundPkg.equals(getPackageName())) return;

        // Get the appId for this package
        String appId = packageToAppId.get(foregroundPkg);
        if (appId == null) return; // Not in our app list

        // Check if there's a rule for this appId
        Object ruleObj = currentRules.get(appId);
        if (!(ruleObj instanceof Map)) return;

        @SuppressWarnings("unchecked")
        Map<String, Object> rule = (Map<String, Object>) ruleObj;

        Boolean enabled = (Boolean) rule.get("enabled");
        if (enabled == null || !enabled) return;

        // Check if current time is allowed
        if (!isAllowedNow(rule)) {
            Log.d(TAG, "BLOCKING: " + foregroundPkg + " (appId: " + appId + ")");
            blockApp(foregroundPkg, appId);
        }
    }

    // ── Time/day check ───────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private boolean isAllowedNow(Map<String, Object> rule) {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1=Sun
        String todayName = DAY_NAMES[dayOfWeek - 1];

        int currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        // Check allowed days
        List<String> days = (List<String>) rule.get("days");
        if (days == null || !days.contains(todayName)) return false;

        // Check time slots
        List<Map<String, Object>> slots = (List<Map<String, Object>>) rule.get("slots");
        if (slots == null || slots.isEmpty()) return false;

        for (Map<String, Object> slot : slots) {
            String from = (String) slot.get("from");
            String to   = (String) slot.get("to");
            if (from == null || to == null) continue;
            if (currentMins >= timeToMins(from) && currentMins < timeToMins(to)) {
                return true;
            }
        }
        return false;
    }

    private int timeToMins(String t) {
        String[] p = t.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    // ── Show block screen ────────────────────────────────────────
    private void blockApp(String packageName, String appId) {
        Intent intent = new Intent(this, BlockActivity.class);
        intent.putExtra("pkg", packageName);
        intent.putExtra("appId", appId);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    // ── Get foreground app ───────────────────────────────────────
    private String getForegroundApp() {
        try {
            UsageStatsManager usm = (UsageStatsManager)
                    getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, now - 10000, now);
            if (stats == null || stats.isEmpty()) return null;
            for (UsageStats us : stats) {
                sortedMap.put(us.getLastTimeUsed(), us);
            }
            if (!sortedMap.isEmpty()) {
                return sortedMap.get(sortedMap.lastKey()).getPackageName();
            }
        } catch (Exception e) {
            Log.e(TAG, "getForegroundApp error", e);
        }
        return null;
    }

    // ── Notification ─────────────────────────────────────────────
    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Guardian Shield", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System Service")
                .setContentText("Running in background")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && checkRunnable != null) handler.removeCallbacks(checkRunnable);
        if (rulesListener != null) rulesListener.remove();
        if (appsListener  != null) appsListener.remove();
        startService(new Intent(this, GuardService.class)); // restart self
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

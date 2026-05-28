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

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class GuardService extends Service {

    private static final String TAG = "GuardService";
    private static final String CHANNEL_ID = "gs_service";
    private static final int CHECK_INTERVAL_MS = 3000; // Check every 3 seconds

    private Handler handler;
    private Runnable checkRunnable;
    private FirebaseFirestore db;
    private ListenerRegistration rulesListener;

    // Current rules from Firebase: packageName -> schedule info
    private Map<String, Object> currentRules = new HashMap<>();

    // Days mapping
    private static final String[] DAY_NAMES = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    @Override
    public void onCreate() {
        super.onCreate();
        db = FirebaseFirestore.getInstance();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(1, buildNotification());
        listenToFirebaseRules();
        startMonitoring();
        Log.d(TAG, "GuardService started");
    }

    // ── Listen to Firebase rules in real-time ────────────────────
    private void listenToFirebaseRules() {
        rulesListener = db.collection("guardianshield").document("rules")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { Log.e(TAG, "Rules listen failed", e); return; }
                    if (snap != null && snap.exists()) {
                        currentRules = snap.getData() != null ? snap.getData() : new HashMap<>();
                        Log.d(TAG, "Rules updated: " + currentRules.size() + " apps");
                    }
                });
    }

    // ── Start the monitoring loop ────────────────────────────────
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

    // ── Check what app is in foreground ──────────────────────────
    private void checkForegroundApp() {
        String foregroundPkg = getForegroundApp();
        if (foregroundPkg == null || foregroundPkg.equals(getPackageName())) return;

        // Check each rule
        for (Map.Entry<String, Object> entry : currentRules.entrySet()) {
            String appId = entry.getKey();
            Object ruleObj = entry.getValue();
            if (!(ruleObj instanceof Map)) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> rule = (Map<String, Object>) ruleObj;

            Boolean enabled = (Boolean) rule.get("enabled");
            if (enabled == null || !enabled) continue;

            // Get package name for this app
            String packageName = AppScanner.getPackageForAppId(appId);
            if (packageName == null || !foregroundPkg.equals(packageName)) continue;

            // Check if current time is within allowed slots
            if (!isAllowedNow(rule)) {
                // BLOCK IT
                blockApp(foregroundPkg, appId);
                return;
            }
        }
    }

    // ── Check if now is within allowed time/day ───────────────────
    @SuppressWarnings("unchecked")
    private boolean isAllowedNow(Map<String, Object> rule) {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK); // 1=Sun, 7=Sat
        String todayName = DAY_NAMES[dayOfWeek - 1];

        int currentHour = cal.get(Calendar.HOUR_OF_DAY);
        int currentMin  = cal.get(Calendar.MINUTE);
        int currentMins = currentHour * 60 + currentMin;

        // Check allowed days
        List<String> days = (List<String>) rule.get("days");
        if (days == null || !days.contains(todayName)) return false;

        // Check time slots
        List<Map<String, Object>> slots = (List<Map<String, Object>>) rule.get("slots");
        if (slots == null || slots.isEmpty()) return false;

        for (Map<String, Object> slot : slots) {
            String from = (String) slot.get("from"); // "16:00"
            String to   = (String) slot.get("to");   // "18:00"
            if (from == null || to == null) continue;

            int fromMins = timeToMins(from);
            int toMins   = timeToMins(to);

            if (currentMins >= fromMins && currentMins < toMins) {
                return true; // Within an allowed slot
            }
        }
        return false;
    }

    private int timeToMins(String time) {
        String[] parts = time.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
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

    // ── Get foreground app using UsageStats ──────────────────────
    private String getForegroundApp() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
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

    // ── Notification (required for foreground service) ────────────
    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Guardian Shield", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Monitoring app usage");
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
        return START_STICKY; // Restart automatically if killed
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && checkRunnable != null) handler.removeCallbacks(checkRunnable);
        if (rulesListener != null) rulesListener.remove();
        // Restart self
        startService(new Intent(this, GuardService.class));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

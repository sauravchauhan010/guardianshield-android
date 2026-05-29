package com.guardianshield.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GuardAccessibilityService extends AccessibilityService {

    private static final String TAG = "GuardAccess";

    private FirebaseFirestore db;
    private ListenerRegistration rulesListener;
    private ListenerRegistration appsListener;

    private Map<String, Object> currentRules  = new HashMap<>();
    private Map<String, String> packageToAppId = new HashMap<>();

    private static final String[] DAY_NAMES = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "✅ AccessibilityService connected!");

        // Configure to receive window state changes
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes  = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags       = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        db = FirebaseFirestore.getInstance();
        listenToRules();
        listenToInstalledApps();
    }

    // ── Firebase rules listener ──────────────────────────────────
    private void listenToRules() {
        rulesListener = db.collection("guardianshield").document("rules")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) return;
                    if (snap != null && snap.exists()) {
                        currentRules = snap.getData() != null ? snap.getData() : new HashMap<>();
                        Log.d(TAG, "Rules loaded: " + currentRules.size());
                    }
                });
    }

    // ── Firebase installed apps listener ─────────────────────────
    @SuppressWarnings("unchecked")
    private void listenToInstalledApps() {
        appsListener = db.collection("guardianshield").document("installed_apps")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) return;
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
                            if (pkg != null && id != null) newMap.put(pkg, id);
                        }
                        packageToAppId = newMap;
                        Log.d(TAG, "Package map: " + packageToAppId.size() + " apps");
                    }
                });
    }

    // ── Called EVERY time a window/app changes ───────────────────
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkgCs = event.getPackageName();
        if (pkgCs == null) return;
        String foregroundPkg = pkgCs.toString();

        // Skip our own app
        if (foregroundPkg.equals(getPackageName())) return;

        Log.d(TAG, "App opened: " + foregroundPkg);

        // Find appId for this package
        String appId = packageToAppId.get(foregroundPkg);

        // Fallback: try sanitized package name directly as appId
        if (appId == null) {
            String sanitized = foregroundPkg.replace(".", "_").replace("-", "_");
            if (currentRules.containsKey(sanitized)) {
                appId = sanitized;
            }
        }

        if (appId == null) return;

        // Get rule
        Object ruleObj = currentRules.get(appId);
        if (!(ruleObj instanceof Map)) return;

        @SuppressWarnings("unchecked")
        Map<String, Object> rule = (Map<String, Object>) ruleObj;

        Boolean enabled = (Boolean) rule.get("enabled");
        if (enabled == null || !enabled) return;

        // Check time
        if (!isAllowedNow(rule)) {
            Log.d(TAG, "🚫 BLOCKING: " + foregroundPkg);
            showBlockScreen(foregroundPkg, appId);
        }
    }

    // ── Time/day check ───────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private boolean isAllowedNow(Map<String, Object> rule) {
        Calendar cal      = Calendar.getInstance();
        String todayName  = DAY_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1];
        int currentMins   = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        List<String> days = (List<String>) rule.get("days");
        if (days == null || !days.contains(todayName)) return false;

        List<Map<String, Object>> slots = (List<Map<String, Object>>) rule.get("slots");
        if (slots == null || slots.isEmpty()) return false;

        for (Map<String, Object> slot : slots) {
            String from = (String) slot.get("from");
            String to   = (String) slot.get("to");
            if (from == null || to == null) continue;
            if (currentMins >= timeToMins(from) && currentMins < timeToMins(to))
                return true;
        }
        return false;
    }

    private int timeToMins(String t) {
        try {
            String[] p = t.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) { return 0; }
    }

    // ── Launch block screen ───────────────────────────────────────
    private void showBlockScreen(String pkg, String appId) {
        Intent i = new Intent(this, BlockActivity.class);
        i.putExtra("pkg",   pkg);
        i.putExtra("appId", appId);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (rulesListener != null) rulesListener.remove();
        if (appsListener  != null) appsListener.remove();
    }
}

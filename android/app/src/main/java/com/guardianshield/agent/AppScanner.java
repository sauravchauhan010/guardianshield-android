package com.guardianshield.agent;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AppScanner {

    private static final String TAG     = "AppScanner";
    private static final String PREFS   = "gs_prefs";
    private static final String KEY_DID = "device_id";

    // ── Stable unique device ID (UUID, persisted in SharedPreferences) ──
    public static String getDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_DID, null);
        if (id == null) {
            // First run: generate a UUID and persist it
            id = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            prefs.edit().putString(KEY_DID, id).apply();
            Log.d(TAG, "Generated new deviceId: " + id);
        }
        return id;
    }

    // Human-readable label shown in the dashboard
    public static String getDeviceLabel() {
        return Build.BRAND + " " + Build.MODEL;
    }

    // ── Known package → emoji icon ───────────────────────────────
    private static String getIconEmoji(String pkg) {
        if (pkg.contains("youtube"))                        return "▶";
        if (pkg.contains("instagram"))                      return "📸";
        if (pkg.contains("facebook"))                       return "👤";
        if (pkg.contains("snapchat"))                       return "👻";
        if (pkg.contains("tiktok") || pkg.contains("musically")) return "🎵";
        if (pkg.contains("whatsapp"))                       return "💬";
        if (pkg.contains("telegram"))                       return "✈";
        if (pkg.contains("chrome"))                         return "🌐";
        if (pkg.contains("netflix"))                        return "🎬";
        if (pkg.contains("freefire") || pkg.contains("dts")) return "🔥";
        if (pkg.contains("pubg") || pkg.contains("imobile")) return "🎯";
        if (pkg.contains("roblox"))                         return "🎮";
        if (pkg.contains("clash"))                          return "⚔";
        if (pkg.contains("subway"))                         return "🏃";
        if (pkg.contains("game") || pkg.contains("play"))  return "🎮";
        return "📱";
    }

    // ── Known package → brand color ──────────────────────────────
    private static String getColor(String pkg) {
        if (pkg.contains("youtube"))   return "#FF0000";
        if (pkg.contains("instagram")) return "#C13584";
        if (pkg.contains("facebook"))  return "#1877F2";
        if (pkg.contains("snapchat"))  return "#FFFC00";
        if (pkg.contains("tiktok") || pkg.contains("musically")) return "#010101";
        if (pkg.contains("whatsapp"))  return "#25D366";
        if (pkg.contains("telegram"))  return "#2CA5E0";
        if (pkg.contains("netflix"))   return "#E50914";
        if (pkg.contains("freefire"))  return "#FF6B00";
        if (pkg.contains("pubg"))      return "#F5A623";
        if (pkg.contains("roblox"))    return "#E62020";
        return "#7C3AED";
    }

    private static String sanitizeId(String pkg) {
        return pkg.replace(".", "_").replace("-", "_");
    }

    /**
     * Scans installed apps and uploads them to:
     *   devices/{deviceId}            ← device registry entry
     *   device_{deviceId}             ← app list for this device
     *
     * Rules are stored separately under rules_{deviceId} and are
     * never overwritten by the scanner.
     */
    public static void uploadInstalledApps(Context context) {
        new Thread(() -> {
            try {
                PackageManager pm       = context.getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
                String deviceId         = getDeviceId(context);
                String deviceLabel      = getDeviceLabel();

                List<Map<String, Object>> appList = new ArrayList<>();

                List<String> skipPrefixes = new ArrayList<>();
                skipPrefixes.add("com.android.internal");
                skipPrefixes.add("com.android.providers");
                skipPrefixes.add("com.android.server");
                skipPrefixes.add("com.qualcomm");
                skipPrefixes.add("com.mediatek");
                skipPrefixes.add("com.guardianshield");

                for (ApplicationInfo app : apps) {
                    String pkgName = app.packageName;

                    boolean isSystemInternal =
                            (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            pm.getLaunchIntentForPackage(pkgName) == null;
                    if (isSystemInternal) continue;

                    boolean shouldSkip = false;
                    for (String prefix : skipPrefixes) {
                        if (pkgName.startsWith(prefix)) { shouldSkip = true; break; }
                    }
                    if (shouldSkip) continue;

                    String appName = pm.getApplicationLabel(app).toString();

                    Map<String, Object> appData = new HashMap<>();
                    appData.put("id",          sanitizeId(pkgName));
                    appData.put("name",        appName);
                    appData.put("packageName", pkgName);
                    appData.put("icon",        getIconEmoji(pkgName));
                    appData.put("color",       getColor(pkgName));
                    appList.add(appData);
                }

                FirebaseFirestore db = FirebaseFirestore.getInstance();

                // 1. Upload app list to device-specific document
                Map<String, Object> deviceApps = new HashMap<>();
                deviceApps.put("list",        appList);
                deviceApps.put("deviceId",    deviceId);
                deviceApps.put("deviceLabel", deviceLabel);
                deviceApps.put("model",       Build.MODEL);
                deviceApps.put("brand",       Build.BRAND);
                deviceApps.put("updatedAt",   System.currentTimeMillis());

                db.collection("guardianshield")
                        .document("device_" + deviceId)
                        .set(deviceApps)
                        .addOnSuccessListener(v ->
                                Log.d(TAG, "✅ Uploaded " + appList.size() + " apps for device: " + deviceId))
                        .addOnFailureListener(e ->
                                Log.e(TAG, "❌ Upload failed", e));

                // 2. Register / update device entry in the devices registry
                Map<String, Object> deviceInfo = new HashMap<>();
                deviceInfo.put("deviceId",    deviceId);
                deviceInfo.put("deviceLabel", deviceLabel);
                deviceInfo.put("model",       Build.MODEL);
                deviceInfo.put("brand",       Build.BRAND);
                deviceInfo.put("lastSeen",    System.currentTimeMillis());
                deviceInfo.put("appCount",    appList.size());
                deviceInfo.put("online",      true);

                db.collection("guardianshield")
                        .document("devices")
                        .collection("list")
                        .document(deviceId)
                        .set(deviceInfo)
                        .addOnSuccessListener(v ->
                                Log.d(TAG, "✅ Device registered: " + deviceId))
                        .addOnFailureListener(e ->
                                Log.e(TAG, "❌ Device register failed", e));

            } catch (Exception e) {
                Log.e(TAG, "Scan error", e);
            }
        }).start();
    }
}

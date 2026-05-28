package com.guardianshield.agent;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppScanner {

    private static final String TAG = "AppScanner";

    // Known app ID → package name mapping
    private static final Map<String, String> APP_ID_TO_PACKAGE = new HashMap<>();
    static {
        APP_ID_TO_PACKAGE.put("youtube",   "com.google.android.youtube");
        APP_ID_TO_PACKAGE.put("freefire",  "com.dts.freefireth");
        APP_ID_TO_PACKAGE.put("pubg",      "com.pubg.imobile");
        APP_ID_TO_PACKAGE.put("instagram", "com.instagram.android");
        APP_ID_TO_PACKAGE.put("snapchat",  "com.snapchat.android");
        APP_ID_TO_PACKAGE.put("facebook",  "com.facebook.katana");
        APP_ID_TO_PACKAGE.put("tiktok",    "com.zhiliaoapp.musically");
        APP_ID_TO_PACKAGE.put("whatsapp",  "com.whatsapp");
        APP_ID_TO_PACKAGE.put("telegram",  "org.telegram.messenger");
        APP_ID_TO_PACKAGE.put("chrome",    "com.android.chrome");
        APP_ID_TO_PACKAGE.put("subway_surfers", "com.kiloo.subwaysurf");
        APP_ID_TO_PACKAGE.put("clash_of_clans", "com.supercell.clashofclans");
        APP_ID_TO_PACKAGE.put("roblox",    "com.roblox.client");
        APP_ID_TO_PACKAGE.put("netflix",   "com.netflix.mediaclient");
    }

    // Display names
    private static final Map<String, String> APP_ID_TO_NAME = new HashMap<>();
    static {
        APP_ID_TO_NAME.put("youtube",   "YouTube");
        APP_ID_TO_NAME.put("freefire",  "Free Fire");
        APP_ID_TO_NAME.put("pubg",      "BGMI / PUBG");
        APP_ID_TO_NAME.put("instagram", "Instagram");
        APP_ID_TO_NAME.put("snapchat",  "Snapchat");
        APP_ID_TO_NAME.put("facebook",  "Facebook");
        APP_ID_TO_NAME.put("tiktok",    "TikTok");
        APP_ID_TO_NAME.put("whatsapp",  "WhatsApp");
        APP_ID_TO_NAME.put("telegram",  "Telegram");
        APP_ID_TO_NAME.put("chrome",    "Chrome");
        APP_ID_TO_NAME.put("subway_surfers", "Subway Surfers");
        APP_ID_TO_NAME.put("clash_of_clans", "Clash of Clans");
        APP_ID_TO_NAME.put("roblox",    "Roblox");
        APP_ID_TO_NAME.put("netflix",   "Netflix");
    }

    public static String getPackageForAppId(String appId) {
        return APP_ID_TO_PACKAGE.get(appId);
    }

    public static String getDisplayNameForAppId(String appId) {
        return APP_ID_TO_NAME.getOrDefault(appId, appId);
    }

    /**
     * Scans all installed apps and uploads to Firebase.
     * This populates the web panel with her actual installed apps.
     */
    public static void uploadInstalledApps(Context context) {
        new Thread(() -> {
            try {
                PackageManager pm = context.getPackageManager();
                List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

                List<Map<String, Object>> appList = new ArrayList<>();

                for (ApplicationInfo app : apps) {
                    // Skip system apps
                    if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

                    String pkgName = app.packageName;
                    String appName = pm.getApplicationLabel(app).toString();

                    Map<String, Object> appData = new HashMap<>();
                    appData.put("id",          sanitizeId(pkgName));
                    appData.put("name",        appName);
                    appData.put("packageName", pkgName);
                    appData.put("icon",        getIconEmoji(pkgName));
                    appData.put("color",       getColor(pkgName));
                    appList.add(appData);

                    // Also update the local mapping
                    APP_ID_TO_PACKAGE.put(sanitizeId(pkgName), pkgName);
                    APP_ID_TO_NAME.put(sanitizeId(pkgName), appName);
                }

                // Upload to Firebase
                Map<String, Object> data = new HashMap<>();
                data.put("list",      appList);
                data.put("deviceId",  android.os.Build.MODEL);
                data.put("updatedAt", System.currentTimeMillis());

                FirebaseFirestore.getInstance()
                        .collection("guardianshield").document("installed_apps")
                        .set(data)
                        .addOnSuccessListener(v -> Log.d(TAG, "Uploaded " + appList.size() + " apps"))
                        .addOnFailureListener(e -> Log.e(TAG, "Upload failed", e));

            } catch (Exception e) {
                Log.e(TAG, "Scan error", e);
            }
        }).start();
    }

    private static String sanitizeId(String pkg) {
        return pkg.replace(".", "_").replace("-", "_");
    }

    private static String getIconEmoji(String pkg) {
        if (pkg.contains("youtube"))   return "▶";
        if (pkg.contains("instagram")) return "📸";
        if (pkg.contains("facebook"))  return "👤";
        if (pkg.contains("snapchat"))  return "👻";
        if (pkg.contains("tiktok") || pkg.contains("musically")) return "🎵";
        if (pkg.contains("whatsapp"))  return "💬";
        if (pkg.contains("telegram"))  return "✈";
        if (pkg.contains("chrome"))    return "🌐";
        if (pkg.contains("netflix"))   return "🎬";
        if (pkg.contains("freefire") || pkg.contains("dts")) return "🔥";
        if (pkg.contains("pubg") || pkg.contains("imobile")) return "🎯";
        if (pkg.contains("roblox"))    return "🎮";
        if (pkg.contains("clash"))     return "⚔";
        if (pkg.contains("subway"))    return "🏃";
        return "📱";
    }

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
}

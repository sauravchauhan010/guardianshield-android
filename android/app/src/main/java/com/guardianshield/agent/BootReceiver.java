package com.guardianshield.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {

            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":
                Log.d(TAG, "Boot detected → starting service");
                context.startForegroundService(new Intent(context, GuardService.class));
                AppScanner.uploadInstalledApps(context);
                break;

            case Intent.ACTION_PACKAGE_ADDED: {
                String pkg = getPackage(intent);
                if (pkg != null && !pkg.equals(context.getPackageName())) {
                    Log.d(TAG, "App installed: " + pkg);
                    AppScanner.uploadInstalledApps(context);
                }
                break;
            }

            case Intent.ACTION_PACKAGE_REMOVED:
            case Intent.ACTION_PACKAGE_FULLY_REMOVED: {
                boolean isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                if (!isReplacing) {
                    String pkg = getPackage(intent);
                    if (pkg != null) {
                        Log.d(TAG, "App uninstalled: " + pkg);
                        removeAppFromFirebase(context, pkg);
                    }
                }
                break;
            }

            case Intent.ACTION_PACKAGE_REPLACED: {
                Log.d(TAG, "App updated: " + getPackage(intent));
                AppScanner.uploadInstalledApps(context);
                break;
            }
        }
    }

    private String getPackage(Intent intent) {
        if (intent.getData() == null) return null;
        return intent.getData().getSchemeSpecificPart();
    }

    // Remove a single app from this device's installed_apps doc
    @SuppressWarnings("unchecked")
    private void removeAppFromFirebase(Context context, String packageName) {
        String deviceId = AppScanner.getDeviceId(context);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("guardianshield")
                .document("device_" + deviceId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) return;
                    List<Map<String, Object>> list =
                            (List<Map<String, Object>>) snap.get("list");
                    if (list == null) return;

                    List<Map<String, Object>> updated = new ArrayList<>();
                    for (Map<String, Object> app : list) {
                        String pkg = (String) app.get("packageName");
                        if (!packageName.equals(pkg)) updated.add(app);
                    }

                    snap.getReference().update("list", updated)
                            .addOnSuccessListener(v ->
                                    Log.d(TAG, "✅ Removed " + packageName + " from Firebase"))
                            .addOnFailureListener(e ->
                                    Log.e(TAG, "❌ Failed to remove app", e));
                });
    }
}

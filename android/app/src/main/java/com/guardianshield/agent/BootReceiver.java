package com.guardianshield.agent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        switch (action) {

            // ── Phone rebooted → start service + rescan all apps ──
            case Intent.ACTION_BOOT_COMPLETED:
            case "android.intent.action.QUICKBOOT_POWERON":
                Log.d(TAG, "Boot detected → starting service");
                context.startForegroundService(new Intent(context, GuardService.class));
                AppScanner.uploadInstalledApps(context);
                break;

            // ── New app installed → add to Firebase list ──────────
            case Intent.ACTION_PACKAGE_ADDED: {
                String pkg = getPackage(intent);
                if (pkg != null && !pkg.equals(context.getPackageName())) {
                    Log.d(TAG, "App installed: " + pkg);
                    AppScanner.uploadInstalledApps(context); // rescan full list
                }
                break;
            }

            // ── App uninstalled → remove from Firebase list ───────
            case Intent.ACTION_PACKAGE_REMOVED:
            case Intent.ACTION_PACKAGE_FULLY_REMOVED: {
                // Make sure it's not a replacement (update)
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

            // ── App updated → refresh list ─────────────────────────
            case Intent.ACTION_PACKAGE_REPLACED: {
                String pkg = getPackage(intent);
                Log.d(TAG, "App updated: " + pkg);
                AppScanner.uploadInstalledApps(context);
                break;
            }
        }
    }

    private String getPackage(Intent intent) {
        if (intent.getData() == null) return null;
        return intent.getData().getSchemeSpecificPart();
    }

    // ── Remove single app from Firebase installed_apps list ───────
    @SuppressWarnings("unchecked")
    private void removeAppFromFirebase(Context context, String packageName) {
        String deviceId = AppScanner.getDeviceId();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("guardianshield")
                .document("device_" + deviceId)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) return;
                    List<Map<String, Object>> list =
                            (List<Map<String, Object>>) snap.get("list");
                    if (list == null) return;

                    // Remove the uninstalled app from list
                    List<Map<String, Object>> updated = new ArrayList<>();
                    for (Map<String, Object> app : list) {
                        String pkg = (String) app.get("packageName");
                        if (!packageName.equals(pkg)) {
                            updated.add(app); // keep it
                        }
                    }

                    // Save updated list back to Firebase
                    snap.getReference().update("list", updated)
                            .addOnSuccessListener(v ->
                                    Log.d(TAG, "✅ Removed " + packageName + " from Firebase"))
                            .addOnFailureListener(e ->
                                    Log.e(TAG, "❌ Failed to remove app", e));
                });
    }
}

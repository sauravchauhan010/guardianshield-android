package com.guardianshield.agent;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "AdminReceiver";

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        sendTamperAlert(context, "DISABLE_REQUESTED");
        Log.w(TAG, "Device admin disable requested!");
        return "⚠ GuardianShield protection will be removed. Your guardian will be notified.";
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        sendTamperAlert(context, "ADMIN_DISABLED");
        Log.w(TAG, "Device admin DISABLED!");
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.d(TAG, "Device admin enabled");
        sendStatusUpdate(context, "ADMIN_ENABLED");
    }

    // ── Send tamper alert, keeping the receiver alive until the
    //    Firestore write actually completes (success OR failure) ──
    private void sendTamperAlert(Context context, String type) {
        // goAsync() tells Android "don't kill me yet, async work pending"
        final PendingResult pendingResult = goAsync();

        try {
            String deviceId    = AppScanner.getDeviceId(context);
            String deviceLabel = AppScanner.getDeviceLabel();

            Map<String, Object> alert = new HashMap<>();
            alert.put("type",        type);
            alert.put("timestamp",   System.currentTimeMillis());
            alert.put("device",      android.os.Build.MODEL);
            alert.put("deviceId",    deviceId);
            alert.put("deviceLabel", deviceLabel);
            alert.put("resolved",    false);
            alert.put("message",     getTamperMessage(type, deviceLabel));

            FirebaseFirestore.getInstance()
                    .collection("guardianshield")
                    .document("tamper_" + deviceId)
                    .set(alert)
                    .addOnSuccessListener(v -> {
                        Log.d(TAG, "✅ Tamper alert sent: " + type);
                        pendingResult.finish(); // release the receiver now
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Failed to send alert", e);
                        pendingResult.finish(); // release even on failure
                    });

        } catch (Exception e) {
            Log.e(TAG, "sendTamperAlert error", e);
            pendingResult.finish(); // always release, even on exception
        }
    }

    // ── Same goAsync() pattern for the status update write ──────
    private void sendStatusUpdate(Context context, String status) {
        final PendingResult pendingResult = goAsync();

        try {
            String deviceId = AppScanner.getDeviceId(context);
            Map<String, Object> data = new HashMap<>();
            data.put("status",    status);
            data.put("timestamp", System.currentTimeMillis());
            data.put("device",    android.os.Build.MODEL);
            data.put("deviceId",  deviceId);

            FirebaseFirestore.getInstance()
                    .collection("guardianshield")
                    .document("devices")
                    .collection("list")
                    .document(deviceId)
                    .update("status", status, "lastSeen", System.currentTimeMillis())
                    .addOnSuccessListener(v -> {
                        Log.d(TAG, "✅ Status update sent: " + status);
                        pendingResult.finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "❌ Status update failed", e);
                        pendingResult.finish();
                    });

        } catch (Exception e) {
            Log.e(TAG, "sendStatusUpdate error", e);
            pendingResult.finish();
        }
    }

    private String getTamperMessage(String type, String deviceLabel) {
        switch (type) {
            case "DISABLE_REQUESTED":
                return "Someone tried to disable Device Admin on " + deviceLabel + "!";
            case "ADMIN_DISABLED":
                return "Device Admin was disabled on " + deviceLabel + ". App can now be uninstalled!";
            default:
                return "Unknown tamper event on " + deviceLabel + ": " + type;
        }
    }
}

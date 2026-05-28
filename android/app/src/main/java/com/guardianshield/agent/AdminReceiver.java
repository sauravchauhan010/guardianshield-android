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
    public void onDisableRequested(Context context, Intent intent) {
        // Someone is trying to disable Device Admin - send tamper alert!
        sendTamperAlert(context, "DISABLE_REQUESTED");
        Log.w(TAG, "Device admin disable requested!");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        // Device admin was disabled - send critical alert!
        sendTamperAlert(context, "ADMIN_DISABLED");
        Log.w(TAG, "Device admin DISABLED!");
    }

    @Override
    public void onEnabled(Context context, Intent intent) {
        Log.d(TAG, "Device admin enabled");
        sendStatusUpdate(context, "ADMIN_ENABLED");
    }

    private void sendTamperAlert(Context context, String type) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("type",      type);
            alert.put("timestamp", System.currentTimeMillis());
            alert.put("device",    android.os.Build.MODEL);
            alert.put("resolved",  false);
            alert.put("message",   getTamperMessage(type));

            FirebaseFirestore.getInstance()
                    .collection("guardianshield").document("tamper_alert")
                    .set(alert)
                    .addOnSuccessListener(v -> Log.d(TAG, "Tamper alert sent: " + type))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to send alert", e));
        } catch (Exception e) {
            Log.e(TAG, "sendTamperAlert error", e);
        }
    }

    private void sendStatusUpdate(Context context, String status) {
        Map<String, Object> data = new HashMap<>();
        data.put("status",    status);
        data.put("timestamp", System.currentTimeMillis());
        data.put("device",    android.os.Build.MODEL);

        FirebaseFirestore.getInstance()
                .collection("guardianshield").document("device_status")
                .set(data);
    }

    private String getTamperMessage(String type) {
        switch (type) {
            case "DISABLE_REQUESTED": return "Someone tried to disable Device Admin on the phone!";
            case "ADMIN_DISABLED":    return "Device Admin was disabled. App can now be uninstalled!";
            default: return "Unknown tamper event: " + type;
        }
    }
}

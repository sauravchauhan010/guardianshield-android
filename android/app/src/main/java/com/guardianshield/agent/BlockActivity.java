package com.guardianshield.agent;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BlockActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make it show over lock screen
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        String appId = getIntent().getStringExtra("appId");
        buildBlockScreen(appId);
    }

    private void buildBlockScreen(String appId) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0f0c29"));
        root.setGravity(Gravity.CENTER);
        root.setPadding(60, 0, 60, 0);

        // Shield icon
        TextView icon = new TextView(this);
        icon.setText("🛡️");
        icon.setTextSize(72);
        icon.setGravity(Gravity.CENTER);
        root.addView(icon);

        // Title
        TextView title = new TextView(this);
        title.setText("App Blocked");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 24, 0, 0);
        root.addView(title);

        // App name
        TextView appName = new TextView(this);
        appName.setText(AppScanner.getDisplayNameForAppId(appId));
        appName.setTextSize(18);
        appName.setTextColor(Color.parseColor("#a78bfa"));
        appName.setGravity(Gravity.CENTER);
        appName.setPadding(0, 8, 0, 0);
        root.addView(appName);

        // Reason
        TextView reason = new TextView(this);
        reason.setText("This app is not available right now.\nCome back during the allowed time.");
        reason.setTextSize(14);
        reason.setTextColor(Color.parseColor("#aaaacc"));
        reason.setGravity(Gravity.CENTER);
        reason.setPadding(0, 24, 0, 0);
        root.addView(reason);

        // Current time
        TextView time = new TextView(this);
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        time.setText("Current time: " + sdf.format(new Date()));
        time.setTextSize(13);
        time.setTextColor(Color.parseColor("#666688"));
        time.setGravity(Gravity.CENTER);
        time.setPadding(0, 32, 0, 0);
        root.addView(time);

        // Next allowed time - fetch from Firestore
        TextView nextAllowed = new TextView(this);
        nextAllowed.setText("Fetching schedule…");
        nextAllowed.setTextSize(13);
        nextAllowed.setTextColor(Color.parseColor("#4ade80"));
        nextAllowed.setGravity(Gravity.CENTER);
        nextAllowed.setPadding(0, 8, 0, 0);
        root.addView(nextAllowed);

        // Fetch schedule info
        if (appId != null) {
            FirebaseFirestore.getInstance()
                    .collection("guardianshield").document("rules")
                    .get()
                    .addOnSuccessListener(snap -> {
                        String info = getNextAllowedText(snap, appId);
                        nextAllowed.setText(info);
                    });
        }

        // Go Home button
        android.widget.Button btnHome = new android.widget.Button(this);
        btnHome.setText("Go to Home Screen");
        btnHome.setTextColor(Color.WHITE);
        btnHome.setTextSize(16);
        btnHome.setTypeface(null, Typeface.BOLD);
        btnHome.setBackgroundColor(Color.parseColor("#7C3AED"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 140);
        lp.topMargin = 60;
        btnHome.setLayoutParams(lp);
        btnHome.setOnClickListener(v -> goHome());
        root.addView(btnHome);

        setContentView(root);
    }

    @SuppressWarnings("unchecked")
    private String getNextAllowedText(DocumentSnapshot snap, String appId) {
        if (snap == null || !snap.exists()) return "";
        Map<String, Object> data = snap.getData();
        if (data == null) return "";

        Object ruleObj = data.get(appId);
        if (!(ruleObj instanceof Map)) return "";

        Map<String, Object> rule = (Map<String, Object>) ruleObj;
        List<Map<String, Object>> slots = (List<Map<String, Object>>) rule.get("slots");
        if (slots == null || slots.isEmpty()) return "No time slots configured.";

        StringBuilder sb = new StringBuilder("Allowed times today:\n");
        for (Map<String, Object> slot : slots) {
            String from = (String) slot.get("from");
            String to   = (String) slot.get("to");
            if (from != null && to != null) {
                sb.append("  • ").append(formatTime(from)).append(" – ").append(formatTime(to)).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String formatTime(String t) {
        // Convert "16:00" to "4:00 PM"
        try {
            String[] parts = t.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            String ampm = h >= 12 ? "PM" : "AM";
            if (h > 12) h -= 12;
            if (h == 0) h = 12;
            return String.format(Locale.getDefault(), "%d:%02d %s", h, m, ampm);
        } catch (Exception e) {
            return t;
        }
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    @Override
    public void onBackPressed() {
        goHome(); // Back button also goes home, not back to blocked app
    }
}

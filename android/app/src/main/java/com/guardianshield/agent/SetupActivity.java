package com.guardianshield.agent;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.Typeface;

public class SetupActivity extends Activity {

    private static final int REQ_DEVICE_ADMIN = 101;

    private TextView tvStatus;
    private Button btnOverlay, btnUsage, btnAdmin, btnFinish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUI();
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
        title.setText("🛡️ GuardianShield");
        title.setTextSize(28);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        // Subtitle
        TextView sub = new TextView(this);
        sub.setText("Setup required — grant permissions below");
        sub.setTextSize(14);
        sub.setTextColor(Color.parseColor("#aaaacc"));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 16, 0, 48);
        root.addView(sub);

        // Step 1 - Overlay
        root.addView(makeStepLabel("Step 1 of 3 — Display Over Other Apps"));
        btnOverlay = makeButton("Grant Overlay Permission", "#7C3AED");
        btnOverlay.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });
        root.addView(btnOverlay);

        // Step 2 - Usage Stats
        root.addView(makeStepLabel("Step 2 of 3 — Usage Access"));
        btnUsage = makeButton("Grant Usage Access", "#7C3AED");
        btnUsage.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        root.addView(btnUsage);

        // Step 3 - Device Admin
        root.addView(makeStepLabel("Step 3 of 3 — Device Administrator"));
        btnAdmin = makeButton("Activate Device Admin", "#7C3AED");
        btnAdmin.setOnClickListener(v -> {
            ComponentName comp = new ComponentName(this, AdminReceiver.class);
            Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, comp);
            i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Required to prevent unauthorized uninstallation of GuardianShield.");
            startActivityForResult(i, REQ_DEVICE_ADMIN);
        });
        root.addView(btnAdmin);

        // Status
        tvStatus = new TextView(this);
        tvStatus.setText("");
        tvStatus.setTextColor(Color.parseColor("#4ade80"));
        tvStatus.setTextSize(13);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 24, 0, 0);
        root.addView(tvStatus);

        // Finish button
        btnFinish = makeButton("✓ All Done — Start Protection", "#22c55e");
        btnFinish.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 140);
        lp.topMargin = 40;
        btnFinish.setLayoutParams(lp);
        btnFinish.setOnClickListener(v -> finishSetup());
        root.addView(btnFinish);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void finishSetup() {
        // Mark setup done
        getSharedPreferences("gs_prefs", MODE_PRIVATE)
                .edit().putBoolean("setup_done", true).apply();

        // Upload installed apps to Firebase
        AppScanner.uploadInstalledApps(this);

        // Start guard service
        startService(new Intent(this, GuardService.class));

        tvStatus.setText("✓ GuardianShield is now active!");
        btnFinish.postDelayed(this::finish, 1500);
    }

    private TextView makeStepLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#a78bfa"));
        tv.setTextSize(13);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 32;
        lp.bottomMargin = 8;
        tv.setLayoutParams(lp);
        return tv;
    }

    private Button makeButton(String text, String hexColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(15);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setBackgroundColor(Color.parseColor(hexColor));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 130);
        lp.topMargin = 8;
        btn.setLayoutParams(lp);
        return btn;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_DEVICE_ADMIN) {
            if (resultCode == RESULT_OK) {
                tvStatus.setText("✓ Device Admin activated!");
            }
        }
    }
}

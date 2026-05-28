package com.guardianshield.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if setup is complete
        boolean setupDone = getSharedPreferences("gs_prefs", MODE_PRIVATE)
                .getBoolean("setup_done", false);

        if (!setupDone) {
            // First launch - go to setup
            startActivity(new Intent(this, SetupActivity.class));
        } else {
            // Already set up - just ensure service is running
            startService(new Intent(this, GuardService.class));
        }

        finish(); // Hide from recents
    }
}

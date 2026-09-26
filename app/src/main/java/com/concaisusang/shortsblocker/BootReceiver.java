package com.concaisusang.shortsblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

/**
 * Accessibility services that the user has enabled are rebound by Android after boot.
 * Apps cannot silently enable an AccessibilityService themselves. This receiver records
 * that boot occurred and gives OEM firmware a concrete app component to keep registered.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences p = context.getSharedPreferences("state", Context.MODE_PRIVATE);
        p.edit()
                .putLong("last_boot_receiver_elapsed", SystemClock.elapsedRealtime())
                .putString("last_boot_action", intent == null ? "" : String.valueOf(intent.getAction()))
                .apply();
    }
}

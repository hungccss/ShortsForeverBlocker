package com.concaisusang.shortsblocker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks the Shorts player in the official YouTube Android app without root.
 *
 * Privacy properties:
 * - The accessibility service is package-scoped to com.google.android.youtube.
 * - The app does not request INTERNET.
 * - No text or browsing data is stored. Only a local blocked counter is persisted.
 */
public class ShortsBlockerService extends AccessibilityService {
    private static final String YOUTUBE = "com.google.android.youtube";
    private static final long BACK_COOLDOWN_MS = 1200L;
    private static final int MAX_NODES = 3000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastBackAt = 0L;
    private boolean scanQueued = false;

    private static final String[] SHORTS_WORDS = {
            "shorts", "youtube shorts"
    };

    // Controls strongly associated with the Shorts vertical player.
    private static final String[] PLAYER_ACTION_WORDS = {
            "remix", "use this sound", "original sound", "sound",
            "phối lại", "dùng âm thanh", "sử dụng âm thanh", "âm thanh"
    };

    private static final String[] COMMON_ACTION_WORDS = {
            "like", "dislike", "comments", "comment", "share",
            "thích", "không thích", "bình luận", "chia sẻ"
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                    | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_CLICKED
                    | AccessibilityEvent.TYPE_VIEW_SELECTED
                    | AccessibilityEvent.TYPE_VIEW_SCROLLED
                    | AccessibilityEvent.TYPE_VIEW_FOCUSED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.notificationTimeout = 70;
            info.packageNames = new String[]{YOUTUBE};
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            setServiceInfo(info);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!YOUTUBE.contentEquals(event.getPackageName())) return;

        // Fast path: user explicitly taps a Shorts navigation entry.
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            AccessibilityNodeInfo src = event.getSource();
            if (src != null) {
                try {
                    if (looksLikeShortsEntry(src)) {
                        blockNow("clicked_shorts_entry");
                        return;
                    }
                } finally {
                    src.recycle();
                }
            }
        }

        queueScan();
    }

    private void queueScan() {
        if (scanQueued) return;
        scanQueued = true;
        handler.postDelayed(() -> {
            scanQueued = false;
            if (isShortsPlayerVisible()) {
                blockNow("shorts_player_detected");
            }
        }, 110L);

        // YouTube often populates the player controls a fraction later.
        handler.postDelayed(() -> {
            if (isShortsPlayerVisible()) {
                blockNow("shorts_player_late_detected");
            }
        }, 360L);
    }

    private boolean looksLikeShortsEntry(AccessibilityNodeInfo node) {
        String id = lower(node.getViewIdResourceName());
        String text = lower(combine(node.getText(), node.getContentDescription()));

        if (containsAny(id, "shorts", "reel", "pivot_shorts")) return true;
        if (matchesShortsLabel(text) && (node.isClickable() || node.isSelected())) return true;

        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null) {
            try {
                String pText = lower(combine(parent.getText(), parent.getContentDescription()));
                String pId = lower(parent.getViewIdResourceName());
                return containsAny(pId, "shorts", "reel") || matchesShortsLabel(pText);
            } finally {
                parent.recycle();
            }
        }
        return false;
    }

    private boolean isShortsPlayerVisible() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        boolean selectedShortsTab = false;
        boolean shortsSpecificId = false;
        boolean shortsSpecificAction = false;
        int commonActionCount = 0;
        int visited = 0;
        Set<String> commonSeen = new HashSet<>();

        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);

        try {
            while (!q.isEmpty() && visited < MAX_NODES) {
                AccessibilityNodeInfo n = q.removeFirst();
                visited++;

                String id = lower(n.getViewIdResourceName());
                String label = lower(combine(n.getText(), n.getContentDescription()));

                if (containsAny(id,
                        "shorts_player", "reel_player", "reel_watch", "shorts_watch",
                        "reel_video", "shorts_video")) {
                    shortsSpecificId = true;
                }

                if ((n.isSelected() || isParentSelected(n)) && matchesShortsLabel(label)) {
                    selectedShortsTab = true;
                }
                if ((n.isSelected() || isParentSelected(n)) && containsAny(id, "shorts", "reel")) {
                    selectedShortsTab = true;
                }

                for (String s : PLAYER_ACTION_WORDS) {
                    if (label.contains(s)) {
                        shortsSpecificAction = true;
                        break;
                    }
                }

                for (String s : COMMON_ACTION_WORDS) {
                    if (label.contains(s)) commonSeen.add(s);
                }
                commonActionCount = commonSeen.size();

                // High-confidence conditions; intentionally do not block a Shorts shelf on Home/Search.
                if (shortsSpecificId) return true;
                if (selectedShortsTab && commonActionCount >= 2) return true;
                if (shortsSpecificAction && commonActionCount >= 2) return true;

                int childCount = n.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    AccessibilityNodeInfo child = n.getChild(i);
                    if (child != null) q.addLast(child);
                }

                // We own each queued node reference; recycle after children were obtained.
                if (n != root) n.recycle();
            }
            return selectedShortsTab && commonActionCount >= 1 && shortsSpecificAction;
        } finally {
            while (!q.isEmpty()) {
                AccessibilityNodeInfo n = q.removeFirst();
                if (n != root) n.recycle();
            }
            root.recycle();
        }
    }

    private boolean isParentSelected(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo p = node.getParent();
        if (p == null) return false;
        try {
            return p.isSelected();
        } finally {
            p.recycle();
        }
    }

    private void blockNow(String reason) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastBackAt < BACK_COOLDOWN_MS) return;
        lastBackAt = now;

        SharedPreferences p = getSharedPreferences("state", Context.MODE_PRIVATE);
        long total = p.getLong("blocked_count", 0L) + 1L;
        p.edit()
                .putLong("blocked_count", total)
                .putString("last_reason", reason)
                .putLong("last_block_elapsed", now)
                .apply();

        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    private static boolean matchesShortsLabel(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String v = value.trim();
        for (String s : SHORTS_WORDS) {
            if (v.equals(s) || v.startsWith(s + " ") || v.endsWith(" " + s)) return true;
        }
        return false;
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null) return false;
        for (String n : needles) if (value.contains(n)) return true;
        return false;
    }

    private static String lower(CharSequence s) {
        if (s == null) return "";
        return s.toString().toLowerCase(Locale.ROOT);
    }

    private static String combine(CharSequence a, CharSequence b) {
        String aa = a == null ? "" : a.toString();
        String bb = b == null ? "" : b.toString();
        return aa + " " + bb;
    }

    @Override
    public void onInterrupt() {
        // Nothing to interrupt; no audio/haptics are produced.
    }
}

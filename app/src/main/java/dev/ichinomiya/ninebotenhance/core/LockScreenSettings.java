package dev.ichinomiya.ninebotenhance.core;

import android.content.SharedPreferences;

/** User-controlled presentation for the custom frame used while the phone display is asleep. */
public record LockScreenSettings(boolean showTime, boolean keepAppRefreshing, boolean dimBackground) {
    public static final LockScreenSettings DEFAULT = new LockScreenSettings(true, true, true);

    public static LockScreenSettings read(SharedPreferences p) {
        return new LockScreenSettings(
                p.getBoolean("lock_show_time", DEFAULT.showTime),
                p.getBoolean("lock_keep_app_refreshing", DEFAULT.keepAppRefreshing),
                p.getBoolean("lock_dim_background", DEFAULT.dimBackground));
    }

    public void save(SharedPreferences.Editor e) {
        e.putBoolean("lock_show_time", showTime)
                .putBoolean("lock_keep_app_refreshing", keepAppRefreshing)
                .putBoolean("lock_dim_background", dimBackground)
                .apply();
    }
}

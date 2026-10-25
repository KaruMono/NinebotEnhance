package dev.ichinomiya.ninebotenhance.core;

import android.content.SharedPreferences;

/** User-controlled presentation for the custom frame used while the phone display is asleep. */
public record LockScreenSettings(boolean showTime, boolean keepAppRefreshing, boolean dimBackground,
                                 boolean showDate, boolean showTemperature, boolean showTemperatureUnit) {
    public static final LockScreenSettings DEFAULT = new LockScreenSettings(true, true, true, true, false, true);

    public static LockScreenSettings read(SharedPreferences p) {
        return new LockScreenSettings(
                p.getBoolean("lock_show_time", DEFAULT.showTime),
                p.getBoolean("lock_keep_app_refreshing", DEFAULT.keepAppRefreshing),
                p.getBoolean("lock_dim_background", DEFAULT.dimBackground),
                p.getBoolean("lock_show_date", DEFAULT.showDate),
                p.getBoolean("lock_show_temperature", DEFAULT.showTemperature),
                p.getBoolean("lock_show_temperature_unit", DEFAULT.showTemperatureUnit));
    }

    public void save(SharedPreferences.Editor e) {
        e.putBoolean("lock_show_time", showTime)
                .putBoolean("lock_keep_app_refreshing", keepAppRefreshing)
                .putBoolean("lock_dim_background", dimBackground)
                .putBoolean("lock_show_date", showDate)
                .putBoolean("lock_show_temperature", showTemperature)
                .putBoolean("lock_show_temperature_unit", showTemperatureUnit)
                .apply();
    }
}

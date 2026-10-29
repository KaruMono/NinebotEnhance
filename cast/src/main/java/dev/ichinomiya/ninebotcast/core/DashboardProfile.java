package dev.ichinomiya.ninebotcast.core;

/** Measured frame profile used to derive the virtual app area and density. */
public record DashboardProfile(String name, int frameWidth, int frameHeight,
                               float scale, float dx, float dy, boolean halfScreen,
                               int topInset, int bottomInset, int appWidth, int appHeight,
                               int appDpi) {
    public static final int SEVEN_INCH_WIDTH = 1024;
    public static final int SEVEN_INCH_HEIGHT = 600;
    public static final int SEVEN_INCH_ALIGNED_HEIGHT = 608;

    private static volatile DashboardProfile last;

    public static DashboardProfile of(int width, int height) {
        DashboardProfile cached = last;
        if (cached != null && cached.frameWidth == width && cached.frameHeight == height) return cached;
        DashboardProfile result = build(width, height);
        last = result;
        return result;
    }

    private static DashboardProfile build(int width, int height) {
        if (width > 0 && height > width) {
            float scale = width / 818f;
            return new DashboardProfile("half-screen", width, height, scale,
                    width - 848 * scale, height - 480 * scale, true,
                    20, 0, 240, 300, DisplaySettings.DEFAULT_DPI);
        }
        if (width == SEVEN_INCH_WIDTH && (height == SEVEN_INCH_HEIGHT || height == SEVEN_INCH_ALIGNED_HEIGHT)) {
            return new DashboardProfile("seven-inch", width, height, 1f,
                    122, -23, false, 48, Math.max(0, height - 544),
                    760, 496, DisplaySettings.DEFAULT_DPI);
        }
        float scale = width <= 0 || height <= 0 ? 0f : Math.min(width / 848f, height / 480f);
        return new DashboardProfile(width == 848 && height == 480 ? "five-inch" : "generic",
                width, height, scale, width - 848 * scale, height - 480 * scale,
                false, 0, 0, DisplaySettings.DEFAULT_VIRTUAL_WIDTH,
                DisplaySettings.DEFAULT_VIRTUAL_HEIGHT, DisplaySettings.DEFAULT_DPI);
    }

    public String describe() {
        return name + " scale=" + scale + " anchor=(" + (848 * scale + dx) + "," +
                (480 * scale + dy) + ") top=" + topInset + " bottom=" + bottomInset +
                " app=" + appWidth + "x" + appHeight + "@" + appDpi;
    }
}

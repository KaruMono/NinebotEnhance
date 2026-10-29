package dev.ichinomiya.ninebotcast.core;

/** Automatic recording dimensions, independent of the saved virtual-app settings. */
public record CaptureSize(int width, int height) {
    public static final int MAX_EDGE = 1280, MAX_PIXELS = 1280 * 720;
    public CaptureSize {
        if (width < 2 || height < 2 || width > MAX_EDGE || height > MAX_EDGE
                || (width & 1) != 0 || (height & 1) != 0 || (long) width * height > MAX_PIXELS)
            throw new IllegalArgumentException("无效的录屏尺寸");
    }
    public static CaptureSize fit(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("未取得录屏尺寸");
        double scale = Math.min(1, Math.min((double) MAX_EDGE / Math.max(width, height),
                Math.sqrt((double) MAX_PIXELS / ((long) width * height))));
        return new CaptureSize(Math.max(2, (int) (width * scale) & ~1), Math.max(2, (int) (height * scale) & ~1));
    }
}

package dev.ichinomiya.ninebotcast.core;

import java.util.Locale;

/** Opaque RGB colours for the composed frame background. */
public final class BandColor {
    public static int parse(String text) {
        String hex = text == null ? "" : text.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("请输入六位颜色值，例如 #242424");
        return 0xff000000 | Integer.parseInt(hex, 16);
    }
    public static String hex(int color) { return String.format(Locale.ROOT, "#%06X", color & 0x00ffffff); }
    public static void requireOpaque(int color) {
        if ((color >>> 24) != 255) throw new IllegalArgumentException("背景颜色必须是不透明颜色");
    }
    private BandColor() {}
}

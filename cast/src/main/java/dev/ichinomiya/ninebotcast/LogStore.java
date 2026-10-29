package dev.ichinomiya.ninebotcast;

import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class LogStore {
    private static final Object LOCK = new Object();
    private static final int MAX = 300;
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static File file;
    private LogStore() {}

    public static void init(Context context) {
        synchronized (LOCK) {
            file = new File(context.getFilesDir(), "cast-session.log");
            if (LINES.isEmpty() && file.isFile()) {
                try (BufferedReader r = new BufferedReader(new FileReader(file))) {
                    String line; while ((line = r.readLine()) != null) addMemory(line);
                } catch (IOException ignored) {}
            }
        }
    }

    public static void add(String message) {
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(new Date()) + " " + message;
        synchronized (LOCK) {
            addMemory(line);
            if (file != null) try (FileWriter w = new FileWriter(file, true)) { w.write(line); w.write('\n'); } catch (IOException ignored) {}
        }
    }

    private static void addMemory(String line) {
        LINES.addLast(line); while (LINES.size() > MAX) LINES.removeFirst();
    }

    public static String text() { synchronized (LOCK) { StringBuilder b = new StringBuilder(); for (String s : LINES) b.append(s).append('\n'); return b.toString(); } }
    public static void clear() { synchronized (LOCK) { LINES.clear(); if (file != null) try (FileWriter w = new FileWriter(file, false)) {} catch (IOException ignored) {} } }
}

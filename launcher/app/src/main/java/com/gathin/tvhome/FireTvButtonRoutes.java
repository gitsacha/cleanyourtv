package com.gathin.tvhome;

import android.os.Handler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Fire OS keeps its branded remote buttons (Prime Video, Music, a TV maker's app) from accessibility
 * key filters and launches them itself, from the system uid. This matches one ActivityTaskManager
 * START line against the configured fire_tv_button_routes and returns the app to open instead.
 * Off unless routes are configured; HomeRedirectService starts it only with READ_LOGS granted.
 */
final class FireTvButtonRoutes {
    private static final Pattern START = Pattern.compile("(?:^|\\s)START u0 ");
    private static final Pattern SYSTEM_UID = Pattern.compile("\\bfrom uid\\s*1000(?:\\s|$)");
    private static final Pattern LEANBACK_CATEGORY = Pattern.compile(
            "\\bcat=\\[[^\\]]*(?<![\\w.])android\\.intent\\.category\\.LEANBACK_LAUNCHER(?=[\\s\\]])[^\\]]*\\]");

    private final List<String[]> routes = new ArrayList<>();
    private volatile boolean running;
    private Thread thread;
    private Process process;

    /** Each entry is "source|package". Malformed entries are skipped. */
    FireTvButtonRoutes(String[] entries) {
        for (String entry : entries) {
            int bar = entry.lastIndexOf('|');
            if (bar <= 0 || bar == entry.length() - 1) continue;
            routes.add(new String[]{entry.substring(0, bar).trim(), entry.substring(bar + 1).trim()});
        }
    }

    /**
     * Reads only ActivityTaskManager lines from the TV's own log, from now on, and hands each target
     * package to the main thread. Nothing is stored. Needs READ_LOGS.
     */
    synchronized void start(Handler mainHandler, Consumer<String> listener) {
        if (thread != null) return;
        running = true;
        thread = new Thread(() -> readLoop(mainHandler, listener), "FireTvButtons");
        thread.start();
    }

    synchronized void stop() {
        running = false;
        if (process != null) process.destroy();
        if (thread != null) thread.interrupt();
    }

    private void readLoop(Handler mainHandler, Consumer<String> listener) {
        long retryMs = 250;
        while (running) {
            Process reader = null;
            try {
                // Seconds since the epoch: logcat -T then skips everything logged before now.
                String since = String.format(Locale.US, "%.3f", System.currentTimeMillis() / 1000.0);
                synchronized (this) {
                    if (!running) return;
                    reader = new ProcessBuilder("logcat", "-b", "all", "-v", "brief", "-T", since,
                            "ActivityTaskManager:I", "*:S").redirectErrorStream(true).start();
                    process = reader;
                }
                try (BufferedReader lines = new BufferedReader(new InputStreamReader(reader.getInputStream()))) {
                    String line;
                    while (running && (line = lines.readLine()) != null) {
                        String target = match(line);
                        if (target != null) mainHandler.post(() -> { if (running) listener.accept(target); });
                    }
                }
            } catch (IOException ignored) {
                // logcat ended or could not start; start again from the current time.
            } finally {
                if (reader != null) reader.destroy();
                synchronized (this) {
                    if (process == reader) process = null;
                }
            }
            try {
                Thread.sleep(retryMs);
            } catch (InterruptedException e) {
                return;
            }
            retryMs = Math.min(retryMs * 2, 8000);
        }
    }

    boolean isEmpty() {
        return routes.isEmpty();
    }

    /** The package to open for this log line, or null. */
    String match(String line) {
        if (line == null || !START.matcher(line).find() || !SYSTEM_UID.matcher(line).find()) return null;
        for (String[] route : routes) {
            String source = route[0];
            boolean hit = source.contains("://")
                    // The Appstore resolves a button bound to an app or a store page (amzns://apps/android?p=... or ?asin=...).
                    ? field(line, "act=android.intent.action.VIEW")
                        && field(line, "dat=" + source)
                        && field(line, "cmp=com.amazon.venezia/.pdi.AppLaunchActivity")
                    // A button whose app is installed can also start it directly.
                    : field(line, "act=android.intent.action.MAIN")
                        && LEANBACK_CATEGORY.matcher(line).find()
                        && field(line, "cmp=" + source);
            if (hit) return route[1];
        }
        return null;
    }

    /** True when value appears as a whole space- or brace-delimited token. */
    private static boolean field(String line, String value) {
        int at = line.indexOf(value);
        while (at >= 0) {
            int end = at + value.length();
            if ((at == 0 || Character.isWhitespace(line.charAt(at - 1)) || line.charAt(at - 1) == '{')
                    && (end == line.length() || Character.isWhitespace(line.charAt(end)) || line.charAt(end) == '}')) {
                return true;
            }
            at = line.indexOf(value, at + 1);
        }
        return false;
    }
}

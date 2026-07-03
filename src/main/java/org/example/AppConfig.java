package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Central place for filesystem paths and tunable constants.
 * Everything lives under ~/.cs_auto_targets so the app is fully self-contained
 * and safe to run as a long-lived background process on a home PC.
 */
public final class AppConfig {

    private AppConfig() {}

    public static final Path APP_HOME = Paths.get(System.getProperty("user.home"), ".cs_auto_targets");
    public static final Path DB_PATH = APP_HOME.resolve("data.db");
    public static final Path LOG_DIR = APP_HOME.resolve("logs");

    /** Default poll interval (minutes) for auto-adjusting targets. Overridable in Settings. */
    public static final int DEFAULT_TARGET_POLL_INTERVAL_MINUTES = 10;

    /** Default poll interval (minutes) for checking alert thresholds. */
    public static final int DEFAULT_ALERT_POLL_INTERVAL_MINUTES = 5;

    /** Default cooldown (minutes) before a triggered alert can fire again. */
    public static final int DEFAULT_ALERT_COOLDOWN_MINUTES = 60;

    public static final String DMARKET_GAME_ID = "a8db";

    public static void ensureAppHomeExists() {
        try {
            Files.createDirectories(APP_HOME);
            Files.createDirectories(LOG_DIR);
        } catch (IOException e) {
            throw new RuntimeException("Could not create app home directory at " + APP_HOME, e);
        }
    }
}

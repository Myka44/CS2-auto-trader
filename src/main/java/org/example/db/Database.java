package org.example.db;

import org.example.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the single SQLite connection URL and schema bootstrap.
 * SQLite + the sqlite-jdbc driver handles concurrent access fine for a
 * single-process desktop app; we open one short-lived Connection per
 * operation rather than holding a single shared connection across threads.
 */
public final class Database {

    private static final Logger log = LoggerFactory.getLogger(Database.class);

    private static volatile boolean initialized = false;

    private Database() {}

    private static String jdbcUrl() {
        return "jdbc:sqlite:" + AppConfig.DB_PATH.toAbsolutePath();
    }

    /** Opens a new connection. Caller is responsible for closing it (use try-with-resources). */
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
        }
        return conn;
    }

    /** Creates the schema if it doesn't already exist. Safe to call on every startup. */
    public static synchronized void initSchema() {
        if (initialized) {
            return;
        }
        AppConfig.ensureAppHomeExists();
        log.info("Initializing database at {}", AppConfig.DB_PATH);

        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS skin_catalog (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    market_hash_name TEXT NOT NULL UNIQUE,
                    weapon           TEXT,
                    skin_name        TEXT,
                    wear             TEXT,
                    float_min        REAL,
                    float_max        REAL,
                    def_index        INTEGER,
                    paint_index      INTEGER,
                    image_url        TEXT,
                    rarity           TEXT,
                    collection       TEXT
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS targets (
                    id                   INTEGER PRIMARY KEY AUTOINCREMENT,
                    skin_id              INTEGER NOT NULL REFERENCES skin_catalog(id),
                    platform             TEXT NOT NULL,
                    platform_target_id   TEXT,
                    max_price_usd_cents  INTEGER NOT NULL DEFAULT 0,
                    price_modifier_cents INTEGER NOT NULL DEFAULT 1,
                    float_range_min      REAL,
                    float_range_max      REAL,
                    float_part_value     TEXT,
                    quantity             INTEGER NOT NULL DEFAULT 10,
                    auto_adjust          INTEGER NOT NULL DEFAULT 1,
                    active               INTEGER NOT NULL DEFAULT 1,
                    last_price_cents     INTEGER,
                    last_checked_at      TEXT,
                    last_error           TEXT,
                    created_at           TEXT NOT NULL,
                    updated_at           TEXT NOT NULL
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS alerts (
                    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                    skin_id             INTEGER NOT NULL REFERENCES skin_catalog(id),
                    platform            TEXT,
                    threshold_usd_cents INTEGER NOT NULL,
                    direction           TEXT NOT NULL DEFAULT 'AT_OR_BELOW',
                    float_range_min     REAL,
                    float_range_max     REAL,
                    wear_condition      TEXT,
                    cooldown_minutes    INTEGER NOT NULL DEFAULT 60,
                    triggered_at        TEXT,
                    last_seen_price_cents INTEGER,
                    active              INTEGER NOT NULL DEFAULT 1,
                    created_at          TEXT NOT NULL,
                    updated_at          TEXT NOT NULL
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS price_history (
                    id              INTEGER PRIMARY KEY AUTOINCREMENT,
                    skin_id         INTEGER NOT NULL REFERENCES skin_catalog(id),
                    platform        TEXT NOT NULL,
                    price_usd_cents INTEGER,
                    float_value     REAL,
                    recorded_at     TEXT NOT NULL
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS api_config (
                    platform    TEXT PRIMARY KEY,
                    public_key  TEXT,
                    secret_key  TEXT,
                    jwt_token   TEXT,
                    enabled     INTEGER NOT NULL DEFAULT 1
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS app_settings (
                    key   TEXT PRIMARY KEY,
                    value TEXT
                )
            """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_targets_skin ON targets(skin_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_alerts_skin ON alerts(skin_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_price_history_skin ON price_history(skin_id, recorded_at)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_skin_catalog_weapon ON skin_catalog(weapon)");

            initialized = true;
            log.info("Database schema ready.");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }
}

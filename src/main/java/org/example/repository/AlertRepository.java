package org.example.repository;

import org.example.db.Database;
import org.example.model.Alert;
import org.example.model.Platform;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AlertRepository {

    private static final String SELECT_JOIN = """
        SELECT a.*, s.market_hash_name AS skin_market_hash_name
        FROM alerts a
        JOIN skin_catalog s ON s.id = a.skin_id
    """;

    public List<Alert> findAll() {
        List<Alert> result = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + " ORDER BY a.updated_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load alerts", e);
        }
        return result;
    }

    public List<Alert> findAllActive() {
        List<Alert> result = new ArrayList<>();
        String sql = SELECT_JOIN + " WHERE a.active = 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load active alerts", e);
        }
        return result;
    }

    public Optional<Alert> findById(long id) {
        String sql = SELECT_JOIN + " WHERE a.id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load alert " + id, e);
        }
        return Optional.empty();
    }

    public Alert insert(Alert a) {
        String sql = """
            INSERT INTO alerts
                (skin_id, platform, threshold_usd_cents, direction, float_range_min, float_range_max,
                 wear_condition, cooldown_minutes, triggered_at, last_seen_price_cents, active,
                 created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        LocalDateTime now = LocalDateTime.now();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, a, now, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    a.setId(keys.getLong(1));
                }
            }
            a.setCreatedAt(now);
            a.setUpdatedAt(now);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert alert", e);
        }
        return a;
    }

    public void update(Alert a) {
        String sql = """
            UPDATE alerts SET
                skin_id = ?, platform = ?, threshold_usd_cents = ?, direction = ?,
                float_range_min = ?, float_range_max = ?, wear_condition = ?, cooldown_minutes = ?,
                triggered_at = ?, last_seen_price_cents = ?, active = ?, updated_at = ?
            WHERE id = ?
        """;
        LocalDateTime now = LocalDateTime.now();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, a.getSkinId());
            ps.setString(2, a.getPlatform() == null ? null : a.getPlatform().name());
            ps.setInt(3, a.getThresholdUsdCents());
            ps.setString(4, a.getDirection().name());
            setNullableDouble(ps, 5, a.getFloatRangeMin());
            setNullableDouble(ps, 6, a.getFloatRangeMax());
            ps.setString(7, a.getWearCondition());
            ps.setInt(8, a.getCooldownMinutes());
            ps.setString(9, a.getTriggeredAt() == null ? null : a.getTriggeredAt().toString());
            setNullableInt(ps, 10, a.getLastSeenPriceCents());
            ps.setInt(11, a.isActive() ? 1 : 0);
            ps.setString(12, now.toString());
            ps.setLong(13, a.getId());
            ps.executeUpdate();
            a.setUpdatedAt(now);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update alert " + a.getId(), e);
        }
    }

    public void delete(long id) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM alerts WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete alert " + id, e);
        }
    }

    private void bind(PreparedStatement ps, Alert a, LocalDateTime created, LocalDateTime updated) throws SQLException {
        ps.setLong(1, a.getSkinId());
        ps.setString(2, a.getPlatform() == null ? null : a.getPlatform().name());
        ps.setInt(3, a.getThresholdUsdCents());
        ps.setString(4, a.getDirection().name());
        setNullableDouble(ps, 5, a.getFloatRangeMin());
        setNullableDouble(ps, 6, a.getFloatRangeMax());
        ps.setString(7, a.getWearCondition());
        ps.setInt(8, a.getCooldownMinutes());
        ps.setString(9, a.getTriggeredAt() == null ? null : a.getTriggeredAt().toString());
        setNullableInt(ps, 10, a.getLastSeenPriceCents());
        ps.setInt(11, a.isActive() ? 1 : 0);
        ps.setString(12, created.toString());
        ps.setString(13, updated.toString());
    }

    private void setNullableDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) ps.setNull(idx, java.sql.Types.REAL);
        else ps.setDouble(idx, value);
    }

    private void setNullableInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) ps.setNull(idx, java.sql.Types.INTEGER);
        else ps.setInt(idx, value);
    }

    private Alert map(ResultSet rs) throws SQLException {
        Alert a = new Alert();
        a.setId(rs.getLong("id"));
        a.setSkinId(rs.getLong("skin_id"));
        a.setSkinMarketHashName(rs.getString("skin_market_hash_name"));
        String platform = rs.getString("platform");
        a.setPlatform(platform == null ? null : Platform.fromDbValue(platform));
        a.setThresholdUsdCents(rs.getInt("threshold_usd_cents"));
        String dir = rs.getString("direction");
        a.setDirection(dir == null ? Alert.Direction.AT_OR_BELOW : Alert.Direction.valueOf(dir));
        a.setFloatRangeMin(getNullableDouble(rs, "float_range_min"));
        a.setFloatRangeMax(getNullableDouble(rs, "float_range_max"));
        a.setWearCondition(rs.getString("wear_condition"));
        a.setCooldownMinutes(rs.getInt("cooldown_minutes"));
        String triggeredAt = rs.getString("triggered_at");
        if (triggeredAt != null) a.setTriggeredAt(LocalDateTime.parse(triggeredAt));
        a.setLastSeenPriceCents(getNullableInt(rs, "last_seen_price_cents"));
        a.setActive(rs.getInt("active") != 0);
        String created = rs.getString("created_at");
        String updated = rs.getString("updated_at");
        if (created != null) a.setCreatedAt(LocalDateTime.parse(created));
        if (updated != null) a.setUpdatedAt(LocalDateTime.parse(updated));
        return a;
    }

    private Double getNullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    private Integer getNullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}

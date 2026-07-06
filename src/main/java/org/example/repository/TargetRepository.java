package org.example.repository;

import org.example.db.Database;
import org.example.model.Platform;
import org.example.model.Target;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TargetRepository {

    private static final String SELECT_JOIN = """
        SELECT t.*, s.market_hash_name AS skin_market_hash_name
        FROM targets t
        JOIN skin_catalog s ON s.id = t.skin_id
    """;

    public List<Target> findAll() {
        List<Target> result = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_JOIN + " ORDER BY t.updated_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load targets", e);
        }
        return result;
    }

    public List<Target> findAllActiveAutoAdjust() {
        List<Target> result = new ArrayList<>();
        String sql = SELECT_JOIN + " WHERE t.active = 1 AND t.auto_adjust = 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load active auto-adjust targets", e);
        }
        return result;
    }

    public Optional<Target> findById(long id) {
        String sql = SELECT_JOIN + " WHERE t.id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load target " + id, e);
        }
        return Optional.empty();
    }

    public Target insert(Target t) {
        String sql = """
            INSERT INTO targets
                (skin_id, platform, platform_target_id, max_price_usd_cents, price_modifier_cents,
                 float_range_min, float_range_max, float_part_value, quantity, auto_adjust, active,
                 auto_calculate, last_price_cents, last_checked_at, last_error, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        LocalDateTime now = LocalDateTime.now();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindForInsertOrUpdate(ps, t, now, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    t.setId(keys.getLong(1));
                }
            }
            t.setCreatedAt(now);
            t.setUpdatedAt(now);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert target", e);
        }
        return t;
    }

    public void update(Target t) {
        String sql = """
            UPDATE targets SET
                skin_id = ?, platform = ?, platform_target_id = ?, max_price_usd_cents = ?,
                price_modifier_cents = ?, float_range_min = ?, float_range_max = ?, float_part_value = ?,
                quantity = ?, auto_adjust = ?, active = ?, auto_calculate = ?, last_price_cents = ?, last_checked_at = ?,
                last_error = ?, updated_at = ?
            WHERE id = ?
        """;
        LocalDateTime now = LocalDateTime.now();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, t.getSkinId());
            ps.setString(2, t.getPlatform().name());
            ps.setString(3, t.getPlatformTargetId());
            ps.setInt(4, t.getMaxPriceUsdCents());
            ps.setInt(5, t.getPriceModifierCents());
            setNullableDouble(ps, 6, t.getFloatRangeMin());
            setNullableDouble(ps, 7, t.getFloatRangeMax());
            ps.setString(8, t.getFloatPartValue());
            ps.setInt(9, t.getQuantity());
            ps.setInt(10, t.isAutoAdjust() ? 1 : 0);
            ps.setInt(11, t.isActive() ? 1 : 0);
            ps.setInt(12, t.isAutoCalculate() ? 1 : 0);
            setNullableInt(ps, 13, t.getLastPriceCents());
            ps.setString(14, t.getLastCheckedAt());
            ps.setString(15, t.getLastError());
            ps.setString(16, now.toString());
            ps.setLong(17, t.getId());
            ps.executeUpdate();
            t.setUpdatedAt(now);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update target " + t.getId(), e);
        }
    }

    public void delete(long id) {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM targets WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete target " + id, e);
        }
    }

    private void bindForInsertOrUpdate(PreparedStatement ps, Target t, LocalDateTime created, LocalDateTime updated) throws SQLException {
        ps.setLong(1, t.getSkinId());
        ps.setString(2, t.getPlatform().name());
        ps.setString(3, t.getPlatformTargetId());
        ps.setInt(4, t.getMaxPriceUsdCents());
        ps.setInt(5, t.getPriceModifierCents());
        setNullableDouble(ps, 6, t.getFloatRangeMin());
        setNullableDouble(ps, 7, t.getFloatRangeMax());
        ps.setString(8, t.getFloatPartValue());
        ps.setInt(9, t.getQuantity());
        ps.setInt(10, t.isAutoAdjust() ? 1 : 0);
        ps.setInt(11, t.isActive() ? 1 : 0);
        ps.setInt(12, t.isAutoCalculate() ? 1 : 0);
        setNullableInt(ps, 13, t.getLastPriceCents());
        ps.setString(14, t.getLastCheckedAt());
        ps.setString(15, t.getLastError());
        ps.setString(16, created.toString());
        ps.setString(17, updated.toString());
    }

    private void setNullableDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) ps.setNull(idx, java.sql.Types.REAL);
        else ps.setDouble(idx, value);
    }

    private void setNullableInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) ps.setNull(idx, java.sql.Types.INTEGER);
        else ps.setInt(idx, value);
    }

    private Target map(ResultSet rs) throws SQLException {
        Target t = new Target();
        t.setId(rs.getLong("id"));
        t.setSkinId(rs.getLong("skin_id"));
        t.setSkinMarketHashName(rs.getString("skin_market_hash_name"));
        t.setPlatform(Platform.fromDbValue(rs.getString("platform")));
        t.setPlatformTargetId(rs.getString("platform_target_id"));
        t.setMaxPriceUsdCents(rs.getInt("max_price_usd_cents"));
        t.setPriceModifierCents(rs.getInt("price_modifier_cents"));
        t.setFloatRangeMin(getNullableDouble(rs, "float_range_min"));
        t.setFloatRangeMax(getNullableDouble(rs, "float_range_max"));
        t.setFloatPartValue(rs.getString("float_part_value"));
        t.setQuantity(rs.getInt("quantity"));
        t.setAutoAdjust(rs.getInt("auto_adjust") != 0);
        t.setActive(rs.getInt("active") != 0);
        t.setAutoCalculate(rs.getInt("auto_calculate") != 0);
        Integer lastPrice = getNullableInt(rs, "last_price_cents");
        t.setLastPriceCents(lastPrice);
        t.setLastCheckedAt(rs.getString("last_checked_at"));
        t.setLastError(rs.getString("last_error"));
        String created = rs.getString("created_at");
        String updated = rs.getString("updated_at");
        if (created != null) t.setCreatedAt(LocalDateTime.parse(created));
        if (updated != null) t.setUpdatedAt(LocalDateTime.parse(updated));
        return t;
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

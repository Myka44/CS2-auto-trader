package org.example.repository;

import org.example.db.Database;
import org.example.model.Platform;
import org.example.model.PriceSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PriceSnapshotRepository {

    public void insert(PriceSnapshot snapshot) {
        String sql = """
            INSERT INTO price_history (skin_id, platform, price_usd_cents, float_value, recorded_at)
            VALUES (?, ?, ?, ?, ?)
        """;
        LocalDateTime now = LocalDateTime.now();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, snapshot.getSkinId());
            ps.setString(2, snapshot.getPlatform().name());
            if (snapshot.getPriceUsdCents() == null) ps.setNull(3, java.sql.Types.INTEGER);
            else ps.setInt(3, snapshot.getPriceUsdCents());
            if (snapshot.getFloatValue() == null) ps.setNull(4, java.sql.Types.REAL);
            else ps.setDouble(4, snapshot.getFloatValue());
            ps.setString(5, now.toString());
            ps.executeUpdate();
            snapshot.setRecordedAt(now);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert price snapshot", e);
        }
    }

    public List<PriceSnapshot> findRecentForSkin(long skinId, int limit) {
        List<PriceSnapshot> result = new ArrayList<>();
        String sql = "SELECT * FROM price_history WHERE skin_id = ? ORDER BY recorded_at DESC LIMIT ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, skinId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load price history for skin " + skinId, e);
        }
        return result;
    }

    /** Deletes snapshots older than the given number of days. Call periodically to keep the DB small. */
    public int pruneOlderThan(int days) {
        String sql = "DELETE FROM price_history WHERE recorded_at < ?";
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cutoff.toString());
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to prune price history", e);
        }
    }

    private PriceSnapshot map(ResultSet rs) throws SQLException {
        PriceSnapshot s = new PriceSnapshot();
        s.setId(rs.getLong("id"));
        s.setSkinId(rs.getLong("skin_id"));
        s.setPlatform(Platform.fromDbValue(rs.getString("platform")));
        int price = rs.getInt("price_usd_cents");
        s.setPriceUsdCents(rs.wasNull() ? null : price);
        double f = rs.getDouble("float_value");
        s.setFloatValue(rs.wasNull() ? null : f);
        String recordedAt = rs.getString("recorded_at");
        if (recordedAt != null) s.setRecordedAt(LocalDateTime.parse(recordedAt));
        return s;
    }
}

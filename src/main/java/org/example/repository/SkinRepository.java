package org.example.repository;

import org.example.db.Database;
import org.example.model.SkinCatalogEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SkinRepository {

    public long count() {
        String sql = "SELECT COUNT(*) FROM skin_catalog";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count skin_catalog", e);
        }
    }

    public Optional<SkinCatalogEntry> findById(long id) {
        String sql = "SELECT * FROM skin_catalog WHERE id = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load skin " + id, e);
        }
        return Optional.empty();
    }

    public Optional<SkinCatalogEntry> findByMarketHashName(String marketHashName) {
        String sql = "SELECT * FROM skin_catalog WHERE market_hash_name = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, marketHashName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load skin " + marketHashName, e);
        }
        return Optional.empty();
    }

    /** Case-insensitive substring search, capped at limit results, ordered alphabetically. Used for autocomplete. */
    public List<SkinCatalogEntry> search(String query, int limit) {
        List<SkinCatalogEntry> result = new ArrayList<>();
        String sql = "SELECT * FROM skin_catalog WHERE market_hash_name LIKE ? ORDER BY market_hash_name LIMIT ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + query.replace("%", "") + "%");
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search skin_catalog", e);
        }
        return result;
    }

    public List<SkinCatalogEntry> findAll(int limit, int offset) {
        List<SkinCatalogEntry> result = new ArrayList<>();
        String sql = "SELECT * FROM skin_catalog ORDER BY market_hash_name LIMIT ? OFFSET ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load skin_catalog page", e);
        }
        return result;
    }

    public void insertBatch(List<SkinCatalogEntry> entries) {
        String sql = """
            INSERT INTO skin_catalog
                (market_hash_name, weapon, skin_name, wear, float_min, float_max,
                 def_index, paint_index, image_url, rarity, collection)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(market_hash_name) DO NOTHING
        """;
        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int batchCount = 0;
                for (SkinCatalogEntry e : entries) {
                    ps.setString(1, e.getMarketHashName());
                    ps.setString(2, e.getWeapon());
                    ps.setString(3, e.getSkinName());
                    ps.setString(4, e.getWear());
                    setNullableDouble(ps, 5, e.getFloatMin());
                    setNullableDouble(ps, 6, e.getFloatMax());
                    setNullableInt(ps, 7, e.getDefIndex());
                    setNullableInt(ps, 8, e.getPaintIndex());
                    ps.setString(9, e.getImageUrl());
                    ps.setString(10, e.getRarity());
                    ps.setString(11, e.getCollection());
                    ps.addBatch();
                    batchCount++;
                    if (batchCount % 500 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to batch-insert skin_catalog", e);
        }
    }

    private void setNullableDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) ps.setNull(idx, java.sql.Types.REAL);
        else ps.setDouble(idx, value);
    }

    private void setNullableInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) ps.setNull(idx, java.sql.Types.INTEGER);
        else ps.setInt(idx, value);
    }

    private SkinCatalogEntry map(ResultSet rs) throws SQLException {
        SkinCatalogEntry e = new SkinCatalogEntry();
        e.setId(rs.getLong("id"));
        e.setMarketHashName(rs.getString("market_hash_name"));
        e.setWeapon(rs.getString("weapon"));
        e.setSkinName(rs.getString("skin_name"));
        e.setWear(rs.getString("wear"));
        e.setFloatMin(getNullableDouble(rs, "float_min"));
        e.setFloatMax(getNullableDouble(rs, "float_max"));
        e.setDefIndex(getNullableInt(rs, "def_index"));
        e.setPaintIndex(getNullableInt(rs, "paint_index"));
        e.setImageUrl(rs.getString("image_url"));
        e.setRarity(rs.getString("rarity"));
        e.setCollection(rs.getString("collection"));
        return e;
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

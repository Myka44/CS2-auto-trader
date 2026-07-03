package org.example.repository;

import org.example.db.Database;
import org.example.model.ApiConfig;
import org.example.model.Platform;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ApiConfigRepository {

    public Optional<ApiConfig> findByPlatform(Platform platform) {
        String sql = "SELECT platform, public_key, secret_key, jwt_token, enabled FROM api_config WHERE platform = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, platform.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load API config for " + platform, e);
        }
        return Optional.empty();
    }

    public List<ApiConfig> findAll() {
        List<ApiConfig> result = new ArrayList<>();
        String sql = "SELECT platform, public_key, secret_key, jwt_token, enabled FROM api_config";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load API configs", e);
        }
        return result;
    }

    public void upsert(ApiConfig config) {
        String sql = """
            INSERT INTO api_config (platform, public_key, secret_key, jwt_token, enabled)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(platform) DO UPDATE SET
                public_key = excluded.public_key,
                secret_key = excluded.secret_key,
                jwt_token = excluded.jwt_token,
                enabled = excluded.enabled
        """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, config.getPlatform().name());
            ps.setString(2, config.getPublicKey());
            ps.setString(3, config.getSecretKey());
            ps.setString(4, config.getJwtToken());
            ps.setInt(5, config.isEnabled() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save API config for " + config.getPlatform(), e);
        }
    }

    private ApiConfig map(ResultSet rs) throws SQLException {
        ApiConfig c = new ApiConfig();
        c.setPlatform(Platform.fromDbValue(rs.getString("platform")));
        c.setPublicKey(rs.getString("public_key"));
        c.setSecretKey(rs.getString("secret_key"));
        c.setJwtToken(rs.getString("jwt_token"));
        c.setEnabled(rs.getInt("enabled") != 0);
        return c;
    }
}

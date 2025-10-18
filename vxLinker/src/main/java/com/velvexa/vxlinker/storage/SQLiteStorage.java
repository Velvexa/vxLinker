package com.velvexa.vxlinker.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.velvexa.vxlinker.VXLinker;

public class SQLiteStorage implements StorageProvider, Migratable {

    private final VXLinker plugin;
    private Connection connection;

    public SQLiteStorage(VXLinker plugin) {
        this.plugin = plugin;
        connect();
        createTable();
    }

    private void connect() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "vxlinker.db");
            if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            plugin.getLogger().info("✅ SQLite bağlantısı kuruldu: " + dbFile.getName());
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ SQLite bağlantısı kurulamadı: " + e.getMessage());
        }
    }

    private void createTable() {
        String sql =
                "CREATE TABLE IF NOT EXISTS linked_accounts (" +
                " uuid TEXT PRIMARY KEY," +
                " username TEXT NOT NULL," +
                " discord_id TEXT NOT NULL UNIQUE" +
                ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ SQLite tablo oluşturulamadı: " + e.getMessage());
        }
    }

    @Override
    public void setLinkedAccount(UUID uuid, String playerName, String discordId) {
        String sql = "INSERT OR REPLACE INTO linked_accounts (uuid, username, discord_id) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, discordId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Hesap kaydı oluşturulamadı: " + e.getMessage());
        }
    }

    @Override
    public String getDiscordId(UUID uuid) {
        String sql = "SELECT discord_id FROM linked_accounts WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("discord_id");
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Discord ID alınamadı: " + e.getMessage());
        }
        return null;
    }

    @Override
    public UUID getPlayerUUID(String discordId) {
        String sql = "SELECT uuid FROM linked_accounts WHERE discord_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, discordId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return UUID.fromString(rs.getString("uuid"));
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ UUID alınamadı: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void removeLinkedAccount(UUID uuid) {
        String sql = "DELETE FROM linked_accounts WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Bağlantı silinemedi: " + e.getMessage());
        }
    }

    @Override
    public boolean isPlayerLinked(UUID uuid) {
        String sql = "SELECT 1 FROM linked_accounts WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Oyuncu bağlantı kontrolü başarısız: " + e.getMessage());
        }
        return false;
    }

    @Override
    public boolean isDiscordLinked(String discordId) {
        String sql = "SELECT 1 FROM linked_accounts WHERE discord_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, discordId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Discord bağlantı kontrolü başarısız: " + e.getMessage());
        }
        return false;
    }

    @Override
    public Map<UUID, Migratable.LinkedData> loadAllLinkedAccounts() {
        Map<UUID, Migratable.LinkedData> map = new HashMap<>();
        String sql = "SELECT uuid, username, discord_id FROM linked_accounts";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                String username = rs.getString("username");
                String discordId = rs.getString("discord_id");
                map.put(uuid, new Migratable.LinkedData(username, discordId));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Kayıtlar okunamadı (SQLite): " + e.getMessage());
        }
        return map;
    }

    @Override
    public void importLinkedAccounts(Map<UUID, Migratable.LinkedData> accounts) {
        String sql = "INSERT OR REPLACE INTO linked_accounts (uuid, username, discord_id) VALUES (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (Map.Entry<UUID, Migratable.LinkedData> e : accounts.entrySet()) {
                ps.setString(1, e.getKey().toString());
                ps.setString(2, e.getValue().playerName);
                ps.setString(3, e.getValue().discordId);
                ps.addBatch();
            }
            ps.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            try { connection.rollback(); } catch (Exception ignored) {}
            plugin.getLogger().severe("❌ Kayıtlar içe aktarılamadı (SQLite): " + e.getMessage());
        }
    }

    public Set<UUID> getAllLinkedPlayers() {
        Set<UUID> uuids = new HashSet<>();
        String sql = "SELECT uuid FROM linked_accounts";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                uuids.add(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Oyuncu listesi alınamadı: " + e.getMessage());
        }
        return uuids;
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("💾 SQLite bağlantısı kapatıldı.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ SQLite bağlantısı kapatılamadı: " + e.getMessage());
        }
    }
}

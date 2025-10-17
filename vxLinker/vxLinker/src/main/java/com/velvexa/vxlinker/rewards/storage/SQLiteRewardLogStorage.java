package com.velvexa.vxlinker.rewards.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import com.velvexa.vxlinker.VXLinker;



public class SQLiteRewardLogStorage implements RewardLogStorage {

    private final VXLinker plugin;
    private Connection connection;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public SQLiteRewardLogStorage(VXLinker plugin) {
        this.plugin = plugin;
        connect();
        createTable();
    }


    private void connect() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "reward-logs.db");
            if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            plugin.getLogger().info("✅ SQLite ödül log bağlantısı kuruldu.");
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ SQLite bağlantısı kurulamadı: " + e.getMessage());
        }
    }

 
    private void createTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS reward_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                reward_type TEXT NOT NULL,
                details TEXT
            );
        """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ reward_logs tablosu oluşturulamadı: " + e.getMessage());
        }
    }


    @Override
    public void log(UUID player, String rewardType, String details) {
        String sql = "INSERT INTO reward_logs (player_uuid, timestamp, reward_type, details) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, dateFormat.format(new Date()));
            ps.setString(3, rewardType);
            ps.setString(4, details);
            ps.executeUpdate();

            plugin.getLogger().info("🧾 [" + rewardType + "] ödülü SQLite loglandı: " + player);
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ Ödül logu SQLite’a kaydedilemedi: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("💾 SQLite ödül log bağlantısı kapatıldı.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("❌ SQLite bağlantısı kapatılamadı: " + e.getMessage());
        }
    }
}

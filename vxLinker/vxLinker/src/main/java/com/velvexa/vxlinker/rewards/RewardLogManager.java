package com.velvexa.vxlinker.rewards;

import java.util.UUID;

import com.velvexa.vxlinker.VXLinker;
import com.velvexa.vxlinker.rewards.storage.MySQLRewardLogStorage;
import com.velvexa.vxlinker.rewards.storage.RewardLogStorage;
import com.velvexa.vxlinker.rewards.storage.SQLiteRewardLogStorage;
import com.velvexa.vxlinker.rewards.storage.YamlRewardLogStorage;


public class RewardLogManager {

    private final VXLinker plugin;
    private RewardLogStorage logStorage;

    public RewardLogManager(VXLinker plugin) {
        this.plugin = plugin;
        initializeStorage();
    }

  
    private void initializeStorage() {
        String type = plugin.getConfig().getString("rewards.log-storage", "YAML").toUpperCase();

        switch (type) {
            case "MYSQL" -> {
                plugin.getLogger().info("💾 Ödül log depolama: MySQL");
                this.logStorage = new MySQLRewardLogStorage(plugin);
            }
            case "SQLITE" -> {
                plugin.getLogger().info("💾 Ödül log depolama: SQLite");
                this.logStorage = new SQLiteRewardLogStorage(plugin);
            }
            default -> {
                plugin.getLogger().info("💾 Ödül log depolama: YAML (varsayılan)");
                this.logStorage = new YamlRewardLogStorage(plugin);
            }
        }
    }


    public void logReward(UUID player, String rewardType, String details) {
        if (logStorage == null) {
            plugin.getLogger().warning("Ödül log sistemi başlatılamadı! Log atlanıyor.");
            return;
        }
        logStorage.log(player, rewardType, details);
    }


    public void close() {
        if (logStorage != null) {
            logStorage.close();
        }
    }
}

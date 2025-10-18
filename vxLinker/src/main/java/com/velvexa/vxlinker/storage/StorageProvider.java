package com.velvexa.vxlinker.storage;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.configuration.file.YamlConfiguration;

import com.velvexa.vxlinker.VXLinker;

public interface StorageProvider {

    void setLinkedAccount(UUID uuid, String playerName, String discordId);

    String getDiscordId(UUID uuid);

    UUID getPlayerUUID(String discordId);

    void removeLinkedAccount(UUID uuid);

    boolean isPlayerLinked(UUID uuid);

    boolean isDiscordLinked(String discordId);

    void close();

    default Set<UUID> getAllLinkedPlayers() {
        Set<UUID> result = new HashSet<>();
        try {
            File yamlFile = new File(VXLinker.getInstance().getDataFolder(), "linked-accounts.yml");
            if (yamlFile.exists()) {
                YamlConfiguration data = YamlConfiguration.loadConfiguration(yamlFile);
                if (data.contains("linked-accounts")) {
                    for (String key : data.getConfigurationSection("linked-accounts").getKeys(false)) {
                        try {
                            result.add(UUID.fromString(key));
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            VXLinker.getInstance().getLogger().severe("❌ getAllLinkedPlayers hatası: " + e.getMessage());
        }
        return result;
    }

    default String getPlayerName(UUID uuid) {
        try {
            File yamlFile = new File(VXLinker.getInstance().getDataFolder(), "linked-accounts.yml");
            if (yamlFile.exists()) {
                YamlConfiguration data = YamlConfiguration.loadConfiguration(yamlFile);
                return data.getString("linked-accounts." + uuid + ".username", "Bilinmiyor");
            }
        } catch (Exception e) {
            VXLinker.getInstance().getLogger().severe("❌ getPlayerName hatası: " + e.getMessage());
        }
        return "Bilinmiyor";
    }
}

package com.velvexa.vxlinker.utils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.velvexa.vxlinker.VXLinker;


public class MessageUtil {

    private static final Map<String, String> cache = new HashMap<>();
    private static FileConfiguration messages;


    public static void load(VXLinker plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
            plugin.getLogger().info("✅ messages.yml oluşturuldu.");
        }

        messages = YamlConfiguration.loadConfiguration(file);
        cache.clear();

        plugin.getLogger().info("💬 Mesaj dosyası yüklendi (" + file.getName() + ")");
    }

  
    public static String get(String key) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        String value = messages.getString(key);
        if (value == null) {
            value = "&c[HATA] Eksik mesaj anahtarı: " + key;
        }

        String colored = ChatColor.translateAlternateColorCodes('&', value);
        cache.put(key, colored);
        return colored;
    }


    public static String format(String key, Map<String, String> placeholders) {
        String msg = get(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return msg;
    }

  
    public static void reload(VXLinker plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) return;

        try {
            messages.load(file);
            cache.clear();
            plugin.getLogger().info("🔁 messages.yml yeniden yüklendi.");
        } catch (Exception e) {
            plugin.getLogger().severe("❌ messages.yml yeniden yüklenemedi: " + e.getMessage());
        }
    }
}

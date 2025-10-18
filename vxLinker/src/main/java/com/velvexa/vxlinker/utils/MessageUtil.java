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
    private static String activeLanguage = "TR";

    public static void load(VXLinker plugin) {
        String lang = plugin.getConfig().getString("language", "TR").toUpperCase();
        activeLanguage = lang;

        String resourceName = "messages_" + lang + ".yml";
        File langFile = new File(plugin.getDataFolder(), resourceName);

        if (!langFile.exists()) {
            if (plugin.getResource(resourceName) != null) {
                plugin.saveResource(resourceName, false);
                plugin.getLogger().info("✅ " + resourceName + " oluşturuldu.");
            } else {
                plugin.saveResource("messages_TR.yml", false);
                plugin.getLogger().warning("⚠ " + resourceName + " bulunamadı, varsayılan messages_TR.yml kullanılıyor.");
                langFile = new File(plugin.getDataFolder(), "messages_TR.yml");
            }
        }

        messages = YamlConfiguration.loadConfiguration(langFile);
        cache.clear();
        plugin.getLogger().info("💬 Mesaj dosyası yüklendi (" + langFile.getName() + ", Dil: " + lang + ")");
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

    public static String format(String key, Map<String, ?> placeholders) {
        String msg = get(key);
        if (placeholders != null) {
            for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
                msg = msg.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return msg;
    }

    public static void reload(VXLinker plugin) {
        String lang = plugin.getConfig().getString("language", activeLanguage).toUpperCase();
        String resourceName = "messages_" + lang + ".yml";
        File langFile = new File(plugin.getDataFolder(), resourceName);

        if (!langFile.exists()) {
            if (plugin.getResource(resourceName) != null) {
                plugin.saveResource(resourceName, false);
            } else {
                plugin.saveResource("messages_TR.yml", false);
                langFile = new File(plugin.getDataFolder(), "messages_TR.yml");
            }
        }

        try {
            messages = YamlConfiguration.loadConfiguration(langFile);
            cache.clear();
            plugin.getLogger().info("🔁 " + langFile.getName() + " yeniden yüklendi (Dil: " + lang + ")");
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Mesaj dosyası yeniden yüklenemedi: " + e.getMessage());
        }
    }
}

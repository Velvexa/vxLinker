package com.velvexa.vxlinker;

import java.io.File;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.velvexa.vxlinker.commands.LinkCommand;
import com.velvexa.vxlinker.commands.UnlinkCommand;
import com.velvexa.vxlinker.commands.VXLinkerReloadCommand;
import com.velvexa.vxlinker.discord.DiscordBot;
import com.velvexa.vxlinker.managers.LinkManager;
import com.velvexa.vxlinker.managers.RewardManager;
import com.velvexa.vxlinker.rewards.RewardLogManager;
import com.velvexa.vxlinker.storage.Migratable;
import com.velvexa.vxlinker.storage.MySQLStorage;
import com.velvexa.vxlinker.storage.SQLiteStorage;
import com.velvexa.vxlinker.storage.StorageProvider;
import com.velvexa.vxlinker.storage.YamlStorage;
import com.velvexa.vxlinker.utils.MessageUtil;


public class VXLinker extends JavaPlugin {

    private static VXLinker instance;
    private LinkManager linkManager;
    private StorageProvider storageProvider;
    private DiscordBot discordBot;
    private RewardManager rewardManager;
    private RewardLogManager rewardLogManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        FileConfiguration config = getConfig();

 
        MessageUtil.load(this);

        int expire = config.getInt("link.expire-seconds", 300);
        linkManager = new LinkManager(expire);

   
        String newType = config.getString("storage.type", "YAML").toUpperCase();
        String lastType = config.getString("storage.last-storage", "YAML").toUpperCase();

   
        storageProvider = createStorage(newType);

 
        if (!newType.equalsIgnoreCase(lastType)) {
            getLogger().warning("⚠ Depolama tipi değişti! (" + lastType + " → " + newType + ")");
            migrateStorage(lastType, newType);
            config.set("storage.last-storage", newType);
            saveConfig();
        }

  
        rewardLogManager = new RewardLogManager(this);
        rewardManager = new RewardManager(this);
        rewardManager.start();

    
        registerCommands();

    
        initializeDiscordBot(config);

        getLogger().info("✅ vxLinker başarıyla etkinleştirildi!");
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.shutdown();
            getLogger().info("🔴 Discord bot kapatıldı.");
        }

        if (rewardManager != null) {
            rewardManager.stop();
            getLogger().info("🎁 Ödül sistemi kapatıldı.");
        }

        if (rewardLogManager != null) {
            rewardLogManager.close();
            getLogger().info("🧾 Ödül log sistemi kapatıldı.");
        }

        if (storageProvider != null) {
            storageProvider.close();
            getLogger().info("💾 Veri bağlantısı kapatıldı.");
        }

        getLogger().info("vxLinker devre dışı bırakıldı.");
    }

    
    private StorageProvider createStorage(String type) {
        switch (type.toUpperCase()) {
            case "MYSQL":
                getLogger().info("💾 Depolama yöntemi: MySQL");
                return new MySQLStorage(this);
            case "SQLITE":
                getLogger().info("💾 Depolama yöntemi: SQLite");
                return new SQLiteStorage(this);
            default:
                getLogger().info("💾 Depolama yöntemi: YAML");
                return new YamlStorage(this);
        }
    }

    
    private void migrateStorage(String oldType, String newType) {
        try {
            StorageProvider oldStorage = createStorage(oldType);

            if (!(oldStorage instanceof Migratable) || !(storageProvider instanceof Migratable)) {
                getLogger().warning("❌ Bu depolama türü migration desteklemiyor, veri taşıma atlandı.");
                return;
            }

            Migratable from = (Migratable) oldStorage;
            Migratable to = (Migratable) storageProvider;

            Map<UUID, Migratable.LinkedData> data = from.loadAllLinkedAccounts();
            if (data.isEmpty()) {
                getLogger().info("ℹ Taşınacak kayıt bulunamadı (" + oldType + ")");
                oldStorage.close();
                return;
            }

            to.importLinkedAccounts(data);
            oldStorage.close();

      
            File yamlFile = new File(getDataFolder(), "linked-accounts.yml");
            if (yamlFile.exists()) {
                File backup = new File(getDataFolder(), "backup-" + oldType.toLowerCase() + ".bak");
                yamlFile.renameTo(backup);
                getLogger().info(" Eski YAML verisi yedeklendi: " + backup.getName());
            }

            getLogger().info("✅ " + data.size() + " kayıt " + oldType + " → " + newType + " sistemine taşındı.");

        } catch (Exception e) {
            getLogger().severe("❌ Veri taşıma hatası: " + e.getMessage());
            e.printStackTrace();
        }
    }

    
    private void registerCommands() {
        if (getCommand("hesapesle") != null) {
            getCommand("hesapesle").setExecutor(new LinkCommand(linkManager));
        } else {
            getLogger().severe("❌ Komut 'hesapesle' plugin.yml içinde bulunamadı!");
        }

        if (getCommand("hesapkaldir") != null) {
            getCommand("hesapkaldir").setExecutor(new UnlinkCommand());
        } else {
            getLogger().warning("⚠ Komut 'hesapkaldir' plugin.yml içinde tanımlı değil, atlanıyor.");
        }

        if (getCommand("vxlinkerreload") != null) {
            getCommand("vxlinkerreload").setExecutor(new VXLinkerReloadCommand());
        } else {
            getLogger().warning("⚠ Komut 'vxlinkerreload' plugin.yml içinde tanımlı değil, atlanıyor.");
        }
    }

   
    private void initializeDiscordBot(FileConfiguration config) {
        if (!config.getBoolean("discord.enabled", false)) {
            getLogger().info("🔕 Discord bağlantısı devre dışı bırakılmış.");
            return;
        }

        String token = config.getString("discord.bot-token");
        String status = config.getString("discord.status", "Minecraft ↔ Discord Linker");

        if (token == null || token.isEmpty() || token.equalsIgnoreCase("BURAYA_DISCORD_BOT_TOKENINI_YAZ")) {
            getLogger().warning("⚠ Discord bot tokeni tanımlanmamış! Bot başlatılmadı.");
            return;
        }

        try {
            discordBot = new DiscordBot(this, token, status);
            getLogger().info("🤖 Discord botu başarıyla başlatıldı.");
        } catch (Exception e) {
            getLogger().severe("❌ Discord botu başlatılamadı: " + e.getMessage());
        }
    }

    

    public static VXLinker getInstance() {
        return instance;
    }

    public LinkManager getLinkManager() {
        return linkManager;
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }

    public DiscordBot getDiscordBot() {
        return discordBot;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public RewardLogManager getRewardLogManager() {
        return rewardLogManager;
    }
}

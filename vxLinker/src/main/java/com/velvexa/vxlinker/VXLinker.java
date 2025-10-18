package com.velvexa.vxlinker;

import java.io.File;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import com.velvexa.vxlinker.commands.LinkCommand;
import com.velvexa.vxlinker.commands.UnlinkCommand;
import com.velvexa.vxlinker.commands.VXLinkerInfoCommand;
import com.velvexa.vxlinker.commands.VXLinkerReloadCommand;
import com.velvexa.vxlinker.commands.VXReverifyCommand;
import com.velvexa.vxlinker.discord.DiscordBot;
import com.velvexa.vxlinker.managers.LinkManager;
import com.velvexa.vxlinker.managers.RewardManager;
import com.velvexa.vxlinker.rewards.RewardLogManager;
import com.velvexa.vxlinker.storage.Migratable;
import com.velvexa.vxlinker.storage.MySQLStorage;
import com.velvexa.vxlinker.storage.SQLiteStorage;
import com.velvexa.vxlinker.storage.StorageProvider;
import com.velvexa.vxlinker.storage.YamlStorage;
import com.velvexa.vxlinker.tasks.ReverifyTask;
import com.velvexa.vxlinker.utils.MessageUtil;
import com.velvexa.vxlinker.utils.UpdateChecker;

public class VXLinker extends JavaPlugin {

    private static VXLinker instance;
    private LinkManager linkManager;
    private StorageProvider storageProvider;
    private DiscordBot discordBot;
    private RewardManager rewardManager;
    private RewardLogManager rewardLogManager;
    private ReverifyTask reverifyTask;

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
            getLogger().warning(MessageUtil.get("storage.type-changed")
                    .replace("{old}", lastType)
                    .replace("{new}", newType));
            migrateStorage(lastType, newType);
            config.set("storage.last-storage", newType);
            saveConfig();
        }

        rewardLogManager = new RewardLogManager(this);
        rewardManager = new RewardManager(this);
        rewardManager.start();

        registerCommands();

        initializeDiscordBot(config);

        if (config.getBoolean("reverify.enabled", false)) {
            reverifyTask = new ReverifyTask(this);
            reverifyTask.start();
            getLogger().info("♻ Akıllı Yeniden Doğrulama sistemi etkinleştirildi.");
        }

        new UpdateChecker(this).checkForUpdates();

        getLogger().info(MessageUtil.get("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.shutdown();
            getLogger().info(MessageUtil.get("discord.bot-stopped"));
        }

        if (rewardManager != null) {
            rewardManager.stop();
            getLogger().info(MessageUtil.get("rewards.system-stopped"));
        }

        if (rewardLogManager != null) {
            rewardLogManager.close();
            getLogger().info(MessageUtil.get("rewards.log-stopped"));
        }

        if (reverifyTask != null) {
            reverifyTask.stop();
            getLogger().info("♻ Akıllı Yeniden Doğrulama sistemi durduruldu.");
        }

        if (storageProvider != null) {
            storageProvider.close();
            getLogger().info(MessageUtil.get("storage.closed"));
        }

        getLogger().info(MessageUtil.get("plugin.disabled"));
    }

    private StorageProvider createStorage(String type) {
        switch (type.toUpperCase()) {
            case "MYSQL":
                getLogger().info(MessageUtil.get("storage.mysql"));
                return new MySQLStorage(this);
            case "SQLITE":
                getLogger().info(MessageUtil.get("storage.sqlite"));
                return new SQLiteStorage(this);
            default:
                getLogger().info(MessageUtil.get("storage.yaml"));
                return new YamlStorage(this);
        }
    }

    private void migrateStorage(String oldType, String newType) {
        try {
            StorageProvider oldStorage = createStorage(oldType);

            if (!(oldStorage instanceof Migratable) || !(storageProvider instanceof Migratable)) {
                getLogger().warning(MessageUtil.get("storage.migrate-unsupported"));
                return;
            }

            Migratable from = (Migratable) oldStorage;
            Migratable to = (Migratable) storageProvider;

            Map<UUID, Migratable.LinkedData> data = from.loadAllLinkedAccounts();
            if (data.isEmpty()) {
                getLogger().info(MessageUtil.get("storage.migrate-empty").replace("{type}", oldType));
                oldStorage.close();
                return;
            }

            to.importLinkedAccounts(data);
            oldStorage.close();

            File yamlFile = new File(getDataFolder(), "linked-accounts.yml");
            if (yamlFile.exists()) {
                File backup = new File(getDataFolder(), "backup-" + oldType.toLowerCase() + ".bak");
                yamlFile.renameTo(backup);
                getLogger().info(MessageUtil.get("storage.backup-created").replace("{file}", backup.getName()));
            }

            getLogger().info(MessageUtil.get("storage.migrate-success")
                    .replace("{count}", String.valueOf(data.size()))
                    .replace("{old}", oldType)
                    .replace("{new}", newType));

        } catch (Exception e) {
            getLogger().severe(MessageUtil.get("storage.migrate-fail").replace("{error}", e.getMessage()));
            e.printStackTrace();
        }
    }

    private void registerCommands() {
        if (getCommand("hesapesle") != null) {
            getCommand("hesapesle").setExecutor(new LinkCommand(linkManager));
        } else {
            getLogger().severe(MessageUtil.get("command.missing").replace("{cmd}", "hesapesle"));
        }

        if (getCommand("hesapkaldir") != null) {
            getCommand("hesapkaldir").setExecutor(new UnlinkCommand());
        } else {
            getLogger().warning(MessageUtil.get("command.undefined").replace("{cmd}", "hesapkaldir"));
        }

        if (getCommand("vxlinkerreload") != null) {
            getCommand("vxlinkerreload").setExecutor(new VXLinkerReloadCommand());
        } else {
            getLogger().warning(MessageUtil.get("command.undefined").replace("{cmd}", "vxlinkerreload"));
        }

        if (getCommand("vxreverify") != null) {
            getCommand("vxreverify").setExecutor(new VXReverifyCommand());
        } else {
            getLogger().warning(MessageUtil.get("command.undefined").replace("{cmd}", "vxreverify"));
        }

        if (getCommand("vxlinkerinfo") != null) {
            getCommand("vxlinkerinfo").setExecutor(new VXLinkerInfoCommand());
        } else {
            getLogger().warning(MessageUtil.get("command.undefined").replace("{cmd}", "vxlinkerinfo"));
        }
    }

    private void initializeDiscordBot(FileConfiguration config) {
        if (!config.getBoolean("discord.enabled", false)) {
            getLogger().info(MessageUtil.get("discord.disabled"));
            return;
        }

        String token = config.getString("discord.bot-token");
        String status = config.getString("discord.status", "Minecraft ↔ Discord Linker");

        if (token == null || token.isEmpty() || token.equalsIgnoreCase("BURAYA_DISCORD_BOT_TOKENINI_YAZ")) {
            getLogger().warning(MessageUtil.get("discord.no-token"));
            return;
        }

        try {
            discordBot = new DiscordBot(this, token, status);
            getLogger().info(MessageUtil.get("discord.started"));
        } catch (Exception e) {
            getLogger().severe(MessageUtil.get("discord.failed").replace("{error}", e.getMessage()));
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

    public ReverifyTask getReverifyTask() {
        return reverifyTask;
    }
}

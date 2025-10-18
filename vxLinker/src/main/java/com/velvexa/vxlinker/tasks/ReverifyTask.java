package com.velvexa.vxlinker.tasks;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.velvexa.vxlinker.VXLinker;
import com.velvexa.vxlinker.storage.StorageProvider;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public class ReverifyTask implements Runnable {

    private final VXLinker plugin;
    private int taskId = -1;

    public ReverifyTask(VXLinker plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long interval = plugin.getConfig().getLong("link.reverify.interval-hours", 6);
        if (interval <= 0) interval = 6;
        long ticks = interval * 60 * 60 * 20;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this, ticks, ticks);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    @Override
    public void run() {
        runReverifyCheck();
    }

    private void runReverifyCheck() {
        try {
            String action = plugin.getConfig().getString("link.reverify.action", "unlink");
            String guildId = plugin.getConfig().getString("discord.guild-id");
            String verifiedRoleId = plugin.getConfig().getString("discord.role-id-verified");

            if (guildId == null || guildId.isEmpty()) {
                plugin.getLogger().warning("❌ Discord sunucu ID’si bulunamadı, doğrulama atlandı.");
                return;
            }

            if (plugin.getDiscordBot() == null || plugin.getDiscordBot().getJda() == null) {
                plugin.getLogger().warning("❌ Discord bot aktif değil, doğrulama kontrolü yapılmadı.");
                return;
            }

            Guild guild = plugin.getDiscordBot().getJda().getGuildById(guildId);
            if (guild == null) {
                plugin.getLogger().warning("⚠ Belirtilen Discord sunucusu bulunamadı: " + guildId);
                return;
            }

            StorageProvider storage = plugin.getStorageProvider();
            Set<UUID> players = storage.getAllLinkedPlayers();
            int checked = 0;
            int unlinked = 0;

            for (UUID uuid : players) {
                String discordId = storage.getDiscordId(uuid);
                if (discordId == null) continue;

                Member member = guild.retrieveMemberById(discordId).onErrorMap(err -> null).complete();
                if (member == null || (verifiedRoleId != null && !verifiedRoleId.isEmpty() && member.getRoles().stream().noneMatch(r -> r.getId().equals(verifiedRoleId)))) {
                    if (action.equalsIgnoreCase("unlink")) {
                        storage.removeLinkedAccount(uuid);
                        unlinked++;
                        Player player = Bukkit.getPlayer(uuid);
                        if (player != null) {
                            player.sendMessage("§cDiscord bağlantın otomatik olarak kaldırıldı (Rol kaybı veya sunucudan ayrılma).");
                        }
                    } else if (action.equalsIgnoreCase("notify")) {
                        plugin.getLogger().warning("⚠ " + storage.getPlayerName(uuid) + " adlı oyuncu doğrulama rolünü kaybetmiş veya sunucuda değil.");
                    }
                }
                checked++;
            }

            plugin.getLogger().info("♻ ReVerify tamamlandı → Kontrol edilen: " + checked + ", kaldırılan: " + unlinked);
        } catch (Exception e) {
            plugin.getLogger().severe("❌ ReVerify hatası: " + e.getMessage());
        }
    }

    public void executeNow() {
        try {
            runReverifyCheck();
        } catch (Exception e) {
            plugin.getLogger().severe("❌ Manuel yeniden doğrulama hatası: " + e.getMessage());
        }
    }
}

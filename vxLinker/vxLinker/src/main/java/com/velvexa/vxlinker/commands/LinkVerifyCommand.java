package com.velvexa.vxlinker.commands;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.velvexa.vxlinker.VXLinker;
import com.velvexa.vxlinker.managers.LinkManager;
import com.velvexa.vxlinker.managers.RewardManager;
import com.velvexa.vxlinker.rewards.RewardLogManager;
import com.velvexa.vxlinker.storage.StorageProvider;
import com.velvexa.vxlinker.utils.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;


public class LinkVerifyCommand extends ListenerAdapter {

    private final VXLinker plugin;

    public LinkVerifyCommand(VXLinker plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("hesapesle")) return;

        String code = event.getOption("kod") != null ? event.getOption("kod").getAsString().trim() : null;
        if (code == null || code.isEmpty()) {
            sendEmbed(event,
                    MessageUtil.get("discord.code-missing-title"),
                    MessageUtil.get("discord.code-missing-desc"),
                    Color.RED);
            return;
        }

        LinkManager linkManager = plugin.getLinkManager();
        StorageProvider storage = plugin.getStorageProvider();


        if (storage.isDiscordLinked(event.getUser().getId())) {
            sendEmbed(event,
                    MessageUtil.get("discord.already-linked-title"),
                    MessageUtil.get("discord.already-linked-desc"),
                    Color.RED);
            return;
        }

        if (!linkManager.isValidCode(code)) {
            sendEmbed(event,
                    MessageUtil.get("discord.invalid-title"),
                    MessageUtil.get("discord.invalid-desc"),
                    Color.RED);
            return;
        }

        UUID uuid = linkManager.getPlayerByCode(code);
        Player player = Bukkit.getPlayer(uuid);


        if (storage.isPlayerLinked(uuid)) {
            sendEmbed(event,
                    MessageUtil.get("discord.player-already-linked-title"),
                    MessageUtil.get("discord.player-already-linked-desc"),
                    Color.RED);
            linkManager.removeCode(player);
            return;
        }


        if (player == null || !player.isOnline()) {
            sendEmbed(event,
                    MessageUtil.get("discord.offline-title"),
                    MessageUtil.get("discord.offline-desc"),
                    new Color(255, 204, 0));
            return;
        }

        String playerName = player.getName();

   
        storage.setLinkedAccount(player.getUniqueId(), playerName, event.getUser().getId());
        linkManager.removeCode(player);

  
        player.sendMessage(MessageUtil.format("link.success", Map.of("player", playerName)));

 
        applyVerifiedRole(event);


        sendEmbed(event,
                MessageUtil.format("discord.success-title", Map.of("player", playerName)),
                MessageUtil.format("discord.success-desc", Map.of("player", playerName)),
                Color.GREEN);


        RewardManager rewardManager = plugin.getRewardManager();
        RewardLogManager logManager = plugin.getRewardLogManager();

        if (rewardManager != null) {
            rewardManager.grantFirstLink(player);


            if (logManager != null) {
                logManager.logReward(
                        player.getUniqueId(),
                        "first-link",
                        "Oyuncu " + playerName + " başarıyla Discord hesabını eşleştirdi ve ilk ödülünü aldı."
                );
            }
        }

        plugin.getLogger().info("✅ Oyuncu " + playerName + " hesabını Discord ile eşleştirdi.");
    }


    private void applyVerifiedRole(SlashCommandInteractionEvent event) {
        String guildId = plugin.getConfig().getString("discord.guild-id");
        String roleId = plugin.getConfig().getString("discord.role-id-verified");

        if (guildId == null || roleId == null || guildId.isEmpty() || roleId.isEmpty()) {
            plugin.getLogger().warning("Discord role-id-verified veya guild-id ayarlanmamış, rol atlanıyor.");
            return;
        }

        Guild guild = event.getJDA().getGuildById(guildId);
        if (guild == null) {
            plugin.getLogger().warning("Guild bulunamadı! Guild ID hatalı olabilir: " + guildId);
            return;
        }

        Role role = guild.getRoleById(roleId);
        if (role == null) {
            plugin.getLogger().warning("Rol bulunamadı! Role ID hatalı olabilir: " + roleId);
            return;
        }

        Member member = event.getMember();
        if (member == null) member = guild.retrieveMemberById(event.getUser().getId()).complete();
        if (member == null) return;

        final Member finalMember = member;
        guild.addRoleToMember(finalMember, role).queue(
                success -> plugin.getLogger().info("✅ " + finalMember.getEffectiveName() + " kullanıcısına doğrulama rolü verildi."),
                error -> plugin.getLogger().warning("❌ Rol atama başarısız: " + error.getMessage())
        );
    }


    private void sendEmbed(SlashCommandInteractionEvent event, String title, String desc, Color color) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(desc)
                .setColor(color)
                .setFooter("vxLinker • Minecraft ↔ Discord")
                .setTimestamp(java.time.Instant.now());
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}

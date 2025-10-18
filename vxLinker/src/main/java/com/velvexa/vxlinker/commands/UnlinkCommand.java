package com.velvexa.vxlinker.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.velvexa.vxlinker.VXLinker;
import com.velvexa.vxlinker.storage.StorageProvider;
import com.velvexa.vxlinker.utils.MessageUtil;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public class UnlinkCommand implements CommandExecutor {

    private final VXLinker plugin;
    private final StorageProvider storage;

    public UnlinkCommand() {
        this.plugin = VXLinker.getInstance();
        this.storage = plugin.getStorageProvider();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtil.get("link.not-player"));
            return true;
        }

        Player player = (Player) sender;

        if (!storage.isPlayerLinked(player.getUniqueId())) {
            player.sendMessage(MessageUtil.get("unlink.not-linked"));
            return true;
        }

        String discordId = storage.getDiscordId(player.getUniqueId());
        if (discordId == null || discordId.isEmpty()) {
            player.sendMessage(MessageUtil.get("unlink.error"));
            return true;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            storage.removeLinkedAccount(player.getUniqueId());
            player.sendMessage(MessageUtil.get("unlink.success"));
            plugin.getLogger().info(MessageUtil.format("unlink.console-success", java.util.Map.of("player", player.getName())));
        });

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> removeVerifiedRole(discordId));

        return true;
    }

    private void removeVerifiedRole(String discordId) {
        try {
            if (plugin.getDiscordBot() == null || plugin.getDiscordBot().getJda() == null) {
                plugin.getLogger().warning(MessageUtil.get("discord.bot-disabled"));
                return;
            }

            String guildId = plugin.getConfig().getString("discord.guild-id");
            String roleId = plugin.getConfig().getString("discord.role-id-verified");

            if (guildId == null || roleId == null || guildId.isEmpty() || roleId.isEmpty()) {
                plugin.getLogger().warning(MessageUtil.get("discord.role-missing"));
                return;
            }

            Guild guild = plugin.getDiscordBot().getJda().getGuildById(guildId);
            if (guild == null) {
                plugin.getLogger().warning(MessageUtil.get("discord.guild-missing"));
                return;
            }

            Role role = guild.getRoleById(roleId);
            if (role == null) {
                plugin.getLogger().warning(MessageUtil.get("discord.role-missing"));
                return;
            }

            Member member = guild.retrieveMemberById(discordId).complete();
            if (member == null) {
                plugin.getLogger().warning(MessageUtil.format("discord.user-not-found", java.util.Map.of("id", discordId)));
                return;
            }

            guild.removeRoleFromMember(member, role).queue(
                    success -> plugin.getLogger().info(MessageUtil.get("discord.unlinked")),
                    error -> plugin.getLogger().warning(MessageUtil.format("discord.role-fail",
                            java.util.Map.of("error", error.getMessage())))
            );

        } catch (Exception e) {
            plugin.getLogger().severe(MessageUtil.format("discord.role-remove-error", java.util.Map.of("error", e.getMessage())));
        }
    }
}

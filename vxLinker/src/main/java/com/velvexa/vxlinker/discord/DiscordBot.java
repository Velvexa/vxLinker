package com.velvexa.vxlinker.discord;

import java.util.EnumSet;

import com.velvexa.vxlinker.VXLinker;
import com.velvexa.vxlinker.commands.LinkVerifyCommand;
import com.velvexa.vxlinker.utils.MessageUtil;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class DiscordBot {

    private final VXLinker plugin;
    private JDA jda;

    public DiscordBot(VXLinker plugin, String token, String status) {
        this.plugin = plugin;
        try {
            EnumSet<GatewayIntent> intents = EnumSet.of(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MESSAGE_REACTIONS
            );

            jda = JDABuilder.createDefault(token)
                    .enableIntents(intents)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setActivity(Activity.playing(status))
                    .addEventListeners(new LinkVerifyCommand(plugin))
                    .build();

            jda.awaitReady();

            jda.updateCommands().addCommands(
                    Commands.slash("hesapesle", MessageUtil.get("discord.slash-description"))
                            .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING,
                                    "kod",
                                    MessageUtil.get("discord.slash-option-description"),
                                    true)
            ).queue(
                    success -> plugin.getLogger().info(MessageUtil.get("discord.slash-registered")),
                    failure -> plugin.getLogger().severe(MessageUtil.format("discord.slash-failed", java.util.Map.of("error", failure.getMessage())))
            );

            plugin.getLogger().info(MessageUtil.get("discord.started"));

        } catch (Exception e) {
            plugin.getLogger().severe(MessageUtil.format("discord.failed", java.util.Map.of("error", e.getMessage())));
            e.printStackTrace();
        }
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdownNow();
            plugin.getLogger().info(MessageUtil.get("discord.bot-stopped"));
        }
    }

    public JDA getJda() {
        return jda;
    }

    public boolean isMemberInGuild(String discordId) {
        if (jda == null) return false;
        try {
            Guild guild = jda.getGuildById(plugin.getConfig().getString("discord.guild-id"));
            if (guild == null) return false;
            Member member = guild.retrieveMemberById(discordId).complete();
            return member != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasVerifiedRole(String discordId) {
        if (jda == null) return false;
        try {
            Guild guild = jda.getGuildById(plugin.getConfig().getString("discord.guild-id"));
            if (guild == null) return false;
            Member member = guild.retrieveMemberById(discordId).complete();
            if (member == null) return false;
            String verifiedRoleId = plugin.getConfig().getString("discord.role-id-verified");
            return member.getRoles().stream().anyMatch(role -> role.getId().equals(verifiedRoleId));
        } catch (Exception e) {
            return false;
        }
    }
}

package com.velvexa.vxlinker.discord;

import java.util.EnumSet;

import com.velvexa.vxlinker.VXLinker;
import com.velvexa.vxlinker.commands.LinkVerifyCommand;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
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
                    Commands.slash("hesapesle", "Minecraft hesabını Discord hesabınla eşleştir.")
                            .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING,
                                    "kod",
                                    "Minecraft'tan aldığın eşleşme kodu",
                                    true)
            ).queue(
                    success -> plugin.getLogger().info("✅ /hesapesle komutu Discord API’ye kaydedildi!"),
                    failure -> plugin.getLogger().severe("❌ Komut kaydı başarısız: " + failure.getMessage())
            );

            plugin.getLogger().info("✅ Discord bot başarıyla başlatıldı!");

        } catch (Exception e) {
            plugin.getLogger().severe("❌ Discord bot başlatılamadı: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdownNow();
            plugin.getLogger().info("🔴 Discord bot kapatıldı.");
        }
    }

    public JDA getJda() {
        return jda;
    }
}

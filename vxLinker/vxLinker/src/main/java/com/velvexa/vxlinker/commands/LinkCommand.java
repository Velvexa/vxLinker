package com.velvexa.vxlinker.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.velvexa.vxlinker.VXLinker;
import com.velvexa.vxlinker.managers.LinkManager;
import com.velvexa.vxlinker.storage.StorageProvider;
import com.velvexa.vxlinker.utils.MessageUtil;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;


public class LinkCommand implements CommandExecutor {

    private final LinkManager linkManager;
    private final VXLinker plugin;
    private final StorageProvider storage;

    public LinkCommand(LinkManager linkManager) {
        this.linkManager = linkManager;
        this.plugin = VXLinker.getInstance();
        this.storage = plugin.getStorageProvider();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

   
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', MessageUtil.get("link.not-player")));
            return true;
        }

        Player player = (Player) sender;


        if (storage.isPlayerLinked(player.getUniqueId())) {
            player.sendMessage(MessageUtil.get("link.already-linked"));
            return true;
        }


        String existing = linkManager.getCode(player);
        if (existing != null) {
            sendStyledCodeMessage(player, existing, true);
            return true;
        }

     
        String newCode = linkManager.generateCode(player);
        sendStyledCodeMessage(player, newCode, false);

        return true;
    }


    private void sendStyledCodeMessage(Player player, String code, boolean existing) {

        player.sendMessage("");
        player.sendMessage(MessageUtil.get("link.header"));
        player.sendMessage(MessageUtil.get("link.title"));

        if (existing) {
            player.sendMessage(MessageUtil.get("link.code-active"));
        } else {
            player.sendMessage(MessageUtil.get("link.code-new"));
        }

  
        TextComponent clickableCode = new TextComponent("「 " + code + " 」");
        clickableCode.setColor(ChatColor.AQUA);
        clickableCode.setBold(true);
        clickableCode.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, code));
        clickableCode.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(MessageUtil.get("link.copied-hover")).create()));

        player.spigot().sendMessage(clickableCode);

        player.sendMessage(MessageUtil.get("link.usage").replace("<kod>", code));
        player.sendMessage(MessageUtil.get("link.header"));
        player.sendMessage("");
    }
}

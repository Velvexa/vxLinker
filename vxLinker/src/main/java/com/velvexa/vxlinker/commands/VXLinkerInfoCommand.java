package com.velvexa.vxlinker.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.velvexa.vxlinker.VXLinker;

public class VXLinkerInfoCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        VXLinker plugin = VXLinker.getInstance();
        sender.sendMessage("§6§m------------------------------");
        sender.sendMessage("§e§lvxLinker §7v" + plugin.getDescription().getVersion());
        sender.sendMessage("§7Depolama: §f" + plugin.getConfig().getString("storage.type"));
        sender.sendMessage("§7Dil: §f" + plugin.getConfig().getString("language"));
        sender.sendMessage("§7ReVerify: §f" + (plugin.getReverifyTask() != null ? "§aAktif" : "§cPasif"));
        sender.sendMessage("§7Discord Botu: §f" + (plugin.getDiscordBot() != null ? "§aBağlı" : "§cKapalı"));
        sender.sendMessage("§6§m------------------------------");
        return true;
    }
}

package com.velvexa.vxlinker.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.velvexa.vxlinker.VXLinker;
import com.velvexa.vxlinker.utils.MessageUtil;


public class VXLinkerReloadCommand implements CommandExecutor {

    private final VXLinker plugin;

    public VXLinkerReloadCommand() {
        this.plugin = VXLinker.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("vxlinker.admin")) {
            sender.sendMessage(MessageUtil.get("system.no-permission"));
            return true;
        }

        long start = System.currentTimeMillis();


        plugin.reloadConfig();

 
        MessageUtil.load(plugin);

        long took = System.currentTimeMillis() - start;
        sender.sendMessage(MessageUtil.get("system.reload") + " &7(" + took + "ms)");

        plugin.getLogger().info("✅ vxLinker yapılandırmaları yeniden yüklendi. (" + took + "ms)");

        return true;
    }
}

package com.velvexa.vxlinker.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import com.velvexa.vxlinker.VXLinker;
import com.velvexa.vxlinker.tasks.ReverifyTask;

public class VXReverifyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            ReverifyTask task = VXLinker.getInstance().getReverifyTask();
            if (task != null) {
                task.executeNow();
                sender.sendMessage("§a♻ Yeniden doğrulama süreci manuel olarak başlatıldı.");
            } else {
                ReverifyTask newTask = new ReverifyTask(VXLinker.getInstance());
                newTask.executeNow();
                sender.sendMessage("§a♻ Yeniden doğrulama süreci oluşturuldu ve başlatıldı.");
            }
        } catch (Exception e) {
            sender.sendMessage("§c❌ Yeniden doğrulama başlatılamadı: " + e.getMessage());
        }
        return true;
    }
}

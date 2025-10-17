package com.velvexa.vxlinker.managers;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import com.velvexa.vxlinker.VXLinker;
import com.velvexa.vxlinker.rewards.RewardLogManager; 
import com.velvexa.vxlinker.storage.StorageProvider;


public class RewardManager {

    private final VXLinker plugin;
    private final StorageProvider storage;


    private final RewardLogManager rewardLogManager;

 
    private final File dataFile;
    private final FileConfiguration data;

 
    private boolean firstEnabled;
    private List<String> firstCommands;
    private List<Object> firstItemsRaw; 

    private boolean intervalEnabled;
    private long intervalMillis;
    private List<String> intervalCommands;
    private List<Object> intervalItemsRaw;


    private BukkitTask intervalTask;


    private final java.util.Set<UUID> firstProcessing =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public RewardManager(VXLinker plugin) {
        this.plugin = plugin;
        this.storage = plugin.getStorageProvider();

   
        this.rewardLogManager = new RewardLogManager(plugin);

    
        this.dataFile = new File(plugin.getDataFolder(), "reward-data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("❌ reward-data.yml oluşturulamadı: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);

        loadConfig();
    }

   
    public final void loadConfig() {
        FileConfiguration cfg = plugin.getConfig();


        String baseFirst = "rewards.first-link";
        firstEnabled = cfg.getBoolean(baseFirst + ".enabled", true);
        firstCommands = cfg.getStringList(baseFirst + ".commands");
        if (firstCommands == null) firstCommands = Collections.emptyList();
        firstItemsRaw = readItemsList(cfg, baseFirst + ".items");

    
        String baseInt = "rewards.interval";
        intervalEnabled = cfg.getBoolean(baseInt + ".enabled", false);
        String every = cfg.getString(baseInt + ".every", "1d"); 
        intervalMillis = parseDurationToMillis(every);
        intervalCommands = cfg.getStringList(baseInt + ".commands");
        if (intervalCommands == null) intervalCommands = Collections.emptyList();
        intervalItemsRaw = readItemsList(cfg, baseInt + ".items");

        plugin.getLogger().info("🎁 Rewards yüklendi: first=" + firstEnabled + ", interval=" + intervalEnabled + " (" + every + ")");
    }

   
    public void start() {
        if (intervalTask != null) {
            intervalTask.cancel();
            intervalTask = null;
        }

        if (!intervalEnabled) {
            plugin.getLogger().info("⏸ Periyodik ödüller devre dışı.");
            return;
        }


        intervalTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                tryGrantInterval(p, now);
            }
        }, 20L * 30, 20L * 60); 
        plugin.getLogger().info("⏱ Periyodik ödül denetleyici başlatıldı.");
    }

   
    public void stop() {
        if (intervalTask != null) {
            intervalTask.cancel();
            intervalTask = null;
        }
        saveData();

        rewardLogManager.close();
    }

    
    public void grantFirstLink(Player player) {
        if (!firstEnabled || player == null) return;

        
        if (!storage.isPlayerLinked(player.getUniqueId())) {
            return;
        }

        final UUID uuid = player.getUniqueId();

      
        if (!firstProcessing.add(uuid)) {
            return;
        }


        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                
                if (data.getBoolean("first-claimed." + uuid, false)) {
                    return;
                }

        
                data.set("first-claimed." + uuid, true);
                saveData();

      
                dispatchCommands(firstCommands, player);

   
                int givenItems = giveItems(firstItemsRaw, player);

 
                String msg = colorize(plugin.getConfig().getString("rewards.first-link.message",
                        "&aTebrikler! İlk eşleşme ödülünü kazandın."));
                if (!msg.isEmpty()) {
                    player.sendMessage(msg);
                }


                rewardLogManager.logReward(
                        uuid,
                        "first-link",
                        "İlk eşleşme ödülü verildi | komut=" + firstCommands.size() + ", item=" + givenItems
                );
            } finally {
 
                firstProcessing.remove(uuid);
            }
        });
    }

 
    private void tryGrantInterval(Player player, long now) {
        if (!intervalEnabled || player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        if (!storage.isPlayerLinked(uuid)) return;

        long last = data.getLong("last-interval." + uuid, 0L);
        if (now - last < intervalMillis) return;


        dispatchCommands(intervalCommands, player);
        int givenItems = giveItems(intervalItemsRaw, player);

        String msg = colorize(plugin.getConfig().getString("rewards.interval.message",
                "&aDiscord bağlantın aktif olduğu için ödül kazandın!"));
        if (!msg.isEmpty()) {
            player.sendMessage(msg);
        }

        data.set("last-interval." + uuid, now);
        saveData();

  
        rewardLogManager.logReward(
                uuid,
                "interval",
                "Periyodik ödül verildi | komut=" + intervalCommands.size() + ", item=" + givenItems
        );
    }



  
    private void dispatchCommands(List<String> commands, Player player) {
        if (commands == null) return;
        for (String raw : commands) {
            if (raw == null || raw.trim().isEmpty()) continue;
            String cmd = raw.replace("{player}", player.getName());


            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
            );
        }
    }


    @SuppressWarnings("unchecked")
    private int giveItems(List<Object> itemsRaw, Player player) {
        if (itemsRaw == null || itemsRaw.isEmpty()) return 0;
        int count = 0;

        for (Object obj : itemsRaw) {
            if (obj instanceof String) {
                ItemStack item = parseSimpleItem((String) obj);
                if (item != null) {
                    safeGive(player, item);
                    count++;
                }
            } else if (obj instanceof Map) {
                ItemStack item = parseAdvancedItem((Map<String, Object>) obj);
                if (item != null) {
                    safeGive(player, item);
                    count++;
                }
            }
        }
        return count;
    }


    private ItemStack parseSimpleItem(String def) {
        try {
            String[] parts = def.split(":");
            String matName = parts[0].trim().toUpperCase(Locale.ROOT);
            int amount = (parts.length > 1) ? Math.max(1, Integer.parseInt(parts[1].trim())) : 1;

            Material mat = Material.matchMaterial(matName);
            if (mat == null) {
                plugin.getLogger().warning("Geçersiz materyal: " + matName);
                return null;
            }
            return new ItemStack(mat, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("Item parse hatası (simple): " + def + " -> " + e.getMessage());
            return null;
        }
    }


    private ItemStack parseAdvancedItem(Map<String, Object> map) {
        try {
            String matName = String.valueOf(map.getOrDefault("material", "STONE")).toUpperCase(Locale.ROOT);
            int amount = Integer.parseInt(String.valueOf(map.getOrDefault("amount", "1")));
            Material mat = Material.matchMaterial(matName);
            if (mat == null) {
                plugin.getLogger().warning("Geçersiz materyal: " + matName);
                return null;
            }
            ItemStack stack = new ItemStack(mat, Math.max(1, amount));

            ItemMeta meta = stack.getItemMeta();
            if (meta == null) return stack;


            if (map.containsKey("name")) {
                meta.setDisplayName(colorize(String.valueOf(map.get("name"))));
            }

 
            if (map.containsKey("lore")) {
                Object loreObj = map.get("lore");
                List<String> loreList = new ArrayList<>();
                if (loreObj instanceof List) {
                    for (Object line : (List<?>) loreObj) {
                        loreList.add(colorize(String.valueOf(line)));
                    }
                } else if (loreObj instanceof String) {
                    loreList.add(colorize((String) loreObj));
                }
                meta.setLore(loreList);
            }

  
            if (map.containsKey("enchants")) {
                Object enchObj = map.get("enchants");
                if (enchObj instanceof Map) {
                    Map<?, ?> enchMap = (Map<?, ?>) enchObj;
                    for (Map.Entry<?, ?> e : enchMap.entrySet()) {
                        Enchantment ench = Enchantment.getByName(String.valueOf(e.getKey()).toUpperCase(Locale.ROOT));
                        int lvl = Integer.parseInt(String.valueOf(e.getValue()));
                        if (ench != null) {
                            meta.addEnchant(ench, Math.max(1, lvl), true);
                        }
                    }
                }
            }

            if (map.containsKey("unbreakable")) {
                boolean unbreak = Boolean.parseBoolean(String.valueOf(map.get("unbreakable")));
                try {
                    meta.setUnbreakable(unbreak);
                } catch (NoSuchMethodError ignored) {
                }
            }


            if (map.containsKey("flags")) {
                Object flagsObj = map.get("flags");
                if (flagsObj instanceof List) {
                    for (Object f : (List<?>) flagsObj) {
                        try {
                            ItemFlag flag = ItemFlag.valueOf(String.valueOf(f).toUpperCase(Locale.ROOT));
                            meta.addItemFlags(flag);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }

            stack.setItemMeta(meta);
            return stack;

        } catch (Exception e) {
            plugin.getLogger().warning("Item parse hatası (advanced): " + e.getMessage());
            return null;
        }
    }


    private void safeGive(Player p, ItemStack item) {
        HashMap<Integer, ItemStack> left = p.getInventory().addItem(item);
        if (!left.isEmpty()) {

            p.getWorld().dropItemNaturally(p.getLocation(), item);
        }
    }

  
    private List<Object> readItemsList(FileConfiguration cfg, String path) {
        List<Object> out = new ArrayList<>();
        if (!cfg.isSet(path)) return out;


        if (cfg.isList(path)) {
            for (Object o : cfg.getList(path)) {
                out.add(o);
            }
            return out;
        }


        if (cfg.isConfigurationSection(path)) {
            ConfigurationSection sec = cfg.getConfigurationSection(path);
            if (sec != null) out.add(sec.getValues(true));
        }

        return out;
    }


    private long parseDurationToMillis(String s) {
        if (s == null || s.trim().isEmpty()) return TimeUnit.DAYS.toMillis(1);
        s = s.trim().toLowerCase(Locale.ROOT);

               try {
            if (s.endsWith("m")) {
                long v = Long.parseLong(s.substring(0, s.length() - 1));
                return TimeUnit.MINUTES.toMillis(Math.max(1, v));
            } else if (s.endsWith("h")) {
                long v = Long.parseLong(s.substring(0, s.length() - 1));
                return TimeUnit.HOURS.toMillis(Math.max(1, v));
            } else if (s.endsWith("d")) {
                long v = Long.parseLong(s.substring(0, s.length() - 1));
                return TimeUnit.DAYS.toMillis(Math.max(1, v));
            } else {
                long v = Long.parseLong(s);
                return TimeUnit.MINUTES.toMillis(Math.max(1, v));
            }
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Geçersiz süre formatı: " + s + " (varsayılan 1d)");
            return TimeUnit.DAYS.toMillis(1);
        }
    }

   
    private String colorize(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

  
    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("❌ reward-data.yml kaydedilemedi: " + e.getMessage());
        }
    }

 
    public void reload() {
        plugin.reloadConfig();
        loadConfig();
        start(); 
    }


    public Optional<Instant> getLastIntervalReward(UUID uuid) {
        long t = data.getLong("last-interval." + uuid, 0L);
        return (t <= 0) ? Optional.empty() : Optional.of(Instant.ofEpochMilli(t));
    }
}

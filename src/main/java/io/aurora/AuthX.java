package io.aurora;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class AuthX extends JavaPlugin implements Listener {

    private final Map<String, GoogleAuthenticatorKey> playerKeys = new HashMap<>();
    private final Map<String, Boolean> verifiedPlayers = new HashMap<>();
    private final GoogleAuthenticator gAuth = new GoogleAuthenticator();
    private final Gson gson = new Gson();
    private final File dataFile = new File(getDataFolder(), "players.json");
    private FileConfiguration messages;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }

        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
        loadMessages();

        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        getServer().getPluginManager().registerEvents(this, this);
        loadPlayerData();
        getLogger().info(getMessage("enable-message"));
    }

    @Override
    public void onDisable() {
        savePlayerData();
        getLogger().info(getMessage("disable-message"));
    }

//    @EventHandler
//    public void onPlayerJoin(PlayerJoinEvent event) {
//        Player player = event.getPlayer();
//        if (!verifiedPlayers.containsKey(player.getName())) {
//            verifiedPlayers.put(player.getName(), false);
//            player.sendMessage(ChatColor.YELLOW + "请使用 /authx setup 命令生成您的密钥");
//            player.sendMessage(ChatColor.YELLOW + "请使用 /authx verify <code> 命令生成/验证您的TOTP");
//        }
//    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        boolean forceTotpOnEveryLogin = getConfig().getBoolean("force-totp-on-every-login");

        if (forceTotpOnEveryLogin || !verifiedPlayers.containsKey(player.getName())) {
            verifiedPlayers.put(player.getName(), false);
            player.sendMessage(ChatColor.YELLOW + getMessage("setup-totp"));
            player.sendMessage(ChatColor.YELLOW + getMessage("verify-totp"));
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!verifiedPlayers.getOrDefault(player.getName(), false)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + getMessage("move-message"));
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();

            if (!verifiedPlayers.getOrDefault(player.getName(), false)) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            if (command.getName().equalsIgnoreCase("authx")) {
                if (args.length == 0) {
                    player.sendMessage(ChatColor.RED + "Usage: /authx <setup | verify | reload>");
                    return true;
                }

                if (args[0].equalsIgnoreCase("reload")) {
                    if (player.isOp()) {
                        reloadConfig();
                        player.sendMessage(ChatColor.GREEN + getMessage("config-reload"));
                    } else {
                        player.sendMessage(ChatColor.RED + getMessage("no-permission"));
                    }
                    return true;
                }

                if (args[0].equalsIgnoreCase("setup")) {
                    GoogleAuthenticatorKey key = gAuth.createCredentials();
                    playerKeys.put(player.getName(), key);
                    savePlayerKey(player.getName(), key.getKey());
                    player.sendMessage(ChatColor.GREEN + getMessage("setup-message") + key.getKey());
                    player.sendMessage(ChatColor.GREEN + getMessage("get-totp-key"));
                } else if (args[0].equalsIgnoreCase("verify")) {
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /authx verify <code>");
                        return true;
                    }

                    GoogleAuthenticatorKey key = playerKeys.get(player.getName());
                    if (key == null) {
                        player.sendMessage(ChatColor.RED + getMessage("no-setup-key"));
                        return true;
                    }

                    int code;
                    try {
                        code = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + getMessage("code-format"));
                        return true;
                    }

                    boolean isCodeValid = gAuth.authorize(key.getKey(), code);
                    if (isCodeValid) {
                        verifiedPlayers.put(player.getName(), true);
                        player.sendMessage(ChatColor.GREEN + getMessage("verify-successful"));
                    } else {
                        player.sendMessage(ChatColor.RED + getMessage("try-again"));
                    }
                }
                return true;
            }
        }

        return false;
    }

    private void savePlayerKey(String playerName, String key) {
        try {
            Map<String, PlayerData> playerDataMap = loadPlayerData();
            playerDataMap.put(playerName, new PlayerData(playerName, key));

            try (Writer writer = new FileWriter(dataFile)) {
                gson.toJson(playerDataMap, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    private Map<String, PlayerData> loadPlayerData() {
//        if (!dataFile.exists()) {
//            return new HashMap<>();
//        }
//
//        try (Reader reader = new FileReader(dataFile)) {
//            Type type = new TypeToken<Map<String, PlayerData>>() {}.getType();
//            Map<String, PlayerData> playerDataMap = gson.fromJson(reader, type);
//            playerDataMap.forEach((name, data) -> playerKeys.put(name, new GoogleAuthenticatorKey.Builder(data.getKey()).build()));
//            return playerDataMap;
//        } catch (IOException e) {
//            e.printStackTrace();
//            return new HashMap<>();
//        }
//    }

    private Map<String, PlayerData> loadPlayerData() {
        if (!dataFile.exists()) {
            return new HashMap<>();
        }

        try (Reader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, PlayerData>>() {}.getType();
            Map<String, PlayerData> playerDataMap = gson.fromJson(reader, type);

            if (playerDataMap == null) {
                playerDataMap = new HashMap<>();
            }

            playerDataMap.forEach((name, data) -> playerKeys.put(name, new GoogleAuthenticatorKey.Builder(data.getKey()).build()));
            return playerDataMap;
        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    private void savePlayerData() {
        try (Writer writer = new FileWriter(dataFile)) {
            Map<String, PlayerData> playerDataMap = new HashMap<>();
            playerKeys.forEach((name, key) -> playerDataMap.put(name, new PlayerData(name, key.getKey())));
            gson.toJson(playerDataMap, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadMessages() {
        String language = getConfig().getString("language", "en");
        File messagesFile = new File(getDataFolder(), "messages_" + language + ".yml");

        if (!messagesFile.exists()) {
            saveResource("messages_" + language + ".yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String getMessage(String key) {
        return messages.getString(key, "Message not found: " + key);
    }
}

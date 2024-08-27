package io.aurora;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        loadPlayerData();
        getLogger().info("AuthX is enable");
    }

    @Override
    public void onDisable() {
        savePlayerData();
        getLogger().info("AuthX is disable");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!verifiedPlayers.containsKey(player.getName())) {
            verifiedPlayers.put(player.getName(), false);
            player.sendMessage(ChatColor.YELLOW + "请使用 /totp verify <setup|code> 命令生成/验证您的TOTP");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!verifiedPlayers.getOrDefault(player.getName(), false)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "在您移动之前您必须验证您的TOTP");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            if (command.getName().equalsIgnoreCase("totp")) {
                if (args.length == 0) {
                    player.sendMessage(ChatColor.RED + "Usage: /totp <setup|verify>");
                    return true;
                }

                if (args[0].equalsIgnoreCase("setup")) {
                    GoogleAuthenticatorKey key = gAuth.createCredentials();
                    playerKeys.put(player.getName(), key);
                    savePlayerKey(player.getName(), key.getKey());
                    player.sendMessage(ChatColor.GREEN + "您的TOTP密钥是: " + key.getKey());
                    player.sendMessage(ChatColor.GREEN + "请将密钥输入进您的TOTP应用以获取一次性密码");
                } else if (args[0].equalsIgnoreCase("verify")) {
                    if (args.length < 2) {
                        player.sendMessage(ChatColor.RED + "Usage: /totp verify <code>");
                        return true;
                    }

                    GoogleAuthenticatorKey key = playerKeys.get(player.getName());
                    if (key == null) {
                        player.sendMessage(ChatColor.RED + "您还没有生成密钥，请使用 /totp setup 生成密钥");
                        return true;
                    }

                    int code;
                    try {
                        code = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "一次性密码格式不正确，它应该是数字");
                        return true;
                    }

                    boolean isCodeValid = gAuth.authorize(key.getKey(), code);
                    if (isCodeValid) {
                        verifiedPlayers.put(player.getName(), true);
                        player.sendMessage(ChatColor.GREEN + "您的TOTP验证成功，现在可以移动了");
                    } else {
                        player.sendMessage(ChatColor.RED + "TOTP代码无效，请重试");
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

    private Map<String, PlayerData> loadPlayerData() {
        if (!dataFile.exists()) {
            return new HashMap<>();
        }

        try (Reader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, PlayerData>>() {}.getType();
            Map<String, PlayerData> playerDataMap = gson.fromJson(reader, type);
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
}

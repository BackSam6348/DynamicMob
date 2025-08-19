package me.linhyeok;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;

public class DynamicMob extends JavaPlugin implements CommandExecutor, TabCompleter {

    private ConfigManager configManager;
    private EquipmentManager equipmentManager;
    private SpawnListener spawnListener;

    @Override
    public void onEnable() {
        // 콘솔 메시지 (세련된 스타일)
        getLogger().info("======================================");
        getLogger().info("  DynamicMob " + getDescription().getVersion() + " starting...");
        getLogger().info("  Paper/Folia ready • Commands: /dm reload");
        getLogger().info("======================================");

        // 기본 config 생성/로드
        saveDefaultConfig();

        // 매니저 구성
        this.configManager = new ConfigManager(this);
        this.configManager.reload(); // 최초 로드
        this.equipmentManager = new EquipmentManager(this, configManager);

        // 리스너 등록
        this.spawnListener = new SpawnListener(this, configManager, equipmentManager);
        getServer().getPluginManager().registerEvents(this.spawnListener, this);

        // plugin.yml 없이 명령 등록
        registerDmCommand();

        getLogger().info("DynamicMob enabled.");
    }

    @Override
    public void onDisable() {
        // 리스너 해제
        HandlerList.unregisterAll(this);
        getLogger().info("======================================");
        getLogger().info("  DynamicMob " + getDescription().getVersion() + " stopped.");
        getLogger().info("======================================");
    }

    /** plugin.yml/paper-plugin.yml 없이 런타임으로 /dm 등록 */
    private void registerDmCommand() {
        try {
            // CommandMap reflection
            Field f = getServer().getPluginManager().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            CommandMap commandMap = (CommandMap) f.get(getServer().getPluginManager());

            // 이미 등록되어 있으면 중복 방지
            if (commandMap.getCommand("dm") != null) {
                return;
            }

            // PluginCommand 생성자 접근
            Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            c.setAccessible(true);
            PluginCommand cmd = c.newInstance("dm", this);

            cmd.setDescription("DynamicMob command");
            cmd.setUsage("/dm reload");
            cmd.setPermission("dynamicmob.reload");
            cmd.setPermissionMessage("You don't have permission!");
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);

            // 실제 등록 (네임스페이스 prefix는 플러그인명 소문자)
            commandMap.register(getDescription().getName().toLowerCase(), cmd);
        } catch (Exception e) {
            getLogger().warning("Failed to register /dm command programmatically: " + e.getMessage());
        }
    }

    // /dm 명령 처리
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // label/dm, command/dm 어느 쪽으로도 들어오므로 이름 기준
        if (!command.getName().equalsIgnoreCase("dm")) return false;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            this.configManager.reload();
            sender.sendMessage(ChatColor.GREEN + "[DynamicMob] Configuration reloaded!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /dm reload");
        return true;
    }

    // /dm 탭완성
    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("dm")) return Collections.emptyList();
        if (args.length == 1) return Collections.singletonList("reload");
        return Collections.emptyList();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EquipmentManager getEquipmentManager() {
        return equipmentManager;
    }

    public SpawnListener getSpawnListener() {
        return spawnListener;
    }
}

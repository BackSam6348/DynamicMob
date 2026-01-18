package me.linhyeok;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.*;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

public class DynamicMob extends JavaPlugin implements CommandExecutor, TabCompleter {

    private ConfigManager configManager;
    private static boolean debugMode = false;

    public static boolean isDebugMode() {
        return debugMode;
    }

    @Override
    public void onEnable() {
        // 콘솔 메시지
        getLogger().info("======================================");
        getLogger().info("  DynamicMob " + getPluginMeta().getVersion() + " starting...");
        getLogger().info("  Paper/Folia ready • Commands: /dm reload");
        getLogger().info("======================================");

        // 기본 config 생성/로드
        saveDefaultConfig();

        // 매니저 구성
        this.configManager = new ConfigManager(this);
        this.configManager.reload();
        EquipmentManager equipmentManager = new EquipmentManager(this, configManager);

        // 리스너 등록
        SpawnListener spawnListener = new SpawnListener(this, configManager, equipmentManager);
        getServer().getPluginManager().registerEvents(spawnListener, this);

        // 명령 등록
        registerDmCommand();

        getLogger().info("DynamicMob enabled.");
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        getLogger().info("======================================");
        getLogger().info("  DynamicMob " + getPluginMeta().getVersion() + " stopped.");
        getLogger().info("======================================");
    }

    private void registerDmCommand() {
        try {
            Field f = getServer().getPluginManager().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            CommandMap commandMap = (CommandMap) f.get(getServer().getPluginManager());

            if (commandMap.getCommand("dm") != null) {
                return;
            }

            Constructor<PluginCommand> c = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            c.setAccessible(true);
            PluginCommand cmd = c.newInstance("dm", this);

            cmd.setDescription("DynamicMob command");
            cmd.setUsage("/dm reload");
            cmd.setPermission("dynamicmob.reload");
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);

            commandMap.register(getPluginMeta().getName().toLowerCase(), cmd);
        } catch (Exception e) {
            getLogger().warning("Failed to register /dm command: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("dm")) return false;

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("dynamicmob.reload")) {
                sender.sendMessage(Component.text("You don't have permission!", NamedTextColor.RED));
                return true;
            }
            reloadConfig();
            this.configManager.reload();
            sender.sendMessage(Component.text("[DynamicMob] Configuration reloaded!", NamedTextColor.GREEN));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            if (!sender.isOp()) {
                sender.sendMessage(Component.text("You must be OP to use this command!", NamedTextColor.RED));
                return true;
            }

            if (args[1].equalsIgnoreCase("on")) {
                debugMode = true;
                sender.sendMessage(Component.text("[DynamicMob] Debug mode enabled!", NamedTextColor.GREEN));
                return true;
            } else if (args[1].equalsIgnoreCase("off")) {
                debugMode = false;
                sender.sendMessage(Component.text("[DynamicMob] Debug mode disabled!", NamedTextColor.YELLOW));
                return true;
            }
        }

        sender.sendMessage(Component.text("Usage: /dm reload | /dm debug <on|off>", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("dm")) return Collections.emptyList();

        if (args.length == 1) {
            List<String> completions = new java.util.ArrayList<>();
            completions.add("reload");
            if (sender.isOp()) {
                completions.add("debug");
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug") && sender.isOp()) {
            return java.util.Arrays.asList("on", "off");
        }

        return Collections.emptyList();
    }
}
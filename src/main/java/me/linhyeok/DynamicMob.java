package me.linhyeok;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.lang.reflect.Field;
import java.util.*;

/**
 * DynamicMob — Paper/Folia 1.21.x
 *
 * 포함된 기능:
 * - 자연/NATURAL, 스포너/SPAWNER, 스폰알/SPAWNER_EGG(옵션) 스폰 커스터마이징
 * - 자연스폰 확률 제한(spawn-chance), 스폰 배수(multiplier)
 * - 대체 자연스폰(replacement-spawn) + 적용 스코프(replacement.apply-to.*)
 * - Piglin / PiglinBrute / Hoglin: 원본 취소 후 즉시 "면역 개체"로 재소환
 * - 조키(치킨/스파이더), 드라운드 채널링, 충전 크리퍼
 * - 무기/갑옷 인첸트(바닐라 범위 내 랜덤), 장비 확률
 * - 블록 헬멧(special.block_helmet.general / special.block_helmet.skeleton)
 * - Killer Bunny 2종:
 *    1) 대체스폰 결과가 Rabbit일 때 킬러로 바꾸기 (기존 killer_bunny_from_replacement / chance)
 *    2) 원래 Rabbit이 스폰될 때 확률적으로 킬러로 전환 (special.killer_bunny_on_rabbit_spawn.*)
 * - 콘솔 배너/리로드 메시지
 * - /dm reload 명령을 코드로 직접 등록( paper-plugin.yml에 commands 없어도 작동 )
 */
public class DynamicMob extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private FileConfiguration config;

    // Global spawn options
    private double mobSpawnMultiplier = 1.0;
    private int lightThreshold = 7; // 정보용(로직엔 사용 안 함)
    private boolean enableSpawnEgg = true; // SPAWNER_EGG 커스텀 반영 여부

    // Enchant chances
    private double weaponEnchantChance = 1.0;
    private double armorEnchantChance = 1.0;

    // Special chances
    private double leavesHelmetChance, boneInHandChance, drownedChannelingChance, chargedCreeperChance;

    // Jockey chances
    private final Map<String, Double> jockeyChances = new HashMap<>();

    // Equipment chances per entity
    private final Map<EntityType, Map<String, Map<Material, Double>>> spawnChances = new EnumMap<>(EntityType.class);
    // Special per entity (e.g., DROWNED: TRIDENT_CHANNELING)
    private final Map<EntityType, Map<String, Double>> specialChances = new EnumMap<>(EntityType.class);
    // Disabled entities
    private final Set<EntityType> disabledEntities = EnumSet.noneOf(EntityType.class);
    // Natural spawn chance limiting
    private final Map<EntityType, Double> naturalSpawnChance = new EnumMap<>(EntityType.class);

    // Replacement spawn: origin -> (target, weight)
    private final Map<EntityType, Map<EntityType, Double>> replacementSpawn = new EnumMap<>(EntityType.class);

    // Replacement apply scope
    private boolean replacementApplyNatural = true;
    private boolean replacementApplySpawner = false;
    private boolean replacementApplySpawnEgg = false;

    // Killer Bunny (for replacement result == Rabbit)
    private boolean killerBunnyFromReplacement = false;
    private double killerBunnyChance = 1.0;

    // Killer Bunny (convert original Rabbit on spawn)
    private boolean killerBunnyOnRabbitEnabled = false;
    private double killerBunnyOnRabbitChance = 0.0;
    private boolean killerRabbitApplyNatural = true;
    private boolean killerRabbitApplySpawner = false;
    private boolean killerRabbitApplySpawnEgg = false;

    // Block helmet chances
    private final Map<Material, Double> generalBlockHelmetChances = new HashMap<>();
    private final Map<Material, Double> skeletonBlockHelmetChances = new HashMap<>();

    // ===== Pretty console messages =====
    private static final String BAR = ChatColor.DARK_GRAY + "────────────────────────────────────────────────";
    private static final String NAME = ChatColor.AQUA + "Dynamic" + ChatColor.WHITE + "Mob";
    private static final String PREFIX = ChatColor.DARK_AQUA + "⟦DynamicMob⟧ " + ChatColor.RESET;

    private void logBannerEnable() {
        CommandSender console = Bukkit.getConsoleSender();
        console.sendMessage("");
        console.sendMessage(BAR);
        console.sendMessage("  " + NAME + ChatColor.GRAY + " v" + getDescription().getVersion());
        console.sendMessage("  " + ChatColor.GRAY + "Status: " + ChatColor.GREEN + "ENABLED");
        console.sendMessage("  " + ChatColor.GRAY + "Server: " + ChatColor.WHITE + Bukkit.getVersion());
        console.sendMessage("  " + ChatColor.GRAY + "Author: " + ChatColor.WHITE + String.join(", ", getDescription().getAuthors()));
        console.sendMessage(BAR);
        console.sendMessage("");
    }
    private void logBannerDisable() {
        CommandSender console = Bukkit.getConsoleSender();
        console.sendMessage("");
        console.sendMessage(BAR);
        console.sendMessage("  " + NAME + ChatColor.GRAY + " v" + getDescription().getVersion());
        console.sendMessage("  " + ChatColor.GRAY + "Status: " + ChatColor.RED + "DISABLED");
        console.sendMessage(BAR);
        console.sendMessage("");
    }
    private void logReload(CommandSender who) {
        String msg = PREFIX + ChatColor.GREEN + "Config reloaded " +
                ChatColor.DARK_GRAY + "(v" + getDescription().getVersion() + ")";
        who.sendMessage(msg);
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    // ====== Command runtime registration ======
    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = getConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        registerDmCommand(); // <- 코드로 /dm 등록
        logBannerEnable();
    }

    @Override
    public void onDisable() {
        logBannerDisable();
    }

    private void registerDmCommand() {
        try {
            CommandMap map = getCommandMap();

            // 이미 플러그인 커맨드가 등록되어 있으면 중복 등록 방지
            if (Bukkit.getPluginCommand("dm") != null) return;

            DmCommand cmd = new DmCommand("dm", this);
            cmd.setDescription("DynamicMob command");
            cmd.setUsage("/dm reload");
            cmd.setPermission("dynamicmob.reload");
            cmd.setPermissionMessage(ChatColor.RED + "You don't have permission!");

            map.register(getDescription().getName().toLowerCase(Locale.ROOT), cmd);
        } catch (Throwable t) {
            getLogger().warning("[DynamicMob] Failed to register /dm command via CommandMap: " + t.getMessage());
        }
    }

    private CommandMap getCommandMap() throws Exception {
        // CraftServer.commandMap 에 reflection 접근 (Paper/Folia에서도 유효)
        Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
        f.setAccessible(true);
        return (CommandMap) f.get(Bukkit.getServer());
    }

    // 내부 커맨드 클래스
    private static final class DmCommand extends Command {
        private final DynamicMob plugin;

        DmCommand(String name, DynamicMob plugin) {
            super(name);
            this.plugin = plugin;
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("dynamicmob.reload")) {
                    sender.sendMessage(ChatColor.RED + "You don't have permission!");
                    return true;
                }
                plugin.reloadConfig();
                plugin.config = plugin.getConfig();
                plugin.loadConfig();
                plugin.logReload(sender);
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + "Usage: /dm reload");
            return true;
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            if (args.length == 1) {
                List<String> opts = Collections.singletonList("reload");
                List<String> out = new ArrayList<>();
                StringUtil.copyPartialMatches(args[0], opts, out);
                return out;
            }
            return Collections.emptyList();
        }
    }
    // ====== /Command ======

    // =================== Config Load ===================
    private void loadConfig() {
        // ---- Global spawn options ----
        ConfigurationSection mobSpawn = config.getConfigurationSection("mob-spawn");
        mobSpawnMultiplier = mobSpawn != null ? mobSpawn.getDouble("multiplier", 1.0) : 1.0;
        enableSpawnEgg = mobSpawn != null && mobSpawn.getBoolean("enable-spawn-egg", true);
        lightThreshold = config.getInt("light-threshold", 7); // info only

        // ---- Enchant chance ----
        ConfigurationSection enchantSec = config.getConfigurationSection("enchant-chance");
        weaponEnchantChance = enchantSec != null ? enchantSec.getDouble("weapon", 1.0) : 1.0;
        armorEnchantChance = enchantSec != null ? enchantSec.getDouble("armor", 1.0) : 1.0;

        // ---- Special ----
        ConfigurationSection special = config.getConfigurationSection("special");
        leavesHelmetChance   = special != null ? special.getDouble("leaves_helmet", 0.0)     : 0.0;
        boneInHandChance     = special != null ? special.getDouble("bone_in_hand", 0.0)      : 0.0;
        chargedCreeperChance = special != null ? special.getDouble("charged_creeper", 0.0)   : 0.0;

        // Killer Bunny (replacement result == Rabbit)
        killerBunnyFromReplacement = special != null && special.getBoolean("killer_bunny_from_replacement", false);
        killerBunnyChance          = special != null ? special.getDouble("killer_bunny_chance", 1.0) : 1.0;

        // Killer Bunny (original rabbit spawn → convert)
        ConfigurationSection kbRabbit = (special != null) ? special.getConfigurationSection("killer_bunny_on_rabbit_spawn") : null;
        if (kbRabbit != null) {
            killerBunnyOnRabbitEnabled = kbRabbit.getBoolean("enabled", false);
            killerBunnyOnRabbitChance  = kbRabbit.getDouble("chance", 0.0);
            ConfigurationSection apply = kbRabbit.getConfigurationSection("apply-to");
            if (apply != null) {
                killerRabbitApplyNatural  = apply.getBoolean("natural", true);
                killerRabbitApplySpawner  = apply.getBoolean("spawner", false);
                killerRabbitApplySpawnEgg = apply.getBoolean("spawn-egg", false);
            } else {
                killerRabbitApplyNatural  = true;
                killerRabbitApplySpawner  = false;
                killerRabbitApplySpawnEgg = false;
            }
        } else {
            killerBunnyOnRabbitEnabled = false;
            killerBunnyOnRabbitChance  = 0.0;
            killerRabbitApplyNatural   = true;
            killerRabbitApplySpawner   = false;
            killerRabbitApplySpawnEgg  = false;
        }

        // Block helmets
        generalBlockHelmetChances.clear();
        skeletonBlockHelmetChances.clear();
        ConfigurationSection helmetRoot = special != null ? special.getConfigurationSection("block_helmet") : null;
        if (helmetRoot != null) {
            ConfigurationSection general = helmetRoot.getConfigurationSection("general");
            if (general != null) {
                for (String key : general.getKeys(false)) {
                    try {
                        Material m = Material.valueOf(key.toUpperCase(Locale.ROOT));
                        generalBlockHelmetChances.put(m, general.getDouble(key, 0.0));
                    } catch (IllegalArgumentException ex) {
                        getLogger().warning("[DynamicMob] Invalid material in special.block_helmet.general: " + key);
                    }
                }
            }
            ConfigurationSection skeleton = helmetRoot.getConfigurationSection("skeleton");
            if (skeleton != null) {
                for (String key : skeleton.getKeys(false)) {
                    try {
                        Material m = Material.valueOf(key.toUpperCase(Locale.ROOT));
                        skeletonBlockHelmetChances.put(m, skeleton.getDouble(key, 0.0));
                    } catch (IllegalArgumentException ex) {
                        getLogger().warning("[DynamicMob] Invalid material in special.block_helmet.skeleton: " + key);
                    }
                }
            }
        }

        // ---- Jockey chance ----
        ConfigurationSection jockey = config.getConfigurationSection("jockey-chance");
        jockeyChances.clear();
        if (jockey != null) {
            for (String key : jockey.getKeys(false)) {
                jockeyChances.put(key, jockey.getDouble(key, 0.0));
            }
        }

        // ---- Equipment per entity ----
        spawnChances.clear();
        specialChances.clear();
        disabledEntities.clear();

        ConfigurationSection spawnSettings = config.getConfigurationSection("spawn-settings");
        if (spawnSettings != null) {
            for (String entityKey : spawnSettings.getKeys(false)) {
                EntityType type;
                try { type = EntityType.valueOf(entityKey); }
                catch (Exception ignored) { continue; }

                ConfigurationSection eqSection = spawnSettings.getConfigurationSection(entityKey);
                if (eqSection == null) continue;

                if (!eqSection.getBoolean("enabled", true)) {
                    disabledEntities.add(type);
                    continue;
                }

                Map<String, Map<Material, Double>> slotMap = new HashMap<>();
                Map<String, Double> slotSpecial = new HashMap<>();
                for (String slot : eqSection.getKeys(false)) {
                    if ("enabled".equals(slot)) continue;
                    ConfigurationSection slotSection = eqSection.getConfigurationSection(slot);
                    if (slotSection == null) continue;

                    Map<Material, Double> chanceMap = new HashMap<>();
                    for (String materialKey : slotSection.getKeys(false)) {
                        try {
                            chanceMap.put(Material.valueOf(materialKey), slotSection.getDouble(materialKey));
                        } catch (IllegalArgumentException e) {
                            // Special token (e.g., TRIDENT_CHANNELING)
                            slotSpecial.put(materialKey, slotSection.getDouble(materialKey));
                        }
                    }
                    slotMap.put(slot, chanceMap);
                }
                spawnChances.put(type, slotMap);
                if (!slotSpecial.isEmpty()) specialChances.put(type, slotSpecial);
            }
        }

        // ---- Drowned Channeling chance ----
        drownedChannelingChance = 0.0;
        if (specialChances.containsKey(EntityType.DROWNED)) {
            drownedChannelingChance = specialChances.get(EntityType.DROWNED).getOrDefault("TRIDENT_CHANNELING", 0.0);
        }

        // ---- Natural spawn chance limiting ----
        naturalSpawnChance.clear();
        ConfigurationSection chanceSection = config.getConfigurationSection("spawn-chance");
        if (chanceSection != null) {
            for (String key : chanceSection.getKeys(false)) {
                try {
                    EntityType type = EntityType.valueOf(key);
                    naturalSpawnChance.put(type, chanceSection.getDouble(key, 1.0));
                } catch (Exception ignored) {}
            }
        }

        // ---- Replacement apply scope ----
        ConfigurationSection replCfg = config.getConfigurationSection("replacement");
        if (replCfg != null) {
            replacementApplyNatural  = replCfg.getBoolean("apply-to-natural", true);
            replacementApplySpawner  = replCfg.getBoolean("apply-to-spawner", false);
            replacementApplySpawnEgg = replCfg.getBoolean("apply-to-spawn-egg", false);
        } else {
            replacementApplyNatural  = true;
            replacementApplySpawner  = false;
            replacementApplySpawnEgg = false;
        }

        // ---- Replacement spawn ----
        replacementSpawn.clear();
        ConfigurationSection repl = config.getConfigurationSection("replacement-spawn");
        if (repl != null) {
            for (String originKey : repl.getKeys(false)) {
                EntityType origin;
                try { origin = EntityType.valueOf(originKey); }
                catch (IllegalArgumentException ex) {
                    getLogger().warning("[DynamicMob] Invalid origin in replacement-spawn: " + originKey);
                    continue;
                }
                ConfigurationSection dests = repl.getConfigurationSection(originKey);
                if (dests == null) continue;

                Map<EntityType, Double> table = new EnumMap<>(EntityType.class);
                for (String destKey : dests.getKeys(false)) {
                    try {
                        EntityType dest = EntityType.valueOf(destKey);
                        double weight = dests.getDouble(destKey, 0.0);
                        if (weight > 0.0) table.put(dest, weight);
                    } catch (IllegalArgumentException ex) {
                        getLogger().warning("[DynamicMob] Invalid target in replacement-spawn." + originKey + ": " + destKey);
                    }
                }
                if (!table.isEmpty()) replacementSpawn.put(origin, table);
            }
        }
    }

    // =================== Events ===================
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!isEligibleSpawn(event)) return;

        Entity entity = event.getEntity();
        EntityType type = entity.getType();

        // Natural spawn limiting
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            double chance = naturalSpawnChance.getOrDefault(type, 1.0);
            if (random.nextDouble() >= chance) {
                event.setCancelled(true);
                return;
            }
        }

        // 1) Piglin/PiglinBrute/Hoglin — 원본 즉시 취소 → "면역" 상태로 재소환
        if (type == EntityType.PIGLIN || type == EntityType.PIGLIN_BRUTE || type == EntityType.HOGLIN) {
            Location loc = entity.getLocation();
            World world = entity.getWorld();
            event.setCancelled(true);

            LivingEntity immune = spawnImmuneVariant(world, loc, type); // CUSTOM 스폰
            if (immune != null) {
                handleJockeysOrEquip(immune, event);
            }
            return;
        }

        // 2) Replacement spawn
        if (replacementSpawn.containsKey(type)) {
            CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
            boolean allowReplacement =
                    (replacementApplyNatural  && reason == CreatureSpawnEvent.SpawnReason.NATURAL) ||
                            (replacementApplySpawner  && reason == CreatureSpawnEvent.SpawnReason.SPAWNER) ||
                            (replacementApplySpawnEgg && reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);

            if (allowReplacement) {
                EntityType chosen = pickReplacementOrOriginal(type, replacementSpawn.get(type));
                if (chosen != null && chosen != type) {
                    Location loc = entity.getLocation();
                    World world = entity.getWorld();
                    event.setCancelled(true); // 원본 취소

                    LivingEntity newMob;
                    if (chosen == EntityType.PIGLIN || chosen == EntityType.PIGLIN_BRUTE || chosen == EntityType.HOGLIN) {
                        newMob = spawnImmuneVariant(world, loc, chosen); // 면역 상태로 처음부터
                    } else {
                        newMob = (LivingEntity) world.spawnEntity(loc, chosen, CreatureSpawnEvent.SpawnReason.CUSTOM);
                    }
                    if (newMob == null) return;

                    // Killer Bunny (replacement → Rabbit)
                    if (killerBunnyFromReplacement && newMob instanceof Rabbit rabbit) {
                        if (random.nextDouble() < killerBunnyChance) {
                            rabbit.setRabbitType(Rabbit.Type.THE_KILLER_BUNNY);
                        }
                    }

                    handleJockeysOrEquip(newMob, event);
                    return;
                }
            }
        }

        // 2.5) Original Rabbit spawn → convert to Killer Bunny
        if (entity.getType() == EntityType.RABBIT && killerBunnyOnRabbitEnabled) {
            CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
            boolean applies =
                    (killerRabbitApplyNatural  && reason == CreatureSpawnEvent.SpawnReason.NATURAL) ||
                            (killerRabbitApplySpawner  && reason == CreatureSpawnEvent.SpawnReason.SPAWNER) ||
                            (killerRabbitApplySpawnEgg && reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);
            if (applies && random.nextDouble() < killerBunnyOnRabbitChance) {
                Rabbit rabbit = (Rabbit) entity;
                rabbit.setRabbitType(Rabbit.Type.THE_KILLER_BUNNY);
            }
        }

        // 3) Default flow
        handleJockeysOrEquip(entity, event);
    }

    @EventHandler
    public void onTransform(EntityTransformEvent event) {
        EntityType from = event.getEntityType();
        EntityType to = event.getTransformedEntity().getType();
        if ((from == EntityType.PIGLIN || from == EntityType.PIGLIN_BRUTE || from == EntityType.HOGLIN)
                && (to == EntityType.ZOMBIFIED_PIGLIN || to == EntityType.ZOGLIN)) {
            event.setCancelled(true);
        }
    }

    // =================== Core Logic ===================
    private void handleJockeysOrEquip(Entity entity, CreatureSpawnEvent originEvent) {
        Location loc = entity.getLocation();
        World world = entity.getWorld();
        EntityType type = entity.getType();

        // Spawn multiplier (natural only)
        if (shouldMultiply(entity, originEvent)) {
            int extra = (int) Math.round(mobSpawnMultiplier - 1);
            for (int i = 0; i < extra; i++) {
                LivingEntity dup = (LivingEntity) world.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                handleCustomEquip(dup);
            }
        }

        // Jockey handling
        if (handleJockeys(entity, originEvent)) return;

        // Equipment / special / block-helmet / enchants
        handleCustomEquip(entity);
    }

    private void handleCustomEquip(Entity entity) {
        if (!(entity instanceof LivingEntity mob)) return;
        if (disabledEntities.contains(mob.getType())) return;

        EntityEquipment eq = mob.getEquipment();
        if (eq == null) return;

        Map<String, Map<Material, Double>> chances  = spawnChances.getOrDefault(mob.getType(), new HashMap<>());
        Map<String, Double> specials                = specialChances.getOrDefault(mob.getType(), new HashMap<>());

        // Base equipment
        eq.setItemInMainHand(getRandomItem(chances.get("weapon"), specials));
        eq.setHelmet(getRandomItem(chances.get("helmet"), null));
        eq.setChestplate(getRandomItem(chances.get("chestplate"), null));
        eq.setLeggings(getRandomItem(chances.get("leggings"), null));
        eq.setBoots(getRandomItem(chances.get("boots"), null));

        // Block helmet (general → skeleton-only), only if empty
        if (eq.getHelmet() == null || eq.getHelmet().getType() == Material.AIR) {
            boolean isSkeletonFamily =
                    mob.getType() == EntityType.SKELETON ||
                            mob.getType() == EntityType.STRAY ||
                            mob.getType() == EntityType.WITHER_SKELETON ||
                            mob.getType() == EntityType.BOGGED;

            if (!generalBlockHelmetChances.isEmpty()) {
                for (Map.Entry<Material, Double> e : generalBlockHelmetChances.entrySet()) {
                    if (random.nextDouble() < e.getValue()) {
                        eq.setHelmet(new ItemStack(e.getKey()));
                        break;
                    }
                }
            }
            if (isSkeletonFamily && (eq.getHelmet() == null || eq.getHelmet().getType() == Material.AIR)) {
                for (Map.Entry<Material, Double> e : skeletonBlockHelmetChances.entrySet()) {
                    if (random.nextDouble() < e.getValue()) {
                        eq.setHelmet(new ItemStack(e.getKey()));
                        break;
                    }
                }
            }
        }

        // Leaves helmet (zombies/skeleton-like)
        if (mob instanceof Zombie || mob instanceof Skeleton || mob.getType() == EntityType.BOGGED) {
            if (random.nextDouble() < leavesHelmetChance) {
                eq.setHelmet(new ItemStack(Material.OAK_LEAVES));
            }
        }

        // Skeleton family: bone in hand
        if (mob instanceof Skeleton || mob.getType() == EntityType.BOGGED) {
            if (random.nextDouble() < boneInHandChance) {
                eq.setItemInMainHand(new ItemStack(Material.BONE));
            }
        }

        // Drowned: channeling trident
        if (mob instanceof Drowned && drownedChannelingChance > 0) {
            if (random.nextDouble() < drownedChannelingChance) {
                ItemStack trident = new ItemStack(Material.TRIDENT);
                trident.addUnsafeEnchantment(Enchantment.CHANNELING, 1);
                eq.setItemInMainHand(trident);
            }
        }

        // Creeper: charged
        if (mob instanceof Creeper creeper && chargedCreeperChance > 0) {
            if (random.nextDouble() < chargedCreeperChance) {
                creeper.setPowered(true);
            }
        }

        // ===== Enchants =====
        ItemStack weapon = eq.getItemInMainHand();
        boolean isSkeletonFamily =
                mob.getType() == EntityType.SKELETON ||
                        mob.getType() == EntityType.STRAY ||
                        mob.getType() == EntityType.WITHER_SKELETON ||
                        mob.getType() == EntityType.BOGGED;
        boolean isZombieFamily =
                mob.getType() == EntityType.ZOMBIE ||
                        mob.getType() == EntityType.HUSK ||
                        mob.getType() == EntityType.DROWNED ||
                        mob.getType() == EntityType.ZOMBIFIED_PIGLIN ||
                        mob.getType() == EntityType.ZOMBIE_VILLAGER;
        boolean isPiglinFamily =
                mob.getType() == EntityType.PIGLIN ||
                        mob.getType() == EntityType.PIGLIN_BRUTE;
        boolean isTargetFamily = isSkeletonFamily || isZombieFamily || isPiglinFamily;

        if (weapon != null && weapon.getType() != Material.AIR) {
            boolean shouldEnchantWeapon = random.nextDouble() < weaponEnchantChance;
            boolean isDrownedTrident    = (mob.getType() == EntityType.DROWNED) && (weapon.getType() == Material.TRIDENT);

            if (shouldEnchantWeapon || isDrownedTrident) {
                if (isSkeletonFamily) {
                    switch (weapon.getType()) {
                        case BOW -> addRandomEnchant(weapon, Map.of(
                                Enchantment.POWER, 5,
                                Enchantment.PUNCH, 2,
                                Enchantment.FLAME, 1,
                                Enchantment.INFINITY, 1
                        ));
                        case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD,
                             WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> addRandomEnchant(weapon, Map.of(
                                Enchantment.SHARPNESS, 5,
                                Enchantment.SMITE, 5,
                                Enchantment.FIRE_ASPECT, 2,
                                Enchantment.LOOTING, 3
                        ));
                        default -> {}
                    }
                    eq.setItemInMainHand(weapon);
                } else if (isZombieFamily || isPiglinFamily) {
                    switch (weapon.getType()) {
                        case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD,
                             WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> addRandomEnchant(weapon, Map.of(
                                Enchantment.SHARPNESS, 5,
                                Enchantment.SMITE, 5,
                                Enchantment.FIRE_ASPECT, 2,
                                Enchantment.LOOTING, 3
                        ));
                        default -> {}
                    }
                    eq.setItemInMainHand(weapon);
                }
            }
        }

        // Armor enchants (only actual armor items)
        if (isTargetFamily) {
            // Helmet
            ItemStack helmet = eq.getHelmet();
            if (helmet != null && helmet.getType() != Material.AIR && isArmor(helmet, EquipmentSlot.HEAD)) {
                if (random.nextDouble() < armorEnchantChance) {
                    enchantArmorIfApplicable(helmet, EquipmentSlot.HEAD);
                    eq.setHelmet(helmet);
                }
            }
            // Chestplate
            ItemStack chest = eq.getChestplate();
            if (chest != null && chest.getType() != Material.AIR && isArmor(chest, EquipmentSlot.CHEST)) {
                if (random.nextDouble() < armorEnchantChance) {
                    enchantArmorIfApplicable(chest, EquipmentSlot.CHEST);
                    eq.setChestplate(chest);
                }
            }
            // Leggings
            ItemStack legs = eq.getLeggings();
            if (legs != null && legs.getType() != Material.AIR && isArmor(legs, EquipmentSlot.LEGS)) {
                if (random.nextDouble() < armorEnchantChance) {
                    enchantArmorIfApplicable(legs, EquipmentSlot.LEGS);
                    eq.setLeggings(legs);
                }
            }
            // Boots
            ItemStack boots = eq.getBoots();
            if (boots != null && boots.getType() != Material.AIR && isArmor(boots, EquipmentSlot.FEET)) {
                if (random.nextDouble() < armorEnchantChance) {
                    enchantArmorIfApplicable(boots, EquipmentSlot.FEET);
                    eq.setBoots(boots);
                }
            }
        }
    }

    private boolean isArmor(ItemStack item, EquipmentSlot slot) {
        if (item == null || item.getType() == Material.AIR) return false;
        return switch (slot) {
            case HEAD -> item.getType().name().endsWith("_HELMET");
            case CHEST -> item.getType().name().endsWith("_CHESTPLATE");
            case LEGS -> item.getType().name().endsWith("_LEGGINGS");
            case FEET -> item.getType().name().endsWith("_BOOTS");
            default -> false;
        };
    }

    private void enchantArmorIfApplicable(ItemStack item, EquipmentSlot slot) {
        if (item == null || item.getType() == Material.AIR) return;
        Map<Enchantment, Integer> enchantOptions = new HashMap<>();
        switch (slot) {
            case HEAD, CHEST, LEGS -> {
                enchantOptions.put(Enchantment.PROTECTION, 4);
                enchantOptions.put(Enchantment.PROJECTILE_PROTECTION, 4);
                enchantOptions.put(Enchantment.BLAST_PROTECTION, 4);
                enchantOptions.put(Enchantment.THORNS, 3);
            }
            case FEET -> {
                enchantOptions.put(Enchantment.PROTECTION, 4);
                enchantOptions.put(Enchantment.PROJECTILE_PROTECTION, 4);
                enchantOptions.put(Enchantment.BLAST_PROTECTION, 4);
                enchantOptions.put(Enchantment.THORNS, 3);
                enchantOptions.put(Enchantment.FEATHER_FALLING, 4);
            }
            default -> {}
        }
        addRandomEnchant(item, enchantOptions);
    }

    private void addRandomEnchant(ItemStack item, Map<Enchantment, Integer> enchants) {
        int num = 1 + random.nextInt(Math.max(1, enchants.size()));
        List<Enchantment> list = new ArrayList<>(enchants.keySet());
        Collections.shuffle(list, random);
        for (int i = 0; i < num; i++) {
            Enchantment ench = list.get(i);
            int maxLvl = enchants.get(ench);
            int lvl = 1 + random.nextInt(maxLvl);
            item.addUnsafeEnchantment(ench, lvl);
        }
    }

    // Jockey logic
    private boolean handleJockeys(Entity entity, CreatureSpawnEvent event) {
        EntityType type = entity.getType();
        Location loc = entity.getLocation();
        World world = entity.getWorld();

        // Chicken jockey (baby zombie-like)
        if ((type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED
                || type == EntityType.ZOMBIFIED_PIGLIN || type == EntityType.ZOMBIE_VILLAGER)
                && entity instanceof Zombie zombie && zombie.isBaby()) {

            String key = switch (type) {
                case ZOMBIE -> "baby_zombie_chicken_jockey";
                case HUSK -> "baby_husk_chicken_jockey";
                case DROWNED -> "baby_drowned_chicken_jockey";
                case ZOMBIFIED_PIGLIN -> "baby_zombified_piglin_chicken_jockey";
                case ZOMBIE_VILLAGER -> "baby_zombie_villager_chicken_jockey";
                default -> "";
            };
            double chance = jockeyChances.getOrDefault(key, 0.0);
            if (random.nextDouble() < chance) {
                event.setCancelled(true);
                Chicken chicken = (Chicken) world.spawnEntity(loc, EntityType.CHICKEN, CreatureSpawnEvent.SpawnReason.CUSTOM);
                LivingEntity baby = (LivingEntity) world.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                if (baby instanceof Zombie bz) bz.setBaby(true);
                chicken.addPassenger(baby);
                handleCustomEquip(baby);
                return true;
            }
        }

        // Spider jockey (skeleton family)
        if (type == EntityType.SKELETON || type == EntityType.STRAY
                || type == EntityType.WITHER_SKELETON || type == EntityType.BOGGED) {
            String key = switch (type) {
                case SKELETON -> "skeleton_spider_jockey";
                case STRAY -> "stray_spider_jockey";
                case WITHER_SKELETON -> "wither_skeleton_spider_jockey";
                case BOGGED -> "bogged_spider_jockey";
                default -> "";
            };
            double chance = jockeyChances.getOrDefault(key, 0.0);
            if (random.nextDouble() < chance) {
                event.setCancelled(true);
                Spider spider = (Spider) world.spawnEntity(loc, EntityType.SPIDER, CreatureSpawnEvent.SpawnReason.CUSTOM);
                LivingEntity skele = (LivingEntity) world.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                spider.addPassenger(skele);
                handleCustomEquip(skele);
                return true;
            }
        }
        return false;
    }

    private void applyPiglinHoglinImmunity(Entity entity) {
        if (entity instanceof Piglin piglin) piglin.setImmuneToZombification(true);
        if (entity instanceof PiglinBrute brute) brute.setImmuneToZombification(true);
        if (entity instanceof Hoglin hoglin) hoglin.setImmuneToZombification(true);
    }

    private boolean shouldMultiply(Entity entity, CreatureSpawnEvent event) {
        if (!(entity instanceof Monster)) return false;
        EntityType type = entity.getType();
        if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER || type == EntityType.WARDEN)
            return false;
        if (mobSpawnMultiplier <= 1.01) return false;
        return event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL;
    }

    private boolean isEligibleSpawn(CreatureSpawnEvent event) {
        // Apply to NATURAL, SPAWNER, and (optionally) SPAWNER_EGG
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) return true;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) return true;
        if (enableSpawnEgg && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return true;
        return false;
    }

    private ItemStack getRandomItem(Map<Material, Double> chanceMap, Map<String, Double> specialMap) {
        if ((chanceMap == null || chanceMap.isEmpty()) && (specialMap == null || specialMap.isEmpty()))
            return null;

        double roll = random.nextDouble();
        double cumulative = 0.0;

        if (chanceMap != null) {
            for (Map.Entry<Material, Double> entry : chanceMap.entrySet()) {
                cumulative += entry.getValue();
                if (roll < cumulative) {
                    ItemStack item = new ItemStack(entry.getKey());
                    ItemMeta meta = item.getItemMeta();
                    if (meta instanceof Damageable dmg) {
                        int max = item.getType().getMaxDurability();
                        if (max > 0) {
                            dmg.setDamage(random.nextInt(Math.max(1, max / 3)));
                            item.setItemMeta(meta);
                        }
                    }
                    return item;
                }
            }
        }
        // Special tokens handled elsewhere when needed (e.g., drowned channeling)
        return null;
    }

    /**
     * replacement-spawn 테이블의 가중치 합이 1.0 미만이면,
     * 남은 (1.0 - 합) 확률로 원본(origin)을 유지한다.
     * 합이 1.0 이상이면 항상 대체된다.
     */
    private EntityType pickReplacementOrOriginal(EntityType origin, Map<EntityType, Double> table) {
        if (table == null || table.isEmpty()) return origin;

        double sum = 0.0;
        for (double w : table.values()) sum += w;
        sum = Math.max(0.0, sum);

        double r = random.nextDouble(); // 0..1
        if (r > Math.min(1.0, sum)) {
            // 남은 확률로 원본 유지
            return origin;
        }

        // 여기까지 왔으면 대체. r을 [0,sum] 구간으로 스케일링해서 선택
        double target = r * (sum <= 0.0 ? 1.0 : sum);
        double acc = 0.0;
        for (Map.Entry<EntityType, Double> e : table.entrySet()) {
            acc += e.getValue();
            if (target <= acc) return e.getKey();
        }
        return origin; // 안전망
    }

    /**
     * Piglin / PiglinBrute / Hoglin을 "처음부터" 면역으로 소환.
     * (Consumer 오버로드가 없는 서버면, 일반 스폰 후 즉시 면역 플래그 적용)
     */
    private LivingEntity spawnImmuneVariant(World world, Location loc, EntityType type) {
        try {
            switch (type) {
                case PIGLIN -> {
                    return world.spawn(loc, Piglin.class, pig -> pig.setImmuneToZombification(true));
                }
                case PIGLIN_BRUTE -> {
                    return world.spawn(loc, PiglinBrute.class, brute -> brute.setImmuneToZombification(true));
                }
                case HOGLIN -> {
                    return world.spawn(loc, Hoglin.class, hog -> hog.setImmuneToZombification(true));
                }
                default -> {
                    return (LivingEntity) world.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                }
            }
        } catch (NoSuchMethodError | UnsupportedOperationException ex) {
            // Consumer 오버로드 미지원 → 일반 소환 후 즉시 면역
            LivingEntity e = (LivingEntity) world.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
            applyPiglinHoglinImmunity(e);
            return e;
        }
    }
}

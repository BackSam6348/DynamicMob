package me.linhyeok;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

/**
 * DynamicMob (single-file build)
 *
 * Features
 * - /dm reload (runtime command registration; no commands in plugin.yml/paper-plugin.yml)
 * - Replacement spawn system (config.replacement & replacement-spawn)
 * - Killer Bunny converter for Rabbit (config.special.killer_bunny_on_rabbit_spawn)
 * - Block helmets: general + skeleton-only (config.special.block_helmet)
 * - Equipment tables per mob (config.spawn-settings)
 * - Vanilla-like enchants with probabilities (config.enchant-chance)
 * - Charged Creeper chance (config.special.charged_creeper)
 * - Jockey spawns (chicken/spider) (config.jockey-chance)
 * - Piglin/Hoglin zombification immunity
 * - Natural spawn gate by light-threshold & per-type chance (config.spawn-chance)
 * - Spawn multiplier for NATURAL spawns
 * - Works for NATURAL/SPAWNER; SPAWNER_EGG is opt-in by config.mob-spawn.enable-spawn-egg
 */
public class DynamicMob extends JavaPlugin implements Listener {

    private final Random random = new Random();
    private FileConfiguration config;

    // global
    private double mobSpawnMultiplier = 1.0;
    private int lightThreshold = 7;
    private boolean enableSpawnEgg = true;

    // enchants
    private double weaponEnchantChance = 0.0;
    private double armorEnchantChance = 0.0;

    // specials
    private double boneInHandChance = 0.0;
    private double drownedChannelingChance = 0.0;
    private double chargedCreeperChance = 0.0;

    // killer bunny
    private boolean kbEnabled = true;
    private double kbChance = 0.0;
    private boolean kbApplyNatural = true;
    private boolean kbApplySpawner = false;
    private boolean kbApplySpawnEgg = true;

    // replacement toggles
    private boolean replApplyNatural = true;
    private boolean replApplySpawner = false;
    private boolean replApplySpawnEgg = false;

    // jockey chances
    private final Map<String, Double> jockeyChances = new HashMap<>();

    // equipment tables
    private final Map<EntityType, Map<String, Map<Material, Double>>> spawnChances = new EnumMap<>(EntityType.class);
    private final Map<EntityType, Map<String, Double>> specialChances = new EnumMap<>(EntityType.class);
    private final Set<EntityType> disabledEntities = EnumSet.noneOf(EntityType.class);

    // natural spawn gate per-type
    private final Map<EntityType, Double> naturalSpawnChance = new EnumMap<>(EntityType.class);

    // replacement map
    private final Map<EntityType, Map<EntityType, Double>> replacementMap = new EnumMap<>(EntityType.class);

    // block helmets
    private Map<Material, Double> generalBlockHelmetChances = new HashMap<>();
    private Map<Material, Double> skeletonBlockHelmetChances = new HashMap<>();

    // ================= lifecycle =================
    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = getConfig();
        loadConfig();

        getServer().getPluginManager().registerEvents(this, this);

        // pretty enable message (single line)
        Bukkit.getConsoleSender().sendMessage(Component.text("» DynamicMob v" + getDescription().getVersion() + " enabled").color(NamedTextColor.GREEN));

        // runtime command registration (no commands section needed)
        try {
            registerRuntimeCommand();
        } catch (Exception e) {
            getLogger().warning("Failed to register /dm command at runtime: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(Component.text("» DynamicMob disabled").color(NamedTextColor.RED));
    }

    // ================= runtime command (/dm reload) =================
    private void registerRuntimeCommand() throws Exception {
        // Build PluginCommand via reflection (PluginCommand(String, Plugin))
        Constructor<PluginCommand> cons = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
        cons.setAccessible(true);

        PluginCommand cmd = cons.newInstance("dm", this);
        cmd.setDescription("DynamicMob command");
        cmd.setUsage("/dm reload");
        cmd.setPermission("dynamicmob.reload");

        // Use this class as executor; handle logic in onCommand()
        cmd.setExecutor(this);

        // Register into the server command map
        SimpleCommandMap map = getCommandMap();
        map.register(getDescription().getName().toLowerCase(Locale.ROOT), cmd);
    }

    private SimpleCommandMap getCommandMap() throws Exception {
        Object server = Bukkit.getServer();
        Field f = server.getClass().getDeclaredField("commandMap");
        f.setAccessible(true);
        return (SimpleCommandMap) f.get(server);
    }

    // ================= config loader =================
    private void loadConfig() {
        // --- enchant-chance ---
        ConfigurationSection enchantSec = config.getConfigurationSection("enchant-chance");
        weaponEnchantChance = enchantSec != null ? enchantSec.getDouble("weapon", 0.0) : 0.0;
        armorEnchantChance  = enchantSec != null ? enchantSec.getDouble("armor", 0.0) : 0.0;

        // --- mob-spawn ---
        ConfigurationSection mobSpawn = config.getConfigurationSection("mob-spawn");
        mobSpawnMultiplier = mobSpawn != null ? mobSpawn.getDouble("multiplier", 1.0) : 1.0;
        enableSpawnEgg     = mobSpawn != null && mobSpawn.getBoolean("enable-spawn-egg", true);

        // --- light-threshold ---
        lightThreshold = config.getInt("light-threshold", 7);

        // --- special ---
        ConfigurationSection special = config.getConfigurationSection("special");
        boneInHandChance     = special != null ? special.getDouble("bone_in_hand", 0.0) : 0.0;
        chargedCreeperChance = special != null ? special.getDouble("charged_creeper", 0.0) : 0.0;

        // killer bunny
        ConfigurationSection kb = special != null ? special.getConfigurationSection("killer_bunny_on_rabbit_spawn") : null;
        if (kb != null) {
            kbEnabled = kb.getBoolean("enabled", true);
            kbChance = kb.getDouble("chance", 0.0);
            ConfigurationSection apply = kb.getConfigurationSection("apply-to");
            if (apply != null) {
                kbApplyNatural  = apply.getBoolean("natural", true);
                kbApplySpawner  = apply.getBoolean("spawner", false);
                kbApplySpawnEgg = apply.getBoolean("spawn-egg", true);
            }
        } else {
            kbEnabled = false; kbChance = 0.0;
            kbApplyNatural = true; kbApplySpawner = false; kbApplySpawnEgg = true;
        }

        // block helmets (general + skeleton)
        generalBlockHelmetChances.clear();
        skeletonBlockHelmetChances.clear();
        if (special != null) {
            ConfigurationSection bh = special.getConfigurationSection("block_helmet");
            if (bh != null) {
                ConfigurationSection general = bh.getConfigurationSection("general");
                if (general != null) {
                    for (String key : general.getKeys(false)) {
                        try {
                            Material m = Material.valueOf(key.toUpperCase(Locale.ROOT));
                            generalBlockHelmetChances.put(m, general.getDouble(key, 0.0));
                        } catch (IllegalArgumentException ex) {
                            getLogger().warning("Invalid material in special.block_helmet.general: " + key);
                        }
                    }
                }
                ConfigurationSection sk = bh.getConfigurationSection("skeleton");
                if (sk != null) {
                    for (String key : sk.getKeys(false)) {
                        try {
                            Material m = Material.valueOf(key.toUpperCase(Locale.ROOT));
                            skeletonBlockHelmetChances.put(m, sk.getDouble(key, 0.0));
                        } catch (IllegalArgumentException ex) {
                            getLogger().warning("Invalid material in special.block_helmet.skeleton: " + key);
                        }
                    }
                }
            }
        }

        // --- jockey-chance ---
        jockeyChances.clear();
        ConfigurationSection jockey = config.getConfigurationSection("jockey-chance");
        if (jockey != null) {
            for (String key : jockey.getKeys(false)) {
                jockeyChances.put(key, jockey.getDouble(key, 0.0));
            }
        }

        // --- spawn-settings ---
        spawnChances.clear();
        specialChances.clear();
        disabledEntities.clear();
        ConfigurationSection spawnSettings = config.getConfigurationSection("spawn-settings");
        if (spawnSettings != null) {
            for (String entityKey : spawnSettings.getKeys(false)) {
                EntityType type;
                try { type = EntityType.valueOf(entityKey); } catch (Exception ignored) { continue; }
                ConfigurationSection eq = spawnSettings.getConfigurationSection(entityKey);
                if (eq == null) continue;

                if (!eq.getBoolean("enabled", true)) {
                    disabledEntities.add(type);
                    continue;
                }

                Map<String, Map<Material, Double>> slotMap = new HashMap<>();
                Map<String, Double> slotSpecial = new HashMap<>();

                for (String slot : eq.getKeys(false)) {
                    if ("enabled".equals(slot)) continue;
                    ConfigurationSection slotSec = eq.getConfigurationSection(slot);
                    if (slotSec == null) continue;

                    Map<Material, Double> chanceMap = new HashMap<>();
                    for (String matKey : slotSec.getKeys(false)) {
                        try {
                            chanceMap.put(Material.valueOf(matKey), slotSec.getDouble(matKey));
                        } catch (IllegalArgumentException ex) {
                            slotSpecial.put(matKey, slotSec.getDouble(matKey));
                        }
                    }
                    slotMap.put(slot, chanceMap);
                }
                spawnChances.put(type, slotMap);
                if (!slotSpecial.isEmpty()) specialChances.put(type, slotSpecial);
            }
        }

        drownedChannelingChance = 0.0;
        if (specialChances.containsKey(EntityType.DROWNED)) {
            drownedChannelingChance = specialChances.get(EntityType.DROWNED).getOrDefault("TRIDENT_CHANNELING", 0.0);
        }

        // --- spawn-chance (natural gate) ---
        naturalSpawnChance.clear();
        ConfigurationSection gate = config.getConfigurationSection("spawn-chance");
        if (gate != null) {
            for (String key : gate.getKeys(false)) {
                try {
                    EntityType t = EntityType.valueOf(key);
                    naturalSpawnChance.put(t, gate.getDouble(key, 1.0));
                } catch (Exception ignored) {}
            }
        }

        // --- replacement toggles & map ---
        ConfigurationSection repl = config.getConfigurationSection("replacement");
        if (repl != null) {
            replApplyNatural  = repl.getBoolean("apply-to-natural", true);
            replApplySpawner  = repl.getBoolean("apply-to-spawner", false);
            replApplySpawnEgg = repl.getBoolean("apply-to-spawn-egg", false);
        } else {
            replApplyNatural = true; replApplySpawner = false; replApplySpawnEgg = false;
        }

        replacementMap.clear();
        ConfigurationSection replMap = config.getConfigurationSection("replacement-spawn");
        if (replMap != null) {
            for (String srcKey : replMap.getKeys(false)) {
                EntityType src;
                try { src = EntityType.valueOf(srcKey); } catch (Exception ignored) { continue; }
                Map<EntityType, Double> targets = new EnumMap<>(EntityType.class);
                ConfigurationSection tSec = replMap.getConfigurationSection(srcKey);
                if (tSec == null) continue;
                for (String trgKey : tSec.getKeys(false)) {
                    try {
                        EntityType trg = EntityType.valueOf(trgKey);
                        targets.put(trg, tSec.getDouble(trgKey, 0.0));
                    } catch (Exception ignored) {}
                }
                if (!targets.isEmpty()) replacementMap.put(src, targets);
            }
        }
    }

    // ================= events =================
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!checkSpawnReasonAllowed(event)) return;

        // Killer Bunny conversion
        maybeConvertToKillerBunny(event);

        // Replacement system (may cancel original & spawn new)
        if (maybeDoReplacement(event)) return;

        Entity e = event.getEntity();
        EntityType type = e.getType();
        Location loc = e.getLocation();
        World world = e.getWorld();

        // Light + per-type chance gate for NATURAL spawns
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            if (e instanceof Monster) {
                if (loc.getBlock().getLightLevel() > lightThreshold) {
                    event.setCancelled(true);
                    return;
                }
            }
            double gate = naturalSpawnChance.getOrDefault(type, 1.0);
            if (random.nextDouble() >= gate) {
                event.setCancelled(true);
                return;
            }
        }

        // Piglin/Hoglin immunities
        applyPiglinHoglinImmunity(e);

        // Spawn multiplier (NATURAL only, non-boss)
        if (shouldMultiply(e, event)) {
            int extra = (int) Math.round(mobSpawnMultiplier - 1.0);
            for (int i = 0; i < extra; i++) {
                LivingEntity dup = (LivingEntity) world.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                handleCustomEquip(dup);
            }
        }

        // Jockeys
        if (handleJockeys(e, event)) return;

        // Equipment/enchants/specials
        handleCustomEquip(e);
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

    // ================= replacement =================
    private boolean maybeDoReplacement(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        boolean allowed =
                (reason == CreatureSpawnEvent.SpawnReason.NATURAL && replApplyNatural) ||
                        (reason == CreatureSpawnEvent.SpawnReason.SPAWNER && replApplySpawner) ||
                        (reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG && replApplySpawnEgg);
        if (!allowed) return false;

        Map<EntityType, Double> table = replacementMap.get(event.getEntityType());
        if (table == null || table.isEmpty()) return false;

        double roll = random.nextDouble();
        double cum = 0.0;
        for (Map.Entry<EntityType, Double> e : table.entrySet()) {
            cum += e.getValue();
            if (roll < cum) {
                event.setCancelled(true);
                Location at = event.getLocation();
                World w = at.getWorld();
                if (w == null) return true;
                LivingEntity spawned = (LivingEntity) w.spawnEntity(at, e.getKey(), CreatureSpawnEvent.SpawnReason.CUSTOM);
                applyPiglinHoglinImmunity(spawned);
                handleCustomEquip(spawned);
                return true;
            }
        }
        return false;
    }

    private boolean checkSpawnReasonAllowed(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) return true;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) return true;
        if (enableSpawnEgg && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) return true;
        return false;
    }

    private void maybeConvertToKillerBunny(CreatureSpawnEvent event) {
        if (!kbEnabled) return;
        if (!(event.getEntity() instanceof Rabbit rabbit)) return;

        boolean allowed =
                (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL && kbApplyNatural) ||
                        (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER && kbApplySpawner) ||
                        (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG && kbApplySpawnEgg);
        if (!allowed) return;

        if (random.nextDouble() < kbChance) {
            rabbit.setRabbitType(Rabbit.Type.THE_KILLER_BUNNY);
        }
    }

    // ================= equipment/enchants/specials =================
    private void handleCustomEquip(Entity entity) {
        if (!(entity instanceof LivingEntity mob)) return;
        if (disabledEntities.contains(mob.getType())) return;

        EntityEquipment eq = mob.getEquipment();
        if (eq == null) return;

        Map<String, Map<Material, Double>> chances = spawnChances.getOrDefault(mob.getType(), new HashMap<>());
        Map<String, Double> specials = specialChances.getOrDefault(mob.getType(), new HashMap<>());

        eq.setItemInMainHand(getRandomItem(chances.get("weapon"), specials));
        eq.setHelmet(getRandomItem(chances.get("helmet"), null));
        eq.setChestplate(getRandomItem(chances.get("chestplate"), null));
        eq.setLeggings(getRandomItem(chances.get("leggings"), null));
        eq.setBoots(getRandomItem(chances.get("boots"), null));

        // skeleton bone
        if (mob instanceof Skeleton && boneInHandChance > 0.0 && random.nextDouble() < boneInHandChance) {
            eq.setItemInMainHand(new ItemStack(Material.BONE));
        }

        // drowned trident with channeling
        if (mob instanceof Drowned && drownedChannelingChance > 0.0 && random.nextDouble() < drownedChannelingChance) {
            ItemStack trident = new ItemStack(Material.TRIDENT);
            trident.addUnsafeEnchantment(Enchantment.CHANNELING, 1);
            eq.setItemInMainHand(trident);
        }

        // charged creeper
        if (mob instanceof Creeper c && chargedCreeperChance > 0.0 && random.nextDouble() < chargedCreeperChance) {
            c.setPowered(true);
        }

        // block helmets (general + skeleton-only)
        applyBlockHelmetIfAny(mob, eq);

        // enchants
        applyVanillaishEnchants(mob, eq);
    }

    private void applyBlockHelmetIfAny(LivingEntity mob, EntityEquipment eq) {
        boolean isSkeletonFamily = mob.getType() == EntityType.SKELETON
                || mob.getType() == EntityType.STRAY
                || mob.getType() == EntityType.WITHER_SKELETON
                || mob.getType() == EntityType.BOGGED;

        Map<Material, Double> map = new HashMap<>(generalBlockHelmetChances);
        if (isSkeletonFamily) {
            for (Map.Entry<Material, Double> e : skeletonBlockHelmetChances.entrySet()) {
                map.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }
        if (map.isEmpty()) return;

        double roll = random.nextDouble();
        double cum = 0.0;
        for (Map.Entry<Material, Double> e : map.entrySet()) {
            cum += e.getValue();
            if (roll < cum) {
                eq.setHelmet(new ItemStack(e.getKey()));
                break;
            }
        }
    }

    private void applyVanillaishEnchants(LivingEntity mob, EntityEquipment eq) {
        boolean isSkeletonFamily = mob.getType() == EntityType.SKELETON
                || mob.getType() == EntityType.STRAY
                || mob.getType() == EntityType.WITHER_SKELETON
                || mob.getType() == EntityType.BOGGED;
        boolean isZombieFamily = mob.getType() == EntityType.ZOMBIE
                || mob.getType() == EntityType.HUSK
                || mob.getType() == EntityType.DROWNED
                || mob.getType() == EntityType.ZOMBIFIED_PIGLIN
                || mob.getType() == EntityType.ZOMBIE_VILLAGER;
        boolean isPiglinFamily = mob.getType() == EntityType.PIGLIN
                || mob.getType() == EntityType.PIGLIN_BRUTE;
        boolean affect = isSkeletonFamily || isZombieFamily || isPiglinFamily;

        ItemStack weapon = eq.getItemInMainHand();
        if (weapon != null && weapon.getType() != Material.AIR) {
            boolean drownedTrident = (mob.getType() == EntityType.DROWNED && weapon.getType() == Material.TRIDENT);
            boolean doEnchant = random.nextDouble() < weaponEnchantChance || drownedTrident;

            if (doEnchant) {
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

        if (affect) {
            ItemStack helm = eq.getHelmet();
            if (isArmor(helm, EquipmentSlot.HEAD) && random.nextDouble() < armorEnchantChance) {
                enchantArmorIfApplicable(helm, EquipmentSlot.HEAD);
                eq.setHelmet(helm);
            }
            ItemStack chest = eq.getChestplate();
            if (isArmor(chest, EquipmentSlot.CHEST) && random.nextDouble() < armorEnchantChance) {
                enchantArmorIfApplicable(chest, EquipmentSlot.CHEST);
                eq.setChestplate(chest);
            }
            ItemStack legs = eq.getLeggings();
            if (isArmor(legs, EquipmentSlot.LEGS) && random.nextDouble() < armorEnchantChance) {
                enchantArmorIfApplicable(legs, EquipmentSlot.LEGS);
                eq.setLeggings(legs);
            }
            ItemStack boots = eq.getBoots();
            if (isArmor(boots, EquipmentSlot.FEET) && random.nextDouble() < armorEnchantChance) {
                enchantArmorIfApplicable(boots, EquipmentSlot.FEET);
                eq.setBoots(boots);
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
        Map<Enchantment, Integer> set = new HashMap<>();
        switch (slot) {
            case HEAD, CHEST, LEGS -> {
                set.put(Enchantment.PROTECTION, 4);
                set.put(Enchantment.PROJECTILE_PROTECTION, 4);
                set.put(Enchantment.BLAST_PROTECTION, 4);
                set.put(Enchantment.THORNS, 3);
            }
            case FEET -> {
                set.put(Enchantment.PROTECTION, 4);
                set.put(Enchantment.PROJECTILE_PROTECTION, 4);
                set.put(Enchantment.BLAST_PROTECTION, 4);
                set.put(Enchantment.THORNS, 3);
                set.put(Enchantment.FEATHER_FALLING, 4);
            }
            default -> {}
        }
        addRandomEnchant(item, set);
    }

    private void addRandomEnchant(ItemStack item, Map<Enchantment, Integer> pool) {
        if (pool.isEmpty()) return;
        int num = 1 + random.nextInt(Math.max(1, pool.size()));
        List<Enchantment> keys = new ArrayList<>(pool.keySet());
        Collections.shuffle(keys, random);
        for (int i = 0; i < num; i++) {
            Enchantment ench = keys.get(i);
            int lvl = 1 + random.nextInt(pool.get(ench));
            item.addUnsafeEnchantment(ench, lvl);
        }
    }

    private ItemStack getRandomItem(Map<Material, Double> chanceMap, Map<String, Double> specialMap) {
        if ((chanceMap == null || chanceMap.isEmpty()) && (specialMap == null || specialMap.isEmpty()))
            return null;

        double roll = random.nextDouble();
        double cumulative = 0.0;

        if (chanceMap != null) {
            for (Map.Entry<Material, Double> e : chanceMap.entrySet()) {
                cumulative += e.getValue();
                if (roll < cumulative) {
                    ItemStack item = new ItemStack(e.getKey());
                    if (item.getItemMeta() instanceof Damageable dmg) {
                        int max = item.getType().getMaxDurability();
                        if (max > 0) {
                            dmg.setDamage(random.nextInt(Math.max(1, max / 3)));
                            item.setItemMeta(dmg);
                        }
                    }
                    return item;
                }
            }
        }
        if (specialMap != null) {
            for (Map.Entry<String, Double> e : specialMap.entrySet()) {
                cumulative += e.getValue();
                if (roll < cumulative && e.getKey().equals("TRIDENT_CHANNELING")) {
                    ItemStack trident = new ItemStack(Material.TRIDENT);
                    trident.addUnsafeEnchantment(Enchantment.CHANNELING, 1);
                    return trident;
                }
            }
        }
        return null;
    }

    private boolean handleJockeys(Entity entity, CreatureSpawnEvent event) {
        EntityType type = entity.getType();
        Location loc = entity.getLocation();
        World world = entity.getWorld();

        if ((type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED
                || type == EntityType.ZOMBIFIED_PIGLIN || type == EntityType.ZOMBIE_VILLAGER)
                && entity instanceof Zombie z && z.isBaby()) {

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
                LivingEntity rider = (LivingEntity) world.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                spider.addPassenger(rider);
                handleCustomEquip(rider);
                return true;
            }
        }
        return false;
    }

    private void applyPiglinHoglinImmunity(Entity e) {
        if (e instanceof Piglin p) p.setImmuneToZombification(true);
        if (e instanceof PiglinBrute b) b.setImmuneToZombification(true);
        if (e instanceof Hoglin h) h.setImmuneToZombification(true);
    }

    private boolean shouldMultiply(Entity entity, CreatureSpawnEvent event) {
        if (!(entity instanceof Monster)) return false;
        EntityType t = entity.getType();
        if (t == EntityType.ENDER_DRAGON || t == EntityType.WITHER || t == EntityType.WARDEN) return false;
        if (mobSpawnMultiplier <= 1.01) return false;
        return event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL;
    }

    // ================= /dm reload =================
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // our runtime command is named "dm"
        if (!label.equalsIgnoreCase("dm")) return false;
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (sender instanceof Player && !sender.hasPermission("dynamicmob.reload")) {
                sender.sendMessage(ChatColor.RED + "[DynamicMob] You don't have permission.");
                return true;
            }
            reloadConfig();
            this.config = getConfig();
            loadConfig();
            // send once only
            sender.sendMessage(ChatColor.GREEN + "[DynamicMob] Config reloaded!");
            getLogger().info("Config reloaded via /dm reload by " + sender.getName());
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Usage: /dm reload");
        return true;
    }
}

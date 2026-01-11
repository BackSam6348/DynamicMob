package me.linhyeok;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * DynamicMob - configuration manager
 * - 월드 화이트리스트(enabled-worlds)
 * - 각종 스폰/장비/특수 확률 로딩
 * - EquipmentManager가 요구하는 전역 특수 확률 게터 포함
 */
public class ConfigManager {
    private final Plugin plugin;

    // ==== 월드 화이트리스트 ====
    private Set<String> enabledWorlds = new HashSet<>();

    // ==== 기본 스폰/설정 ====
    private double mobSpawnMultiplier = 1.0;
    private boolean enableSpawnEgg = true;
    private int lightThreshold = 7;

    // ==== 인첸트 확률 ====
    private double weaponEnchantChance = 1.0;
    private double armorEnchantChance  = 1.0;

    // ==== Killer Bunny ====
    private boolean killerBunnyEnabled = true;
    private double  killerBunnyChance  = 0.001;
    private boolean killerBunnyApplyNatural  = true;
    private boolean killerBunnyApplySpawner  = false;
    private boolean killerBunnyApplySpawnEgg = true;

    // ==== 조키 확률 ====
    private final Map<String, Double> jockeyChances = new HashMap<>();

    // ==== 엔티티별 장비/특수 확률 및 제한 ====
    private final Map<EntityType, Map<String, Map<Material, Double>>> spawnChances = new EnumMap<>(EntityType.class);
    private final Map<EntityType, Map<String, Double>> specialChances = new EnumMap<>(EntityType.class);
    private final Set<EntityType> disabledEntities = EnumSet.noneOf(EntityType.class);
    private final Map<EntityType, Double> naturalSpawnChance = new EnumMap<>(EntityType.class);

    // ==== 대체 스폰 ====
    private final Map<EntityType, Map<EntityType, Double>> replacementChances = new EnumMap<>(EntityType.class);
    private boolean replacementApplyNatural  = true;
    private boolean replacementApplySpawner  = false;
    private boolean replacementApplySpawnEgg = false;

    // ==== 블록 헬멧 ====
    private final Map<Material, Double> generalBlockHelmetChances   = new EnumMap<>(Material.class);
    private final Map<Material, Double> skeletonBlockHelmetChances  = new EnumMap<>(Material.class);
    private boolean blockHelmetEnabled = true;

    // ==== EquipmentManager 전역 특수 확률 ====
    private double boneInHandChance        = 0.0;
    private double drownedChannelingChance = 0.0;
    private double chargedCreeperChance    = 0.0;

    // ==== Vindicator/Illusioner 손 아이템 ====
    private Material vindicatorHandItem = null;
    private Material illusionerHandItem = null;
    private double illusionerFlameChance = 0.0;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        final var cfg = plugin.getConfig();

        // ---- enabled-worlds ----
        List<String> worlds = cfg.getStringList("enabled-worlds");
        if (worlds == null || worlds.isEmpty()) {
            enabledWorlds = new HashSet<>(Arrays.asList("world", "world_nether", "world_the_end"));
        } else {
            enabledWorlds = new HashSet<>(worlds);
        }

        // ---- mob-spawn ----
        ConfigurationSection mobSpawn = cfg.getConfigurationSection("mob-spawn");
        mobSpawnMultiplier = (mobSpawn != null) ? mobSpawn.getDouble("multiplier", 1.0) : 1.0;
        enableSpawnEgg     = mobSpawn != null && mobSpawn.getBoolean("enable-spawn-egg", true);

        // ---- light-threshold ----
        lightThreshold = cfg.getInt("light-threshold", 7);

        // ---- enchant-chance ----
        ConfigurationSection enchantSec = cfg.getConfigurationSection("enchant-chance");
        weaponEnchantChance = (enchantSec != null) ? enchantSec.getDouble("weapon", 1.0) : 1.0;
        armorEnchantChance  = (enchantSec != null) ? enchantSec.getDouble("armor", 1.0)  : 1.0;

        // ---- special root ----
        ConfigurationSection specialRoot = cfg.getConfigurationSection("special");

        // Killer Bunny
        if (specialRoot != null) {
            ConfigurationSection killer = specialRoot.getConfigurationSection("killer_bunny_on_rabbit_spawn");
            if (killer != null) {
                killerBunnyEnabled       = killer.getBoolean("enabled", true);
                killerBunnyChance        = killer.getDouble("chance", 0.001);
                killerBunnyApplyNatural  = killer.getBoolean("apply-to-natural", true);
                killerBunnyApplySpawner  = killer.getBoolean("apply-to-spawner", false);
                killerBunnyApplySpawnEgg = killer.getBoolean("apply-to-spawn-egg", true);
            }

            // EquipmentManager 전역 특수 확률
            boneInHandChance        = specialRoot.getDouble("bone_in_hand_chance", 0.0);
            drownedChannelingChance = specialRoot.getDouble("drowned_channeling_chance", 0.0);
            chargedCreeperChance    = specialRoot.getDouble("charged_creeper_chance", 0.0);

            // Vindicator/Illusioner 손 아이템
            String vindicatorItemStr = specialRoot.getString("vindicator_hand_item", null);
            if (vindicatorItemStr != null && !vindicatorItemStr.equalsIgnoreCase("NONE")) {
                try {
                    vindicatorHandItem = Material.valueOf(vindicatorItemStr.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid vindicator_hand_item: " + vindicatorItemStr);
                    vindicatorHandItem = null;
                }
            } else {
                vindicatorHandItem = null;
            }

            String illusionerItemStr = specialRoot.getString("illusioner_hand_item", null);
            if (illusionerItemStr != null && !illusionerItemStr.equalsIgnoreCase("NONE")) {
                try {
                    illusionerHandItem = Material.valueOf(illusionerItemStr.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid illusioner_hand_item: " + illusionerItemStr);
                    illusionerHandItem = null;
                }
            } else {
                illusionerHandItem = null;
            }

            // Illusioner Flame 인챈트 확률
            illusionerFlameChance = specialRoot.getDouble("illusioner_flame_chance", 0.0);
        }

        // ---- jockey-chance ----
        jockeyChances.clear();
        ConfigurationSection jcs = cfg.getConfigurationSection("jockey-chance");
        if (jcs != null) {
            for (String key : jcs.getKeys(false)) {
                jockeyChances.put(key.toLowerCase(Locale.ROOT), jcs.getDouble(key, 0.0));
            }
        }

        // ---- spawn-chance & entity specials ----
        spawnChances.clear();
        specialChances.clear();
        disabledEntities.clear();
        naturalSpawnChance.clear();

        ConfigurationSection spawnSec = cfg.getConfigurationSection("spawn-chance");
        if (spawnSec != null) {
            for (String etName : spawnSec.getKeys(false)) {
                EntityType type;
                try {
                    type = EntityType.valueOf(etName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Unknown entity type in spawn-chance: " + etName);
                    continue;
                }
                ConfigurationSection entSec = spawnSec.getConfigurationSection(etName);
                if (entSec == null) continue;

                // enable/disable
                boolean enabled = entSec.getBoolean("enabled", true);
                if (!enabled) disabledEntities.add(type);

                // natural-limit
                if (entSec.isSet("natural-limit")) {
                    naturalSpawnChance.put(type, entSec.getDouble("natural-limit", 1.0));
                }

                // equipment chances
                Map<String, Map<Material, Double>> slotMap = new HashMap<>();
                for (String slot : Arrays.asList("weapon", "helmet", "chestplate", "leggings", "boots")) {
                    ConfigurationSection slotSec = entSec.getConfigurationSection(slot);
                    if (slotSec != null) {
                        Map<Material, Double> m = new EnumMap<>(Material.class);
                        for (String matName : slotSec.getKeys(false)) {
                            try {
                                m.put(Material.valueOf(matName.toUpperCase(Locale.ROOT)), slotSec.getDouble(matName, 0.0));
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Invalid material in " + etName + "." + slot + ": " + matName);
                            }
                        }
                        slotMap.put(slot, m);
                    }
                }
                spawnChances.put(type, slotMap);

                // entity special chances
                Map<String, Double> spec = new HashMap<>();
                ConfigurationSection entSpecSec = entSec.getConfigurationSection("special");
                if (entSpecSec != null) {
                    for (String k : entSpecSec.getKeys(false)) {
                        spec.put(k.toUpperCase(Locale.ROOT), entSpecSec.getDouble(k, 0.0));
                    }
                }
                specialChances.put(type, spec);
            }
        }

        // ---- replacement-spawn ----
        replacementChances.clear();
        ConfigurationSection rep = cfg.getConfigurationSection("replacement-spawn");
        if (rep != null) {
            for (String srcName : rep.getKeys(false)) {
                EntityType src;
                try {
                    src = EntityType.valueOf(srcName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Unknown entity type in replacement-spawn: " + srcName);
                    continue;
                }
                ConfigurationSection m = rep.getConfigurationSection(srcName);
                if (m == null) continue;

                Map<EntityType, Double> inner = new EnumMap<>(EntityType.class);
                for (String tgtName : m.getKeys(false)) {
                    try {
                        inner.put(EntityType.valueOf(tgtName.toUpperCase(Locale.ROOT)), m.getDouble(tgtName, 0.0));
                    } catch (IllegalArgumentException e1) {
                        plugin.getLogger().warning("Unknown target entity in replacement-spawn." + srcName + ": " + tgtName);
                    }
                }
                replacementChances.put(src, inner);
            }
        }

        // ---- replacement toggles ----
        ConfigurationSection repTog = cfg.getConfigurationSection("replacement-toggles");
        replacementApplyNatural  = repTog != null && repTog.getBoolean("apply-to-natural", true);
        replacementApplySpawner  = repTog != null && repTog.getBoolean("apply-to-spawner", false);
        replacementApplySpawnEgg = repTog != null && repTog.getBoolean("apply-to-spawn-egg", false);

        // ---- block_helmet ----
        generalBlockHelmetChances.clear();
        skeletonBlockHelmetChances.clear();
        ConfigurationSection specialRoot2 = cfg.getConfigurationSection("special");
        if (specialRoot2 != null) {
            ConfigurationSection bh = specialRoot2.getConfigurationSection("block_helmet");
            if (bh != null) {
                ConfigurationSection general = bh.getConfigurationSection("general");
                if (general != null) {
                    for (String k : general.getKeys(false)) {
                        try {
                            generalBlockHelmetChances.put(Material.valueOf(k.toUpperCase(Locale.ROOT)), general.getDouble(k, 0.0));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid block_helmet.general material: " + k);
                        }
                    }
                }
                ConfigurationSection skeleton = bh.getConfigurationSection("skeleton-only");
                if (skeleton != null) {
                    for (String k : skeleton.getKeys(false)) {
                        try {
                            skeletonBlockHelmetChances.put(Material.valueOf(k.toUpperCase(Locale.ROOT)), skeleton.getDouble(k, 0.0));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid block_helmet.skeleton-only material: " + k);
                        }
                    }
                }
                blockHelmetEnabled = bh.getBoolean("enabled", true);
            }
        }
    }

    // ===== Getter =====

    // 기본 스폰/설정
    public double getMobSpawnMultiplier() { return mobSpawnMultiplier; }
    public boolean isEnableSpawnEgg()     { return enableSpawnEgg; }
    public int getLightThreshold()        { return lightThreshold; }

    // 인첸트 확률
    public double getWeaponEnchantChance() { return weaponEnchantChance; }
    public double getArmorEnchantChance()  { return armorEnchantChance; }

    // Killer Bunny
    public boolean isKillerBunnyEnabled()         { return killerBunnyEnabled; }
    public double  getKillerBunnyChance()         { return killerBunnyChance; }
    public boolean isKillerBunnyApplyNatural()    { return killerBunnyApplyNatural; }
    public boolean isKillerBunnyApplySpawner()    { return killerBunnyApplySpawner; }
    public boolean isKillerBunnyApplySpawnEgg()   { return killerBunnyApplySpawnEgg; }

    // 조키/스폰/특수/비활성/자연 제한
    public Map<String, Double> getJockeyChances() { return jockeyChances; }
    public Map<EntityType, Map<String, Map<Material, Double>>> getSpawnChances() { return spawnChances; }
    public Map<EntityType, Map<String, Double>> getSpecialChances()              { return specialChances; }
    public Set<EntityType> getDisabledEntities()                                  { return disabledEntities; }
    public Map<EntityType, Double> getNaturalSpawnChance()                        { return naturalSpawnChance; }

    // 대체 스폰
    public Map<EntityType, Map<EntityType, Double>> getReplacementChances() { return replacementChances; }
    public boolean isReplacementApplyNatural()   { return replacementApplyNatural; }
    public boolean isReplacementApplySpawner()   { return replacementApplySpawner; }
    public boolean isReplacementApplySpawnEgg()  { return replacementApplySpawnEgg; }

    // 블록 헬멧
    public Map<Material, Double> getGeneralBlockHelmetChances()  { return generalBlockHelmetChances; }
    public Map<Material, Double> getSkeletonBlockHelmetChances() { return skeletonBlockHelmetChances; }
    public boolean isBlockHelmetEnabled()                         { return blockHelmetEnabled; }

    // 월드 화이트리스트
    public Set<String> getEnabledWorlds() {
        return Collections.unmodifiableSet(enabledWorlds);
    }
    public boolean isWorldEnabled(World world) {
        if (world == null) return false;
        return enabledWorlds.contains(world.getName());
    }

    // EquipmentManager 전역 특수 확률
    public double getBoneInHandChance()        { return boneInHandChance; }
    public double getDrownedChannelingChance() { return drownedChannelingChance; }
    public double getChargedCreeperChance()    { return chargedCreeperChance; }

    // Vindicator/Illusioner 손 아이템
    public Material getVindicatorHandItem() { return vindicatorHandItem; }
    public Material getIllusionerHandItem() { return illusionerHandItem; }
    public double getIllusionerFlameChance() { return illusionerFlameChance; }
}

package me.linhyeok;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class ConfigManager {
    private final Plugin plugin;

    // 기본 스폰/설정
    private double mobSpawnMultiplier = 1.0;
    private boolean enableSpawnEgg = true;
    private int lightThreshold = 7; // 읽긴 하지만 강제 사용 안 함 (요청 반영 유지)

    // 인첸트 확률
    private double weaponEnchantChance = 1.0;
    private double armorEnchantChance = 1.0;

    // 스페셜
    private double boneInHandChance = 0.0;
    private double chargedCreeperChance = 0.0;
    private double drownedChannelingChance = 0.0;

    // 블록 헬멧
    private final Map<Material, Double> generalBlockHelmetChances = new HashMap<>();
    private final Map<Material, Double> skeletonBlockHelmetChances = new HashMap<>();

    // 킬러토끼
    private boolean killerBunnyEnabled = false;
    private double killerBunnyChance = 0.0;
    private boolean killerBunnyApplyNatural = true;
    private boolean killerBunnyApplySpawner = false;
    private boolean killerBunnyApplySpawnEgg = true;

    // 조키 확률
    private final Map<String, Double> jockeyChances = new HashMap<>();

    // 장비/특수 확률 테이블
    // entity -> slot(weapon/helmet/...) -> material -> chance
    private final Map<EntityType, Map<String, Map<Material, Double>>> spawnChances = new EnumMap<>(EntityType.class);
    // entity -> specialKey -> chance (예: TRIDENT_CHANNELING)
    private final Map<EntityType, Map<String, Double>> specialChances = new EnumMap<>(EntityType.class);
    private final Set<EntityType> disabledEntities = EnumSet.noneOf(EntityType.class);

    // 자연스폰 확률(개별 엔티티 제한)
    private final Map<EntityType, Double> naturalSpawnChance = new EnumMap<>(EntityType.class);

    // 대체 스폰
    // source -> (target -> chance)
    private final Map<EntityType, Map<EntityType, Double>> replacementChances = new EnumMap<>(EntityType.class);
    private boolean replacementApplyNatural = true;
    private boolean replacementApplySpawner = false;
    private boolean replacementApplySpawnEgg = false;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        var cfg = plugin.getConfig();

        // mob-spawn
        ConfigurationSection mobSpawn = cfg.getConfigurationSection("mob-spawn");
        mobSpawnMultiplier = (mobSpawn != null) ? mobSpawn.getDouble("multiplier", 1.0) : 1.0;
        enableSpawnEgg = mobSpawn != null && mobSpawn.getBoolean("enable-spawn-egg", true);

        lightThreshold = cfg.getInt("light-threshold", 7);

        // enchant-chance
        ConfigurationSection enchantSec = cfg.getConfigurationSection("enchant-chance");
        weaponEnchantChance = enchantSec != null ? enchantSec.getDouble("weapon", 1.0) : 1.0;
        armorEnchantChance  = enchantSec != null ? enchantSec.getDouble("armor", 1.0)  : 1.0;

        // special
        ConfigurationSection special = cfg.getConfigurationSection("special");
        boneInHandChance = (special != null) ? special.getDouble("bone_in_hand", 0.0) : 0.0;
        chargedCreeperChance = (special != null) ? special.getDouble("charged_creeper", 0.0) : 0.0;

        // block_helmet
        generalBlockHelmetChances.clear();
        skeletonBlockHelmetChances.clear();
        if (special != null) {
            ConfigurationSection bh = special.getConfigurationSection("block_helmet");
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
                ConfigurationSection skeleton = bh.getConfigurationSection("skeleton");
                if (skeleton != null) {
                    for (String k : skeleton.getKeys(false)) {
                        try {
                            skeletonBlockHelmetChances.put(Material.valueOf(k.toUpperCase(Locale.ROOT)), skeleton.getDouble(k, 0.0));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid block_helmet.skeleton material: " + k);
                        }
                    }
                }
            }
        }

        // killer bunny
        if (special != null) {
            ConfigurationSection kb = special.getConfigurationSection("killer_bunny_on_rabbit_spawn");
            if (kb != null) {
                killerBunnyEnabled = kb.getBoolean("enabled", false);
                killerBunnyChance = kb.getDouble("chance", 0.0);
                ConfigurationSection applyTo = kb.getConfigurationSection("apply-to");
                if (applyTo != null) {
                    killerBunnyApplyNatural = applyTo.getBoolean("natural", true);
                    killerBunnyApplySpawner = applyTo.getBoolean("spawner", false);
                    killerBunnyApplySpawnEgg = applyTo.getBoolean("spawn-egg", true);
                }
            }
        }

        // jockey-chance
        jockeyChances.clear();
        ConfigurationSection jockey = cfg.getConfigurationSection("jockey-chance");
        if (jockey != null) {
            for (String key : jockey.getKeys(false)) {
                jockeyChances.put(key, jockey.getDouble(key, 0.0));
            }
        }

        // spawn-settings
        spawnChances.clear();
        specialChances.clear();
        disabledEntities.clear();
        ConfigurationSection spawnSettings = cfg.getConfigurationSection("spawn-settings");
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
                            // 특수 키 (TRIDENT_CHANNELING 등)
                            slotSpecial.put(materialKey, slotSection.getDouble(materialKey));
                        }
                    }
                    slotMap.put(slot, chanceMap);
                }

                spawnChances.put(type, slotMap);
                if (!slotSpecial.isEmpty()) specialChances.put(type, slotSpecial);
            }
        }

        // drowned channeling 확률 별도 저장
        drownedChannelingChance = 0.0;
        if (specialChances.containsKey(EntityType.DROWNED)) {
            drownedChannelingChance = specialChances.get(EntityType.DROWNED).getOrDefault("TRIDENT_CHANNELING", 0.0);
        }

        // 자연스폰 확률
        naturalSpawnChance.clear();
        ConfigurationSection chanceSection = cfg.getConfigurationSection("spawn-chance");
        if (chanceSection != null) {
            for (String key : chanceSection.getKeys(false)) {
                try {
                    EntityType type = EntityType.valueOf(key);
                    naturalSpawnChance.put(type, chanceSection.getDouble(key, 1.0));
                } catch (Exception ignored) {}
            }
        }

        // replacement
        replacementChances.clear();
        ConfigurationSection replApply = cfg.getConfigurationSection("replacement");
        if (replApply != null) {
            replacementApplyNatural = replApply.getBoolean("apply-to-natural", true);
            replacementApplySpawner = replApply.getBoolean("apply-to-spawner", false);
            replacementApplySpawnEgg = replApply.getBoolean("apply-to-spawn-egg", false);
        }

        ConfigurationSection repl = cfg.getConfigurationSection("replacement-spawn");
        if (repl != null) {
            for (String srcKey : repl.getKeys(false)) {
                try {
                    EntityType src = EntityType.valueOf(srcKey);
                    ConfigurationSection inner = repl.getConfigurationSection(srcKey);
                    if (inner == null) continue;
                    Map<EntityType, Double> map = new HashMap<>();
                    for (String dstKey : inner.getKeys(false)) {
                        try {
                            EntityType dst = EntityType.valueOf(dstKey);
                            double v = inner.getDouble(dstKey, 0.0);
                            if (v > 0) map.put(dst, v);
                        } catch (IllegalArgumentException ignored) {
                            plugin.getLogger().warning("Invalid replacement target: " + dstKey);
                        }
                    }
                    replacementChances.put(src, map);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid replacement source: " + srcKey);
                }
            }
        }
    }

    // ======= Getters =======
    public double getMobSpawnMultiplier() { return mobSpawnMultiplier; }
    public boolean isEnableSpawnEgg() { return enableSpawnEgg; }
    public int getLightThreshold() { return lightThreshold; } // 현재 강제 적용 안 함 (요청에 따라 유지)

    public double getWeaponEnchantChance() { return weaponEnchantChance; }
    public double getArmorEnchantChance() { return armorEnchantChance; }

    public double getBoneInHandChance() { return boneInHandChance; }
    public double getChargedCreeperChance() { return chargedCreeperChance; }
    public double getDrownedChannelingChance() { return drownedChannelingChance; }

    public Map<Material, Double> getGeneralBlockHelmetChances() { return generalBlockHelmetChances; }
    public Map<Material, Double> getSkeletonBlockHelmetChances() { return skeletonBlockHelmetChances; }

    public boolean isKillerBunnyEnabled() { return killerBunnyEnabled; }
    public double getKillerBunnyChance() { return killerBunnyChance; }
    public boolean isKillerBunnyApplyNatural() { return killerBunnyApplyNatural; }
    public boolean isKillerBunnyApplySpawner() { return killerBunnyApplySpawner; }
    public boolean isKillerBunnyApplySpawnEgg() { return killerBunnyApplySpawnEgg; }

    public Map<String, Double> getJockeyChances() { return jockeyChances; }

    public Map<EntityType, Map<String, Map<Material, Double>>> getSpawnChances() { return spawnChances; }
    public Map<EntityType, Map<String, Double>> getSpecialChances() { return specialChances; }
    public Set<EntityType> getDisabledEntities() { return disabledEntities; }

    public Map<EntityType, Double> getNaturalSpawnChance() { return naturalSpawnChance; }

    public Map<EntityType, Map<EntityType, Double>> getReplacementChances() { return replacementChances; }
    public boolean isReplacementApplyNatural() { return replacementApplyNatural; }
    public boolean isReplacementApplySpawner() { return replacementApplySpawner; }
    public boolean isReplacementApplySpawnEgg() { return replacementApplySpawnEgg; }
}

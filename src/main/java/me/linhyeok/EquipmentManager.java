package me.linhyeok;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class EquipmentManager {
    private final Plugin plugin;
    private final ConfigManager cfg;
    private final Random random = new Random();

    public EquipmentManager(Plugin plugin, ConfigManager cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    /** 엔티티에 커스텀 장비/블록헬멧/특수효과/인첸트 적용 */
    public void applyAll(LivingEntity mob) {
        if (cfg.getDisabledEntities().contains(mob.getType())) return;

        EntityEquipment eq = mob.getEquipment();
        if (eq == null) return;

        // 기본 장비 확률 테이블
        Map<String, Map<Material, Double>> chances = cfg.getSpawnChances().getOrDefault(mob.getType(), Collections.emptyMap());
        Map<String, Double> specials = cfg.getSpecialChances().getOrDefault(mob.getType(), Collections.emptyMap());

        eq.setItemInMainHand(getRandomItem(chances.get("weapon"), specials));
        eq.setHelmet(getRandomItem(chances.get("helmet"), null));
        eq.setChestplate(getRandomItem(chances.get("chestplate"), null));
        eq.setLeggings(getRandomItem(chances.get("leggings"), null));
        eq.setBoots(getRandomItem(chances.get("boots"), null));

        // 블록 헬멧 (일반 + 스켈레톤 계열 전용)
        applyBlockHelmetIfAny(mob);

        // 스켈레톤 뼈다귀 손(확률)
        if (mob instanceof Skeleton && random.nextDouble() < cfg.getBoneInHandChance()) {
            eq.setItemInMainHand(new ItemStack(Material.BONE));
        }

        // 드라운드: 채널링 삼지창 확률
        if (mob instanceof Drowned && cfg.getDrownedChannelingChance() > 0) {
            if (random.nextDouble() < cfg.getDrownedChannelingChance()) {
                ItemStack trident = new ItemStack(Material.TRIDENT);
                trident.addUnsafeEnchantment(Enchantment.CHANNELING, 1);
                eq.setItemInMainHand(trident);
            }
        }

        // 충전 크리퍼
        if (mob instanceof Creeper creeper && cfg.getChargedCreeperChance() > 0) {
            if (random.nextDouble() < cfg.getChargedCreeperChance()) creeper.setPowered(true);
        }

        // 인첸트 (무기/방어구)
        applyEnchantments(mob, eq);
    }

    private void applyBlockHelmetIfAny(LivingEntity mob) {
        EntityEquipment eq = mob.getEquipment();
        if (eq == null) return;

        boolean isSkeletonFamily = mob.getType() == EntityType.SKELETON
                || mob.getType() == EntityType.STRAY
                || mob.getType() == EntityType.WITHER_SKELETON
                || mob.getType() == EntityType.BOGGED;

        Map<Material, Double> pool = new HashMap<>(cfg.getGeneralBlockHelmetChances());
        if (isSkeletonFamily) {
            // 스켈레톤 계열은 전용 풀 추가
            pool.putAll(cfg.getSkeletonBlockHelmetChances());
        }
        if (pool.isEmpty()) return;

        // 첫 성공 확률의 블록을 헬멧으로 장착
        for (Map.Entry<Material, Double> e : pool.entrySet()) {
            if (random.nextDouble() < e.getValue()) {
                eq.setHelmet(new ItemStack(e.getKey()));
                break;
            }
        }
    }

    private ItemStack getRandomItem(Map<Material, Double> chanceMap, Map<String, Double> specialMap) {
        if ((chanceMap == null || chanceMap.isEmpty()) && (specialMap == null || specialMap.isEmpty())) return null;

        double roll = random.nextDouble(), cumulative = 0.0;
        if (chanceMap != null && !chanceMap.isEmpty()) {
            for (Map.Entry<Material, Double> entry : chanceMap.entrySet()) {
                cumulative += entry.getValue();
                if (roll < cumulative) {
                    ItemStack item = new ItemStack(entry.getKey());
                    // 내구도 랜덤
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
        if (specialMap != null && !specialMap.isEmpty()) {
            for (Map.Entry<String, Double> entry : specialMap.entrySet()) {
                cumulative += entry.getValue();
                if (roll < cumulative && entry.getKey().equalsIgnoreCase("TRIDENT_CHANNELING")) {
                    ItemStack trident = new ItemStack(Material.TRIDENT);
                    trident.addUnsafeEnchantment(Enchantment.CHANNELING, 1);
                    return trident;
                }
            }
        }
        return null;
    }

    private void applyEnchantments(LivingEntity mob, EntityEquipment eq) {
        if (eq == null) return;

        boolean isSkeleton = mob.getType() == EntityType.SKELETON
                || mob.getType() == EntityType.STRAY
                || mob.getType() == EntityType.WITHER_SKELETON
                || mob.getType() == EntityType.BOGGED;

        boolean isZombie = mob.getType() == EntityType.ZOMBIE
                || mob.getType() == EntityType.HUSK
                || mob.getType() == EntityType.DROWNED
                || mob.getType() == EntityType.ZOMBIFIED_PIGLIN
                || mob.getType() == EntityType.ZOMBIE_VILLAGER;

        boolean isPiglin = mob.getType() == EntityType.PIGLIN
                || mob.getType() == EntityType.PIGLIN_BRUTE;

        boolean targetFamily = isSkeleton || isZombie || isPiglin;

        // 무기
        ItemStack main = eq.getItemInMainHand();
        if (main != null && main.getType() != Material.AIR) {
            boolean isDrownedTrident = (mob.getType() == EntityType.DROWNED) && (main.getType() == Material.TRIDENT);
            if (isDrownedTrident || Math.random() < cfg.getWeaponEnchantChance()) {
                if (isSkeleton) {
                    switch (main.getType()) {
                        case BOW -> addRandomEnchant(main, Map.of(
                                Enchantment.POWER, 5,
                                Enchantment.PUNCH, 2,
                                Enchantment.FLAME, 1,
                                Enchantment.INFINITY, 1
                        ));
                        case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD,
                             WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE ->
                                addRandomEnchant(main, Map.of(
                                        Enchantment.SHARPNESS, 5,
                                        Enchantment.SMITE, 5,
                                        Enchantment.FIRE_ASPECT, 2,
                                        Enchantment.LOOTING, 3
                                ));
                        default -> {}
                    }
                } else if (isZombie || isPiglin) {
                    switch (main.getType()) {
                        case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD,
                             WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE ->
                                addRandomEnchant(main, Map.of(
                                        Enchantment.SHARPNESS, 5,
                                        Enchantment.SMITE, 5,
                                        Enchantment.FIRE_ASPECT, 2,
                                        Enchantment.LOOTING, 3
                                ));
                        default -> {}
                    }
                }
            }
        }

        // 방어구
        if (targetFamily) {
            enchantArmorPiece(eq.getHelmet(), EquipmentSlot.HEAD);
            enchantArmorPiece(eq.getChestplate(), EquipmentSlot.CHEST);
            enchantArmorPiece(eq.getLeggings(), EquipmentSlot.LEGS);
            enchantArmorPiece(eq.getBoots(), EquipmentSlot.FEET);
        }
    }

    private boolean isArmor(ItemStack item, EquipmentSlot slot) {
        if (item == null || item.getType() == Material.AIR) return false;
        switch (slot) {
            case HEAD:  return item.getType().name().endsWith("_HELMET");
            case CHEST: return item.getType().name().endsWith("_CHESTPLATE");
            case LEGS:  return item.getType().name().endsWith("_LEGGINGS");
            case FEET:  return item.getType().name().endsWith("_BOOTS");
            default:    return false;
        }
    }

    private void enchantArmorPiece(ItemStack item, EquipmentSlot slot) {
        if (item == null || item.getType() == Material.AIR) return;
        if (!isArmor(item, slot)) return;
        if (Math.random() >= cfg.getArmorEnchantChance()) return;

        Map<Enchantment, Integer> opts = new HashMap<>();
        switch (slot) {
            case HEAD, CHEST, LEGS -> {
                opts.put(Enchantment.PROTECTION, 4);
                opts.put(Enchantment.PROJECTILE_PROTECTION, 4);
                opts.put(Enchantment.BLAST_PROTECTION, 4);
                opts.put(Enchantment.THORNS, 3);
            }
            case FEET -> {
                opts.put(Enchantment.PROTECTION, 4);
                opts.put(Enchantment.PROJECTILE_PROTECTION, 4);
                opts.put(Enchantment.BLAST_PROTECTION, 4);
                opts.put(Enchantment.THORNS, 3);
                opts.put(Enchantment.FEATHER_FALLING, 4);
            }
        }
        addRandomEnchant(item, opts);
    }

    private void addRandomEnchant(ItemStack item, Map<Enchantment, Integer> enchants) {
        if (enchants.isEmpty()) return;
        int num = 1 + random.nextInt(Math.max(1, enchants.size()));
        List<Enchantment> list = new ArrayList<>(enchants.keySet());
        Collections.shuffle(list, random);
        for (int i = 0; i < num; i++) {
            Enchantment ench = list.get(i);
            int maxLvl = enchants.get(ench);
            int lvl = 1 + random.nextInt(maxLvl);
            try {
                item.addUnsafeEnchantment(ench, lvl);
            } catch (Exception ignored) {}
        }
    }
}

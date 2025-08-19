package me.linhyeok;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;

import java.util.Map;
import java.util.Random;

public class SpawnListener implements Listener {

    private final DynamicMob plugin;
    private final ConfigManager cfg;
    private final EquipmentManager equip;
    private final Random random = new Random();

    public SpawnListener(DynamicMob plugin, ConfigManager cfg, EquipmentManager equip) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.equip = equip;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();

        // --- Killer Bunny (토끼 스폰 시 변환)
        if (entity instanceof Rabbit rabbit) {
            if (cfg.isKillerBunnyEnabled() && shouldApplyForReason(reason,
                    cfg.isKillerBunnyApplyNatural(), cfg.isKillerBunnyApplySpawner(), cfg.isKillerBunnyApplySpawnEgg())) {
                if (random.nextDouble() < cfg.getKillerBunnyChance()) {
                    try {
                        rabbit.setRabbitType(Rabbit.Type.THE_KILLER_BUNNY);
                    } catch (Throwable t) {
                        // 버전에 따라 비허용인 경우도 있으므로 실패해도 무시
                        plugin.getLogger().warning("Failed to set Killer Bunny type (ignored).");
                    }
                }
            }
            // 토끼는 여기서 종료 (나머지 로직은 몬스터 처리)
        }

        // --- Replacement (대체 스폰)
        if (shouldApplyReplacementForReason(reason)) {
            if (tryReplacement(event)) {
                return; // 대체 성공 시 함수 종료 (대체 엔티티는 커스텀 스폰에서 처리됨)
            }
        }

        // --- NATURAL 스폰 확률 제한 (원본 엔티티에만, 대체 성공 시엔 이미 return)
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL) {
            double chance = cfg.getNaturalSpawnChance().getOrDefault(entity.getType(), 1.0);
            if (random.nextDouble() >= chance) {
                event.setCancelled(true);
                return;
            }
        }

        // --- 몹 스폰 멀티플라이어 (보스 제외, NATURAL 만)
        if (shouldMultiply(entity, reason)) {
            multiply(entity);
        }

        // --- 조키 (확률에 따라 원래 엔티티를 취소하고 커스텀 스폰) ---
        if (handleJockeys(entity, event)) return;

        // --- 피글린/브루트/호글린 좀비화 면역 부여
        applyPiglinHoglinImmunity(entity);

        // --- 장비/인첸트/충전 등 적용 (스포너/스폰알 포함)
        if (entity instanceof LivingEntity le) {
            equip.applyAll(le);
        }
    }

    // 변환(좀비화) 방지
    @EventHandler
    public void onTransform(EntityTransformEvent event) {
        EntityType from = event.getEntityType();
        EntityType to = event.getTransformedEntity().getType();
        if ((from == EntityType.PIGLIN || from == EntityType.PIGLIN_BRUTE || from == EntityType.HOGLIN)
                && (to == EntityType.ZOMBIFIED_PIGLIN || to == EntityType.ZOGLIN)) {
            event.setCancelled(true);
        }
    }

    private boolean shouldApplyForReason(CreatureSpawnEvent.SpawnReason reason, boolean nat, boolean spawner, boolean egg) {
        return switch (reason) {
            case NATURAL -> nat;
            case SPAWNER -> spawner;
            case SPAWNER_EGG -> egg;
            default -> false;
        };
    }

    private boolean shouldApplyReplacementForReason(CreatureSpawnEvent.SpawnReason reason) {
        return switch (reason) {
            case NATURAL -> cfg.isReplacementApplyNatural();
            case SPAWNER -> cfg.isReplacementApplySpawner();
            case SPAWNER_EGG -> cfg.isReplacementApplySpawnEgg();
            default -> false;
        };
    }

    private boolean tryReplacement(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        var map = cfg.getReplacementChances().get(entity.getType());
        if (map == null || map.isEmpty()) return false;

        // 누적 확률로 하나 고르기
        double roll = random.nextDouble();
        double cumulative = 0.0;
        for (Map.Entry<EntityType, Double> e : map.entrySet()) {
            cumulative += e.getValue();
            if (roll < cumulative) {
                // 원본 취소 후 대체 엔티티 커스텀 스폰
                event.setCancelled(true);
                spawnReplacement(entity.getLocation(), e.getKey());
                return true;
            }
        }
        return false;
    }

    private void spawnReplacement(Location loc, EntityType targetType) {
        World world = loc.getWorld();
        if (world == null) return;
        Entity spawned = world.spawnEntity(loc, targetType, CreatureSpawnEvent.SpawnReason.CUSTOM);

        // 면역/장비 등 적용
        applyPiglinHoglinImmunity(spawned);
        if (spawned instanceof LivingEntity le) {
            equip.applyAll(le);
        }
    }

    private boolean shouldMultiply(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        if (!(entity instanceof Monster)) return false;
        EntityType type = entity.getType();
        if (type == EntityType.ENDER_DRAGON || type == EntityType.WITHER || type == EntityType.WARDEN) return false;
        if (cfg.getMobSpawnMultiplier() <= 1.01) return false;
        return reason == CreatureSpawnEvent.SpawnReason.NATURAL;
    }

    private void multiply(Entity entity) {
        int extra = (int) Math.round(cfg.getMobSpawnMultiplier() - 1);
        if (extra <= 0) return;
        World w = entity.getWorld();
        Location loc = entity.getLocation();
        for (int i = 0; i < extra; i++) {
            Entity dup = w.spawnEntity(loc, entity.getType(), CreatureSpawnEvent.SpawnReason.CUSTOM);
            applyPiglinHoglinImmunity(dup);
            if (dup instanceof LivingEntity le) equip.applyAll(le);
        }
    }

    // 조키 처리 (원래 엔티티 취소 후 커스텀 스폰)
    private boolean handleJockeys(Entity entity, CreatureSpawnEvent event) {
        EntityType type = entity.getType();
        Location loc = entity.getLocation();
        World world = entity.getWorld();

        // 베이비 좀비류 치킨 조키
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
            double chance = cfg.getJockeyChances().getOrDefault(key, 0.0);
            if (random.nextDouble() < chance) {
                event.setCancelled(true);
                Chicken chicken = (Chicken) world.spawnEntity(loc, EntityType.CHICKEN, CreatureSpawnEvent.SpawnReason.CUSTOM);
                LivingEntity baby = (LivingEntity) world.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                if (baby instanceof Zombie bz) bz.setBaby(true);
                chicken.addPassenger(baby);
                applyPiglinHoglinImmunity(baby);
                equip.applyAll(baby);
                return true;
            }
        }

        // 스켈레톤 계열 스파이더 조키
        if (type == EntityType.SKELETON || type == EntityType.STRAY
                || type == EntityType.WITHER_SKELETON || type == EntityType.BOGGED) {
            String key = switch (type) {
                case SKELETON -> "skeleton_spider_jockey";
                case STRAY -> "stray_spider_jockey";
                case WITHER_SKELETON -> "wither_skeleton_spider_jockey";
                case BOGGED -> "bogged_spider_jockey";
                default -> "";
            };
            double chance = cfg.getJockeyChances().getOrDefault(key, 0.0);
            if (random.nextDouble() < chance) {
                event.setCancelled(true);
                Spider spider = (Spider) world.spawnEntity(loc, EntityType.SPIDER, CreatureSpawnEvent.SpawnReason.CUSTOM);
                LivingEntity skele = (LivingEntity) world.spawnEntity(loc, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
                spider.addPassenger(skele);
                equip.applyAll(skele);
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
}

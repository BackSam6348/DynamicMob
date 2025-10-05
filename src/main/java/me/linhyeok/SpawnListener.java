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

        // 월드별 동작 가드
        World w = entity.getWorld();
        if (!cfg.isWorldEnabled(w)) return;

        // --- Killer Bunny (토끼 스폰 시 변환)
        if (entity instanceof Rabbit rabbit) {
            if (cfg.isKillerBunnyEnabled() && shouldApplyForReason(reason,
                    cfg.isKillerBunnyApplyNatural(), cfg.isKillerBunnyApplySpawner(), cfg.isKillerBunnyApplySpawnEgg())) {
                if (random.nextDouble() < cfg.getKillerBunnyChance()) {
                    try {
                        rabbit.setRabbitType(Rabbit.Type.THE_KILLER_BUNNY);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Failed to set Killer Bunny type (ignored).");
                    }
                }
            }
        }

        // 비활성화된 엔티티면 즉시 중단
        if (cfg.getDisabledEntities().contains(entity.getType())) return;

        // --- 대체 스폰 처리 ---
        if (shouldApplyReplacementForReason(reason)) {
            if (tryReplacement(event)) {
                return; // 원래 스폰 취소 후 대체 성공
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

    @EventHandler
    public void onTransform(EntityTransformEvent event) {
        // 월드별 동작 가드 (좀비화/변형 이벤트)
        if (!cfg.isWorldEnabled(event.getEntity().getWorld())) return;

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
        Map<EntityType, Double> table = cfg.getReplacementChances().get(entity.getType());
        if (table == null || table.isEmpty()) return false;

        double r = random.nextDouble();
        double acc = 0.0;
        for (Map.Entry<EntityType, Double> e : table.entrySet()) {
            acc += e.getValue();
            if (r < acc) {
                // 대체 스폰
                Location loc = entity.getLocation();
                Entity spawned = loc.getWorld().spawnEntity(loc, e.getKey());
                if (spawned instanceof LivingEntity le) {
                    equip.applyAll(le);
                }
                event.setCancelled(true);
                return true;
            }
        }
        return false;
    }

    private boolean shouldMultiply(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL) return false;
        if (entity instanceof Boss) return false;
        return cfg.getMobSpawnMultiplier() > 1.01;
    }

    private void multiply(Entity entity) {
        double mult = cfg.getMobSpawnMultiplier();
        int extra = (int) Math.floor(mult) - 1;
        double frac = mult - Math.floor(mult);

        Location loc = entity.getLocation();
        for (int i = 0; i < extra; i++) {
            loc.getWorld().spawnEntity(loc, entity.getType());
        }
        if (random.nextDouble() < frac) {
            loc.getWorld().spawnEntity(loc, entity.getType());
        }
    }

    private boolean handleJockeys(Entity entity, CreatureSpawnEvent event) {
        if (entity.getType() == EntityType.CHICKEN) {
            double chance = cfg.getJockeyChances().getOrDefault("chicken_jockey_chance", 0.0);
            if (random.nextDouble() < chance) {
                Entity rider = entity.getWorld().spawnEntity(entity.getLocation(), EntityType.ZOMBIE);
                if (rider instanceof Zombie zombie) {
                    // ✅ 올바른 방법: 차량(닭)에 승객(좀비)을 태움
                    entity.addPassenger(zombie);
                    equip.applyAll(zombie);
                    event.setCancelled(true);
                    return true;
                }
            }
        } else if (entity.getType() == EntityType.SPIDER) {
            double chance = cfg.getJockeyChances().getOrDefault("spider_jockey_chance", 0.0);
            if (random.nextDouble() < chance) {
                Location loc = entity.getLocation();
                Spider spider = (Spider) entity;
                Skeleton skele = (Skeleton) loc.getWorld().spawnEntity(loc, EntityType.SKELETON);
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

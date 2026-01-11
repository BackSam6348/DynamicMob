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
            if (cfg.isKillerBunnyEnabled() &&
                    shouldApplyForReason(reason,
                            cfg.isKillerBunnyApplyNatural(),
                            cfg.isKillerBunnyApplySpawner(),
                            cfg.isKillerBunnyApplySpawnEgg())) {
                if (random.nextDouble() < cfg.getKillerBunnyChance()) {
                    try {
                        rabbit.setRabbitType(Rabbit.Type.THE_KILLER_BUNNY);
                        plugin.getLogger().fine("Killer Bunny spawned at " + rabbit.getLocation());
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Failed to set Killer Bunny type at " +
                                rabbit.getLocation() + ". This may be disabled by your server configuration or version. Error: " +
                                t.getClass().getSimpleName() + " - " + t.getMessage());
                    }
                }
            }
        }

        // --- 피글린/브루트/호글린 좀비화 면역 (원본에 직접 설정)
        if (entity instanceof Piglin piglin) {
            piglin.setImmuneToZombification(true);
        } else if (entity instanceof PiglinBrute brute) {
            brute.setImmuneToZombification(true);
        } else if (entity instanceof Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }

        // --- 자연 스폰 제한 (natural-limit)
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL) {
            Double limit = cfg.getNaturalSpawnChance().get(entity.getType());
            if (limit != null && random.nextDouble() >= limit) {
                event.setCancelled(true);
                return;
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

        // --- 조키 (확률에 따라 원래 엔티티에 라이더 추가)
        if (handleJockeys(entity, event)) {
            // 조키 구성 완료. 이후 장비 로직은 각각의 1틱 지연 블록에서 처리
        }

        // --- 약탈자는 바닐라 장비 유지 (커스텀 장비 적용 안 함)
        if (entity instanceof Pillager) {
            return; // 약탈자는 아무것도 하지 않음
        }

        // --- 장비/인챈트/충전 등 적용 (스포너/스폰알 포함) – 1틱 지연
        if (entity instanceof LivingEntity le) {
            FoliaCompat.runOneTickLater(plugin, le, () -> {
                if (!le.isValid()) return;
                equip.applyAll(le);
            });
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

    // ===== 내부 메서드 =====

    private boolean shouldApplyForReason(CreatureSpawnEvent.SpawnReason reason,
                                         boolean nat, boolean spawner, boolean egg) {
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

    /**
     * 대체 스폰: 원본 스폰 이벤트를 취소하고 새로운 엔티티로 교체.
     * 새로 스폰된 엔티티에도 1틱 지연 장비 적용.
     */
    private boolean tryReplacement(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        Map<EntityType, Double> table = cfg.getReplacementChances().get(entity.getType());
        if (table == null || table.isEmpty()) return false;

        double r = random.nextDouble();
        double acc = 0.0;
        for (Map.Entry<EntityType, Double> e : table.entrySet()) {
            acc += e.getValue();
            if (r < acc) {
                Location loc = entity.getLocation();
                Entity spawned = loc.getWorld().spawnEntity(loc, e.getKey());

                // 약탈자로 대체되는 경우 바닐라 장비 유지
                if (spawned instanceof Pillager) {
                    event.setCancelled(true);
                    return true;
                }

                if (spawned instanceof LivingEntity le) {
                    FoliaCompat.runOneTickLater(plugin, le, () -> {
                        if (!le.isValid()) return;
                        equip.applyAll(le);
                    });
                }
                event.setCancelled(true);
                return true;
            }
        }
        return false;
    }

    /**
     * NATURAL 스폰만 배수 적용, 보스는 제외
     */
    private boolean shouldMultiply(Entity entity, CreatureSpawnEvent.SpawnReason reason) {
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL) return false;
        if (entity instanceof Boss) return false;
        return cfg.getMobSpawnMultiplier() > 1.01;
    }

    /**
     * 스폰 배수 적용. 복제 스폰 개체에도 1틱 지연 장비 적용.
     */
    private void multiply(Entity entity) {
        double mult = cfg.getMobSpawnMultiplier();
        int extra = (int) Math.floor(mult) - 1;
        double frac = mult - Math.floor(mult);

        Location loc = entity.getLocation();

        for (int i = 0; i < extra; i++) {
            Entity clone = loc.getWorld().spawnEntity(loc, entity.getType());

            // 약탈자는 장비 적용 안 함
            if (clone instanceof Pillager) {
                continue;
            }

            if (clone instanceof LivingEntity le) {
                FoliaCompat.runOneTickLater(plugin, le, () -> {
                    if (!le.isValid()) return;
                    equip.applyAll(le);
                });
            }
        }
        if (random.nextDouble() < frac) {
            Entity clone = loc.getWorld().spawnEntity(loc, entity.getType());

            // 약탈자는 장비 적용 안 함
            if (clone instanceof Pillager) {
                return;
            }

            if (clone instanceof LivingEntity le) {
                FoliaCompat.runOneTickLater(plugin, le, () -> {
                    if (!le.isValid()) return;
                    equip.applyAll(le);
                });
            }
        }
    }

    /**
     * 조키 구성. 베이비 좀비/스켈레톤 등이 탈것에 승차.
     * 승객(라이더) 장비는 1틱 지연으로 적용.
     * 반환값 true는 "조키 로직 수행됨"을 의미 (이벤트 취소는 하지 않음).
     */
    private boolean handleJockeys(Entity entity, CreatureSpawnEvent event) {
        // 베이비 좀비 계열 + 닭 조키
        if (entity instanceof Zombie zombie && zombie.isBaby()) {
            String configKey = getZombieJockeyKey(zombie);
            double chance = cfg.getJockeyChances().getOrDefault(configKey, 0.0);
            if (random.nextDouble() < chance) {
                Entity chicken = entity.getWorld().spawnEntity(entity.getLocation(), EntityType.CHICKEN);
                chicken.addPassenger(zombie);
                FoliaCompat.runOneTickLater(plugin, zombie, () -> {
                    if (!zombie.isValid()) return;
                    equip.applyAll(zombie);
                });
                return true;
            }
        }

        // 스켈레톤 계열 + 거미 조키 (PARCHED 포함)
        if (entity instanceof AbstractSkeleton skeleton) {
            String configKey = getSkeletonJockeyKey(skeleton);
            double chance = cfg.getJockeyChances().getOrDefault(configKey, 0.0);
            if (random.nextDouble() < chance) {
                Entity spider = entity.getWorld().spawnEntity(entity.getLocation(), EntityType.SPIDER);
                spider.addPassenger(skeleton);
                FoliaCompat.runOneTickLater(plugin, skeleton, () -> {
                    if (!skeleton.isValid()) return;
                    equip.applyAll(skeleton);
                });
                return true;
            }
        }

        return false;
    }

    /**
     * 좀비 타입에 따른 조키 config 키 반환
     */
    private String getZombieJockeyKey(Zombie zombie) {
        return switch (zombie.getType()) {
            case HUSK -> "baby_husk_chicken_jockey";
            case DROWNED -> "baby_drowned_chicken_jockey";
            case ZOMBIFIED_PIGLIN -> "baby_zombified_piglin_chicken_jockey";
            case ZOMBIE_VILLAGER -> "baby_zombie_villager_chicken_jockey";
            default -> "baby_zombie_chicken_jockey";
        };
    }

    /**
     * 스켈레톤 타입에 따른 조키 config 키 반환 (PARCHED 추가)
     */
    private String getSkeletonJockeyKey(AbstractSkeleton skeleton) {
        // PARCHED가 실제로 존재하는지 확인
        try {
            EntityType parchedType = EntityType.valueOf("PARCHED");
            if (skeleton.getType() == parchedType) {
                return "parched_spider_jockey";
            }
        } catch (IllegalArgumentException e) {
            // PARCHED 타입이 없으면 무시
        }

        return switch (skeleton.getType()) {
            case STRAY -> "stray_spider_jockey";
            case WITHER_SKELETON -> "wither_skeleton_spider_jockey";
            case BOGGED -> "bogged_spider_jockey";
            default -> "skeleton_spider_jockey";
        };
    }
}
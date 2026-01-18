package me.linhyeok;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

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
        handleJockeys(entity);

        // --- 약탈자는 바닐라 장비 유지 (커스텀 장비 적용 안 함)
        // 대체 스폰으로 이미 교체되었다면 이 코드에 도달하지 않음
        if (entity instanceof Pillager) {
            return; // 약탈자는 아무것도 하지 않음
        }

        // --- 스케일 즉시 적용 ---
        LivingEntity le = (LivingEntity) entity;
        applyScale(le);

        // --- 장비/인챈트/충전 등 적용 (스포너/스폰알 포함) – 1틱 지연
        FoliaCompat.runOneTickLater(plugin, le, () -> {
            if (!le.isValid()) return;
            equip.applyAll(le);
        });
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

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // 월드별 동작 가드
        if (!cfg.isWorldEnabled(entity.getWorld())) return;

        // 커스텀 드랍 적용
        Map<Material, Double> drops = cfg.getDropChances().get(entity.getType());
        if (drops == null || drops.isEmpty()) return;

        Location loc = entity.getLocation();
        for (Map.Entry<Material, Double> entry : drops.entrySet()) {
            if (random.nextDouble() < entry.getValue()) {
                entity.getWorld().dropItemNaturally(loc, new ItemStack(entry.getKey(), 1));
            }
        }
    }

    /**
     * 몹에 스케일 적용
     */
    private void applyScale(LivingEntity entity) {
        Map<Double, Double> scales = cfg.getScaleChances().get(entity.getType());
        if (scales == null || scales.isEmpty()) return;

        double roll = random.nextDouble();
        double cumulative = 0.0;

        for (Map.Entry<Double, Double> entry : scales.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                try {
                    var attr = entity.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                    if (attr != null) {
                        attr.setBaseValue(entry.getKey());
                    }
                    break;
                } catch (Exception ignored) {
                    break;
                }
            }
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
                // 환술사로 대체되는 경우 커스텀 장비 적용
                if (spawned instanceof Pillager) {
                    event.setCancelled(true);
                    return true;
                }

                if (spawned instanceof LivingEntity le) {
                    applyScale(le);
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
                applyScale(le);
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
                applyScale(le);
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
     */
    private void handleJockeys(Entity entity) {
        // 베이비 좀비 계열 + 닭 조키
        if (entity instanceof Zombie zombie && !zombie.isAdult()) {
            String configKey = getZombieJockeyKey(zombie);
            double chance = cfg.getJockeyChances().getOrDefault(configKey, 0.0);
            if (random.nextDouble() < chance) {
                Entity chicken = entity.getWorld().spawnEntity(entity.getLocation(), EntityType.CHICKEN);
                chicken.addPassenger(zombie);
                applyScale(zombie);
                FoliaCompat.runOneTickLater(plugin, zombie, () -> {
                    if (!zombie.isValid()) return;
                    equip.applyAll(zombie);
                });
                return;
            }
        }

        // 1.21.11+ 좀비 + 좀비 말 조키 (베이비 아님)
        if (entity instanceof Zombie zombie && zombie.isAdult() && zombie.getType() == EntityType.ZOMBIE) {
            double horseChance = cfg.getZombieHorseJockeyChance();
            if (horseChance > 0 && random.nextDouble() < horseChance) {
                try {
                    Entity zombieHorse = entity.getWorld().spawnEntity(entity.getLocation(), EntityType.ZOMBIE_HORSE);
                    zombieHorse.addPassenger(zombie);
                    applyScale(zombie);
                    FoliaCompat.runOneTickLater(plugin, zombie, () -> {
                        if (!zombie.isValid()) return;
                        equip.applyAll(zombie);
                    });
                    return;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to spawn zombie horse jockey: " + e.getMessage());
                }
            }
        }

        // 1.21.11+ 허스크 + 낙타 조키 (베이비 아님)
        if (entity instanceof Husk husk && husk.isAdult()) {
            double camelChance = cfg.getHuskCamelJockeyChance();
            if (camelChance > 0 && random.nextDouble() < camelChance) {
                try {
                    Entity camel = entity.getWorld().spawnEntity(entity.getLocation(), EntityType.CAMEL);
                    camel.addPassenger(husk);
                    applyScale(husk);
                    FoliaCompat.runOneTickLater(plugin, husk, () -> {
                        if (!husk.isValid()) return;
                        equip.applyAll(husk);
                    });
                    return;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to spawn husk camel jockey (CAMEL may not be available): " + e.getMessage());
                }
            }
        }

        // 1.21.11+ 드라운드 + 좀비 앵무조개 조키
        if (entity instanceof Drowned drowned) {
            double nautilusChance = cfg.getDrownedNautilusJockeyChance();
            if (nautilusChance > 0 && random.nextDouble() < nautilusChance) {
                try {
                    EntityType nautilusType = EntityType.valueOf("ZOMBIE_NAUTILUS");
                    Entity nautilusZombie = entity.getWorld().spawnEntity(entity.getLocation(), nautilusType);
                    nautilusZombie.addPassenger(drowned);
                    applyScale(drowned);
                    FoliaCompat.runOneTickLater(plugin, drowned, () -> {
                        if (!drowned.isValid()) return;
                        equip.applyAll(drowned);
                    });
                    return;
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("ZOMBIE_NAUTILUS entity type not available (requires 1.21.11+)");
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to spawn drowned nautilus jockey: " + e.getMessage());
                }
            }
        }

        // 스켈레톤 계열 + 거미 조키 (PARCHED 포함)
        if (entity instanceof AbstractSkeleton skeleton) {
            String configKey = getSkeletonJockeyKey(skeleton);
            double chance = cfg.getJockeyChances().getOrDefault(configKey, 0.0);
            if (random.nextDouble() < chance) {
                Entity spider = entity.getWorld().spawnEntity(entity.getLocation(), EntityType.SPIDER);
                spider.addPassenger(skeleton);
                applyScale(skeleton);
                FoliaCompat.runOneTickLater(plugin, skeleton, () -> {
                    if (!skeleton.isValid()) return;
                    equip.applyAll(skeleton);
                });
            }
        }
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
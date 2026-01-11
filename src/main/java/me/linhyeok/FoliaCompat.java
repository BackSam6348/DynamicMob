package me.linhyeok;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Folia/Paper 양쪽에서 "스폰 1틱 뒤 실행"을 안전하게 수행하기 위한 헬퍼.
 * - Folia가 있으면 RegionScheduler.runDelayed(...)를 리플렉션으로 호출
 * - 그렇지 않으면 BukkitScheduler.runTaskLater(...)로 폴백
 */
public final class FoliaCompat {
    private FoliaCompat() {}

    public static void runOneTickLater(Plugin plugin, Entity entity, Runnable task) {
        try {
            // Folia: Bukkit.getRegionScheduler().runDelayed(plugin, location, Consumer<ScheduledTask>, delay)
            Object regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
            Class<?> rsClass = regionScheduler.getClass();

            var runDelayed = rsClass.getMethod(
                    "runDelayed",
                    Plugin.class,
                    Location.class,
                    Consumer.class,
                    long.class
            );

            Location loc = entity.getLocation();
            Consumer<Object> consumer = (scheduledTask) -> task.run();
            runDelayed.invoke(regionScheduler, plugin, loc, consumer, 1L);
        } catch (Throwable t) {
            // Paper/Spigot: 고전 스케줄러로 1틱 지연
            Bukkit.getScheduler().runTaskLater(plugin, task, 1L);
        }
    }
}

# DynamicMob

An advanced mob-spawn customizer for **Paper/Folia 1.21.x**.  
Replace natural spawns with **other mobs** (weighted), give equipment and **vanilla-style enchantments**, enable **chicken/spider jockeys**, **charged creepers**, **block helmets** for skeletons and zombies, **custom drops**, **mob scaling**, and even turn rabbits into the **Killer Bunny**—all controlled via `config.yml`.

- Works with **NATURAL**, **SPAWNER**, and (optionally) **SPAWNER_EGG** spawns  
- Fully **Folia** compatible  
- `/dm reload` and `/dm debug` commands **registered at runtime** in code → use **paper-plugin.yml only** (no `plugin.yml` needed)

---

## ✨ Features

- **Replacement spawns:** convert originally spawning mobs (e.g., `ZOMBIE`, `SKELETON`) into other mobs (e.g., `PIGLIN`, `WITHER_SKELETON`, `BOGGED`, `ILLUSIONER`) using per-source weighted tables  
- **Scope control:** choose where replacement applies—**natural**, **spawner**, **spawn egg**  
- **Natural spawn chance limiter:** per-entity throttle for NATURAL spawns (can effectively disable)  
- **Spawn multiplier:** clone NATURAL monster spawns (bosses excluded)  
- **Equipment tables:** per-mob slot (weapon/helmet/chest/legs/boots) with probabilities + randomized durability  
- **Vanilla-like enchantments:**  
  - Bow: Power / Punch / Flame / Infinity  
  - Swords/Axes: Sharpness / Smite / Fire Aspect / Looting  
  - Tridents: Sharpness / Impaling / Looting  
  - **1.21.11+ Spears:** Sharpness / Impaling / Looting  
  - Armor: Protection / Projectile / Blast / Thorns, and (boots) Feather Falling  
- **Special spawns:**  
  - **Charged creeper** chance  
  - **Drowned Channeling trident** via special token  
  - **Chicken/Spider jockeys** (baby zombies, skeletons)  
  - **1.21.11+ Zombie Horse jockey** (adult zombies)  
  - **1.21.11+ Camel jockey** (adult husks)  
  - **1.21.11+ Zombie Nautilus jockey** (drowned)  
  - **1.21.11+ Spear weapons** for zombies/husks/piglins  
- **Block helmets:**  
  - Global block-helmet pool (`special.block_helmet.general`) - applies to zombies  
  - Skeleton-only pool (`special.block_helmet.skeleton`)  
  - Applied **only if the helmet slot is empty**  
- **Custom drops:**  
  - Per-mob drop tables with configurable chances  
  - Any item, any mob  
- **Mob scaling:**  
  - Adjust mob size dynamically (0.1x to 10x+)  
  - Per-mob scale values with individual chances  
- **Piglin / Piglin Brute / Hoglin zombification immunity:**  
  - Cancel original spawn and re-spawn as **immune from the start**  
    (equivalent to `/summon ... {IsImmuneToZombification:1}`)  
- **Killer Bunny:**  
  - Convert **original rabbit spawns** into Killer Bunny based on chance + scope  
- **Manual mob configuration:**  
  - Add ANY mob to config.yml with custom equipment, drops, scales, and replacement spawns  
  - Full flexibility for modded or custom setups

---

## ✅ Requirements

- **Java 21+** (tested on Java 25)  
- **Paper** or **Folia** `1.21.x` (supports 1.21.11+ features)

---

## 📦 Installation

1. Put the plugin JAR into `plugins/`.  
2. Restart server. (We recommend restarting the server using the /restart or /stop commands instead of the /reload command.)

---

## 🔧 Commands & Permissions

- **`/dm reload`** — reloads `config.yml` (registered at runtime; works without `plugin.yml`)  
  - **Permission:** `dynamicmob.reload` (default: OP)
- **`/dm debug <on|off>`** — toggle debug mode for troubleshooting (OP only)  
  - Shows detailed logs for scale loading, equipment application, etc.

---

## ⚙️ Config Highlights

### 1) Enchant chance
```yml
enchant-chance:
  weapon: 0.0001
  armor: 0.0001
```
Extremely low (0.01%) chance to add vanilla-like enchants to weapons/armor.

### 2) Spawn multiplier
```yml
mob-spawn:
  multiplier: 1.3
```
- **Note:** `1.3` may result in 0 extra clones due to rounding.  
  For a guaranteed +1 clone, use **`2.0`** (or `3.0` for +2).

### 3) Special features
```yml
special:
  killer_bunny_on_rabbit_spawn:
    enabled: true
    chance: 0.0001
    apply-to:
      natural: true
      spawner: false
      spawn-egg: true
  block_helmet:
    general:
      OAK_LEAVES: 0.01
    skeleton:
      BONE_BLOCK: 0.05
      OBSIDIAN: 0.02
  bone_in_hand: 0.03
  charged_creeper: 0.0006
  drowned_channeling: 0.03
  
  # 1.21.11+ features
  zombie_spear_chance: 0.015
  husk_spear_chance: 0.015
  piglin_gold_spear_chance: 0.012
  zombified_piglin_gold_spear_chance: 0.012
```

### 4) Replacement tables
```yml
replacement:
  apply-to-natural: true
  apply-to-spawner: false
  apply-to-spawn-egg: false

replacement-spawn:
  ZOMBIE:
    PIGLIN: 0.16
    PIGLIN_BRUTE: 0.0015
    HUSK: 0.21
    ZOMBIFIED_PIGLIN: 0.21
    HOGLIN: 0.16
    ZOMBIE_VILLAGER: 0.04
  
  PILLAGER:
    ILLUSIONER: 0.05
    VINDICATOR: 0.03
  
  VINDICATOR:
    ILLUSIONER: 0.08
    EVOKER: 0.02
```

### 5) Jockey chances
```yml
jockey-chance:
  baby_zombie_chicken_jockey: 0.001
  skeleton_spider_jockey: 0.001
  
  # 1.21.11+ jockeys
  zombie_horse_jockey: 0.005
  husk_camel_jockey: 0.006
  drowned_nautilus_jockey: 0.004
```

### 6) Equipment tables (per mob)
```yml
spawn-chance:
  ZOMBIE:
    enabled: true
    natural-limit: 1.0
    weapon:
      IRON_SWORD: 0.05
      DIAMOND_SWORD: 0.01
    helmet:
      IRON_HELMET: 0.03
    drops:
      DIAMOND: 0.01
      EMERALD: 0.005
    scale_values:
      - size: 0.5
        chance: 0.1
      - size: 1.5
        chance: 0.05
      - size: 2.0
        chance: 0.01
```

### 7) Custom drops
Any mob can have custom drops with configurable chances:
```yml
spawn-chance:
  SKELETON:
    drops:
      ARROW: 0.5
      BONE: 0.3
      DIAMOND: 0.01
```

### 8) Mob scaling
Adjust mob size dynamically using `scale_values`:
```yml
spawn-chance:
  WITHER_SKELETON:
    scale_values:
      - size: 0.5    # 50% size
        chance: 0.1  # 10% chance
      - size: 3.0    # 300% size (giant)
        chance: 0.05 # 5% chance
```
**Note:** Scale keys must use the list format shown above.

### 9) Manual mob configuration
You can add ANY mob to config.yml with full customization:
```yml
spawn-chance:
  EVOKER:
    enabled: true
    natural-limit: 0.3
    weapon:
      TOTEM_OF_UNDYING: 0.1
    helmet:
      GOLDEN_HELMET: 0.05
    drops:
      EMERALD: 0.2
    scale_values:
      - size: 1.2
        chance: 0.1

replacement-spawn:
  WITCH:
    EVOKER: 0.05
```

---

## 🧠 Processing order (simplified)

1. For NATURAL spawns, apply **`spawn-chance`** gate (natural-limit).  
2. If the mob is **Piglin / Piglin Brute / Hoglin**, **cancel** the original and **re-spawn immune**.  
3. If replacement scope allows, roll **replacement-spawn** tables.  
4. If the **original spawn is a RABBIT** and the option is enabled, convert to **Killer Bunny** by chance/scope.  
5. Apply **scale** immediately (before equipment).  
6. Equipment tables → random durability → enchant chance → block helmets (only if helmet empty) → **bone_in_hand** → **charged_creeper** → **spear weapons** (1.21.11+).  
7. Jockey rolls (including 1.21.11+ jockeys).  
8. Apply **custom drops** on death.

---

## 🛠️ Troubleshooting

- **`/dm reload` not recognized**  
  - Ensure the plugin is enabled (check console banner).  
  - Command is registered at runtime; no plugin.yml needed.  
- **Replacement doesn't apply to spawn eggs**  
  - Check `replacement.apply-to-spawn-egg` is set to `true`.  
- **Scale not applying**  
  - Use the list format for `scale_values` (see example above).  
  - Enable debug mode: `/dm debug on` to see detailed logs.  
- **Block helmets appearing on wrong mobs**  
  - Block helmets only apply to skeletons and zombies.  
  - Use the `helmet:` section for other mobs.  
- **1.21.11 features not working**  
  - Ensure server is running 1.21.11+.  
  - Check console for warnings about missing entity types.

---

## 🤝 Contributing

PRs/issues welcome. Please include:
- Paper/Folia version  
- Java version  
- Your `config.yml` and minimal reproduction steps

---

## 📄 License

MIT license

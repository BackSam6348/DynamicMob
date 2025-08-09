# DynamicMob

An advanced mob-spawn customizer for **Paper/Folia 1.21.x**.  
Replace natural spawns with **other mobs** (weighted), give equipment and **vanilla-style enchantments**, enable **chicken/spider jockeys**, **charged creepers**, **block helmets** for skeletons (and more), and even turn rabbits into the **Killer Bunny**—all controlled via `config.yml`.

- Works with **NATURAL**, **SPAWNER**, and (optionally) **SPAWNER_EGG** spawns  
- Fully **Folia** compatible  
- `/dm reload` is **registered at runtime** in code → use **paper-plugin.yml only** (no `plugin.yml` needed)

---

## ✨ Features

- **Replacement spawns:** convert originally spawning mobs (e.g., `ZOMBIE`, `SKELETON`) into other mobs (e.g., `PIGLIN`, `WITHER_SKELETON`, `BOGGED`) using per-source weighted tables  
- **Scope control:** choose where replacement applies—**natural**, **spawner**, **spawn egg**  
- **Natural spawn chance limiter:** per-entity throttle for NATURAL spawns (can effectively disable)  
- **Spawn multiplier:** clone NATURAL monster spawns (bosses excluded)  
- **Equipment tables:** per-mob slot (weapon/helmet/chest/legs/boots) with probabilities + randomized durability  
- **Vanilla-like enchantments:**  
  - Bow: Power / Punch / Flame / Infinity  
  - Swords/Axes: Sharpness / Smite / Fire Aspect / Looting  
  - Armor: Protection / Projectile / Blast / Thorns, and (boots) Feather Falling  
- **Special spawns:**  
  - **Charged creeper** chance  
  - **Drowned Channeling trident** via special token  
  - **Chicken/Spider jockeys**  
- **Block helmets:**  
  - Global block-helmet pool (`special.block_helmet.general`)  
  - Skeleton-only pool (`special.block_helmet.skeleton`)  
  - Applied **only if the helmet slot is empty**  
- **Piglin / Piglin Brute / Hoglin zombification immunity:**  
  - Cancel original spawn and re-spawn as **immune from the start**  
    (equivalent to `/summon ... {IsImmuneToZombification:1}`)  
- **Killer Bunny:**  
  - Convert **original rabbit spawns** into Killer Bunny based on chance + scope

---

## ✅ Requirements

- **Java 17+**  
- **Paper** or **Folia** `1.21.x`

---

## 📦 Installation

1. Put the plugin JAR into `plugins/`.  
2. Use **paper-plugin.yml** only (no `plugin.yml`).  
3. Start the server to generate `config.yml`.  
4. Edit `config.yml` to your liking.  
5. Apply changes with **`/dm reload`**.

---

## 🔧 Commands & Permissions

- **`/dm reload`** — reloads `config.yml` (registered at runtime; works without `plugin.yml`)  
- **Permission:** `dynamicmob.reload` (default: OP)

---

## ⚙️ Config Highlights (based on the provided structure)

### 1) Enchant chance
```yml
enchant-chance:
  weapon: 0.0001
  armor: 0.0001
```
Extremely low (0.01%) chance to add vanilla-like enchants to weapons/armor.

### 2) Spawn multiplier & spawn-egg scope
```yml
mob-spawn:
  multiplier: 1.3
  enable-spawn-egg: true
```
- **Note:** depending on the code (e.g., `extra = round(multiplier - 1)`), **1.3 may result in 0 extra clones**.  
  For a guaranteed +1 clone, use **`2.0`** (or `3.0` for +2).  
- Custom logic also applies to **spawn eggs** (`true`).

### 3) Light threshold (informational)
```yml
light-threshold: 9
```
Informational value (vanilla hostile baseline is ~7; Nether depends on mob).

### 4) Special
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
```
- **Original rabbit → Killer Bunny**: enabled, **0.01%**, applies to NATURAL & SPAWN_EGG  
- **Block helmets:**  
  - Global: OAK_LEAVES 1%  
  - Skeleton-only: BONE_BLOCK 5%, OBSIDIAN 2%  
  - Only if helmet slot is empty  
- **bone_in_hand (skeleton):** 3%  
- **charged_creeper:** 0.06%

### 5) Replacement scope
```yml
replacement:
  apply-to-natural: true
  apply-to-spawner: false
  apply-to-spawn-egg: false
```
Replacement is **natural-only** (not applied to spawner/egg by default).

### 6) Replacement tables (source → weighted targets)
```yml
replacement-spawn:
  ZOMBIE:
    PIGLIN: 0.16
    PIGLIN_BRUTE: 0.0015
    HUSK: 0.21
    ZOMBIFIED_PIGLIN: 0.21
    HOGLIN: 0.16
    ZOMBIE_VILLAGER: 0.04

  PIG:
    PIGLIN: 0.21
    HOGLIN: 0.21

  ZOMBIFIED_PIGLIN:
    ZOGLIN: 0.35

  SKELETON:
    STRAY: 0.13
    WITHER_SKELETON: 0.12
    BOGGED: 0.11
```
- If weights sum **< 1.0**, the remaining chance keeps the **original**.  
  Example: `ZOMBIE` totals ≈ **0.7815** → ~**78%** replaced, ~**22%** kept  
- When replacing into `PIGLIN` / `PIGLIN_BRUTE` / `HOGLIN`, the plugin **re-spawns them as immune** (no zombification).

### 7) Natural spawn chance limits
```yml
spawn-chance:
  BOGGED: 0.6
  SKELETON: 1.0
  STRAY: 0.6
  WITHER_SKELETON: 0.9
  ZOMBIE: 1.0
  HUSK: 0.8
  DROWNED: 0.4
  ZOMBIFIED_PIGLIN: 0.8
  ZOMBIE_VILLAGER: 0.8
  PIGLIN: 0.8
  PIGLIN_BRUTE: 0.3
  HOGLIN: 0.6
  ZOGLIN: 0.6
```
Applied to **NATURAL** spawns only. `1.0` = no limit; `0.0` = effectively blocked.

### 8) Jockey chance
```yml
jockey-chance:
  baby_zombie_chicken_jockey: 0.001
  baby_husk_chicken_jockey: 0.001
  baby_drowned_chicken_jockey: 0.001
  baby_zombified_piglin_chicken_jockey: 0.001
  baby_zombie_villager_chicken_jockey: 0.001

  skeleton_spider_jockey: 0.001
  stray_spider_jockey: 0.001
  wither_skeleton_spider_jockey: 0.001
  bogged_spider_jockey: 0.001
```
All at **0.1%** (very rare).

### 9) Spawn settings (equipment tables)
- Per-mob slot probabilities for `weapon/helmet/chestplate/leggings/boots`  
- **Random durability** for vanilla vibes  
- **Special token:** `TRIDENT_CHANNELING` for Drowned to hold a Channeling trident

> Heads-up: If you place blocks under `weapon:` (e.g., `OBSIDIAN`), mobs will **hold** them.  
> If you intend them as helmets, put them under `helmet:` or in `special.block_helmet.skeleton`.

---

## 🧠 Processing order (simplified)

1. For NATURAL spawns, apply **`spawn-chance`** gate.  
2. If the mob is **Piglin / Piglin Brute / Hoglin**, **cancel** the original and **re-spawn immune**.  
3. If replacement scope allows, roll **replacement-spawn** tables.  
4. If the **original spawn is a RABBIT** and the option is enabled, convert to **Killer Bunny** by chance/scope.  
5. Equipment tables → random durability → enchant chance → block helmets (only if helmet empty) → **bone_in_hand** → **charged_creeper**.  
6. Jockey rolls.

---

## 🛠️ Troubleshooting

- **`/dm reload` not recognized / prints twice**  
  - Ensure the plugin is enabled (check console banner).  
  - Double-print from console can be fixed by echoing to console **only when the executor isn’t console** (use patched build).  
- **Replacement doesn’t apply to spawn eggs**  
  - Your current config: `replacement.apply-to-spawn-egg: false`.  
  - Set to `true` if you want it.  
- **Nether mobs in Overworld**  
  - Prefer **replacement** to emulate “natural” appearances instead of forcing biome rules.

---

## 🤝 Contributing

PRs/issues welcome. Please include:
- Paper/Folia version  
- Java version  
- Your `config.yml` and minimal reproduction steps

---

## 📄 License

MIT license

# DynamicMob

A highly configurable mob–spawn customizer for Paper/Folia 1.21.x.
Replace natural spawns with different mobs, equip them with randomized gear & vanilla-style enchants, create jockeys, power creepers, give skeletons block helmets, and even turn normal rabbits into The Killer Bunny — all driven by config.yml.

    Works with NATURAL spawns, SPAWNER spawns, and (optionally) SPAWNER_EGG.

    Fully compatible with Folia.

    No plugin.yml required: /dm reload is registered at runtime (paper-plugin.yml only is fine).

✨ Features

    Replacement Spawns: Convert “original” mobs (e.g., ZOMBIE, SKELETON) into other types (e.g., PIGLIN, WITHER_SKELETON, BOGGED) with weighted chances.

    Scope Control: Choose where replacement applies: natural, spawner, and/or spawn egg.

    Spawn Chance Limits: Per–entity natural spawn probability (limit or disable certain mobs).

    Multiplier: Duplicate NATURAL monster spawns (excludes bosses).

    Equipment Tables: Slot-based item chances per mob (weapon/helmet/chest/legs/boots), with randomized durability.

    Vanilla-like Enchanting:

        Bows (Power, Punch, Flame, Infinity)

        Swords/Axes (Sharpness, Smite, Fire Aspect, Looting)

        Armor (Protection/Projectile/Blast, Thorns, Feather Falling on boots)

    Special Spawns:

        Charged Creeper chance

        Drowned with Channeling Trident chance

        Chicken/Spider Jockeys for the usual families

    Block Helmets:

        Global block helmets (e.g., Pumpkin)

        Skeleton-only block helmets (e.g., Obsidian, Bone Block)

        Applied only if the helmet slot is empty

    Piglin / Piglin Brute / Hoglin Zombification Immunity:

        Original spawn is canceled and replaced with a fresh immune entity (equivalent to /summon ... {IsImmuneToZombification:1})

    Killer Bunny (two ways):

        If a replacement result is RABBIT, optionally convert to THE_KILLER_BUNNY

        When a normal rabbit spawns, optionally convert to THE_KILLER_BUNNY based on chance & scope

    Pretty console banners on load/unload, and a tidy reload message.

✅ Requirements

    Java 17+

    Paper or Folia 1.21.x

📦 Installation

    Build the plugin (or download the release JAR).

    Put the JAR in your server’s plugins/ directory.

    Use paper-plugin.yml (no plugin.yml needed).

    Start the server to generate config.yml.

    Tweak config.yml to your liking.

    Reload configs via /dm reload.

🔧 Commands & Permissions

    /dm reload – Reloads config.yml.
    Registered in code, so you don’t need to declare the command in paper-plugin.yml.

Permission: dynamicmob.reload (default: OP)
⚙️ Configuration (quick start)

Below is a compact example with the most commonly used sections:

mob-spawn:
  multiplier: 1.0          # Duplicate NATURAL monster spawns (excludes bosses)
  enable-spawn-egg: true   # Apply customizations to SPAWNER_EGG spawns

light-threshold: 9         # Informational only (vanilla hostiles = 7, Nether variants vary)

enchant-chance:
  weapon: 0.35             # Chance to enchant weapon
  armor: 0.25              # Chance to enchant each armor piece

special:
  leaves_helmet: 0.01
  bone_in_hand: 0.04
  charged_creeper: 0.001

  # Block helmets (applied only if helmet slot empty)
  block_helmet:
    general:
      PUMPKIN: 0.02
    skeleton:
      BONE_BLOCK: 0.03
      OBSIDIAN: 0.01

  # Killer Bunny when the replacement result is a Rabbit
  killer_bunny_from_replacement: true
  killer_bunny_chance: 1.0

  # Convert normal rabbits to Killer Bunny on spawn
  killer_bunny_on_rabbit_spawn:
    enabled: true
    chance: 0.05
    apply-to:
      natural: true
      spawner: false
      spawn-egg: false

# Replacement scope
replacement:
  apply-to-natural: true
  apply-to-spawner: false
  apply-to-spawn-egg: false

# Replacement tables: origin -> weighted targets
replacement-spawn:
  ZOMBIE:
    PIGLIN: 0.12
    PIGLIN_BRUTE: 0.05
    HUSK: 0.10
    ZOMBIFIED_PIGLIN: 0.07
    ZOGLIN: 0.03
  SKELETON:
    STRAY: 0.10
    WITHER_SKELETON: 0.07
    BOGGED: 0.08

# Natural spawn chance limiting (NATURAL only; 1.0 = no limit)
spawn-chance:
  ZOMBIE: 1.0
  HUSK: 0.6
  DROWNED: 0.35
  ZOMBIFIED_PIGLIN: 0.7
  PIGLIN: 0.7
  PIGLIN_BRUTE: 0.2
  HOGLIN: 0.5
  ZOGLIN: 0.5
  SKELETON: 1.0
  STRAY: 0.5
  WITHER_SKELETON: 0.8
  BOGGED: 0.5

# Jockey chances
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

# Slot-based equipment tables (per mob)
spawn-settings:
  SKELETON:
    enabled: true
    weapon:
      BOW: 0.3
      STONE_SWORD: 0.01
    helmet:
      LEATHER_HELMET: 0.05
    chestplate:
      LEATHER_CHESTPLATE: 0.05
    leggings:
      LEATHER_LEGGINGS: 0.05
    boots:
      LEATHER_BOOTS: 0.05

  DROWNED:
    enabled: true
    weapon:
      TRIDENT: 0.001
      TRIDENT_CHANNELING: 0.0001   # Special token handled by the plugin
      WOODEN_SWORD: 0.02

  PIGLIN:
    enabled: true
    weapon:
      GOLDEN_SWORD: 0.08
      GOLDEN_AXE: 0.05

    Notes

        In replacement-spawn, if the sum of weights is less than 1.0, the remaining probability keeps the original mob.

        If the sum is ≥ 1.0, an original spawn of that mob is always replaced.

        Block helmets apply only when the mob’s helmet slot is empty. Skeleton-only block helmets are tried after general ones.

🧠 How It Works

    Spawn Flow

        NATURAL spawn chance limit (per entity)

        Piglin/PiglinBrute/Hoglin: cancel & re-spawn as immune (no zombification, same as using summon NBT)

        Replacement: based on replacement-spawn + replacement.apply-to-*

            If the chosen result is RABBIT and killer_bunny_from_replacement is true, optionally convert to Killer Bunny

        Killer Bunny on Rabbit spawn: if enabled and scope matches

        Default equipment/specials/enchants/jockey logic

    Multiplier duplicates NATURAL monster spawns only (excludes Ender Dragon, Wither, Warden).

    Drowned Channeling is applied by a special token TRIDENT_CHANNELING in spawn-settings.weapon.

    Durability is randomized to feel more vanilla.

🕹️ Examples
Replace SKELETON with WITHER_SKELETON 50% of the time

replacement:
  apply-to-natural: true
  apply-to-spawner: false
  apply-to-spawn-egg: false

replacement-spawn:
  SKELETON:
    WITHER_SKELETON: 0.5

Give Skeletons a chance to wear Obsidian helmets

special:
  block_helmet:
    skeleton:
      OBSIDIAN: 0.02

Make normal Rabbits sometimes become The Killer Bunny on spawn

special:
  killer_bunny_on_rabbit_spawn:
    enabled: true
    chance: 0.03
    apply-to:
      natural: true
      spawner: false
      spawn-egg: false

🛠️ Troubleshooting

    /dm reload says “unknown command”
    The command is registered in code at runtime. If it doesn’t appear:

        Ensure the plugin actually enabled (check console banner).

        Check for command name conflicts from other plugins.

        You only need paper-plugin.yml, not plugin.yml.

    Spawn eggs aren’t using custom settings
    Set mob-spawn.enable-spawn-egg: true.

    Some Nether mobs don’t appear naturally in the Overworld
    That’s vanilla behavior. Use replacement-spawn to convert Overworld mobs into Nether variants.

    Another plugin is also modifying spawns
    Conflicts can occur. Adjust priorities/order or disable overlapping features.

🤝 Contributing

PRs and issues welcome!
Please include:

    Paper/Folia version

    Java version

    Your config.yml and minimal reproduction steps

📄 License

MIT License

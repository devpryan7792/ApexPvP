# ApexPvP

> The complete competitive PvP engine for modern Minecraft servers. Features 18-tier ranked leagues, casual duels with escrow wagering, custom schematic arenas, WorldEdit in-game map creation, shrinking safe zones, hotbar kit editors, and autonomous practice bots.

---

## Table of Contents
1. [30-Second Player Quickstart](#30-second-player-quickstart)
2. [Casual Duels & Wagers](#casual-duels--wagers)
3. [Competitive Ranked League](#competitive-ranked-league)
4. [Combat Kits & Hotbar Editor](#combat-kits--hotbar-editor)
5. [Arenas & Safe Zone Mechanics](#arenas--safe-zone-mechanics)
6. [Parties & Team Scrimmages](#parties--team-scrimmages)
7. [Practice Bots](#practice-bots)
8. [Spectator Betting](#spectator-betting)
9. [Admin Guide: In-Game Arena Creation](#admin-guide-in-game-arena-creation)
10. [Complete Command Cheat Sheet](#complete-command-cheat-sheet)
11. [Permissions Guide](#permissions-guide)
12. [Server Setup & Connectivity](#server-setup--connectivity)

---

## 30-Second Player Quickstart

| What you want to do | Command to run | What happens next |
| :--- | :--- | :--- |
| **Duel a friend** | `/duel <player>` | Pick a kit &rarr; Pick an arena &rarr; Friend gets preview invite &rarr; Fight! |
| **Play competitive ranked** | `/ranked` | Opens the Ranked Hub to view your ELO and queue Solo (1v1) or Team (2v2). |
| **Fight a practice bot** | `/practice <kit> [difficulty]` | Spawns an arena with an AI bot so you can warm up solo anytime. |
| **Customize your kit hotbar** | `/kiteditor <kit>` | Drag & drop weapons, pearls, and potions to your preferred slots. |
| **Check your stats & rank** | `/stats` | Displays your ELO, rank badge, win streak, win rate, and match history. |

---

## Casual Duels & Wagers

Casual duels let you challenge any player on the server for fun or money without risking your competitive ELO rank.

### 1. Issuing a Challenge
```text
/duel <player>
/duel <player> spear
/duel <player> crystal 500
```
- If you don't specify a kit or stake, the **Kit Selector GUI** opens automatically.
- After picking a kit, the **Arena Selector GUI** opens to let you choose the battlefield (e.g. Celestial Bastion, GLACIEM, Colosseum, Sumo, etc.).
- Optional: Add an amount to place a wager (e.g. `/duel Notch 500`). Both players' money is held safely in escrow.

### 2. Previewing & Accepting an Invite
When someone challenges you, clickable buttons appear directly in chat:
- Click **`[PREVIEW & ACCEPT]`** (or type `/duel preview <challenger>`):
  Opens an interactive preview card displaying the kit gear, arena size/theme, and wager stakes.
- Click **`[ACCEPT]`** (or type `/duel accept <challenger>`): Accepts immediately.
- Click **`[DECLINE]`** (or type `/duel decline <challenger>`): Declines the invite.

### 3. Casual Rules Guarantee
- **No ELO Risk:** Winning or losing a `/duel` does **not** change your competitive ELO rating.
- **Wager Payouts:** If money was staked, the winner takes the pot. On a draw, disconnect, or server restart, coins are automatically refunded.

---

## Competitive Ranked League

Ranked matchmaking is where your ELO rating, win streaks, and competitive badges are on the line.

### 1. Opening the Ranked Hub & Matchmaking
```text
/ranked                  - Open competitive Ranked Hub (Solo 1v1 / Team 2v2)
/ranked menu             - Open the Ranked Hub GUI directly
/ranked queue <kit>      - Jump directly into matchmaking queue for a kit
/ranked <kit>            - Quick shortcut (e.g. /ranked spear)
/ranked leave            - Exit matchmaking queue safely
/ranked status           - Check current queue time & ±ELO search radius
```
- **Transparent Aliases:** `/queue <kit>` and `/leavequeue` continue to work seamlessly.
- **Competitive Profile Banner:** Displays your current Tier, Division, ELO rating, active Win Streak, and Win Rate.
- **Solo Ranked (1v1):** Click to select your kit and enter the competitive queue.
- **Team Ranked (2v2):** Form a party of 2 (`/party create`, `/party invite <friend>`), then click to enter team queue.

### 2. The 18-Division Hierarchy & Automatic Rankups
Players progress through 6 tiers, each divided into 3 divisions:

```text
Copper I, II, III      (800 - 999 ELO)
Iron I, II, III        (1000 - 1199 ELO)
Gold I, II, III        (1200 - 1399 ELO)
Diamond I, II, III     (1400 - 1599 ELO)
Emerald I, II, III     (1600 - 1799 ELO)
Netherite I, II, III   (1800+ ELO)
```

- **Automatic Rank Promotion:** The moment your ELO satisfies the next rank tier threshold upon winning a ranked match, you are **automatically promoted**!
  - Full-screen celebration Title (`RANK PROMOTION!`)
  - Sound effect (`UI_TOAST_CHALLENGE_COMPLETE`)
  - Server-wide broadcast announcement
  - Real-time Lobby Boss Bar badge update
- **Manual Check (`/rankup`):** Running `/rankup` at any time displays your current rank and the exact remaining ELO needed for your next automatic promotion (or triggers it immediately if pending).
- **Win Streak Multipliers:** Consecutive wins grant coin multipliers:
  - 3 Wins: **1.25x Coins**
  - 5 Wins: **1.50x Coins**
  - 10+ Wins: **2.00x Coins**

---

## Combat Kits & Hotbar Editor

Every match equips both players with identical, balanced gear. Your previous inventory and armor are completely saved and automatically restored when the match ends.

### Popular Built-in Kits
- **Spear PvP:** Netherite Spear (Impaling, Loyalty, Sharpness), full enchanted Netherite armor, offhand Totem of Undying, 3 Golden Apples, Ender Pearls, and Healing/Harming splash potions.
- **Crystal PvP:** Netherite armor (Blast Protection IV), Obsidian, End Crystals, Respawn Anchors, Glowstone, Totems, and Crossbows.
- **NoDebuff (PotPvP):** Diamond armor (Protection IV), Diamond Sword (Sharpness V), Ender Pearls, Speed II & Fire Resistance potions, and full splash Health II potions.
- **Axe PvP:** Netherite Axe, Netherite Sword, Shield, Crossbow with loaded arrows, and Golden Apples.
- **Sumo:** Knockback stick on an elevated platform with open drops. Knock the opponent off the ring to win!

### Hotbar Layout Editor (`/kiteditor`)
Everyone has their own preferred hotbar layout (e.g. sword in slot 1 vs slot 2, pearls in slot 4 vs slot 9).
```text
/kiteditor <kit>
/editkit spear
```
1. Run the command to open your interactive kit editor GUI.
2. Drag and drop items into whichever hotbar and inventory slots you prefer.
3. Close the GUI or click **Save**. Whenever you enter a match with that kit, your custom layout will be automatically applied.

---

## Arenas & Safe Zone Mechanics

Matches take place in isolated arena instances with automatic anti-lag rollback and anti-stall mechanics.

### 1. Built-in Arena Maps
1. **Celestial Bastion** (`celestial`) - 159x121x199 majestic community fortress.
2. **GLACIEM Glacier** (`glaciem`) - 63x26x106 frozen ice and snow citadel.
3. **Spiritual Sanctuary** (`spiritual`) - 168x83x168 Japanese spiritual shrine grounds.
4. **Colosseum** (`colosseum`) - Classic roman sand & stone brick gladiatorial stadium.
5. **Temple of Olympus** (`temple`) - Greek quartz temple with glowing cyan prismarine accents.
6. **Nether Forge** (`netherforge`) - Blackstone and basalt forge flanked by magma and crimson pylons.
7. **Cyber Void** (`cybervoid`) - Dark obsidian and neon stained glass cybernetic arena.
8. **Desert Dune** (`desertdune`) - Red sandstone ruins with terracotta accents.
9. **Frost Peak** (`frostpeak`) - Packed ice and blue ice glacial arena.
10. **Sumo Dohyo** (`sumo`) - Circular elevated red and white sumo ring.

### 2. Solid Bedrock Subfloor (Crystal PvP Blast-Proof)
- Arena platforms feature 3 reinforced solid Bedrock layers directly beneath the combat floor.
- The arena world floor at `Y=0` has solid Bedrock, Deepslate, and Stone.
- End Crystals, Respawn Anchors, and TNT can never puncture into the void or drop players into endless void falls.

### 3. Shrinking Safe Zones (Anti-Stall & Anti-Lag)
To prevent players or bots from stalling a fight indefinitely:
- **Delay:** Safe zone remains full size for the first **3 minutes** (`180s`) of combat.
- **Warning Alert:** 15 seconds before shrinking begins, a yellow warning title flashes: `BORDER SHRINKING IN 15s!` with bass chimes.
- **Shrinking In Action:** Over 60 seconds, a visible **ring of flame particles** contracts toward the arena center (down to a minimum 6-block radius).
- **Out-of-Bounds Damage:** Players outside the flame circle take **4 HP/second** damage and receive an action bar alert `OUTSIDE SAFE ZONE! (-4 HP)`.
- **10-Minute Hard Timeout:** If a match reaches 10 minutes (`600s`), it automatically terminates as a draw to prevent server memory bloat.

---

## Parties & Team Scrimmages

Team up with friends for 2v2 duels or practice against each other.

```text
/party create
/party invite <player>
/party accept <leader>
/party split
/party leave
/party disband
```

- **Queuing 2v2 Ranked:** Create a party of 2, run `/ranked`, and click **Team Ranked (2v2)**.
- **Party Scrimmage (`/party split`):** Divides your party into Team 1 vs Team 2 and immediately launches a friendly match so friends can scrimmage each other.

---

## Practice Bots

Fight AI-driven practice bots to warm up your mechanics, test kits, or practice solo whenever friends are offline.

```text
/practice <kit> [difficulty]
/practice spear hard
/practice crystal medium
/practice sumo easy
```

### Bot Difficulties
- **`easy`** - Relaxed movement, 6-8 CPS, basic melee tracking.
- **`medium`** - Strafing, 9-11 CPS, responsive knockback recovery, basic potion healing.
- **`hard`** - Aggressive strafing, 12-14 CPS, tactical pearl tracking, fast potion cycling.
- **`apex`** - Maximum CPS, precision combo chains, optimal shield & anchor timing.

---

## Spectator Betting

Spectators can watch live matches from the spectator platform and place wagers on active duelists before combat begins.

```text
/bet <player> <amount>
/bet Notch 250
```

- Bets can be placed during the `PREPARING` and `COUNTDOWN` phases.
- Payouts use a pari-mutuel pool: winning spectators divide the losing pool in proportion to their bets.

---

## Admin Guide: Custom Maps & In-Game Arena Creation

Server Admins and OPs can upload custom schematic maps or create brand-new arenas directly in-game without manually editing YAML files.

### 1. Uploading Custom Maps (`plugins/ApexPvP/maps/`)
Admins can upload any `.schem` or `.schematic` file directly to the server:
1. Drop your schematic file into `plugins/ApexPvP/maps/` (e.g. `castle.schem`).
2. In-game, run:
   ```text
   /arena import castle.schem castle "Castle Arena"
   ```
   *The command autocompletes all uploaded maps in your folder, calculates the bounding box, registers the template, saves to `arenas.yml`, and pastes instance 0.*
3. Set your spawns:
   ```text
   /arena setspawn castle a
   /arena setspawn castle b
   /arena setspawn castle spec
   ```
4. The arena is instantly live and ready for duels!

### 2. Creating a Map with WorldEdit (`//wand`)
1. Equip the WorldEdit wand:
   ```text
   //wand
   ```
2. Left-click corner 1 (`pos1`) and Right-click opposite corner 2 (`pos2`) around your map.
3. Run the creation command:
   ```text
   /arena create <id> "<Display Name>"
   /arena create neo_tokyo "Neo Tokyo Arena"
   ```
   *The plugin captures your WorldEdit bounds, centers coordinates, registers the template, and allocates instance 0 immediately.*
4. If you don't have a WorldEdit selection, you can pass a custom radius instead:
   ```text
   /arena create neo_tokyo "Neo Tokyo Arena" 35
   ```

### 2. Setting Arena Spawn Points
Stand at the exact location and look in the direction you want players to face:
- **Set Player 1 Spawn:**
  ```text
  /arena setspawn <id> a
  ```
- **Set Player 2 Spawn:**
  ```text
  /arena setspawn <id> b
  ```
- **Set Spectator Floating Spawn:**
  ```text
  /arena setspawn <id> spec
  ```

### 3. Inspecting & Managing Arenas
- **List all loaded templates & instances:**
  ```text
  /arena list
  ```
- **Teleport to an arena's spectator spawn:**
  ```text
  /arena tp <id>
  ```
- **Hot-reload arenas and rebuild platforms:**
  ```text
  /arena reload
  ```

---

## Complete Command Cheat Sheet

### Player Commands

| Command | Usage | Description |
| :--- | :--- | :--- |
| `/duel` | `/duel <player> [kit] [stake]` | Issue a 1v1 casual duel challenge (opens Kit/Arena GUI if omitted) |
| `/duel accept` | `/duel accept <player>` | Accept a pending duel challenge |
| `/duel decline` | `/duel decline <player>` | Decline a pending duel challenge |
| `/duel preview` | `/duel preview <player>` | Open the interactive preview card showing kit, arena, and stakes |
| `/ranked` | `/ranked [menu\|queue <kit>\|leave\|status]` | Competitive hub, direct kit queuing, leaving, and status |
| `/queue` | `/queue <kit>` | Quick alias to join ranked matchmaking queue |
| `/leavequeue` | `/leavequeue` | Quick alias to exit ranked matchmaking queue |
| `/practice` | `/practice <kit> [difficulty]` | Start a PvE battle against an AI practice bot |
| `/kits` | `/kits` | Open the visual kit showcase and preview menu |
| `/kiteditor` | `/kiteditor <kit>` | Open the custom hotbar layout editor for a kit |
| `/stats` | `/stats [player]` | View your or another player's ELO, rank, streaks, and win rate |
| `/top` | `/top [elo\|wins\|streak\|<kit>]` | View top-10 cached competitive leaderboards |
| `/rankup` | `/rankup` | Check current rank and exact remaining ELO needed for promotion |
| `/party create` | `/party create` | Create a new party |
| `/party invite` | `/party invite <player>` | Invite a player to your party |
| `/party accept` | `/party accept <leader>` | Accept an invite to join a party |
| `/party split` | `/party split` | Split your party into two teams for an instant scrimmage |
| `/party leave` | `/party leave` | Leave your current party |
| `/party disband` | `/party disband` | Disband your party (leader only) |
| `/bet` | `/bet <player> <amount>` | Wager coins on an active duel combatant as a spectator |

### Staff & Admin Commands (`apexpvp.admin` / OP)

| Command | Usage | Description |
| :--- | :--- | :--- |
| `/arena create` | `/arena create <id> [displayName] [radius]` | Register a new arena from WorldEdit selection or radius |
| `/arena import` | `/arena import <file> <id> [displayName]` | Import uploaded `.schem`/`.schematic` map from `maps/` folder |
| `/arena setspawn` | `/arena setspawn <id> <a\|b\|spec>` | Set relative spawn points at your player's exact location |
| `/arena tp` | `/arena tp <id>` | Teleport to the spectator spawn of an arena template |
| `/arena list` | `/arena list` | View all loaded arena templates and active game instances |
| `/arena reload` | `/arena reload` | Reload `arenas.yml` and rebuild standby arena platforms |
| `/apex reload` | `/apex reload` | Reload `config.yml`, kits, and database settings |
| `/apex eco` | `/apex eco <give\|take\|set> <player> <amt>` | Manage player coin balances |
| `/apex simduel` | `/apex simduel <kit> [diff] [stake]` | Launch a full 2-bot combat simulation for testing |

---

## Permissions Guide

```yaml
# Player Permissions (Granted by default)
apexpvp.duel: true          # /duel, /duel preview, accept, decline
apexpvp.queue: true         # /queue, /leavequeue, /ranked
apexpvp.party: true         # /party commands
apexpvp.practice: true      # /practice (bot fights)
apexpvp.kit: true           # /kits and /kiteditor
apexpvp.stats: true         # /stats and /profile
apexpvp.top: true           # /top and /leaderboard
apexpvp.rankup: true        # /rankup promotions
apexpvp.bet: true           # /bet spectator wagers

# Staff & Admin Permissions (Default: OP)
apexpvp.arena.admin: true   # /arena create, setspawn, tp, reload
apexpvp.admin: true         # /apex eco, reload, simduel
apexpvp.kit.admin: true     # /kits create, delete, configure
```

---

## Server Setup & Connectivity

### Requirements
- **Server:** Paper 1.20.4+ / Paper 26.3
- **Java:** OpenJDK 21 or Java 25
- **Plugins:**
  - `Citizens 2` (Required for practice bots and combat simulation)
  - `FastAsyncWorldEdit` or `WorldEdit` (Optional, required for in-game `//wand` `/arena create`)
  - `Vault` + Economy provider like EssentialsX (Optional, required for coin wagers)
  - `PlaceholderAPI` (Optional, for lobby scoreboards and chat tags)
  - `ViaVersion` + `ViaBackwards` (Optional, enables clients from 1.8 to 1.21+ to connect)

### Connecting to the Server
- **Local:** `localhost:25565` (or `127.0.0.1:25565`)
- **Remote / Friends:** `malls-marion.tun.ply.gg:36457` (or `147.185.221.26:36457`)

### Compiling from Source
```bash
# Compile and run test suite (96+ unit tests)
./gradlew check

# Build shaded plugin jar
./gradlew shadowJar

# The compiled jar is located at:
# apex-core/build/libs/apex-core-1.0.0-SNAPSHOT.jar
```

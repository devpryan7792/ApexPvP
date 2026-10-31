# ApexPvP

> High-performance competitive PvP and duels engine for modern Minecraft servers. Features an in-game admin GUI editor, player duel hub, 18-tier ranked leagues with automatic promotions, item betting escrow, custom schematic arenas with WorldEdit integration, shrinking safe zones, soup PvP, hotbar kit editors, and autonomous practice bots.

---

## Table of Contents
1. [Quickstart](#quickstart)
2. [Player Hub & Duel Privacy (`/duels`)](#player-hub--duel-privacy-duels)
3. [Casual Duels, Item Betting & Escrow](#casual-duels-item-betting--escrow)
4. [Competitive Ranked League](#competitive-ranked-league)
5. [Parties & Team Scrimmages](#parties--team-scrimmages)
6. [Free-For-All (FFA) Deathmatch](#free-for-all-ffa-deathmatch)
7. [Combat Kits, Soup PvP & Hotbar Editor](#combat-kits-soup-pvp--hotbar-editor)
8. [Arenas & Safe Zone Mechanics](#arenas--safe-zone-mechanics)
9. [Practice Bots](#practice-bots)
10. [Spectator Betting](#spectator-betting)
11. [Admin In-Game GUI Editor (`/apex editor` & `/pduels editor`)](#admin-in-game-gui-editor-apex-editor--pduels-editor)
12. [Custom Maps & WorldEdit Creation](#custom-maps--worldedit-creation)
13. [Complete Command Cheat Sheet](#complete-command-cheat-sheet)
14. [Permissions Guide](#permissions-guide)
15. [Server Setup & Connectivity](#server-setup--connectivity)
16. [Master In-Game Testing Runbook (Test Everything)](#master-in-game-testing-runbook-test-everything)

---

## Quickstart

| Goal | Command | Description |
| :--- | :--- | :--- |
| **Open Player Hub** | `/duels` | Central 36-slot dashboard for matchmaking, stats, leaderboards, and privacy settings. |
| **Duel a Player** | `/duel <player>` | Pick a kit and arena, challenge with optional coin stakes or physical items. |
| **Wager Items in Escrow** | `/duel <player> item` | Open the 54-slot shared anti-scam escrow window to wager physical items. |
| **Competitive Ranked** | `/ranked` | Open Ranked Hub to view your ELO and queue Solo (1v1) or Team (2v2 - 4v4). |
| **Free-For-All (FFA)** | `/ffa queue <kit>` | Join 4-player solo FFA queue or run `/party ffa` for all-vs-all chaos. |
| **Party Management** | `/party` | Open 27-slot GUI for party queues, direct party duels (2v2 - 4v4), and scrimmages. |
| **Fight a Practice Bot** | `/practice <kit> [difficulty]` | Warm up solo anytime against an AI combatant. |
| **Customize Kit Hotbar** | `/kiteditor <kit>` | Drag and drop weapons, pearls, and potions into your preferred slots. |
| **Admin In-Game Editor** | `/apex editor` | Comprehensive GUI to configure arenas, modes, kits, and toggles without YAML editing. |

---

## Player Hub & Duel Privacy (`/duels`)

The `/duels` command opens a unified, interactive dashboard accessible to every player:

```text
/duels
```

### Dashboard Features
- **Matchmaking Portals:** Quick-queue into Solo (1v1) and Team (2v2 - 4v4) competitive or casual matches with 1 click.
- **Kit Layout Customizer:** Quick link to the Hotbar Layout Editor for any kit.
- **Personal Statistics:** Displays current ELO, rank badge, win streak, win rate, and total matches.
- **Top Leaderboards:** Live top-10 leaderboard rankings.
- **Duel Request Privacy Toggle:**
  - Click the **Privacy Bell** inside `/duels` or use the chat commands:
    ```text
    /duel toggle
    /duel privacy
    ```
  - When muted, other players cannot send you duel challenges (`"Player is currently not accepting duel requests."`).
  - Privacy state is persistently tracked in the database across server restarts.

---

## Casual Duels, Item Betting & Escrow

Casual duels allow challenging any player on the server for practice, coin stakes, or high-stakes physical item wagers without risking competitive ELO ratings.

### 1. Issuing Challenges
```text
/duel <player>               - Opens Kit & Arena selectors
/duel <player> spear         - Duel with the Spear kit
/duel <player> crystal 500   - Duel with a 500-coin escrow stake
/duel <player> item          - Open the shared Item Betting Escrow GUI
```

### 2. Item Betting Escrow (`/duel <player> item`)
The physical item betting system enables safe, scam-proof high-stakes wagers:
- **Shared Escrow Grid:** Opens a 54-slot chest interface divided into Challenger slots (left) and Challenged slots (right).
- **Anti-Scam Locking Mechanism:**
  - Both players place the items they want to wager into their designated slots.
  - When ready, each player clicks their Confirmation Wool button.
  - **Auto-Reset Guarantee:** If *either* player moves, swaps, or adds an item while in a confirmed state, **both confirmations are immediately revoked** with an alert message. Combat cannot start unless both players lock in the exact final item state.
- **Pot Payout & Draw Protection:**
  - Upon victory, the winner receives all escrowed items directly into their inventory (or drops at their feet if full).
  - If the match draws, disconnects, or the server restarts, all wagered items are safely refunded to their original owners.

### 3. Own Inventory Mode
- Duel matches can be configured to use players' actual inventories rather than standard kits.
- On death, the defeated player's items drop naturally on the arena floor.
- **15-Second Post-Match Looting Window:** A 15-second countdown window gives the winner time to collect the loser's dropped gear before teleporting back to the lobby.

---

## Competitive Ranked League

Ranked matchmaking pits players against each other in competitive 1v1 and Team (2v2, 3v3, 4v4) modes with ELO ratings, divisions, and automated promotions. Parties of 2 to 4 members are automatically matched against parties of identical size.

### 1. Ranked Hub & Matchmaking
```text
/ranked                      - Open the competitive Ranked Hub GUI
/ranked queue <kit>          - Direct matchmaking queue shortcut
/queue <kit>                 - Seamless queue alias
/ranked leave                - Safely exit matchmaking queue
/leavequeue                  - Seamless exit alias
/ranked status               - Check current queue time & search radius
```

### 2. The 18-Division Hierarchy & Automatic Rankups
Players climb through 6 tiers, each divided into 3 divisions:

```text
Copper I, II, III      (800 - 999 ELO)
Iron I, II, III        (1000 - 1199 ELO)
Gold I, II, III        (1200 - 1399 ELO)
Diamond I, II, III     (1400 - 1599 ELO)
Emerald I, II, III     (1600 - 1799 ELO)
Netherite I, II, III   (1800+ ELO)
```

- **Automatic Promotions:** The moment your ELO reaches a new tier threshold upon winning a match, promotion triggers automatically:
  - Celebration title banner (`RANK PROMOTION!`)
  - Audio fanfare (`UI_TOAST_CHALLENGE_COMPLETE`)
  - Server-wide broadcast announcement
  - Real-time Boss Bar badge update
- **Manual Promotion Status (`/rankup`):** Displays your current division and the exact ELO points needed for your next promotion.
- **Win Streak Multipliers:**
  - 3 Wins: **1.25x Coins**
  - 5 Wins: **1.50x Coins**
  - 10+ Wins: **2.00x Coins**

---

## Parties & Team Scrimmages

Form parties for competitive 2v2, 3v3, and 4v4 team queues, direct party-vs-party challenges, or instant intra-party scrimmages.

### 1. Interactive Party GUI (`/party`)
Running `/party` (with no arguments), `/party menu`, or `/party gui` opens the 27-slot Party Management interface:
- **Member Roster:** Displays player heads for each party member with their rank, role, and ping.
- **Leader Crown Badge:** Indicates the party leader.
- **Open Slots:** Visual empty slot indicators showing remaining capacity (up to 8 members for full 4v4 scrimmages).
- **Party Scrimmage Button:** Instantly divides the party into Team 1 vs Team 2 (supports 2v2, 3v3, and 4v4) and starts a private match.
- **Disband / Leave Buttons:** Quick 1-click controls to exit or disband the party.

### 2. Team Matchmaking Queues (2v2 - 4v4)
- Party leaders with **2 to 4 members** can queue the entire party via the `/ranked` GUI (Slot 15: `Team Ranked (2v2 - 4v4)`).
- **Matchmaking Isolation:** A 2v2 party only fights another 2v2 party, 3v3 fights 3v3, and 4v4 fights 4v4.
- Party leaders and all members are registered into the queue ticket simultaneously with automatic fee verification and lateral spawn offsets.

### 3. Direct Party Duels (`/party duel`)
Party leaders can challenge other parties directly to a team duel without queueing:
```text
/party duel <targetLeader> [kit]       - Challenge another party of the same size (2v2, 3v3, or 4v4)
/party accept-duel <senderLeader>      - Accept a pending party duel challenge
/party decline-duel <senderLeader>     - Decline a pending party duel challenge
```
- Both parties must have between 2 and 4 players, and both party member counts must match exactly.
- Challenge invitations feature clickable chat buttons (`[ACCEPT DUEL]` / `[DECLINE]`).

### 4. Party Split Scrimmages (`/party split`)
Intra-party scrimmages let friends battle each other within the same party:
```text
/party split [kit]                     - Split the party evenly into Team 1 vs Team 2
```
- Supports **4 players** (2v2), **6 players** (3v3), or **8 players** (4v4).
- Odd party sizes or sizes exceeding 8 players are rejected with clear error feedback.

### 5. Visual Party Invite GUI & Commands
- Running `/party invite` with no target player opens a 54-slot GUI displaying skulls of all online server players.
- Clicking any player's head sends an invitation instantly.

```text
/party create                          - Create a party
/party invite [player]                 - Open player picker GUI or invite directly
/party accept <leader>                 - Accept a party invite
/party duel <leader> [kit]             - Challenge an opposing party to a duel (2v2 - 4v4)
/party accept-duel <leader>            - Accept a party duel
/party decline-duel <leader>           - Decline a party duel
/party split [kit]                     - Split party into teams for a scrimmage (2v2 - 4v4)
/party ffa [kit]                       - Start all-vs-all FFA deathmatch (2-16 players)
/party leave                           - Leave current party (auto-dequeues if queued)
/party disband                         - Disband party (leader only)
```

---

## Free-For-All (FFA) Deathmatch

ApexPvP includes a fully automated Free-For-All (FFA) battle-royale engine with radial perimeter spawning, mutual damage combat, and last-man-standing win conditions.

### 1. Solo 4-Player Matchmaking Queue (`/ffa`)
Jump into quick, casual multi-player deathmatches without needing a pre-made party:
```text
/ffa queue <kit>                       - Queue for a 4-player FFA match with specified kit
/ffa leave                             - Leave your active FFA queue
/ffa status                            - Check queue progress (players waiting out of 4)
/ffa [kit]                             - Quick shortcut to join queue or open kit selector
```
- **Instant Auto-Drain:** As soon as 4 players queue for the same kit, an arena instance is allocated and all 4 combatants are warped into combat simultaneously.
- **Queue Protection:** Queuing FFA automatically removes you from 1v1 / ranked queues to prevent double-booking.

### 2. Party FFA Deathmatch (`/party ffa`)
Party leaders can launch an all-vs-all bloodbath among party members:
```text
/party ffa [kit]                       - Launch Party FFA deathmatch (2 to 16 members)
```
- **Uneven Party Sizes Supported:** Unlike 2v2/3v3/4v4 split scrimmages that require even teams, Party FFA supports **any party size from 2 to 16 players** (e.g. 3, 5, 7 players).
- **Mutual Combat:** Friendly fire is completely disabled — every combatant can attack every other combatant.

### 3. Radial Perimeter Spawning
- Players are dynamically distributed along an equidistant radial perimeter around the arena center (`arena.getFfaSpawn()` or geometric center).
- Every player spawns with their yaw pointed directly towards the center of the arena, ensuring a fair line of sight and zero spawn-camping advantage.

### 4. Interactive GUI Access
- **Player Hub (`/duels`):** Slot 10 features the **Free-For-All Portal** (Netherite Axe). Left-click to join the solo queue, or Right-click to trigger Party FFA.
- **Party Menu (`/party`):** Slot 21 features the **Party FFA button** (Netherite Axe) for instant 1-click deathmatches with your party.

---

## Combat Kits, Soup PvP & Hotbar Editor

Every match equips players with balanced, identical gear. Your pre-match inventory, armor, and location are saved and restored cleanly upon match completion.

### Built-in Kits
- **Spear PvP:** Netherite Spear (Impaling, Loyalty, Sharpness), enchanted Netherite armor, offhand Totem of Undying, Golden Apples, Ender Pearls, and splash potions.
- **Crystal PvP:** Netherite armor (Blast Protection IV), Obsidian, End Crystals, Respawn Anchors, Glowstone, Totems, and Crossbows.
- **NoDebuff (PotPvP):** Protection IV Diamond armor, Sharpness V Diamond Sword, Speed II & Fire Resistance potions, splash Healing II potions, and Ender Pearls.
- **Soup PvP:** Iron armor, Diamond sword, and inventory loaded with mushroom stew.
- **Axe PvP:** Netherite Axe, Sword, Shield, Crossbow with loaded arrows, and Golden Apples.
- **Sumo:** Knockback stick on an elevated ring. Knock the opponent off to win.

### Extended Combat Rules
- **Instant Soup Healing:** Right-clicking Mushroom Stew, Rabbit Stew, or Beetroot Soup instantly restores **3.5 hearts (7.0 HP)** up to max health and swaps the held bowl to an empty bowl.
- **Empty Bottle Auto-Removal:** Consuming splash or drinkable potions automatically removes empty glass bottles from the player's inventory, eliminating clutter during high-intensity pot duels.
- **Custom Cooldown Overrides:** Configurable per-kit cooldowns for Ender Pearls and Golden Apples.
- **Crafting Lockout:** Disables workbench crafting and inventory 2x2 crafting grids during duels to prevent combat exploits.

### Hotbar Layout Editor (`/kiteditor`)
```text
/kiteditor <kit>
/editkit <kit>
```
1. Open the interactive kit editor GUI.
2. Drag and drop weapons, pearls, potions, and food into your preferred slots.
3. Save your layout. Every match played with that kit will automatically sort your items to match your layout.

---

## Arenas & Safe Zone Mechanics

Matches take place in isolated arena instances with automatic anti-lag rollback and anti-stall mechanics.

### 1. Built-in Arena Maps
1. **Celestial Bastion** (`celestial`) - 159x121x199 fortress.
2. **GLACIEM Glacier** (`glaciem`) - 63x26x106 frozen citadel.
3. **Spiritual Sanctuary** (`spiritual`) - 168x83x168 Japanese shrine grounds.
4. **Colosseum** (`colosseum`) - Sand and stone gladiatorial stadium.
5. **Temple of Olympus** (`temple`) - Quartz and prismarine temple.
6. **Nether Forge** (`netherforge`) - Blackstone and magma forge.
7. **Cyber Void** (`cybervoid`) - Dark obsidian and neon cyber arena.
8. **Desert Dune** (`desertdune`) - Red sandstone ruins.
9. **Frost Peak** (`frostpeak`) - Glacial arena.
10. **Sumo Dohyo** (`sumo`) - Elevated sumo ring.

### 2. Solid Bedrock Subfloor
- 3 reinforced solid Bedrock layers directly beneath the arena floor.
- World floor at `Y=0` has solid Bedrock, Deepslate, and Stone to prevent void drops during End Crystal or Respawn Anchor explosions.

### 3. Shrinking Safe Zones (Anti-Stall)
- **Delay:** Safe zone remains full size for the first **3 minutes** (`180s`).
- **Warning:** 15 seconds before shrinking, a title alert flashes: `BORDER SHRINKING IN 15s!`.
- **Shrinking:** Contracts over 60 seconds down to a minimum 6-block radius, visible by a ring of flame particles.
- **Out-of-Bounds Damage:** Players outside take **4 HP/second** damage.
- **10-Minute Timeout:** Matches terminate as a draw at 10 minutes (`600s`) to prevent server resource exhaustion.

---

## Practice Bots

Fight AI-driven practice bots to warm up mechanics, test kit hotbars, or practice solo.

```text
/practice <kit> [difficulty]
/practice spear hard
/practice crystal medium
/practice sumo easy
```

### Bot Difficulties
- **`easy`** - Relaxed movement, 6-8 CPS, basic melee tracking.
- **`medium`** - Strafing, 9-11 CPS, knockback recovery, basic potion healing.
- **`hard`** - Aggressive strafing, 12-14 CPS, tactical pearl tracking, fast potion cycling.
- **`apex`** - Maximum CPS, precision combo chains, optimal shield & anchor timing.

---

## Spectator Betting

Spectators can watch live matches and place wagers on active duelists before combat begins.

```text
/bet <player> <amount>
/bet Notch 250
```

- Bets can be placed during `PREPARING` and `COUNTDOWN` phases.
- Payouts use a pari-mutuel pool: winning spectators divide the losing pool in proportion to their wagers.

---

## Admin In-Game GUI Editor (`/apex editor` & `/pduels editor`)

Server administrators can manage the entire duels system visually without manually editing YAML configuration files.

```text
/apex editor
/pduels editor
```

### 1. Root Editor Navigation
- **Arenas:** Visual list of all configured arena templates.
- **Game Modes:** Toggle enabled kits and rulesets.
- **Settings:** General settings and match timeouts.
- **Reload:** Hot-reload configs, kits, and arena templates with zero server downtime.

### 2. Visual Arena Manager (54-Slot Grid)
- **Status Indicators:**
  - **Lime Wool:** Arena active and available for matchmaking.
  - **Red Wool:** Arena under maintenance.
- **Quick Controls:** Click any arena to enter its Detail Editor, or click the Nether Star to create a new arena.

### 3. Arena Detail Editor
- **Maintenance Toggle:** Enable/disable an arena instantly.
- **Auto-Rollback Toggle:** Enable or disable automated block rollback upon match completion.
- **Lobby Teleport Toggle:** Control whether players return to lobby after matches.
- **1-Click Spawn Setting:**
  - Click **Spawn A** to set Player 1 spawn at your current player coordinates.
  - Click **Spawn B** to set Player 2 spawn at your current player coordinates.
  - Click **Spectator Spawn** to set the floating spectator view point.
  - Click **FFA Spawn** to set the multi-player deathmatch spawn.
- **Clone Arena:** Duplicate an existing arena template and its coordinates with one click.
- **Delete Arena:** Remove an arena template from memory and `arenas.yml`.

---

## Custom Maps & WorldEdit Creation

### 1. Uploading Custom Maps (`plugins/ApexPvP/maps/`)
1. Place `.schem` or `.schematic` files into `plugins/ApexPvP/maps/` (e.g. `gladiator.schem`).
2. Run in-game:
   ```text
   /arena import gladiator.schem gladiator "Gladiator Arena"
   ```
   *The command autocompletes files from the maps directory, calculates dimensions, registers the template, and generates instance 0.*
3. Set your spawns:
   ```text
   /arena setspawn gladiator a
   /arena setspawn gladiator b
   /arena setspawn gladiator spec
   ```

### 2. Creating an Arena with WorldEdit (`//wand`)
1. Select corner 1 and corner 2 using `//wand`.
2. Register the arena:
   ```text
   /arena create <id> "<Display Name>"
   /arena create neo_arena "Neo Arena"
   ```
3. Set spawns using `/arena setspawn <id> <a|b|spec>` or via `/apex editor`.

---

## Complete Command Cheat Sheet

### Player Commands

| Command | Usage | Description |
| :--- | :--- | :--- |
| `/duels` | `/duels` | Open the main Player Hub GUI |
| `/duel` | `/duel <player> [kit] [stake]` | Issue a 1v1 casual duel challenge |
| `/duel item` | `/duel <player> item` | Open the Item Betting Escrow GUI |
| `/duel toggle` | `/duel toggle` | Toggle accepting/muting duel requests |
| `/duel accept` | `/duel accept <player>` | Accept a pending duel challenge |
| `/duel decline` | `/duel decline <player>` | Decline a pending duel challenge |
| `/duel preview` | `/duel preview <player>` | Open preview card showing kit, arena, and stakes |
| `/ranked` | `/ranked [menu\|queue <kit>\|leave\|status]` | Competitive hub, Solo (1v1) and Team (2v2 - 4v4) queues |
| `/queue` | `/queue <kit>` | Quick alias to join ranked queue |
| `/leavequeue` | `/leavequeue` | Quick alias to exit ranked queue |
| `/ffa` | `/ffa [queue <kit>\|leave\|status]` | Join 4-player solo FFA queue or check queue status |
| `/party` | `/party [menu\|gui]` | Open interactive 27-slot Party GUI |
| `/party invite` | `/party invite [player]` | Open online player picker GUI or invite by name |
| `/party duel` | `/party duel <leader> [kit]` | Challenge an opposing party to a duel (2v2 - 4v4) |
| `/party accept-duel` | `/party accept-duel <leader>` | Accept an incoming party-vs-party challenge |
| `/party decline-duel` | `/party decline-duel <leader>` | Decline an incoming party-vs-party challenge |
| `/party split` | `/party split [kit]` | Split party into two teams for scrimmage (2v2, 3v3, 4v4) |
| `/party ffa` | `/party ffa [kit]` | Start all-vs-all FFA deathmatch (2-16 players) |
| `/party leave` | `/party leave` | Leave current party (auto-dequeues if queued) |
| `/party disband` | `/party disband` | Disband party (leader only) |
| `/practice` | `/practice <kit> [difficulty]` | Start battle against an AI practice bot |
| `/kits` | `/kits` | Open the visual kit showcase menu |
| `/kiteditor` | `/kiteditor <kit>` | Open custom hotbar layout editor |
| `/stats` | `/stats [player]` | View your or another player's ELO and statistics |
| `/top` | `/top [elo\|wins\|streak\|<kit>]` | View top-10 competitive leaderboards |
| `/rankup` | `/rankup` | Check current rank and ELO needed for next promotion |
| `/bet` | `/bet <player> <amount>` | Wager coins on an active combatant as a spectator |

### Admin & Staff Commands (`apexpvp.admin` / OP)

| Command | Usage | Description |
| :--- | :--- | :--- |
| `/apex editor` | `/apex editor` | Open the main Admin In-Game GUI Editor |
| `/pduels editor`| `/pduels editor` | Alias for the Admin In-Game GUI Editor |
| `/arena create` | `/arena create <id> [displayName] [radius]` | Register arena from WorldEdit selection or radius |
| `/arena import` | `/arena import <file> <id> [displayName]` | Import uploaded `.schem` map from `maps/` folder |
| `/arena setspawn`| `/arena setspawn <id> <a\|b\|spec\|ffa>` | Set relative spawn points at current location |
| `/arena tp` | `/arena tp <id>` | Teleport to spectator spawn of an arena template |
| `/arena list` | `/arena list` | View loaded arena templates and game instances |
| `/arena reload` | `/arena reload` | Reload `arenas.yml` and rebuild standby platforms |
| `/apex reload` | `/apex reload` | Reload `config.yml`, kits, and database settings |
| `/apex eco` | `/apex eco <give\|take\|set> <player> <amt>` | Manage player coin balances |
| `/apex simduel` | `/apex simduel <kit> [diff] [stake]` | Launch 2-bot combat simulation for testing |

---

## Permissions Guide

```yaml
# Player Permissions (Default: All Players)
apexpvp.duel: true          # /duels, /duel, /duel preview, accept, decline, toggle
apexpvp.queue: true         # /queue, /leavequeue, /ranked
apexpvp.ffa: true           # /ffa, /ffa queue, leave, status
apexpvp.party: true         # /party commands and Party GUI
apexpvp.practice: true      # /practice (bot fights)
apexpvp.kit: true           # /kits and /kiteditor
apexpvp.stats: true         # /stats and /profile
apexpvp.top: true           # /top and /leaderboard
apexpvp.rankup: true        # /rankup promotions
apexpvp.bet: true           # /bet spectator wagers

# Staff & Admin Permissions (Default: OP)
apexpvp.admin: true         # /apex editor, /pduels editor, /apex eco, reload, simduel
apexpvp.arena.admin: true   # /arena create, setspawn, tp, reload
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
# Run full test suite (100+ unit tests with 100% pass rate)
./gradlew check

# Build shaded production plugin jar
./gradlew shadowJar

# The compiled jar is located at:
# apex-core/build/libs/apex-core-1.0.0-SNAPSHOT.jar
```

---

## Master In-Game Testing Runbook (Test Everything)

Use this complete copy-pasteable testing sequence to verify every feature, GUI, and combat mode in ApexPvP on your live server.

### 1. Admin Verification & Management
```text
/apex editor                    - Open the In-Game Admin Editor GUI (arenas, kits, settings)
/pduels editor                  - Test the admin GUI alias
/arena list                     - View all registered arena templates & active instances
/arena tp celestial             - Teleport to the spectator spawn of the 'celestial' arena
/apex reload                    - Reload all YAML configurations, kits, and database caches
/apex eco give <player> 1000    - Give test coins to test coin-staked duels and fees
```

### 2. Player Hub & Privacy Settings
```text
/duels                          - Open the 36-slot central Player Hub GUI
/duel toggle                    - Toggle duel challenge requests ON/OFF (privacy mute)
/duel privacy                   - Check or toggle duel privacy state
/stats                          - View your personal combat ELO, win streak, and stats
/stats <player>                 - View another player's statistics card
/top                            - View the top-10 global leaderboard
/rankup                         - Check current division and ELO needed for next promotion
```

### 3. Autonomous Practice Bots (AI Combatants)
*Warm up and verify Citizens bot mechanics (strafing, pearls, totem pops, shield blocks, healing):*
```text
/practice spear easy            - Fight an Easy Spear PvP bot
/practice spear medium          - Fight a Medium Spear PvP bot
/practice spear hard            - Fight a Hard Spear PvP bot (aggressive sprint-resetting)
/practice crystal hard          - Fight a Hard End Crystal PvP bot (anchor & crystal detonator)
/practice nodebuff hard         - Fight a PotPvP bot (instant splash potion healing)
/practice sumo nightmare        - Fight a Nightmare Sumo bot on the ring
/apex simduel spear hard 50     - Spawn 2 bots fighting each other with a 50-coin pot
```

### 4. Casual 1v1 Duels & Escrow Betting
```text
/duel <player>                  - Open interactive Kit & Arena selectors
/duel <player> spear            - Direct 1v1 challenge with Spear kit
/duel <player> crystal 100      - Challenge with a 100-coin escrow wager
/duel <player> item             - Open the 54-slot shared Anti-Scam Item Betting Escrow GUI
/duel preview <player>          - Open visual preview card showing kit, arena, and wagers
/duel accept <player>           - Accept an incoming duel request
/duel decline <player>          - Decline an incoming duel request
```

### 5. Competitive Ranked Matchmaking
```text
/ranked                         - Open Ranked Hub GUI (view division badge & queue portals)
/queue spear                    - Quick-queue for 1v1 Ranked Spear PvP
/queue crystal                  - Quick-queue for 1v1 Ranked Crystal PvP
/leavequeue                     - Exit active ranked queue
```

### 6. Free-For-All (FFA) Deathmatch
```text
/ffa                            - Open FFA kit selector or queue status
/ffa queue spear                - Join the 4-player solo FFA deathmatch queue
/ffa status                     - Check queue status (number of players waiting)
/ffa leave                      - Exit active FFA queue
```
*(In `/duels`, click **Slot 10 Netherite Axe** to access the FFA menu directly).*

### 7. Parties & Team Scrimmages
```text
/party                          - Open the interactive 27-slot Party Management GUI
/party create                   - Create a new party
/party invite                   - Open the 54-slot online player head invite picker GUI
/party invite <player>          - Directly invite a player to your party
/party accept <leader>          - Accept a party invite
/party decline <leader>         - Decline a party invite
/party ffa spear                - Launch Party FFA (all-vs-all deathmatch, 2-16 players)
/party split spear              - Split party into Team 1 vs Team 2 scrimmage (2v2, 3v3, 4v4)
/party duel <targetLeader> spear - Challenge another party to an official team duel
/party accept-duel <leader>     - Accept an incoming party duel challenge
/party decline-duel <leader>    - Decline an incoming party duel challenge
/party leave                    - Leave party (auto-dequeues party from matchmaking)
/party disband                  - Disband the party (leader only)
```
*(In `/party`, click **Slot 21 Netherite Axe** to trigger Party FFA with 1 click).*

### 8. Kits, Soup PvP & Hotbar Editor
```text
/kits                           - Open the visual showcase of all available kits
/kiteditor spear                - Open custom drag-and-drop hotbar layout editor
/kiteditor nodebuff             - Edit your custom layout for PotPvP
```
*(In match: Right-click Mushroom Stew to test instant 3.5-heart soup healing; drink potions to verify auto-clearing empty glass bottles).*

### 9. Spectator Wagers & Betting
*When spectating an active duel match:*
```text
/bet <combatant> 50             - Wager 50 coins on that player to win
```


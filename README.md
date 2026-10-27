# ApexPvP

> High-throughput competitive PvP engine with deterministic void grid instancing, amortized zero-lag arena rollback, 18-tier ELO hierarchy, atomic escrow wagering, and autonomous combat simulation for Paper 1.20+ (Java 21+).

```
 ┌────────────────────────────────────────────────────────────────────────────────┐
 │                                APEXPVP ENGINE                                  │
 └────────────────────────────────────────────────────────────────────────────────┘
        │                                 │                                 │
        ▼                                 ▼                                 ▼
 ┌──────────────┐                 ┌──────────────┐                  ┌──────────────┐
 │ Arena Engine │                 │ Match Engine │                  │  Kit Engine  │
 │ • Void Grid  │                 │ • State Mach │                  │ • Serializer │
 │ • Amortized  │                 │ • 18 Div ELO │                  │ • Injective  │
 │   Rollback   │                 │ • Streaks    │                  │   Validation │
 └──────────────┘                 └──────────────┘                  └──────────────┘
        │                                 │                                 │
        └─────────────────┬───────────────┴─────────────────────────────────┘
                          │
        ┌─────────────────┴─────────────────┐
        ▼                                   ▼
 ┌──────────────┐                    ┌──────────────┐
 │   Economy    │                    │ Persistence  │
 │ • Escrow 2PC │                    │ • SQLite WAL │
 │ • Spectator  │                    │ • PostgreSQL │
 │   Betting    │                    │ • MariaDB    │
 └──────────────┘                    └──────────────┘
```

---

## Technical Highlights

- **Deterministic Arena Grid Engine:** Spatially isolates arenas across coordinate offsets $(1000 \times \text{template}, 1000 \times \text{instance})$ in an isolated void dimension (`apex_arenas`), eliminating chunk loading contention with $>900$ blocks of void buffer between arenas.
- **Zero-Lag Amortized Arena Rollback:** Restores player-modified blocks at a strictly budgeted 250 blocks/tick using `BlockState#update(true, false)` without cascading block physics or server frame drops.
- **18-Division Competitive Hierarchy:** 6 competitive tiers (`Copper`, `Iron`, `Gold`, `Diamond`, `Emerald`, `Netherite`) with numbered divisions `1`, `2`, `3` starting at 800 base ELO.
- **Persistent Win Streaks & Multipliers:** Tracks win streaks with automatic schema migration across SQLite/PostgreSQL/MySQL, providing configurable reward multipliers (1.25x at 3 wins, 1.50x at 5 wins, 2.00x at 10 wins).
- **Atomic Escrow & Spectator Wagering:** Two-phase commit escrow locking for duel wagers with automatic refunds on match aborts or server restarts. Spectators can place pari-mutuel bets on active duels before combat starts.
- **Solo Combat Simulation Engine (`/apex simduel`):** Full 2-player combat simulation with autonomous Citizens 2 bots featuring difficulty-scaled CPS, reach clamping, and strafing—exercising ELO adjustments, streaks, escrow, and arena rollback completely solo.

---

## Quickstart

### 1. Requirements
- **Java:** OpenJDK 21 or higher.
- **Server:** Paper 1.20.4+ / Paper 1.20.6 / Paper 26.3.
- **Dependencies:**
  - `Citizens 2` (Required for practice bot combat and solo simulation)
  - `Vault` + Economy provider like EssentialsX (Optional, required for escrow staking & betting)
  - `PlaceholderAPI` (Optional, for scoreboards and chat tags)

### 2. Installation
1. Compile or download `apex-core-1.0.0-SNAPSHOT.jar`.
2. Place the jar into your server's `plugins/` directory.
3. Start the server. The plugin will automatically generate default configuration and SQLite database files.

```bash
# Build from source
./gradlew shadowJar

# Output artifact:
# apex-core/build/libs/apex-core-1.0.0-SNAPSHOT.jar
```

---

## Command Reference

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/duel <player> [kit] [stake]` | `apexpvp.duel` | Issue or manage 1v1 duel challenges |
| `/queue <kit>` | `apexpvp.queue` | Enter the ranked matchmaking queue |
| `/leavequeue` | `apexpvp.queue` | Leave the matchmaking queue |
| `/practice <kit> [difficulty]` | `apexpvp.practice` | Fight a PvE practice bot |
| `/stats [player]` | `apexpvp.stats` | Inspect competitive stats, win streaks, and rank card |
| `/top [elo\|wins\|streak\|<kit>]` | `apexpvp.top` | View top-10 cached competitive leaderboards |
| `/bet <player> <amount>` | `apexpvp.bet` | Place a spectator wager on an active duel |
| `/rankup` | `apexpvp.rankup` | Promote to the next rank tier when criteria are met |
| `/party <create\|invite\|leave\|disband\|split>` | `apexpvp.party` | Party management and team scrimmages |
| `/apex simduel <kit> [diff] [stake]` | `apexpvp.simduel` | Launch solo 2-player combat simulation |
| `/apex eco <give\|take\|set> <player> <amt>` | `apexpvp.admin` | Administrative economy balance management |
| `/apex reload` | `apexpvp.admin` | Reload configuration and messages |

---

## Permissions Tree

```yaml
permissions:
  apexpvp.admin:
    description: Master administrator permission for ApexPvP
    default: op
    children:
      apexpvp.eco: true
      apexpvp.reload: true
      apexpvp.simduel: true
  apexpvp.simduel:
    description: Access to /apex simduel solo combat simulator
    default: op
  apexpvp.duel:
    description: Access to /duel
    default: true
  apexpvp.queue:
    description: Access to /queue and /leavequeue
    default: true
  apexpvp.party:
    description: Access to /party
    default: true
  apexpvp.practice:
    description: Access to /practice
    default: true
  apexpvp.rankup:
    description: Access to /rankup
    default: true
  apexpvp.stats:
    description: Access to /stats
    default: true
  apexpvp.top:
    description: Access to /top and /leaderboard
    default: true
  apexpvp.bet:
    description: Access to /bet
    default: true
```

---

## PlaceholderAPI Expansion

| Identifier | Description |
| :--- | :--- |
| `%apexpvp_rank%` | Current rank display name (e.g. `Copper 1`, `Netherite 3`) |
| `%apexpvp_rank_badge%` | Compact rank badge (e.g. `[C-1]`, `[N-3]`) |
| `%apexpvp_tier%` | Tier name (`COPPER`, `IRON`, `GOLD`, etc.) |
| `%apexpvp_division%` | Sub-rank division number (`1`, `2`, `3`) |
| `%apexpvp_elo%` | Player competitive ELO rating |
| `%apexpvp_streak%` | Current consecutive win streak |
| `%apexpvp_best_streak%` | All-time highest win streak |
| `%apexpvp_streak_multiplier%` | Active coin reward multiplier (e.g. `1.25x`, `2.00x`) |
| `%apexpvp_coins%` | Player coin balance |
| `%apexpvp_wins%` | Total competitive wins across all kits |
| `%apexpvp_losses%` | Total losses across all kits |
| `%apexpvp_kdr%` | Overall Kill/Death ratio |
| `%apexpvp_active_matches%` | Number of currently active matches |
| `%apexpvp_available_arenas%` | Number of idle arena instances ready for combat |

---

## Architecture Guarantees

1. **Anti-Duplication / Anti-Smuggling:** Players entering any match undergo a full inventory, potion effect, and game state snapshot. Upon exit or unexpected disconnect, inventories are restored in full. Kit gear is strictly quarantined and cannot be smuggled into the lobby.
2. **Crash & Abort Escrow Safety:** Active wagers held in escrow are tracked in memory and persisted against sudden crashes. On server restart, match cancellation, or draw, funds are refunded symmetrically without deductions.
3. **Pari-Mutuel Spectator Pools:** Bets placed during `PREPARING` or `COUNTDOWN` phases are pooled. Upon victory, the net pot (minus configurable server tax) is distributed to winning bettors in proportion to their wagers.

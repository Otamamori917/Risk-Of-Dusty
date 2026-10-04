package riskod.world.run;

import arc.Events;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.entities.abilities.Ability;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import riskod.world.RiskodMaps;
import riskod.world.RiskodMaps.RiskodSector;
import riskod.world.abilites.ArchEnemyAbility;
import riskod.world.meta.Meta;
import riskod.world.unit.HuntHeroAI;

import java.util.Arrays;

/** Continuous enemy spawns; rate from stage + run time; stops when teleporter is fully charged. */
public class EnemySpawnDirector {
    public static Seq<UnitType> enemyPool = new Seq<>();
    public static float baseInterval = 90f;
    public static int maxAlive = 50;
    public static float timer;

    /// Health multiplier per time the arch enemy has killed the hero; compounds, no cap.
    public static float archHealthMul = 1.06f;

    /// Speed multiplier per time the arch enemy has killed the hero; compounds, no cap.
    public static float archSpeedMul = 1.01f;

    /// Chance floor per spawn roll for a boss to spawn without a teleporter.
    public static float rareBossBase = 0.00001f;

    /// Chance added per point of map difficulty on each spawn roll.
    public static float rareBossPerDifficulty = 0.00007f;

    /// Chance added per minute of run time on each spawn roll.
    public static float rareBossPerMinute = 0.00005f;

    /// Set once a teleporter-less boss has spawned on the current map; cleared when a world loads.
    static boolean rareBossSpawned;

    static {
        Events.on(EventType.WorldLoadEvent.class, e -> rareBossSpawned = false);
    }

    public static void register(UnitType type) {
        if (type != null) enemyPool.add(type);
    }

    public static Seq<UnitType> currentPool() {
        RiskodSector map = RiskodMaps.currentPreset();
        if (map != null && map.enemyPool.any()) return map.enemyPool;
        return enemyPool;
    }

    public static int archKills(UnitType type) {
        String arch = null;
        int best = 0;
        for (ObjectMap.Entry<String, Meta.EnemyStat> e : Meta.data().enemies) {
            if (e.value.deaths > best) {
                best = e.value.deaths;
                arch = e.key;
            }
        }
        return arch != null && arch.equals(type.name) ? best : 0;
    }

    /** Attaches the arch speed/highlight ability when the unit is the arch enemy; returns the health multiplier to apply. */
    public static float applyArchBuff(Unit u) {
        int kills = archKills(u.type);
        if (kills <= 0) return 1f;

        Ability[] next = Arrays.copyOf(u.abilities, u.abilities.length + 1);
        next[next.length - 1] = new ArchEnemyAbility(Mathf.pow(archSpeedMul, kills));
        u.abilities = next;
        return Mathf.pow(archHealthMul, kills);
    }

    public static float rareBossChance() {
        RiskodSector map = RiskodMaps.currentPreset();
        float difficulty = map != null ? map.difficulty : 0f;
        float minutes = RunState.current.runTime / 3600f;
        return rareBossBase + (rareBossPerDifficulty * difficulty) + (rareBossPerMinute * minutes);
    }

    public static void update() {
        if (!RunState.active()) return;
        if (!RunState.current.spawnsEnabled) return;
        if (Vars.world == null) return;

        Seq<UnitType> pool = currentPool();
        if (pool.isEmpty()) return;

        float interval = baseInterval / Math.max(0.25f, RunState.current.spawnRateMul());
        if(!Vars.state.isPaused()) timer += Time.delta;
        if (timer < interval) return;
        timer = 0f;

        int alive = 0;
        Team enemyTeam = Vars.state.rules.waveTeam;
        for (Unit u : mindustry.gen.Groups.unit) {
            if (u.team == enemyTeam && u.isValid()) alive++;
        }
        if (alive >= maxAlive) return;

        Tile tile = randomTile();
        if (tile == null) return;

        UnitType type = pool.random();
        boolean rareBoss = false;

        RiskodSector map = RiskodMaps.currentPreset();
        if (!rareBossSpawned && map != null && map.bossPool.any() && Mathf.chance(rareBossChance())) {
            type = map.bossPool.random();
            rareBoss = true;
        }

        Unit u = type.spawn(enemyTeam, tile.worldx(), tile.worldy());
        if (u != null) {
            if (rareBoss) rareBossSpawned = true;
            HuntHeroAI.apply(u);
            u.apply(StatusEffects.disarmed, 100);
            u.apply(StatusEffects.unmoving, 45);
            float mul = RunState.current.enemyStatMul() + applyArchBuff(u);
            u.maxHealth(u.maxHealth * mul);
            u.health(u.maxHealth);
        }
    }

    public static Tile randomTile() {
        return randomTile(2, 2);
    }

    public static Tile randomTile(int marginX, int marginY) {
        int w = Vars.world.width();
        int h = Vars.world.height();
        for (int i = 0; i < 25; i++) {
            int tx = Mathf.random(marginX, Math.max(marginX + 1, w - marginX - 1));
            int ty = Mathf.random(marginY, Math.max(marginY + 1, h - marginY - 1));
            Tile t = Vars.world.tile(tx, ty);
            if (t != null && t.floor().placeableOn && !t.solid() && !t.isDeep()) return t;
        }
        return null;
    }
}
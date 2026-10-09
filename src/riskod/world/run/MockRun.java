package riskod.world.run;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.struct.IntSet;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import riskod.world.RiskodMaps;
import riskod.world.RiskodMaps.RiskodSector;
import riskod.world.block.Teleporter;
import riskod.world.unit.HuntHeroAI;
import riskod.world.unit.PlayerCharUnitType;
import riskod.world.unit.RelicPickupUnitType;

/**
 * Sandbox-only stand-in for a run: spawns enemies and interactables on the current map without a RunState, so nothing is recorded to Meta.
 */
public class MockRun {
    public static boolean active;

    /// Number of bosses the teleporter spawns while a mock run is active.
    public static UnitType boss = null;

    /// AI style given to teleporter bosses; null means each boss type's saved style, or aggressive.
    static HuntHeroAI.HuntStyle bossStyle;

    /// True when teleporter bosses should keep their default AI.
    static boolean bossKeepAi;

    static boolean registered;

    public static int teleporterMult = 1;

    /// Ticks since the mock run started; drives spawn rate and enemy stat scaling.
    static float time;
    static float timer;
    static boolean spawnsEnabled;

    /// Random origin tries per block when looking for a free footprint.
    static final int SPOT_ATTEMPTS = 400;

    static final Seq<Placed> placed = new Seq<>();

    /// Unit ids that existed before the mock run started; anything else on the wave team is removed at the end.
    static final IntSet before = new IntSet();

    /// Consumed uniques as they were when the mock run started, restored when it ends.
    static final ObjectSet<String> uniquesBefore = new ObjectSet<>();

    static final Seq<UnitType> fallbackPool = new Seq<>();

    record Placed(Tile tile, Block block) {
    }

    public static void register() {
        if (registered) return;
        registered = true;

        Events.run(EventType.Trigger.update, MockRun::update);
        Events.on(EventType.WorldLoadEvent.class, e -> reset());
        Events.on(EventType.UnitDestroyEvent.class, e -> {
            if (!active || e.unit == null) return;
            if (e.unit.type instanceof PlayerCharUnitType && e.unit.team == Vars.state.rules.defaultTeam) {
                Core.app.post(() -> end("Hero died, mock run ended and cleared"));
            }
        });
    }

    public static int teleporterMult() {
        return active ? 1 : Math.max(1, teleporterMult);
    }


    /// A negative chests or shrines count keeps the random default;.
    public static boolean start(int chests, int shrines, UnitType unit, HuntHeroAI.HuntStyle style, boolean keepAi) {
        if (active || Vars.world == null || Vars.state == null) return false;

        before.clear();
        Groups.unit.each(u -> before.add(u.id));
        uniquesBefore.clear();
        for (String s : RunState.consumedUniques) uniquesBefore.add(s);
        placed.clear();
        time = 0f;
        timer = 0f;
        spawnsEnabled = true;
        boss = unit;
        bossStyle = style;
        bossKeepAi = keepAi;

        say(placeInteractables(chests, shrines));
        active = true;
        return true;
    }

    public static void styleBoss(Unit boss) {
        if (bossKeepAi) return;
        HuntHeroAI.HuntStyle st = bossStyle;
        if (st == null) {
            st = HuntHeroAI.styles.get(boss.type);
            if (st == null) st = HuntHeroAI.HuntStyle.aggressive;
        }
        HuntHeroAI ai = new HuntHeroAI();
        ai.style = st;
        boss.controller(ai);
    }

    public static void end() {
        end("Mock run ended, spawned content removed");
    }

    public static void end(String reason) {
        if (!active) return;
        active = false;

        for (Placed p : placed) {
            if (p.tile().build != null && p.tile().block() == p.block()) p.tile().setAir();
        }
        placed.clear();

        Seq<Unit> doomed = new Seq<>();
        Team enemy = Vars.state.rules.waveTeam;
        Groups.unit.each(u -> {
            if (before.contains(u.id)) return;
            if (u.team == enemy || u.type instanceof RelicPickupUnitType) doomed.add(u);
        });
        for (Unit u : doomed) u.remove();
        before.clear();

        restoreUniques();
        teleporterMult = 1;
        boss = null;
        bossStyle = null;
        bossKeepAi = false;

        if (Vars.ui != null) Vars.ui.showInfoToast(reason, 3f);
    }

    static void reset() {
        if (active) restoreUniques();
        active = false;
        boss = null;
        bossStyle = null;
        bossKeepAi = false;
        placed.clear();
        before.clear();
    }

    static void restoreUniques() {
        RunState.consumedUniques.clear();
        for (String s : uniquesBefore) RunState.consumedUniques.add(s);
        uniquesBefore.clear();
    }

    static void say(String text) {
        Player p = Vars.player;
        if (p != null) p.sendMessage("[accent][riskod][] " + text);
        arc.util.Log.info("[riskod] " + text);
    }

    static Teleporter findTeleporterBlock() {
        RiskodSector map = RiskodMaps.currentPreset();
        if (map != null && map.teleporterBlock != null) return map.teleporterBlock;
        if (InteractablesGenerator.teleporter != null) return InteractablesGenerator.teleporter;
        Block found = Vars.content.blocks().find(b -> b instanceof Teleporter);
        return found instanceof Teleporter t ? t : null;
    }

    static String ratio(int made, int wanted) {
        return made == wanted ? String.valueOf(made) : made + "/" + wanted;
    }

    static String bossStyleLabel() {
        if (bossKeepAi) return "default AI";
        if (bossStyle != null) return bossStyle.name();
        return "saved style or aggressive";
    }

    static String placeInteractables(int chestsWanted, int shrinesWanted) {
        Team team = Vars.state.rules.defaultTeam;
        RiskodSector map = RiskodMaps.currentPreset();

        int cMin = InteractablesGenerator.pick(map == null ? -1 : map.minChests, InteractablesGenerator.minChests);
        int cMax = Math.max(cMin, InteractablesGenerator.pick(map == null ? -1 : map.maxChests, InteractablesGenerator.maxChests));
        int sMin = InteractablesGenerator.pick(map == null ? -1 : map.minShrines, InteractablesGenerator.minShrines);
        int sMax = Math.max(sMin, InteractablesGenerator.pick(map == null ? -1 : map.maxShrines, InteractablesGenerator.maxShrines));

        int chestCount = chestsWanted >= 0 ? chestsWanted : Mathf.random(cMin, cMax);
        int shrineCount = shrinesWanted >= 0 ? shrinesWanted : Mathf.random(sMin, sMax);

        String tpResult;
        if (InteractablesGenerator.worldHas(Teleporter.class)) {
            tpResult = "map already has a teleporter (left alone)";
        } else {
            Teleporter tp = findTeleporterBlock();
            if (tp == null) {
                tpResult = "no teleporter block found";
            } else {
                tpResult = put(tp, team) ? "teleporter placed" : "no free spot for the teleporter";
            }
        }

        int chests = scatter(InteractablesGenerator.chests, chestCount, team);
        int shrines = scatter(InteractablesGenerator.shrines, shrineCount, team);
        return "Placed " + ratio(chests, chestCount) + " chests, " + ratio(shrines, shrineCount)
                + " shrines; " + tpResult + "; teleporter boss: " + boss.localizedName + " (" + bossStyleLabel() + ")";
    }

    static int scatter(Seq<InteractablesGenerator.SpawnEntry> list, int count, Team team) {
        if (list.isEmpty()) return 0;
        int made = 0;
        for (int i = 0; i < count; i++) {
            Block type = InteractablesGenerator.roll(list);
            if (type != null && put(type, team)) made++;
        }
        return made;
    }

    static boolean put(Block block, Team team) {
        Tile tile = findSpot(block);
        if (tile == null) return false;
        tile.setNet(block, team, 0);
        placed.add(new Placed(tile, block));
        return true;
    }

    static Tile findSpot(Block block) {
        int w = Vars.world.width();
        int h = Vars.world.height();
        int margin = InteractablesGenerator.marginTiles;

        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
            int tx = Mathf.random(margin, Math.max(margin + 1, w - margin - 1));
            int ty = Mathf.random(margin, Math.max(margin + 1, h - margin - 1));
            Tile origin = Vars.world.tile(tx, ty);
            if (origin != null && footprintFree(origin, block)) return origin;
        }
        return null;
    }

    static boolean footprintFree(Tile origin, Block block) {
        int o = block.sizeOffset;
        for (int dx = 0; dx < block.size; dx++) {
            for (int dy = 0; dy < block.size; dy++) {
                Tile t = Vars.world.tile(origin.x + dx + o, origin.y + dy + o);
                if (t == null || t.solid() || t.block() != Blocks.air) return false;
                if (!t.floor().placeableOn || t.floor().isLiquid) return false;
            }
        }
        return true;
    }

    static boolean teleporterReady() {
        for (Building b : Groups.build) {
            if (b instanceof Teleporter.TeleporterBuild tb
                    && (tb.phase == Teleporter.Phase.ready || tb.phase == Teleporter.Phase.done)) {
                return true;
            }
        }
        return false;
    }

    static Seq<UnitType> pool() {
        Seq<UnitType> pool = EnemySpawnDirector.currentPool();
        if (pool.any()) return pool;
        if (fallbackPool.isEmpty()) fallbackPool.add(UnitTypes.dagger, UnitTypes.flare);
        return fallbackPool;
    }

    static void update() {
        if (!active || Vars.world == null || !Vars.state.isGame() || Vars.state.isPaused()) return;

        time += Time.delta;
        if (spawnsEnabled && teleporterReady()) spawnsEnabled = false;
        if (!spawnsEnabled) return;

        float interval = EnemySpawnDirector.baseInterval / (1f + (time / 3600f) * 0.2f);
        timer += Time.delta;
        if (timer < interval) return;
        timer = 0f;

        Team enemy = Vars.state.rules.waveTeam;
        int alive = 0;
        for (Unit u : Groups.unit) {
            if (u.team == enemy && u.isValid()) alive++;
        }
        if (alive >= EnemySpawnDirector.maxAlive) return;

        Tile tile = EnemySpawnDirector.randomTile();
        if (tile == null) return;

        Unit u = pool().random().spawn(enemy, tile.worldx(), tile.worldy());
        if (u == null) return;

        HuntHeroAI.apply(u);
        u.apply(StatusEffects.disarmed, 100);
        u.apply(StatusEffects.unmoving, 45);
        int minutes = Math.max(0, (int) (time / 3600f));
        float mul = (float) Math.pow(RunState.ENEMY_SCALE_PER_MINUTE, minutes) + EnemySpawnDirector.applyArchBuff(u);
        u.maxHealth(u.maxHealth * mul);
        u.health(u.maxHealth);
    }
}
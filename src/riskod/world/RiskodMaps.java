package riskod.world;

import arc.Core;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.content.UnitTypes;
import mindustry.core.GameState;
import mindustry.game.Saves;
import mindustry.type.SectorPreset;
import mindustry.type.UnitType;
import riskod.content.RiskodCont;
import riskod.world.block.Teleporter;
import riskod.world.relic.RelicType;
import riskod.world.run.RunState;

import java.util.Objects;

import static mindustry.Vars.content;
import static mindustry.Vars.control;
import static riskod.world.run.RunState.clear;

/**
 * Premade maps via SectorPreset.
 * Stages: 1–3 easy, 4–6 hard, 7 launch, 8 moon, 9+ victory.
 */
public class RiskodMaps {
    public static final Seq<RiskodSector> easy = new Seq<>();
    public static final Seq<RiskodSector> hard = new Seq<>();
    public static RiskodSector launch;
    public static RiskodSector moon;

    public static void load() {
        addEasy("idk", 0).unfinished = true;

        RiskodSector fortress = addEasy("riskod-abandoned-fortess", 1)
                .chests(4, 10)
                .shrines(1, 3)
                .teleporter((Teleporter) RiskodCont.teleporter, UnitTypes.mace, UnitTypes.fortress)
                .teleporterCharge(60f * 25f, 80f)
                .teleporterBossCharge(0.35f)
                .enemies(RiskodCont.grunt, RiskodCont.thief)
                .addBossRelic(RiskodCont.speedCharm)
                .addRareBossRelic(RiskodCont.mendGear);

        fortress.rareBossRelicChance = 0.2f;
        fortress.difficulty = 2;

        RiskodSector monument = addEasy("riskod-smoldering-monument", 2)
                .chests(16, 28)
                .shrines(1, 2)
                .teleporter((Teleporter) RiskodCont.teleporter)
                .teleporterCharge(60f * 25f, 80f)
                .teleporterBossCharge(0.35f)
                .bosses(UnitTypes.pulsar, UnitTypes.zenith)
                .enemies(RiskodCont.grunt, RiskodCont.thief)
                .addRareBossRelic(RiskodCont.mendGear);

        monument.rareBossRelicChance = 0.2f;
        monument.difficulty = 5;


        addEasy("idk-1", 3).unfinished = true;

        addHard("idk-2", 4)
                .chests(8, 16)
                .shrines(2, 4).unfinished = true;
        addHard("idk-3", 5).unfinished = true;
        addHard("idk-4", 6).unfinished = true;

        launch = add("idk-5", RiskodPlanet.riskod, 60, Pool.launch);
        launch.alwaysUnlocked = false;
        launch.unfinished = true;
        launch.chests(6, 10).shrines(2, 3);

        moon = add("idk-6", RiskodPlanet.riskodMoon, 0, Pool.moon);
        moon.isLastSector = true;
        launch.unfinished = true;
        moon.alwaysUnlocked = false;
        moon.chests(10, 18).shrines(3, 6);

        refreshStartUnlocks();
    }

    public static RiskodSector addEasy(String name, int sectorId) {
        RiskodSector s = add(name, RiskodPlanet.riskod, sectorId, Pool.easy);
        easy.add(s);
        s.alwaysUnlocked = false;
        return s;
    }

    public static RiskodSector addHard(String name, int sectorId) {
        RiskodSector s = add(name, RiskodPlanet.riskod, sectorId, Pool.hard);
        hard.add(s);
        s.alwaysUnlocked = false;
        return s;
    }



    /** Only the hub is manually unlockable; random starts use playSector. */
    public static void refreshStartUnlocks() {
        for (RiskodSector s : easy) {
            s.alwaysUnlocked = (s.sector != null && s.sector.id == 0);
        }
        for (RiskodSector s : hard) s.alwaysUnlocked = false;
        if (launch != null) launch.alwaysUnlocked = false;
        if (moon != null) moon.alwaysUnlocked = false;
    }

    static RiskodSector unused(Seq<RiskodSector> pool) {
        Seq<RiskodSector> left = pool.select(s ->
                s != null
                        && !s.unfinished
                        && (RunState.current == null || !RunState.usedMaps.contains(s.name)));
        if (left.isEmpty()) return null; // do NOT reuse
        return left.random();
    }

    static RiskodSector pick(int stage) {
        if (stage >= 1 && stage <= 3) return unused(easy);
        if (stage >= 4 && stage <= 6) return unused(hard);
        if (stage == 7) return (launch != null && !launch.unfinished) ? launch : null;
        if (stage == 8) return (moon != null && !moon.unfinished) ? moon : null;
        return null;
    }

    public static void playNext() {
        if (!RunState.active()) return;

        int stage = RunState.current.stage;
        if (stage >= 9) {
            RunState.current.onVictory();
            return;
        }

        RiskodSector next = pick(stage);
        if (next == null || next.unfinished || next.sector == null) {
            Vars.ui.showInfoToast("No more maps ready — you win for now!", 8f);
            RunState.current.onVictory();
            return;
        }

        if (!RunState.usedMaps.contains(next.name)) {
            RunState.usedMaps.add(next.name);
        }
        next.alwaysUnlocked = true;

        Core.app.post(() -> {
            if (Vars.ui != null && Vars.ui.planet != null) {
                Vars.ui.planet.hide();
            }
            Vars.control.playSector(next.sector);
        });
    }

    /** Call after RunState.start(hero) — loads a random stage-1 map. */
    public static void playRunStart() {
        if (!RunState.active()) return;

        RiskodSector next = pick(1);
        if (next == null || next.unfinished || next.sector == null) {
            Log.warn("Riskod: no start map");
            return;
        }

        if (!RunState.usedMaps.contains(next.name)) {
            RunState.usedMaps.add(next.name);
        }
        next.alwaysUnlocked = true;

        Core.app.post(() -> {
            if (Vars.ui != null && Vars.ui.planet != null) {
                Vars.ui.planet.hide();
            }
            Vars.control.playSector(next.sector);
        });
    }

    static RiskodSector add(String name, mindustry.type.Planet planet, int sectorId, Pool pool) {
        return new RiskodSector(name, planet, sectorId, pool) {{
            captureWave = 0;
            addStartingItems = false;
            allowLaunchLoadout = false;
            allowLaunchSchematics = false;
            overrideLaunchDefaults = true;
            requireUnlock = false;
            difficulty = pool == Pool.easy ? 2 : pool == Pool.hard ? 6 : pool == Pool.launch ? 8 : 10;
        }};
    }

    public static RiskodSector currentPreset() {
        if (Vars.state == null || Vars.state.rules == null || Vars.state.rules.sector == null) return null;
        var p = Vars.state.rules.sector.preset;
        return p instanceof RiskodSector r ? r : null;
    }

    public static void applyTeleporter(Teleporter.TeleporterBuild build) {
        if (build == null) return;
        RiskodSector preset = currentPreset();
        if (preset == null) return;

        if (preset.bossPool.any()) {
            build.localBosses.set(preset.bossPool);
        }
        if (preset.bossRelicPool.any()) {
            build.localRelics.set(preset.bossRelicPool);
        }
        if (preset.rareBossRelicPool.any()) {
            build.localRareRelics.set(preset.rareBossRelicPool);
        }
        if (preset.teleporterChargeTime > 0f) build.localChargeTime = preset.teleporterChargeTime;
        if (preset.teleporterChargeRadius > 0f) build.localChargeRadius = preset.teleporterChargeRadius;
        if (preset.teleporterBossChargeMul > 0f) build.localBossChargeMul = preset.teleporterBossChargeMul;
        if (preset.rareBossRelicChance >= 0f) build.localRareChance = preset.rareBossRelicChance;
    }

    ///spent an actually annoying amount of time and vanilla code diving to figure this out
    public static void exitRun(){
        clear(); Vars.logic.reset(); Vars.state.set(GameState.State.menu);
        if(Vars.ui != null && Vars.ui.planet != null){
            Vars.ui.planet.launchSector = null; Vars.ui.planet.selected = null;
        }
        for(var planet : content.planets()){
            if(planet != RiskodPlanet.riskod && planet != RiskodPlanet.riskodMoon) continue;
            planet.clearStats(); boolean any = false;
            for(var sec : planet.sectors){
                sec.clearInfo();
                if(sec.save != null){
                    any = true; sec.clearInfo();
                    sec.save.delete(); sec.save = null;
                }
            }
            if(any){
                planet.reloadMeshAsync();
            }
        }
        for(var slot : control.saves.getSaveSlots().copy()){
            if(slot.isSector() && (slot.getSector().planet == RiskodPlanet.riskod || slot.getSector().planet == RiskodPlanet.riskodMoon)){
                slot.delete();
            }
        }
        for(RiskodSector sector : easy){
            sector.alwaysUnlocked = false;
            sector.clearUnlock();
            if(sector.sector != null && sector.sector.id == 0) sector.alwaysUnlocked = true;
        }
        for(RiskodSector sector : hard){

            sector.alwaysUnlocked = false;
            sector.clearUnlock();
        }
        launch.alwaysUnlocked = false;
        launch.clearUnlock();
        moon.alwaysUnlocked = false;
        moon.clearUnlock();
        refreshStartUnlocks();
    }

    public enum Pool {
        easy, hard, launch, moon
    }

    public static class RiskodSector extends SectorPreset {
        public Pool pool;
        public Seq<UnitType> enemyPool = new Seq<>();
        public Seq<UnitType> bossPool = new Seq<>();
        public Seq<RelicType> bossRelicPool = new Seq<>();
        public Seq<RelicType> rareBossRelicPool = new Seq<>();
        public float rareBossRelicChance = -1f;
        public boolean unfinished = false;

        public int minChests = -1, maxChests = -1;
        public int minShrines = -1, maxShrines = -1;
        public boolean spawnTeleporter = true;
        public Teleporter teleporterBlock;
        public float teleporterChargeTime = -1f;
        public float teleporterChargeRadius = -1f;
        public float teleporterBossChargeMul = -1f;

        public RiskodSector(String name, mindustry.type.Planet planet, int sector, Pool pool) {
            super(name, planet, sector);
            this.pool = pool;
        }

        public RiskodSector addBoss(UnitType type) {
            if (type != null) bossPool.add(type);
            return this;
        }

        public RiskodSector addEnemy(UnitType type) {
            if (type != null) enemyPool.add(type);
            return this;
        }

        public RiskodSector enemies(UnitType... types) {
            for (UnitType t : types) addEnemy(t);
            return this;
        }

        public RiskodSector bosses(UnitType... types) {
            for (UnitType t : types) addBoss(t);
            return this;
        }

        public RiskodSector addBossRelic(RelicType relic) {
            if (relic != null) bossRelicPool.add(relic);
            return this;
        }

        public RiskodSector addRareBossRelic(RelicType relic) {
            if (relic != null) rareBossRelicPool.add(relic);
            return this;
        }

        public RiskodSector chests(int min, int max) {
            minChests = min;
            maxChests = max;
            return this;
        }

        public RiskodSector shrines(int min, int max) {
            minShrines = min;
            maxShrines = max;
            return this;
        }

        public RiskodSector placeTeleporter(boolean spawn) {
            spawnTeleporter = spawn;
            return this;
        }

        public RiskodSector teleporter(boolean spawn) {
            return placeTeleporter(spawn);
        }

        public RiskodSector teleporter(Teleporter block) {
            teleporterBlock = block;
            spawnTeleporter = block != null;
            return this;
        }

        public RiskodSector teleporter(Teleporter block, UnitType... bosses) {
            teleporter(block);
            for (UnitType u : bosses) addBoss(u);
            return this;
        }

        public RiskodSector teleporterCharge(float time, float radius) {
            teleporterChargeTime = time;
            teleporterChargeRadius = radius;
            return this;
        }

        public RiskodSector teleporterBossCharge(float mul) {
            teleporterBossChargeMul = mul;
            return this;
        }
    }
}
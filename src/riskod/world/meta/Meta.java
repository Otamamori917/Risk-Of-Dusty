package riskod.world.meta;

import arc.files.Fi;
import arc.func.Prov;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Time;
import arc.util.serialization.Json;
import arc.util.serialization.JsonWriter;
import mindustry.Vars;
import riskod.world.RiskodMaps;
import riskod.world.RiskodMaps.RiskodSector;
import riskod.world.run.RunState;

/** Persistent meta-progression: logbook unlocks, lifetime stats and run history, stored as JSON in the data directory. */
public class Meta {

    public static class RelicStat {
        public int found;
        public int maxStack;
    }

    public static class EnemyStat {
        public int kills;
        public int deaths;
    }

    public static class MapStat {
        public int escapes;
        public int shrines;
        public int droneChests;
        public int relicChests;
    }

    public static class HeroStat {
        public int runs;
        public int wins;
        public int highestLevel;
        public float timePlayed;
    }

    public static class Totals {
        public float damageDealt;
        public float damageReceived;
        public float healingReceived;
        public float xpGained;
        public float timePlayed;
        public float bestWinTime;
        public int itemsObtained;
        public int relicsObtained;
        public int enemiesKilled;
        public int bossesKilled;
        public int shrines;
        public int relicChests;
        public int droneChests;
        public int escapes;
        public int runsWon;
        public int runsLost;
    }

    public static class RunRecord {
        public boolean win;
        public String hero = "";
        public String killedBy;
        public long date;
        public int stage;
        public int level;
        public int kills;
        public int bossesKilled;
        public int itemsObtained;
        public int relicsObtained;
        public float time;
        public float damageDealt;
        public float damageReceived;
        public float healingReceived;
        public String[] relics = new String[0];
    }

    public static class Data {
        public ObjectMap<String, RelicStat> relics = new ObjectMap<>();
        public ObjectMap<String, EnemyStat> enemies = new ObjectMap<>();
        public ObjectMap<String, MapStat> maps = new ObjectMap<>();
        public ObjectMap<String, HeroStat> heroes = new ObjectMap<>();
        public Totals totals = new Totals();
        public Seq<RunRecord> history = new Seq<>();
    }

    /// Ticks between disk writes while data keeps changing.
    static final float FLUSH_TICKS = 300f;

    static Data data;
    static boolean dirty;
    static float flushTimer;

    public static Data data() {
        if (data == null) load();
        return data;
    }

    public static RelicStat relic(String name) {
        return data().relics.get(name);
    }

    public static EnemyStat enemy(String name) {
        return data().enemies.get(name);
    }

    public static MapStat map(String name) {
        return data().maps.get(name);
    }

    public static HeroStat hero(String name) {
        return data().heroes.get(name);
    }

    static Fi file() {
        return Vars.dataDirectory.child("riskod-meta.json");
    }

    static Json json() {
        Json j = new Json();
        j.setOutputType(JsonWriter.OutputType.json);
        j.setIgnoreUnknownFields(true);
        j.setElementType(Data.class, "relics", RelicStat.class);
        j.setElementType(Data.class, "enemies", EnemyStat.class);
        j.setElementType(Data.class, "maps", MapStat.class);
        j.setElementType(Data.class, "heroes", HeroStat.class);
        j.setElementType(Data.class, "history", RunRecord.class);
        return j;
    }

    static void load() {
        data = new Data();
        Fi f = file();
        if (!f.exists()) return;
        try {
            Data d = json().fromJson(Data.class, f);
            if (d != null) data = d;
        } catch (Throwable t) {
            Log.err("Riskod: could not read " + f.name() + ", keeping a backup copy", t);
            try {
                f.copyTo(f.sibling(f.name() + ".bak"));
            } catch (Throwable ignored) {
            }
        }
    }

    public static void save() {
        if (data == null) return;
        try {
            file().writeString(json().toJson(data));
            dirty = false;
        } catch (Throwable t) {
            Log.err("Riskod: could not write meta file", t);
        }
    }

    public static void flush() {
        flushTimer = 0f;
        if (dirty) save();
    }

    public static void update() {
        if (!dirty) return;
        flushTimer += Time.delta;
        if (flushTimer >= FLUSH_TICKS) flush();
    }

    static <T> T entry(ObjectMap<String, T> map, String key, Prov<T> make) {
        T v = map.get(key);
        if (v == null) {
            v = make.get();
            map.put(key, v);
        }
        return v;
    }

    static MapStat currentMap() {
        if (!RunState.active()) return null;
        RiskodSector p = RiskodMaps.currentPreset();
        if (p == null) return null;
        return entry(data().maps, p.name, MapStat::new);
    }

    public static void relicObtained(String name, int stack) {
        Data d = data();
        RelicStat s = entry(d.relics, name, RelicStat::new);
        s.found++;
        s.maxStack = Math.max(s.maxStack, stack);
        d.totals.relicsObtained++;
        dirty = true;
    }

    public static void enemyKilled(String name, boolean boss) {
        Data d = data();
        entry(d.enemies, name, EnemyStat::new).kills++;
        d.totals.enemiesKilled++;
        if (boss) d.totals.bossesKilled++;
        dirty = true;
    }

    public static void shrineActivated() {
        MapStat m = currentMap();
        if (m == null) return;
        m.shrines++;
        data().totals.shrines++;
        dirty = true;
    }

    public static void relicChestOpened() {
        MapStat m = currentMap();
        if (m == null) return;
        m.relicChests++;
        data().totals.relicChests++;
        dirty = true;
    }

    public static void droneChestOpened() {
        MapStat m = currentMap();
        if (m == null) return;
        m.droneChests++;
        data().totals.droneChests++;
        dirty = true;
    }

    public static void mapEscape() {
        MapStat m = currentMap();
        if (m == null) return;
        m.escapes++;
        data().totals.escapes++;
        dirty = true;
    }

    public static void damageDealt(float amount) {
        data().totals.damageDealt += amount;
        dirty = true;
    }

    public static void damageReceived(float amount) {
        data().totals.damageReceived += amount;
        dirty = true;
    }

    public static void healingReceived(float amount) {
        data().totals.healingReceived += amount;
        dirty = true;
    }

    public static void itemsObtained(int amount) {
        data().totals.itemsObtained += amount;
        dirty = true;
    }

    public static void xpGained(float amount) {
        data().totals.xpGained += amount;
        dirty = true;
    }

    public static void heroTime(String hero, float ticks) {
        Data d = data();
        d.totals.timePlayed += ticks;
        entry(d.heroes, hero, HeroStat::new).timePlayed += ticks;
        dirty = true;
    }


    public static void reset() {
        data = new Data();
        flushTimer = 0f;
        dirty = true;
        save();
    }

    public static void heroLevel(String hero, int level) {
        HeroStat h = entry(data().heroes, hero, HeroStat::new);
        if (level > h.highestLevel) {
            h.highestLevel = level;
            dirty = true;
        }
    }

    public static void runFinished(RunState run, boolean win) {
        if (run == null || run.recorded) return;
        run.recorded = true;

        Data d = data();
        String hero = run.heroType == null ? "" : run.heroType.name;

        HeroStat h = entry(d.heroes, hero, HeroStat::new);
        h.runs++;
        if (win) h.wins++;
        h.highestLevel = Math.max(h.highestLevel, run.level);

        Totals t = d.totals;
        if (win) {
            t.runsWon++;
            if (t.bestWinTime <= 0f || run.runTime < t.bestWinTime) t.bestWinTime = run.runTime;
        } else {
            t.runsLost++;
            if (run.lastHitBy != null) entry(d.enemies, run.lastHitBy, EnemyStat::new).deaths++;
        }

        RunRecord r = new RunRecord();
        r.win = win;
        r.hero = hero;
        r.killedBy = win ? null : run.lastHitBy;
        r.date = Time.millis();
        r.stage = run.stage;
        r.level = run.level;
        r.kills = run.kills;
        r.bossesKilled = run.bossesKilled;
        r.itemsObtained = run.itemsObtained;
        r.relicsObtained = run.relicsObtained;
        r.time = run.runTime;
        r.damageDealt = run.damageDealt;
        r.damageReceived = run.damageReceived;
        r.healingReceived = run.healingReceived;
        r.relics = run.relicNames.toArray(String.class);
        d.history.add(r);

        dirty = true;
        save();
    }
}
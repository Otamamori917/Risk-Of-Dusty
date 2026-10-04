package riskod.world.run;

import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.UnitType;
import riskod.world.RiskodPlanet;
import riskod.world.abilites.KitSwapAbility;
import riskod.world.defect.DefectState;
import riskod.world.defect.DefectUnitType;
import riskod.world.meta.Meta;
import riskod.world.relic.RelicType;
import riskod.world.ui.RunStatsUi;
import riskod.world.unit.PlayerCharUnitType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static arc.Core.settings;

public class RunState {
    public static RunState current;

    public UnitType heroType;
    public int level = 1;
    public float xp;
    public float xpToNext = XP_BASE;

    public int stage = 1;
    public float runTime;
    public boolean runActive;
    public boolean runOver;

    public int teleporterMult = 1;
    public boolean teleporterFullyCharged;
    public boolean spawnsEnabled = true;

    public final Seq<RelicType> carriedRelics = new Seq<>();
    public final Seq<RelicType> logbook = new Seq<>();
    public static final Seq<String> usedMaps = new Seq<>();

    public PlayerLoadout pendingLoadout;

    public float damageDealt;
    public int kills;
    public int bossesKilled;
    public float damageReceived;
    public float healingReceived;
    public int itemsObtained;
    public int relicsObtained;
    public final Seq<String> relicNames = new Seq<>();

    /** Enemy unit type that last damaged the hero, credited when the run ends in a loss. */
    public String lastHitBy;
    /** Set once the run has been written to the history so it is never recorded twice. */
    public boolean recorded;

    /** Ticks since the run was last written into the sector save's rules tags. */
    float persistTimer;

    public static final float LEVEL_STAT_PER_LEVEL = 0.04f;
    public static final float ENEMY_SCALE_PER_MINUTE = 1.1f;
    public static final float ITEM_XP_MULT = 2f;
    public static final float XP_BASE = 100f;
    public static final float XP_GROWTH = 1.4f;

    static final String K_HERO = "riskod.hero", K_STAGE = "riskod.stage", K_LEVEL = "riskod.level",
            K_XP = "riskod.xp", K_TIME = "riskod.time", K_KILLS = "riskod.kills", K_BOSSES = "riskod.bosses",
            K_DAMAGE = "riskod.damage", K_TMULT = "riskod.tmult", K_CHARGED = "riskod.charged",
            K_SPAWNS = "riskod.spawns", K_USED = "riskod.used", K_LOGBOOK = "riskod.logbook",
            K_LOADOUT = "riskod.loadout", K_DMGIN = "riskod.dmgin", K_HEAL = "riskod.heal",
            K_ITEMS = "riskod.items", K_RELICS = "riskod.relics", K_RELICNAMES = "riskod.relicnames";
    static final String[] KEYS = {K_HERO, K_STAGE, K_LEVEL, K_XP, K_TIME, K_KILLS, K_BOSSES, K_DAMAGE,
            K_TMULT, K_CHARGED, K_SPAWNS, K_USED, K_LOGBOOK, K_LOADOUT, K_DMGIN, K_HEAL, K_ITEMS, K_RELICS, K_RELICNAMES};

    static final String RUN_MAP_SETTING = "riskod-run-map";

    public static RunState start(UnitType hero) {
        KitSwapAbility.clearAll();
        DefectState.clearAll();
        current = new RunState();
        current.heroType = hero;
        current.runActive = true;
        current.runOver = false;
        current.level = 1;
        current.xp = 0f;
        current.xpToNext = XP_BASE;
        current.stage = 1;
        current.runTime = 0f;
        current.spawnsEnabled = true;
        current.teleporterFullyCharged = false;
        current.teleporterMult = 1;
        return current;
    }

    public static void clear() {
        KitSwapAbility.clearAll();
        DefectState.clearAll();
        current = null;
        usedMaps.clear();
        settings.remove(RUN_MAP_SETTING);
    }

    public static boolean active() {
        return current != null && current.runActive && !current.runOver && RiskodPlanet.onRiskod();
    }

    /** Name of the map the run is currently on, or null when no run is in progress. */
    public static String runMap() {
        if (current != null && current.runActive && !current.runOver && usedMaps.any()) {
            return usedMaps.peek();
        }
        return settings.getString(RUN_MAP_SETTING, null);
    }

    public static int teleporterMult() {
        return current == null ? 1 : Math.max(1, current.teleporterMult);
    }

    public void update() {
        if (!active()) return;
        runTime += Time.delta;
        if (heroType != null) Meta.heroTime(heroType.name, Time.delta);
        persistTimer += Time.delta;
        if (persistTimer >= 60f) {
            persistTimer = 0f;
            persist();
        }
    }

    public static void persist() {
        RunState r = current;
        if (r == null || !active() || r.heroType == null) return;
        if (Vars.state == null || Vars.state.rules == null) return;

        var t = Vars.state.rules.tags;
        t.put(K_HERO, r.heroType.name);
        t.put(K_STAGE, String.valueOf(r.stage));
        t.put(K_LEVEL, String.valueOf(r.level));
        t.put(K_XP, String.valueOf(r.xp));
        t.put(K_TIME, String.valueOf(r.runTime));
        t.put(K_KILLS, String.valueOf(r.kills));
        t.put(K_BOSSES, String.valueOf(r.bossesKilled));
        t.put(K_DAMAGE, String.valueOf(r.damageDealt));
        t.put(K_TMULT, String.valueOf(r.teleporterMult));
        t.put(K_CHARGED, r.teleporterFullyCharged ? "1" : "0");
        t.put(K_SPAWNS, r.spawnsEnabled ? "1" : "0");
        t.put(K_USED, usedMaps.toString(","));
        t.put(K_LOGBOOK, r.logbook.toString(",", x -> x.name));
        t.put(K_DMGIN, String.valueOf(r.damageReceived));
        t.put(K_HEAL, String.valueOf(r.healingReceived));
        t.put(K_ITEMS, String.valueOf(r.itemsObtained));
        t.put(K_RELICS, String.valueOf(r.relicsObtained));
        t.put(K_RELICNAMES, r.relicNames.toString(","));

        PlayerLoadout l = r.pendingLoadout;
        if (l == null && Vars.player != null) {
            Unit u = Vars.player.unit();
            if (u != null && u.isValid() && u.type instanceof PlayerCharUnitType) {
                l = PlayerCharUnitType.loadout(u);
            }
        }
        if (l != null) t.put(K_LOADOUT, encode(l));

        if (usedMaps.any()) {
            String map = usedMaps.peek();
            if (!map.equals(settings.getString(RUN_MAP_SETTING, null))) {
                settings.put(RUN_MAP_SETTING, map);
            }
        }
    }

    public static void clearPersist() {
        if (Vars.state != null && Vars.state.rules != null) {
            for (String k : KEYS) Vars.state.rules.tags.remove(k);
        }
        settings.remove(RUN_MAP_SETTING);
    }

    /** Rebuilds the run from the loaded sector's rules tags. Returns false when the sector holds no run. */
    public static boolean restore() {
        if (current != null) return false;
        if (Vars.state == null || Vars.state.rules == null || !RiskodPlanet.onRiskod()) return false;

        var t = Vars.state.rules.tags;
        String heroName = t.get(K_HERO);
        if (heroName == null) return false;
        UnitType hero = Vars.content.unit(heroName);
        if (hero == null) return false;

        RunState r = new RunState();
        r.heroType = hero;
        r.runActive = true;
        r.runOver = false;
        r.stage = Strings.parseInt(t.get(K_STAGE, "1"), 1);
        r.level = Strings.parseInt(t.get(K_LEVEL, "1"), 1);
        r.xp = Strings.parseFloat(t.get(K_XP, "0"), 0f);
        r.xpToNext = XP_BASE * (float) Math.pow(XP_GROWTH, r.level - 1);
        r.runTime = Strings.parseFloat(t.get(K_TIME, "0"), 0f);
        r.kills = Strings.parseInt(t.get(K_KILLS, "0"), 0);
        r.bossesKilled = Strings.parseInt(t.get(K_BOSSES, "0"), 0);
        r.damageDealt = Strings.parseFloat(t.get(K_DAMAGE, "0"), 0f);
        r.teleporterMult = Strings.parseInt(t.get(K_TMULT, "1"), 1);
        r.teleporterFullyCharged = "1".equals(t.get(K_CHARGED, "0"));
        r.spawnsEnabled = "1".equals(t.get(K_SPAWNS, "1"));
        r.damageReceived = Strings.parseFloat(t.get(K_DMGIN, "0"), 0f);
        r.healingReceived = Strings.parseFloat(t.get(K_HEAL, "0"), 0f);
        r.itemsObtained = Strings.parseInt(t.get(K_ITEMS, "0"), 0);
        r.relicsObtained = Strings.parseInt(t.get(K_RELICS, "0"), 0);
        String names = t.get(K_RELICNAMES, "");
        if (!names.isEmpty()) {
            for (String s : names.split(",")) {
                if (!s.isEmpty()) r.relicNames.add(s);
            }
        }
        current = r;

        usedMaps.clear();
        String used = t.get(K_USED, "");
        if (!used.isEmpty()) {
            for (String s : used.split(",")) {
                if (!s.isEmpty()) usedMaps.add(s);
            }
        }

        String book = t.get(K_LOGBOOK, "");
        if (!book.isEmpty()) {
            for (String s : book.split(",")) {
                RelicType relic = PlayerLoadout.find(s);
                if (relic != null && !r.logbook.contains(relic)) r.logbook.add(relic);
            }
        }

        String lo = t.get(K_LOADOUT, "");
        if (!lo.isEmpty()) {
            try {
                r.pendingLoadout = decode(lo);
            } catch (Throwable ignored) {
                r.pendingLoadout = null;
            }
        }
        return true;
    }

    static String encode(PlayerLoadout l) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        l.write(new Writes(new DataOutputStream(bout)));
        byte[] bytes = bout.toByteArray();
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            s.append(Character.forDigit((b >> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
        }
        return s.toString();
    }

    static PlayerLoadout decode(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Character.digit(hex.charAt(i * 2), 16) << 4) | Character.digit(hex.charAt(i * 2 + 1), 16));
        }
        PlayerLoadout l = new PlayerLoadout();
        l.read(new Reads(new DataInputStream(new ByteArrayInputStream(bytes))));
        return l;
    }

    public float levelBonus() {
        return 1f + LEVEL_STAT_PER_LEVEL * (level - 1);
    }

    public float enemyStatMul() {
        int minutes = Math.max(0, (int) (runTime / 3600f));
        return (float) Math.pow(ENEMY_SCALE_PER_MINUTE, minutes);
    }

    public float spawnRateMul() {
        float stageMul = 1f + (stage - 1) * 0.15f;
        float timeMul = 1f + (runTime / 3600f) * 0.2f;
        return stageMul * timeMul;
    }

    public void addXp(float amount) {
        if (!active() || amount <= 0f) return;
        xp += amount;
        Meta.xpGained(amount);
        while (xp >= xpToNext) {
            xp -= xpToNext;
            level++;
            xpToNext = XP_BASE * (float) Math.pow(XP_GROWTH, level - 1);
        }
        if (heroType != null) Meta.heroLevel(heroType.name, level);
    }

    public void addKillXp(boolean boss) {
        if (!active()) return;
        kills++;
        if (boss) {
            bossesKilled++;
            addXp(80f + stage * 20f);
        } else {
            addXp(5f + stage * 1.5f);
        }
    }

    public void unlockLogbook(RelicType relic) {
        if (!active()) return;
        if (relic != null && !logbook.contains(relic)) {
            logbook.add(relic);
        }
    }

    public void noteRelic(RelicType relic, int stack) {
        if (!active()) return;
        relicsObtained++;
        relicNames.add(relic.name);
        Meta.relicObtained(relic.name, stack);
    }

    public void noteDamageDealt(float amount) {
        if (!active()) return;
        damageDealt += amount;
        Meta.damageDealt(amount);
    }

    public void noteDamageReceived(float amount) {
        if (!active()) return;
        damageReceived += amount;
        Meta.damageReceived(amount);
    }

    public void noteHealing(float amount) {
        if (!active()) return;
        healingReceived += amount;
        Meta.healingReceived(amount);
    }

    public void noteItems(int amount) {
        if (!active()) return;
        itemsObtained += amount;
        Meta.itemsObtained(amount);
    }

    public void convertCoreItemsToXp(mindustry.game.Team team) {
        if (!active()) return;
        if (team == null || team.core() == null || team.core().items == null) return;
        float total = 0f;
        for (Item item : Vars.content.items()) {
            if (item == null) continue;
            int n = team.core().items.get(item);
            if (n > 0) {
                total += n * ITEM_XP_MULT;
                team.core().items.set(item, 0);
            }
        }
        addXp(total);
    }

    public void onHeroDeath(Unit unit) {
        runActive = false;
        runOver = true;
        spawnsEnabled = false;
        KitSwapAbility.clearAll();
        DefectState.clearAll();
        Meta.runFinished(this, false);
        clearPersist();
        RunStatsUi.show(this, false);
    }

    public void onVictory() {
        runActive = false;
        runOver = true;
        spawnsEnabled = false;
        KitSwapAbility.clearAll();
        DefectState.clearAll();
        Meta.runFinished(this, true);
        clearPersist();
        RunStatsUi.show(this, true);
    }

    public void onTeleporterFullyCharged() {
        teleporterFullyCharged = true;
        spawnsEnabled = false;
    }

    public void prepareNextSector() {
        stage++;
        teleporterMult = 1;
        teleporterFullyCharged = false;
        spawnsEnabled = true;
        DefectUnitType.onSectorAdvance();
    }

    public void applyLevelToLoadout(PlayerLoadout loadout) {
        if (loadout == null) return;
        float lb = levelBonus();
        loadout.speedMul *= lb;
        loadout.healthMul *= lb;
        for (int i = 0; i < PlayerLoadout.FLEX_SLOTS; i++) {
            loadout.slotDamageMul[i] *= lb;
            loadout.slotRangeMul[i] *= lb;
        }
    }
}
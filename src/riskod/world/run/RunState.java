package riskod.world.run;

import arc.Core;
import arc.math.Mathf;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.core.GameState;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.Sector;
import mindustry.type.UnitType;
import riskod.world.RiskodPlanet;
import riskod.world.abilites.KitSwapAbility;
import riskod.world.block.Teleporter;
import riskod.world.defect.DefectState;
import riskod.world.defect.DefectUnitType;
import riskod.world.meta.Meta;
import riskod.world.relic.GearType;
import riskod.world.relic.RelicType;
import riskod.world.ui.RelicPickupToast;
import riskod.world.ui.RunStatsUi;
import riskod.world.unit.ChantDroneType;
import riskod.world.unit.CompanionDroneType;
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

    public static PlayerLoadout pendingLoadout;

    public float damageDealt;
    public int kills;
    public int bossesKilled;
    public float damageReceived;
    public float healingReceived;
    public int itemsObtained;
    public int relicsObtained;
    public int peakDrones;
    public final Seq<String> relicNames = new Seq<>();

    /// Shrines activated on the current sector; any paid activation counts, successful or not.
    public int shrinesThisSector;

    /// Shrine count recorded for each equipped (non-passive) chant relic type when it was last given; kept across sectors.
    public final ObjectIntMap<String> chantShrines = new ObjectIntMap<>();

    /// Living companion drones captured when leaving a sector, waiting to be respawned in the next one.
    public final Seq<CarriedDrone> carriedDrones = new Seq<>();

    /// Sector the carried drones were captured in; they respawn once a different sector is loaded.
    Sector carriedFrom;

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

    /// Bonus scale added per shrine counted when a chant relic/drone is given.
    public static final float CHANT_SCALE_PER_SHRINE = 1.25f;

    public static final ObjectSet<String> consumedUniques = new ObjectSet<>();

    static final String K_HERO = "riskod.hero", K_STAGE = "riskod.stage", K_LEVEL = "riskod.level",
            K_XP = "riskod.xp", K_TIME = "riskod.time", K_KILLS = "riskod.kills", K_BOSSES = "riskod.bosses",
            K_DAMAGE = "riskod.damage", K_TMULT = "riskod.tmult", K_CHARGED = "riskod.charged",
            K_SPAWNS = "riskod.spawns", K_USED = "riskod.used", K_LOGBOOK = "riskod.logbook",
            K_LOADOUT = "riskod.loadout", K_DMGIN = "riskod.dmgin", K_HEAL = "riskod.heal",
            K_ITEMS = "riskod.items", K_RELICS = "riskod.relics", K_RELICNAMES = "riskod.relicnames",
            K_SHRINES = "riskod.shrines", K_CHANT = "riskod.chant", K_DRONES = "riskod.chantdrones", K_UNIQUES = "riskod.uniques";
    static final String[] KEYS = {K_HERO, K_STAGE, K_LEVEL, K_XP, K_TIME, K_KILLS, K_BOSSES, K_DAMAGE,
            K_TMULT, K_CHARGED, K_SPAWNS, K_USED, K_LOGBOOK, K_LOADOUT, K_DMGIN, K_HEAL, K_ITEMS, K_RELICS, K_RELICNAMES,
            K_SHRINES, K_CHANT, K_DRONES,K_UNIQUES};

    static final String RUN_MAP_SETTING = "riskod-run-map";

    public static class CarriedDrone {
        public String type;
        public int shrines;
        public String gear;
        public int gearCharges;
        public float gearCd;
    }

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

    /** Bonus scale for an equipped chant relic type: zero until it has been given, then shrines-when-given x 1.25. */
    public static float chantScale(String name) {
        if (current == null || name == null) return 0f;
        return current.chantShrines.get(name, 0) * CHANT_SCALE_PER_SHRINE;
    }

    /** Records the current sector's shrine count for an equipped chant relic type; replaces any earlier record. */
    public void setChantScale(String name) {
        if (name == null) return;
        chantShrines.put(name, shrinesThisSector);
    }

    public void noteShrine() {
        if (!active()) return;
        shrinesThisSector++;
    }

    static boolean heroPresent() {
        if (Vars.player == null) return false;
        Unit u = Vars.player.unit();
        return u != null && u.isValid() && u.type instanceof PlayerCharUnitType;
    }

    void captureDrones() {
        carriedDrones.clear();
        carriedFrom = Vars.state != null && Vars.state.rules != null ? Vars.state.rules.sector : null;
        if (Vars.player == null) return;

        Team team = Vars.player.team();
        Groups.unit.each(u -> {
            if (u.team != team || !u.isValid() || !(u.type instanceof CompanionDroneType)) return;

            CarriedDrone c = new CarriedDrone();
            c.type = u.type.name;
            if (u.type instanceof ChantDroneType) c.shrines = ChantDroneType.shrinesById.get(u.id, 0);

            CompanionDroneType.ActionState s = CompanionDroneType.ActionState.get(u);
            if (s.gear != null) {
                c.gear = s.gear.name;
                c.gearCharges = s.gearCharges;
                c.gearCd = s.gearCd;
            }
            carriedDrones.add(c);
        });
    }

    void spawnCarriedDrones() {
        Unit hero = Vars.player.unit();
        for (CarriedDrone c : carriedDrones) {
            UnitType type = Vars.content.unit(c.type);
            if (type == null) continue;

            Unit u = type.spawn(hero.team, hero.x + Mathf.range(16f), hero.y + Mathf.range(16f));
            if (u == null) continue;

            if (type instanceof ChantDroneType) ChantDroneType.shrinesById.put(u.id, c.shrines);
            if (c.gear != null && PlayerLoadout.find(c.gear) instanceof GearType g) {
                CompanionDroneType.giveGear(u, g);
                CompanionDroneType.ActionState s = CompanionDroneType.ActionState.get(u);
                s.gearCharges = c.gearCharges;
                s.gearCd = c.gearCd;
            }
        }
        carriedDrones.clear();
        carriedFrom = null;
    }

    public void noteDroneCount() {
        if (!active() && !MockRun.active) return;
        if (Vars.player == null) return;
        Team team = Vars.player.team();
        int n = 0;
        for (Unit u : Groups.unit) {
            if (u.team == team && u.isValid() && u.type instanceof CompanionDroneType) n++;
        }
        peakDrones = Math.max(peakDrones, n);
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
        if (carriedDrones.any() && heroPresent() && (carriedFrom == null || Vars.state.rules.sector != carriedFrom)) {
            spawnCarriedDrones();
        }
        noteDroneCount();
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
        t.put(K_SHRINES, String.valueOf(r.shrinesThisSector));
        t.put(K_CHANT, encodeChant(r));
        t.put(K_DRONES, encodeDrones());
        t.put(K_UNIQUES, consumedUniques.toString(","));

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

        r.shrinesThisSector = Strings.parseInt(t.get(K_SHRINES, "0"), 0);
        String chant = t.get(K_CHANT, "");
        if (!chant.isEmpty()) {
            for (String s : chant.split(",")) {
                int i = s.lastIndexOf(':');
                if (i <= 0) continue;
                r.chantShrines.put(s.substring(0, i), Strings.parseInt(s.substring(i + 1), 0));
            }
        }

        String droneValues = t.get(K_DRONES, "");
        if (!droneValues.isEmpty()) {
            for (String s : droneValues.split(",")) {
                int i = s.indexOf(':');
                if (i <= 0) continue;
                int id = Strings.parseInt(s.substring(0, i), -1);
                if (id < 0) continue;
                ChantDroneType.shrinesById.put(id, Strings.parseInt(s.substring(i + 1), 0));
            }
        }
        r.consumedUniques.clear();
        String uniques = t.get(K_UNIQUES, "");
        if (!uniques.isEmpty()) {
            for (String s : uniques.split(",")) {
                if (!s.isEmpty()) r.consumedUniques.add(s);
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

    static String encodeChant(RunState r) {
        StringBuilder s = new StringBuilder();
        for (ObjectIntMap.Entry<String> e : r.chantShrines) {
            if (s.length() > 0) s.append(',');
            s.append(e.key).append(':').append(e.value);
        }
        return s.toString();
    }

    static String encodeDrones() {
        StringBuilder s = new StringBuilder();
        Groups.unit.each(u -> {
            if (!(u.type instanceof ChantDroneType) || !u.isValid()) return;
            if (s.length() > 0) s.append(',');
            s.append(u.id).append(':').append(ChantDroneType.shrinesById.get(u.id, 0));
        });
        return s.toString();
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
        Vars.state.set(GameState.State.paused);
        runActive = false;
        runOver = true;
        spawnsEnabled = false;
        KitSwapAbility.clearAll();
        DefectState.clearAll();
        Meta.runFinished(this, false);
        clearPersist();
        RunStatsUi.show(this, false);
    }

    public static float teleporterChargeFrac() {
        for (var b : Groups.build) {
            if (b instanceof Teleporter.TeleporterBuild tb) {
                return tb.chargeFrac();
            }
        }
        return 0f;
    }

    static void setSpawns(boolean on) {
        if (active()) current.spawnsEnabled = on;
        else if (MockRun.active) MockRun.spawnsEnabled = on;
    }

    public static boolean tryMrBones(Unit unit, PlayerLoadout l) {
        if (!(active() || MockRun.active) || unit == null || l == null) return false;

        RelicType bones = null;
        for (RelicType r : l.passives) {
            if (r.mrBones) { bones = r; break; }
        }
        if (bones == null) return false;
        if (teleporterChargeFrac() < 0.25f) return false;

        unit.apply(StatusEffects.invincible, 5*60);
        unit.apply(StatusEffects.slow, 5*60);
        unit.health = Math.max(unit.maxHealth * 0.05f, 1f);
        PlayerCharUnitType.syncHealth(unit);

        Groups.unit.each(u -> {
            if (u.team == unit.team || !u.isValid()) return;
            if (u.isBoss()) {
                u.apply(StatusEffects.unmoving, 60f * 5f);
                u.apply(StatusEffects.disarmed, 60f * 5f);
            } else {
                u.kill();
            }
        });

        setSpawns(false);
        Time.run(60f * 5f, () -> {
            if (active()) {
                if (!current.teleporterFullyCharged) current.spawnsEnabled = true;
            } else if (MockRun.active && !MockRun.teleporterReady()) {
                MockRun.spawnsEnabled = true;
            }
        });

        l.takePassive(bones);
        consumedUniques.add(bones.name);
        RelicPickupToast.show(RelicPickupToast.ToastChannel.ABILITY, bones.localizedName, "Destroyed", bones.rarity, bones.icon, "");
        return true;
    }

    public void onVictory() {
        Vars.state.set(GameState.State.paused);
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
        captureDrones();
        stage++;
        teleporterMult = 1;
        teleporterFullyCharged = false;
        spawnsEnabled = true;
        shrinesThisSector = 0;
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